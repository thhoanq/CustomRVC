# See LICENSE for license details.

# Synthesize the design
if { [catch { exec >@stdout 2>@stderr quartus_fit $top }] } {
  return -code error "Running quartus_fit"
}

