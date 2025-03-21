package riscvconsole.system

import chisel3._
import chisel3.util._
import chipsalliance.rocketchip.config._
import freechips.rocketchip.subsystem._
import sifive.blocks.devices.gpio._
import sifive.blocks.devices.uart._
import sifive.blocks.devices.spi._
import sifive.blocks.devices.i2c._
import freechips.rocketchip.devices.tilelink._
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.prci._
import freechips.rocketchip.tilelink.{TLFragmenter, TLRAM}
import riscvconsole.devices.altera.ddr3._
import riscvconsole.devices.codec._
import riscvconsole.devices.fft._
import riscvconsole.devices.sdram._
import riscvconsole.devices.xilinx.artya7ddr._
import riscvconsole.devices.xilinx.nexys4ddr._
import testchipip._
//import riscvconsole.devices.gcd._
import riscvconsole.devices.klein._
import riscvconsole.devices.blake2s._
import riscvconsole.devices.chacha._

case class SRAMConfig
(
  address: BigInt,
  size: BigInt
)
case object SRAMKey extends Field[Seq[SRAMConfig]](Nil)

class RVCSystem(implicit p: Parameters) extends RVCSubsystem
  with HasPeripheryGPIO
  with HasPeripheryUART
  with HasPeripherySPIFlash
  with HasPeripheryI2C
  with HasSDRAM
  with HasQsysDDR3
  with HasArtyA7MIG
  with HasNexys4DDRMIG
  with HasPeripheryCodec
  with HasPeripheryFFT
  with CanHaveMasterAXI4MemPort
  with CanHavePeripheryTLSerial
  //with HasPeripheryGCD
  with HasPeripheryKLEIN
  with HasPeripheryBLAKE2S
  with HasPeripheryCHACHA
{
  val spiDevs = p(PeripherySPIKey).map { ps =>
    SPIAttachParams(ps).attachTo(this)
  }
  val spiNodes = spiDevs.map ( _.ioNode.makeSink() )
  spiDevs.zipWithIndex.foreach { case (ps, i) =>
    val mmc = new MMCDevice(ps.device, 5) // Only the first one is mmc
    ResourceBinding {
      Resource(mmc, "reg").bind(ResourceAddress(0))
    }
  }

  // Add the chosen, for the bootargs to be output in the console at boot
  val chosen = new Device {
    def describe(resources: ResourceBindings): Description = {
      Description("chosen", Map("bootargs" -> Seq(ResourceString("console=hvc0 earlycon=sbi"))))
    }
  }
  ResourceBinding {
    Resource(chosen, "bootargs").bind(ResourceString(""))
  }

  val maskROMs = p(MaskROMLocated(location)).map { MaskROM.attach(_, this, CBUS) }

  val srams = p(SRAMKey).zipWithIndex.map { case(sramcfg, i) =>
    val sram = LazyModule(new TLRAM(AddressSet.misaligned(sramcfg.address, sramcfg.size).head, cacheable = true))
    val mbus = locateTLBusWrapper(MBUS)
    mbus.coupleTo(s"sram_${i}") { bus => sram.node := TLFragmenter(4, mbus.blockBytes) := bus }
    sram
  }

  val maskROMResetVectorSourceNode = BundleBridgeSource[UInt]()
  tileResetVectorNexusNode := maskROMResetVectorSourceNode

  // Create the ClockGroupSource (only 1...)
  val clockGroup = ClockGroupSourceNode(List.fill(1) { ClockGroupSourceParameters() })
  // Create the Aggregator. This will just take the SourceNode, then just replicate it in a Nexus
  val clocksAggregator = LazyModule(new ClockGroupAggregator("allClocks")).node
  // Connect it to the asyncClockGroupsNode, with the aggregator
  asyncClockGroupsNode :*= clocksAggregator := clockGroup

  override lazy val module = new RVCSystemModuleImp(this)
}

class RVCSystemModuleImp[+L <: RVCSystem](_outer: L) extends RVCSubsystemModuleImp(_outer)
  with HasPeripheryGPIOModuleImp
  with HasPeripheryUARTModuleImp
  with HasPeripherySPIFlashModuleImp
  with HasPeripheryI2CModuleImp
  with HasSDRAMModuleImp
  with HasQsysDDR3ModuleImp
  with HasArtyA7MIGModuleImp
  with HasNexys4DDRMIGModuleImp
  with HasPeripheryCodecModuleImp
  with HasPeripheryFFTModuleImp
  with HasRTCModuleImp
{
  val spi  = outer.spiNodes.zipWithIndex.map  { case(n,i) => n.makeIO()(ValName(s"spi_$i")).asInstanceOf[SPIPortIO] }
  val possible_addresses = outer.p(MaskROMLocated(outer.location)).map(_.address) ++ p(PeripherySPIFlashKey).map(_.fAddress)
  outer.maskROMResetVectorSourceNode.bundle := possible_addresses(0).U

  println(s"Connecting clocks...")
  val extclocks = outer.clockGroup.out.flatMap(_._1.member.elements)
  val namedclocks = outer.clocksAggregator.out.flatMap(_._1.member.elements)
  val otherclock = IO(Input(Clock()))
  (extclocks zip namedclocks).foreach{ case ((_, o), (name, _)) =>
    println(s"  Connecting ${name}")
    o.clock := clock
    o.reset := reset
    if(name.contains("sdramClockGroup")) {
      println("    Connected as otherclock")
      o.clock := otherclock
    }
  }
}