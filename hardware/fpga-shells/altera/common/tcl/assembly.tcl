# See LICENSE for license details.

# Synthesize the design
if { [catch { exec >@stdout 2>@stderr quartus_asm $top }] } {
  return -code error "Running quartus_asm"
}

