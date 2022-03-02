#!/bin/csh
#----------------------------------------------------
# Sample usage:
# ./run.sh -until 300
#----------------------------------------------------


set h=`(cd ..; pwd)`
setenv CLASSPATH $h/work/lib/demo.jar:$h/lib/'*':$h/lib/libraries/'*':/opt/java3d/lib/ext/'*'



#java edu.rutgers.pharma.Test -until 300
java edu.rutgers.pharma2.DemoWithUI  $argv[1-]

#-time 10
