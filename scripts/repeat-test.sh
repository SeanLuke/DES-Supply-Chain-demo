#!/bin/csh
#----------------------------------------------------
# Sample usage:
# scripts/repeat-test.sh -config config/pharma3.orig.csv -until 360 -repeat 100
#----------------------------------------------------


#-- The directory where this script is
set d=`dirname $0`


set h=`(cd $d/../..; pwd)`
setenv CLASSPATH $h/work/lib/demo.jar:$h/lib/'*'


time java -Xprof edu.rutgers.pharma3.RepeatTest  $argv[1-]

#-time 10
