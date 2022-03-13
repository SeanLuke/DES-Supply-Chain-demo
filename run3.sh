#!/bin/csh
#----------------------------------------------------
# Sample usage:
# ./run.sh -until 300
#----------------------------------------------------


set h=`(cd ..; pwd)`
setenv CLASSPATH $h/work/lib/demo.jar:$h/lib/'*'


#java edu.rutgers.pharma.Test -until 300
java edu.rutgers.pharma3.Demo  $argv[1-]

#-time 10
