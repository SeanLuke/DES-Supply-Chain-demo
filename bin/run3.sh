#!/bin/csh
#----------------------------------------------------
# Sample usage:
# ./run.sh -until 300
# ./run3.sh -until 360 -config config/old_config_file -charts chart-01 
#----------------------------------------------------


#-- The directory where this script is
set d=`dirname $0`


set h=`(cd $d/..; pwd)`
setenv CLASSPATH $h/work/lib/demo.jar:$h/lib/'*':$h/classes:$h/classes


#time java -Xprof edu.rutgers.pharma3.Demo  $argv[1-]
time java  edu.rutgers.pharma3.Demo  $argv[1-]

#-time 10
