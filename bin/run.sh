#!/bin/csh
#----------------------------------------------------
# Sample usage:
# ./run.sh -until 300
#----------------------------------------------------


set h=`(cd ..; pwd)`
setenv CLASSPATH $h/work/lib/demo.jar:$h/lib/'*':$h/classes

#echo java -cp $cp edu.rutgers.pharma.Demo
#java edu.rutgers.pharma.Demo -until 300
time java edu.rutgers.pharma.Demo  $argv[1-]

#-time 10
