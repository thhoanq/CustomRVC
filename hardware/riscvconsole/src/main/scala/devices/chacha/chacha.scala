package riscvconsole.devices.chacha

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
class chacha extends BlackBox with HasBlackBoxResource {
  val io = IO(new Bundle() {
    val clk         = Input(Clock())
    val reset_n     = Input(Bool())
    val cs          = Input(Bool())
    val we          = Input(Bool())
    val addr        = Input(UInt(8.W))
    val write_data  = Input(UInt(32.W))
    val read_data   = Output(UInt(32.W))
  })
  addResource("ChaCha/chacha.v")
  addResource("ChaCha/chacha_core.v")
  addResource("ChaCha/chacha_qr.v")
}

// declare params
case class CHACHAParams(address: BigInt)

// declare register-map structure
object chachaCtrlRegs {
  val trigger     = 0x00
  val data_a      = 0x04
  val data_b      = 0x08
  val data_c      = 0x0C
}

// mapping between HW ports and register-map
abstract class chachamod(busWidthBytes: Int, c: CHACHAParams)(implicit p : Parameters)
  extends RegisterRouter(
    RegisterRouterParams(
      name = "chacha",
      compat = Seq("console,chacha0"),
      base = c.address,
      beatBytes = busWidthBytes))
{
  lazy val module = new LazyModuleImp(this) {
    // HW instantiation
    val mod = Module(new chacha)

    // declare inputs
    val address     = Reg(UInt(8.W))
    val write_data  = Reg(UInt(32.W))
    val rst_n       = RegInit(true.B)
    val cs          = RegInit(false.B)
    val we          = RegInit(false.B)
    // mapping inputs
    mod.io.clk         := clock
    mod.io.reset_n     := !(reset.asBool) || rst_n
    mod.io.cs          := cs
    mod.io.we          := we
    mod.io.addr        := address
    mod.io.write_data  := write_data

    // declare outputs
    val read_data   = Wire(UInt(32.W))
    // mapping outputs
    read_data           := mod.io.read_data

    // map inputs & outputs to register positions
    val mapping = Seq(
      chachaCtrlRegs.trigger -> Seq(
        RegField(1, cs, RegFieldDesc("cs", "Chacha chipselect")),
        RegField(7),
        RegField(1, we, RegFieldDesc("we", "Chacha write enable")),
        RegField(7),
        RegField(1, rst_n, RegFieldDesc("rst", "Chacha reset"))
      ),
      chachaCtrlRegs.data_a -> Seq(RegField(8, address, RegFieldDesc("address", "Chacha address"))),
      chachaCtrlRegs.data_b -> Seq(RegField(32, write_data, RegFieldDesc("write_data", "Chacha data input"))),
      chachaCtrlRegs.data_c -> Seq(RegField.r(32, read_data, RegFieldDesc("read_data", "Chacha data output", volatile = true))),
    )
    regmap(mapping :_*)
    val omRegMap = OMRegister.convert(mapping:_*)
  }
}

// declare TileLink-wrapper class for Blake2s-module
class TLchacha(busWidthBytes: Int, params: CHACHAParams)(implicit p: Parameters)
  extends chachamod(busWidthBytes, params) with HasTLControlRegMap

// this will auto +1 ID if there are many Blake2s modules
object chachaID {
  val nextId = {
    var i = -1; () => {
      i += 1; i
    }
  }
}

// attach TLchacha to a bus
case class chachaAttachParams
(
  device: CHACHAParams,
  controlWhere: TLBusWrapperLocation = PBUS)
{
  def attachTo(where: Attachable)(implicit p: Parameters): TLchacha = where {
    val name = s"Chacha_${chachaID.nextId()}"
    val cbus = where.locateTLBusWrapper(controlWhere)
    val chacha = LazyModule(new TLchacha(cbus.beatBytes, device))
    chacha.suggestName(name)

    cbus.coupleTo(s"device_named_$name") { bus =>
      (chacha.controlXing(NoCrossing)
        := TLFragmenter(cbus)
        := bus)
    }
    chacha
  }
}

// declare trait to be called in a system
case object PeripheryCHACHAKey extends Field[Seq[CHACHAParams]](Nil)

// trait to be called in a system
trait HasPeripheryCHACHA{ this: BaseSubsystem =>
  val chachaNodes = p(PeripheryCHACHAKey).map { ps =>
    chachaAttachParams(ps).attachTo(this)
  }
}