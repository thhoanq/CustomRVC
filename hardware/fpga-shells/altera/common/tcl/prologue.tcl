# See LICENSE for license details.

# Process command line arguments
# http://wiki.tcl.tk/1730
set ip_quartus_tcls {}
set ip_quartus_qsys {}
set ip_quartus_sdc {}

while {[llength $argv]} {
  set argv [lassign $argv[set argv {}] flag]
  switch -glob $flag {
    -top-module {
      set argv [lassign $argv[set argv {}] top]
    }
    -F {
      # This should be a simple file format with one filepath per line
      set argv [lassign $argv[set argv {}] vsrc_manifest]
    }
    -board {
      set argv [lassign $argv[set argv {}] board]
    }
    -ip-quartus-tcls {
      set argv [lassign $argv[set argv {}] ip_quartus_tcls]
    }
    -ip-quartus-qsys {
      set argv [lassign $argv[set argv {}] ip_quartus_qsys]
    }
    -ip-quartus-sdc {
      set argv [lassign $argv[set argv {}] ip_quartus_sdc]
    }
    -pre-impl-debug-tcl {
      set argv [lassign $argv[set argv {}] pre_impl_debug_tcl]
    }
    -post-impl-debug-tcl {
      set argv [lassign $argv[set argv {}] post_impl_debug_tcl]
    }
    -env-var-srcs {
      set argv [lassign $argv[set argv {}] env_var_srcs]
    }
    default {
      return -code error [list {unknown option} $flag]
    }
  }
}
# tcl-env-srcs: Command line argument to pass the name of an environment variable that contains additional vsrcs 
# (from what is contained in .F file) that you want to have read in

if {![info exists top]} {
  return -code error [list {--top-module option is required}]
}

if {![info exists vsrc_manifest]} {
  return -code error [list {-F option is required}]
}

if {![info exists board]} {
  return -code error [list {--board option is required}]
}

# Set the variable for all the common files
set commondir [file dirname $scriptdir]

# Set the variable that points to board specific files
set boarddir [file join [file dirname $commondir] $board]
source [file join $boarddir tcl board.tcl]

# Set the variable that points to board constraint files
set constraintsdir [file join $boarddir constraints]

# Set the variable that points to common verilog sources (TODO: Not used)
set srcdir [file join $commondir vsrc]

# Creates a work directory
set wrkdir [file join [pwd] obj]

# Create the directory for IPs (TODO: Not used)
set ipdir [file join $wrkdir ip]

# Quartus: Load the packages
package require ::quartus::project
load_package ::quartus::flow
load_package ::quartus::misc

# Create an project. Is possible that creates auxiliars
project_new -overwrite -revision $top $top

# Set the board part, target language, default library, and IP directory
# paths for the current project
# TODO: Needs to define: FAMILY, MIN_CORE_JUNCTION_TEMP, MAX_CORE_JUNCTION_TEMP, ERROR_CHECK_FREQUENCY_DIVISOR
# POWER_PRESET_COOLING_SOLUTION, POWER_BOARD_THERMAL_MODEL
set_global_assignment -name FAMILY $FAMILY
set_global_assignment -name DEVICE $part_fpga
set_global_assignment -name TOP_LEVEL_ENTITY $top
set_global_assignment -name PROJECT_OUTPUT_DIRECTORY $wrkdir
set_global_assignment -name MIN_CORE_JUNCTION_TEMP $MIN_CORE_JUNCTION_TEMP
set_global_assignment -name MAX_CORE_JUNCTION_TEMP $MAX_CORE_JUNCTION_TEMP
set_global_assignment -name ERROR_CHECK_FREQUENCY_DIVISOR $ERROR_CHECK_FREQUENCY_DIVISOR
set_global_assignment -name POWER_PRESET_COOLING_SOLUTION $POWER_PRESET_COOLING_SOLUTION
set_global_assignment -name POWER_BOARD_THERMAL_MODEL $POWER_BOARD_THERMAL_MODEL
set_global_assignment -name PARTITION_NETLIST_TYPE SOURCE -section_id Top
set_global_assignment -name PARTITION_FITTER_PRESERVATION_LEVEL PLACEMENT_AND_ROUTING -section_id Top
set_global_assignment -name PARTITION_COLOR 16764057 -section_id Top
set_global_assignment -name VERILOG_MACRO "SYNTHESIS=1"
set_global_assignment -name VERILOG_INPUT_VERSION SYSTEMVERILOG_2005
set_global_assignment -name VERILOG_SHOW_LMF_MAPPING_MESSAGES OFF
set_instance_assignment -name PARTITION_HIERARCHY root_partition -to | -section_id Top

# Add verilog files from manifest
proc load_vsrc_manifest {vsrc_manifest} {
  set fp [open $vsrc_manifest r]
  set files [lsearch -not -exact -all -inline [split [read $fp] "\n"] {}]
  set relative_files {}
  foreach path $files {
    if {[string match {/*} $path]} {
      lappend relative_files $path
    } elseif {![string match {#*} $path]} {
      lappend relative_files [file join [file dirname $vsrc_manifest] $path]
    }
  }
  # Read environment variable vsrcs and append to relative_files
  if {[info exists env_var_srcs)]} {
    if {[info exists ::env($env_var_srcs)]} {
      set resources [split $::env($env_var_srcs) :]
      set relative_files [list {*}$relative_files {*}$resources]
    }
  }
  # Iterate the files in relative_files
  foreach fi {*}$relative_files {
    if {[regexp {^.*\.(v|vh)$} $fi]} {
      set_global_assignment -name VERILOG_FILE $fi
    } elseif {[regexp {^.*\.(sv|svh)$} $fi]} {
      set_global_assignment -name SYSTEMVERILOG_FILE $fi
    } elseif {[regexp {^.*\.(vhd|vhdl)$} $fi]} {
      set_global_assignment -name VHDL_FILE $fi
    } else {
      puts "Putting source file $fi"
      set_global_assignment -name SOURCE_FILE $fi
    }
  }
  close $fp
}

load_vsrc_manifest $vsrc_manifest

# Add IP Quartus TCL
if {$ip_quartus_tcls ne {}} {
  # Split string into words even with multiple consecutive spaces
  # http://wiki.tcl.tk/989
  set ip_quartus_tcls [regexp -inline -all -- {\S+} $ip_quartus_tcls]
}

