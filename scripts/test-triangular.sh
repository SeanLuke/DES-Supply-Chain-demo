#!/bin/csh

#-- The directory where this script is
set sc=`dirname $0`
set h=`(cd $sc/..; pwd)`
source "$sc/set-var.sh"


# mkdir tmp
java sim.util.distribution.Triangular $argv[1-]



