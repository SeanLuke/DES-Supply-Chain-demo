#!/bin/csh

set h=`(cd ..; pwd)`
setenv CLASSPATH $h/work/lib/demo.jar:$h/lib/'*'

#echo java -cp $cp edu.rutgers.pharma.Demo
java edu.rutgers.masondemo1.Population 10 2.8 -until 300

#-steps  100

#-time 101
