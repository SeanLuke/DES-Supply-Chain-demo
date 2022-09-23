#!/bin/csh
#----------------------------------------------------
# Sample usage:
# ./run.sh -until 300
#----------------------------------------------------


#-- The directory where this script is
set d=`dirname $0`


set h=`(cd $d/../..; pwd)`
setenv CLASSPATH $h/work/lib/demo.jar:$h/lib/'*'


#java edu.rutgers.pharma.Test -until 300
java  edu.rutgers.pharma3.GraphAnalysis  $argv[1-]

#-time 10
