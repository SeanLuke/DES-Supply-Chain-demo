
set datafile separator ','
set grid mxtics mytics

set term qt 0
set grid mxtics mytics
set title 'Hospital/Pharmacy Pool'
plot 'HospitalPool.csv'  using ($1):($2)  with lines title 'HP.Stock', \
 'HospitalPool.csv'  using ($1):($3)  with lines title 'HP.Ordered', \
'HospitalPool.csv'  using  ($1):($4)  with lines title 'HP.Received'
set term png large
set out 'HospitalPool.png'
replot

set term qt 1
set title 'Wholesaler Pool'
plot 'WholesalerPool.csv'  using ($1):($2)  with lines title 'WP.Stock', \
'WholesalerPool.csv'  using ($1):($3)  with lines title 'WP.Ordered', \
'WholesalerPool.csv'  using  ($1):($4)  with lines title 'WP.Received', \
'UntrustedPool.csv'  using  ($1):($2)  with lines title 'Sent to WP from Untrusted Pool'
set term png large
set out 'WholesalerPool.png'
replot

set term qt 2
set title 'Distribution Center'
plot 'Distributor.csv'  using ($1):($2)  with lines title 'DC.Stock', \
'Distributor.csv'  using ($1):($3)  with lines title 'DC.Ordered', \
'Distributor.csv'  using  ($1):($4)  with lines title 'DC.Received'
set term png large
set out 'Distributor.png'
replot

set term qt 4
set title 'Production units: daily output from QA'
plot 'ApiProduction.csv' with lines,  'DrugProduction.csv' with lines, 'Packaging.csv' with lines
set term png large
set out 'Production.png'
replot


