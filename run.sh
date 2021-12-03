#!/bin/csh

set h=`(cd ..; pwd)`
setenv CLASSPATH $h/work/lib/demo.jar:$h/lib/'*'

#echo java -cp $cp edu.rutgers.pharma.Demo
java edu.rutgers.pharma.Demo -until 300

#-time 10
