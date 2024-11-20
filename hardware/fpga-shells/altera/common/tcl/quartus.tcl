# See LICENSE for license details.

# Set the variable for the directory that includes all scripts
set scriptdir [file dirname [info script]]

# Set up variables and objects
source [file join $scriptdir "prologue.tcl"]

# Initialize project files
source [file join $scriptdir "init.tcl"]

# Synthesize the design
source [file join $scriptdir "synth.tcl"]

# Pre-implementation debug
if {[info exists pre_impl_debug_tcl]} {
  source [file join $scriptdir $pre_impl_debug_tcl]
}

# Fitter (Takes care of the place & route)
source [file join $scriptdir "fit.tcl"]

# Generate bitstream and save verilog netlist
source [file join $scriptdir "assembly.tcl"]

# Post-implementation debug
if {[info exists post_impl_debug_tcl]} {
  source [file join $scriptdir $post_impl_debug_tcl]
}

# Do the timing analysis
source [file join $scriptdir "sta.tcl"]

# TODO: Export the reports in a place where is visible
