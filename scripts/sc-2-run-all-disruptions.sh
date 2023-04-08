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
set from=~vmenkov/mason/work

#------------------------------------------------------------------
# The config file to use for all runs. Change this if you use your
# own customized config file.
#------------------------------------------------------------------
set config=$from/config/sc2.csv

#-- Try every sample disruption scenario file
foreach x ($from/config/dis-sc2/sample-*.csv)

set y=`basename -s .csv $x`

#-- The directory to be created for the output of this run
set charts="charts-$y"
echo "For scenario $x, the output will go to directory $charts"

$from/run-sc2.sh -config $config -charts $charts -disrupt $x -until 2000  > out.log
mv out.log $charts/
cp $config $charts/
cp $x $charts/

#-- Plotting with Gnuplot
#$from/scripts/extract-anomaly-columns.sh $charts
#(cd $charts; gnuplot ../charts-batch.gnu)
#(cd $charts; ls | $from/scripts/mk-index.pl > index.html)

end


#ls | $from/scripts/mk-index.pl > index.html
