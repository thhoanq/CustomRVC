package riscvconsole.devices.blake2s

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
class BLAKE2S extends BlackBox with HasBlackBoxResource {
  val io = IO(new Bundle() {
    val clk        = Input(Clock())
    val reset      = Input(Bool())
    val cs         = Input(Bool())
    val we         = Input(Bool())
    val address    = Input(UInt(8.W))
    val write_data = Input(UInt(32.W))
    val read_data  = Output(UInt(32.W))
  })
  addResource("Blake2s/BLAKE2S.v") // automatically find the given file-name in resources/ folder
  addResource("Blake2s/blake2s_core.v")
  addResource("Blake2s/blake2s_G.v")
  addResource("Blake2s/blake2s_m_select.v")
}

// declare params
case class BLAKE2SParams(address: BigInt)

// declare register-map structure
object BLAKE2SCtrlRegs {
  val trigger     = 0x00
  val data_a      = 0x04
  val data_b      = 0x08
  val data_c      = 0x0C
}

// mapping between HW ports and register-map
abstract class BLAKE2Smod(busWidthBytes: Int, c: BLAKE2SParams)(implicit p : Parameters)
  extends RegisterRouter(
    RegisterRouterParams(
      name = "blake2s",
      compat = Seq("console,blake2s0"),
      base = c.address,
      beatBytes = busWidthBytes))
{
  lazy val module = new LazyModuleImp(this) {
    // HW instantiation
    val mod = Module(new BLAKE2S)

    // declare inputs
    val address     = Reg(UInt(8.W))
    val write_data  = Reg(UInt(32.W))
    val rst         = RegInit(false.B)
    val cs          = RegInit(false.B)
    val we          = RegInit(false.B)
    // mapping inputs
    mod.io.clk         := clock
    mod.io.reset       := reset.asBool || rst
    mod.io.cs          := cs
    mod.io.we          := we
    mod.io.address     := address
    mod.io.write_data  := write_data

    // declare outputs
    val read_data   = Wire(UInt(32.W))
    // mapping outputs
    read_data           := mod.io.read_data

    // map inputs & outputs to register positions
    val mapping = Seq(
      BLAKE2SCtrlRegs.trigger -> Seq(
        RegField(1, cs, RegFieldDesc("cs", "Blake2s chipselect")),
        RegField(7),
        RegField(1, we, RegFieldDesc("we", "Blake2s write enable")),
        RegField(7),
        RegField(1, rst, RegFieldDesc("rst", "Blake2s reset"))
      ),
      BLAKE2SCtrlRegs.data_a -> Seq(RegField(8, address, RegFieldDesc("address", "Blake2s address"))),
      BLAKE2SCtrlRegs.data_b -> Seq(RegField(32, write_data, RegFieldDesc("write_data", "Blake2s data input"))),
      BLAKE2SCtrlRegs.data_c -> Seq(RegField.r(32, read_data, RegFieldDesc("read_data", "Blake2s data output", volatile = true))),
    )
    regmap(mapping :_*)
    val omRegMap = OMRegister.convert(mapping:_*)
  }
}

// declare TileLink-wrapper class for Blake2s-module
class TLBLAKE2S(busWidthBytes: Int, params: BLAKE2SParams)(implicit p: Parameters)
  extends BLAKE2Smod(busWidthBytes, params) with HasTLControlRegMap

// this will auto +1 ID if there are many Blake2s modules
object BLAKE2SID {
  val nextId = {
    var i = -1; () => {
      i += 1; i
    }
  }
}

// attach TLBLAKE2S to a bus
case class BLAKE2SAttachParams
(
  device: BLAKE2SParams,
  controlWhere: TLBusWrapperLocation = PBUS)
{
  def attachTo(where: Attachable)(implicit p: Parameters): TLBLAKE2S = where {
    val name = s"Blake2s_${BLAKE2SID.nextId()}"
    val cbus = where.locateTLBusWrapper(controlWhere)
    val blake2s = LazyModule(new TLBLAKE2S(cbus.beatBytes, device))
    blake2s.suggestName(name)

    cbus.coupleTo(s"device_named_$name") { bus =>
      (blake2s.controlXing(NoCrossing)
        := TLFragmenter(cbus)
        := bus)
    }
    blake2s
  }
}

// declare trait to be called in a system
case object PeripheryBLAKE2SKey extends Field[Seq[BLAKE2SParams]](Nil)

// trait to be called in a system
trait HasPeripheryBLAKE2S { this: BaseSubsystem =>
  val blake2sNodes = p(PeripheryBLAKE2SKey).map { ps =>
    BLAKE2SAttachParams(ps).attachTo(this)
  }
}