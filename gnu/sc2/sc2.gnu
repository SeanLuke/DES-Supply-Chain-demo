
set datafile separator ','

#-- this does not seem to work
set grid mxtics mytic
show grid

#-- to make sure the tic labels on the Y-axis fit into the PNG image
set lmargin 8
set grid mxtics mytics

set term aqua 0
set title 'Patients'
plot  'WaitingPatientQueue.csv' using ($1):($2) w l t 'Waiting patients', \
 'ServicedPatientPool.csv' using ($1):($2) w l lc 'red' t 'Patients being treated', \
 'eeHEP.csv'  using ($1):($2) w l t 'EE stock on hand', \
 'dsHEP.csv'  using ($1):($2) w l t 'DS stock on hand'

set term aqua 1
set title 'EE Production'
plot 'eePackaging.csv' using ($1):($2) w l t 'EE units packages'


set term aqua 2
set title 'DS H/E Pool'
plot 'dsHEP.csv'  using ($1):($2) w l t 'in stock', \
 'dsHEP.csv'  using ($1):($5) w l t 'on order'


set term aqua 3
set title 'DS DC'
plot 'dsDC.csv'  using ($1):($2) w l t 'in stock', \
 'dsDC.csv'  using ($1):($5) w l t 'on order'

set term aqua 4
set title 'DS DP'
plot 'dsDP.csv'  using ($1):($2) w l t 'in stock', \
 'dsDP.csv'  using ($1):($5) w l t 'on order'

set term aqua 5
set title 'DS Production'
plot 'dsCmoProd.csv' using ($1):($2) w l t 'at DS CMO Prod',\
 'dsProd.csv' using ($1):($2) w l t 'at DS Prod (in-house)', \
 'dsPackaging.csv' using ($1):($2) with lines lc 'red' t 'DS Packaging'
 
