
set datafile separator ','

#-- this does not seem to work
set grid mxtics mytic
show grid

#-- to make sure the tic labels on the Y-axis fit into the PNG image
set lmargin 8
set grid mxtics mytics

set term aqua 0
set title 'Prepreg: inputs'
plot [0:*] [0:*] 'prepregProd.csv' using ($1):($4) w l t 'Fiber stock', \
  'prepregProd.csv' using ($1):($6) w l t 'Resin stock'

set term aqua 1
set title 'Prepreg production'
plot  'prepregProd.csv' using ($1):($3) w l t 'Outstanding plan', \
  'prepregProd.csv' using ($1):($2) w impulses t 'Daily production'

set term aqua 2
set title 'Substrate: inputs'
plot  'substrateSmallProd.csv' using ($1):($4) w l t 'Prepreg stock', \
 'substrateSmallProd.csv' using ($1):($6) w l t 'Aluminum hc stock'
# 'substrateSmallProd.csv' using ($1):($5) w l t 'Prepreg daily receipt'


set term aqua 3
set title 'Substrate production'
plot  'substrateSmallProd.csv' using ($1):($3) w l t 'Small: Outstanding plan', \
 'substrateSmallProd.csv' using ($1):($2) w l t 'Small: Daily production', \
 'substrateLargeProd.csv' using ($1):($3) w l t 'Large: Outstanding plan', \
 'substrateLargeProd.csv' using ($1):($2) w l t 'Large: Daily production'

set term aqua 4
set title 'Cell assembly: inputs'
plot  'cellProd.csv' using ($1):($6) w l t 'Cell stock', \
  'cellProd.csv' using ($1):($8) w l t 'Coverglass stock'



set term aqua 5
set title 'SALP/SASP inputs'
plot  'arraySmallAssembly.csv' using ($1):($4) w l t 'Sub small stock', \
  'arraySmallAssembly.csv' using ($1):($6) w l t 'Sub large stock', \
  'arraySmallAssembly.csv' using ($1):(($8)/100) w l t 'Cell stock/100', \
  'arraySmallAssembly.csv' using ($1):($10) w l t 'Adhesive stock', \
  'arraySmallAssembly.csv' using ($1):(($12)/10) w l t 'Diode stock/10', \


set term aqua 6
set title 'SASP (Small Array) Assembly'
plot  'arraySmallAssembly.csv' using ($1):($3) w l t 'Outstanding plan', \
 'arraySmallAssembly.csv' using ($1):($2) w impulses t 'Daily production'

set term aqua 7
set title 'SALP (Large Array) Assembly'
plot  'arrayLargeAssembly.csv' using ($1):($3) w l t 'Outstanding plan', \
  'arrayLargeAssembly.csv' using ($1):($2) w impulses t 'Daily production'


set term aqua 8
set title 'Orders SASP'
plot  'arraySmallCustomer.csv' index 0 using ($2):(0):($3-$2):($3-$2) w vectors lc 'green' t 'Filled', \
'arraySmallCustomer.csv' index 1 using ($2):(0):($3-$2):($3-$2) w vectors lc 'red' t 'Unfilled'

set term aqua 9
set title 'Orders SALP'
plot  'arrayLargeCustomer.csv' index 0 using ($2):(0):($3-$2):($3-$2) w vectors lc 'green' t 'Filled', \
'arrayLargeCustomer.csv' index 1 using ($2):(0):($3-$2):($3-$2) w vectors lc 'red' t 'Unfilled'

