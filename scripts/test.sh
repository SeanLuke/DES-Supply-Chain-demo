#!/bin/csh

#------------------------------------------------------------------
set from=~vmenkov/mason/work

#-- The config file to use for all runs
set config=config/sc2.csv

foreach x (config/dis-sc2/sample-D11.csv)

set y=`basename -s .csv $x`

set charts="charts-$y"
echo "For scenario $x, the output will go to directory $charts"

$from/run-sc2.sh -config $config -charts $charts -disrupt $x -until 2000  > out.log
mv out.log $charts/
cp $config $charts/
cp $x $charts/

#$from/scripts/extract-anomaly-columns.sh $charts
#(cd $charts; gnuplot ../charts-batch.gnu)
#(cd $charts; ls | $from/scripts/mk-index.pl > index.html)



end


#ls | $from/scripts/mk-index.pl > index.html
