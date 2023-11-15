#!/bin/csh
#----------------------------------------------------
# Sample usage:
# ./run.sh -until 300
#----------------------------------------------------


set h=`(cd ..; pwd)`
setenv CLASSPATH $h/work/lib/demo.jar:$h/lib/'*':$h/classes


#java edu.rutgers.pharma.Test -until 300
java edu.rutgers.pharma2.Demo  $argv[1-]

#-time 10
