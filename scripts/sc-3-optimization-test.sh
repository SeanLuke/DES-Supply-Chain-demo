#!/bin/csh

#------------------------------------------------------------------
# This script runs the SC-2 simulation app with each sample
# disruption files
#------------------------------------------------------------------

#------------------------------------------------------------------
# The directory in which my copy of the code (pulled from GitHub)
# is. This is determined dynamically, based on the location of this script,
# so it should work no matter where you deploy the code too.
#
#------------------------------------------------------------------

#-- The directory where this script is
set sc=`dirname $0`
set work=`(cd $sc/..; pwd)`


#------------------------------------------------------------------
# The config file to use for all runs. Change this if you use your
# own customized config file.
#------------------------------------------------------------------
set config=$work/config/sc3.csv


set d=`dirname $0`


set h=`(cd $work/..; pwd)`
setenv CLASSPATH $work/lib/demo.jar:$h/lib/'*'


# time java -Xprof edu.rutgers.sc3.Demo  $argv[1-]
time java  edu.rutgers.test.TestSc3 -config $config $argv[1-]



