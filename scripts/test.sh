#!/bin/csh

#------------------------------------------------------------------
# This script runs the SC-2 simulation app with each sample
# disruption files
#------------------------------------------------------------------

#------------------------------------------------------------------
# The directory in which my copy of the code (pulled from GitHub)
# is. Change this as appropriate if you use your own copy of the code
# (which you have pulled from GitHub yourself.
#------------------------------------------------------------------


#-- The directory where this script is
set sc=`dirname $0`
set work=`(cd $sc/..; pwd)`


#------------------------------------------------------------------
# The config file to use for all runs. Change this if you use your
# own customized config file.
#------------------------------------------------------------------
set config=$work/config/sc2.csv

#-- Try every sample disruption scenario file
foreach x ($work/config/dis-sc2/sample-D26.csv)
#foreach x ($work/config/dis-sc2/severe-D14+D20.csv)

set y=`basename -s .csv $x`

#-- The directory to be created for the output of this run
set charts="charts-$y"
echo "For scenario $x, the output will go to directory $charts"

$work/run-sc2.sh -config $config -charts $charts -disrupt $x -until 2000  > out.log
mv out.log $charts/
cp $config $charts/
cp $x $charts/

#-- Plotting with Gnuplot
#$work/scripts/extract-anomaly-columns.sh $charts
#(cd $charts; gnuplot ../charts-batch.gnu)
#(cd $charts; ls | $work/scripts/mk-index.pl > index.html)

end


#ls | $from/scripts/mk-index.pl > index.html
