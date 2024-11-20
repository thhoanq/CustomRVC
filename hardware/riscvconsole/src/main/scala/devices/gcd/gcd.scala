package riscvconsole.devices.gcd

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
class GCD extends BlackBox with HasBlackBoxResource {
  val io = IO(new Bundle{
    val iClk   = Input(Clock())
    val iRst   = Input(Bool())
    val iA     = Input(UInt(16.W))
    val iB     = Input(UInt(16.W))
    val iValid = Input(Bool())
    val oReady = Output(Bool())
    val oValid = Output(Bool())
    val oC     = Output(UInt(16.W))
  })
  addResource("GCD.v") // automatically find the given file-name in resources/ folder
}

// declare params
case class GCDParams(address: BigInt)

// declare register-map structure
object GCDCtrlRegs {
  val trigger     = 0x00
  val data_a      = 0x04
  val data_b      = 0x08
  val data_c      = 0x0C
  val status      = 0x10
}

// mapping between HW ports and register-map
abstract class GCDmod(busWidthBytes: Int, c: GCDParams)(implicit p: Parameters)
  extends RegisterRouter(
    RegisterRouterParams(
      name = "gcd",
      compat = Seq("console,gcd0"),
      base = c.address,
      beatBytes = busWidthBytes))
{
  lazy val module = new LazyModuleImp(this) {
    // HW instantiation
    val mod = Module(new GCD)

    // declare inputs
    val data_a = Reg(UInt(16.W))
    val data_b = Reg(UInt(16.W))
    val rst    = RegInit(false.B)
    val trig   = WireInit(false.B)
    // mapping inputs
    mod.io.iClk   := clock
    mod.io.iRst   := reset.asBool || rst
    mod.io.iValid := trig
    mod.io.iA     := data_a
    mod.io.iB     := data_b

    // declare outputs
    val ready  = Wire(Bool())
    val valid  = Wire(Bool())
    val data_c = Wire(UInt(16.W))
    // mapping outputs
    ready  := mod.io.oReady
    valid  := mod.io.oValid
    data_c := RegEnable(mod.io.oC, valid)

    // map inputs & outputs to register positions
    val mapping = Seq(
      GCDCtrlRegs.trigger -> Seq(
        RegField(1, trig, RegFieldDesc("trigger", "GCD trigger/start")),
        RegField(7),
        RegField(1, rst, RegFieldDesc("rst", "GCD Reset", reset = Some(0)))
      ),
      GCDCtrlRegs.data_a -> Seq(RegField(16, data_a, RegFieldDesc("data_a", "A data for GCD"))),
      GCDCtrlRegs.data_b -> Seq(RegField(16, data_b, RegFieldDesc("data_b", "B data for GCD"))),
      GCDCtrlRegs.data_c -> Seq(RegField.r(16, data_c, RegFieldDesc("data_c", "C output for GCD", volatile = true))),
      GCDCtrlRegs.status -> Seq(RegField.r(1, ready, RegFieldDesc("ready", "GCD data ready", volatile = true))),
    )
    regmap(mapping :_*)
    val omRegMap = OMRegister.convert(mapping:_*)
  }
}

// declare TileLink-wrapper class for GCD-module
class TLGCD(busWidthBytes: Int, params: GCDParams)(implicit p: Parameters)
  extends GCDmod(busWidthBytes, params) with HasTLControlRegMap

// this will auto +1 ID if there are many GCD modules
object GCDID {
  val nextId = {
    var i = -1; () => {
      i += 1; i
    }
  }
}

// attach TLGCD to a bus
case class GCDAttachParams
(
  device: GCDParams,
  controlWhere: TLBusWrapperLocation = PBUS)
{
  def attachTo(where: Attachable)(implicit p: Parameters): TLGCD = where {
    val name = s"gcd_${GCDID.nextId()}"
    val cbus = where.locateTLBusWrapper(controlWhere)
    val gcd = LazyModule(new TLGCD(cbus.beatBytes, device))
    gcd.suggestName(name)

    cbus.coupleTo(s"device_named_$name") { bus =>
      (gcd.controlXing(NoCrossing)
        := TLFragmenter(cbus)
        := bus )
    }
    gcd
  }
}

// declare trait to be called in a system
case object PeripheryGCDKey extends Field[Seq[GCDParams]](Nil)

// trait to be called in a system
trait HasPeripheryGCD { this: BaseSubsystem =>
  val gcdNodes = p(PeripheryGCDKey).map { ps =>
    GCDAttachParams(ps).attachTo(this)
  }
}


