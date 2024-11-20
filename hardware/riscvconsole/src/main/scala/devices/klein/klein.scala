package riscvconsole.devices.klein

import chipsalliance.rocketchip.config.{Field, Parameters}
import chisel3._
import chisel3.util._
import freechips.rocketchip.devices.tilelink._
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.diplomaticobjectmodel.DiplomaticObjectModelAddressing
import freechips.rocketchip.diplomaticobjectmodel.logicaltree._
import freechips.rocketchip.diplomaticobjectmodel.model._
import freechips.rocketchip.interrupts._
import freechips.rocketchip.prci._
import freechips.rocketchip.regmapper._
import freechips.rocketchip.subsystem._
import freechips.rocketchip.tilelink._

// hardware wrapper for Verilog file: module name & ports MUST MATCH the Verilog's
class KLEIN extends BlackBox with HasBlackBoxResource {
  val io = IO(new Bundle() {
    val iclk        = Input(Clock())
    val ireset      = Input(Bool())
    val ics         = Input(Bool())
    val iwe         = Input(Bool())
    val iaddress    = Input(UInt(8.W))
    val iwrite_data = Input(UInt(32.W))
    val oread_data  = Output(UInt(32.W))
  })
  addResource("KLEIN/KLEIN.v") // automatically find the given file-name in resources/ folder
  addResource("KLEIN/klein_core.v")
  addResource("KLEIN/klein_cipher.v")
  addResource("KLEIN/klein_decipher.v")
  addResource("KLEIN/klein_keyschedule.v")
  addResource("KLEIN/klein_mixcolumn.v")
  addResource("KLEIN/klein_sbox.v")
}

// declare params
case class KLEINParams(address: BigInt)

// declare register-map structure
object KLEINCtrlRegs {
  val trigger     = 0x00
  val data_a      = 0x04
  val data_b      = 0x08
  val data_c      = 0x0C
}

// mapping between HW ports and register-map
abstract class KLEINmod(busWidthBytes: Int, c: KLEINParams)(implicit p : Parameters)
  extends RegisterRouter(
    RegisterRouterParams(
      name = "klein",
      compat = Seq("console,klein0"),
      base = c.address,
      beatBytes = busWidthBytes))
{
  lazy val module = new LazyModuleImp(this) {
    // HW instantiation
    val mod = Module(new KLEIN)

    // declare inputs
    val address     = Reg(UInt(8.W))
    val write_data  = Reg(UInt(32.W))
    val rst         = RegInit(false.B)
    val cs          = RegInit(false.B)
    val we          = RegInit(false.B)
    // mapping inputs
    mod.io.iclk         := clock
    mod.io.ireset       := reset.asBool || rst
    mod.io.ics          := cs
    mod.io.iwe          := we
    mod.io.iaddress     := address
    mod.io.iwrite_data  := write_data

    // declare outputs
    val read_data   = Wire(UInt(32.W))
    // mapping outputs
    read_data           := mod.io.oread_data

    // map inputs & outputs to register positions
    val mapping = Seq(
      KLEINCtrlRegs.trigger -> Seq(
        RegField(1, cs, RegFieldDesc("cs", "KLEIN chipselect")),
        RegField(7),
        RegField(1, we, RegFieldDesc("we", "KLEIN write enable")),
        RegField(7),
        RegField(1, rst, RegFieldDesc("rst", "KLEIN reset"))
      ),
      KLEINCtrlRegs.data_a -> Seq(RegField(8, address, RegFieldDesc("address", "KLEIN address"))),
      KLEINCtrlRegs.data_b -> Seq(RegField(32, write_data, RegFieldDesc("write_data", "KLEIN data input"))),
      KLEINCtrlRegs.data_c -> Seq(RegField.r(32, read_data, RegFieldDesc("read_data", "KLEIN data output", volatile = true))),
    )
    regmap(mapping :_*)
    val omRegMap = OMRegister.convert(mapping:_*)
  }
}

// declare TileLink-wrapper class for KLEIN-module
class TLKLEIN(busWidthBytes: Int, params: KLEINParams)(implicit p: Parameters)
  extends KLEINmod(busWidthBytes, params) with HasTLControlRegMap

// this will auto +1 ID if there are many KLEIN modules
object KLEINID {
  val nextId = {
    var i = -1; () => {
      i += 1; i
    }
  }
}

// attach TLKLEIN to a bus
case class KLEINAttachParams
(
  device: KLEINParams,
  controlWhere: TLBusWrapperLocation = PBUS)
{
  def attachTo(where: Attachable)(implicit p: Parameters): TLKLEIN = where {
    val name = s"KLEIN_${KLEINID.nextId()}"
    val cbus = where.locateTLBusWrapper(controlWhere)
    val klein = LazyModule(new TLKLEIN(cbus.beatBytes, device))
    klein.suggestName(name)

    cbus.coupleTo(s"device_named_$name") { bus =>
      (klein.controlXing(NoCrossing)
        := TLFragmenter(cbus)
        := bus)
    }
    klein
  }
}

// declare trait to be called in a system
case object PeripheryKLEINKey extends Field[Seq[KLEINParams]](Nil)

// trait to be called in a system
trait HasPeripheryKLEIN { this: BaseSubsystem =>
  val kleinNodes = p(PeripheryKLEINKey).map { ps =>
    KLEINAttachParams(ps).attachTo(this)
  }
}