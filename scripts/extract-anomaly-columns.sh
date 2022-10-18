#!/bin/csh

#-- Run this in a directory such as runs/2.009/exp-1-a, in order to extract and
#-- join the anomaly columns from all relevant CSV files.
#-- Usage:
#---   extract-anomaly-columns.sh charts-dis*

foreach d  ($argv)
  cd $d
  pwd
  cut -d , -f 1,5 ApiProduction.csv | perl -pe 's/\.0//g; s/$/,/' > a.tmp
  cut -d , -f 6-7 DrugProduction.csv | perl -pe 's/\.0//g; s/$/,/' > b.tmp
  cut -d , -f 6-7 Packaging.csv | perl -pe 's/\.0//g; s/$/,/' > c.tmp

  #-- remove a spurious extra header line
  grep -v ',sentToday$'   Distributor.csv > x.tmp
  cut -d , -f 8- x.tmp  | perl -pe 's/\.0//g; s/anomaly/Anomaly.DC/' > d.tmp


  paste a.tmp b.tmp c.tmp d.tmp | perl -pe 's/[ \t]+//g' > Anomaly.csv
  cd ..
end
