
set datafile separator ','

#-- this does not seem to work
set grid mxtics mytic

#-- to make sure the tic labels on the Y-axis fit into the PNG image
set lmargin 8
set grid mxtics mytics

set term aqua 0

set title 'Consumption of safety stocks' 
plot \
'ApiProduction.safety.RawMaterial.csv' using ($1):($7)   with lines title 'RM \@ ApiProd', \
'DrugProduction.safety.Api.csv' using ($1):($7)   with lines title 'API \@ DrugProd', \
'DrugProduction.safety.Excipient.csv' using ($1):($7)   with lines title 'Excipient \@ DrugProd', \
'Packaging.safety.BulkDrug.csv' using ($1):($7)   with lines title 'Bulk drug \@ Packgn', \
'Packaging.safety.PackagingMaterial.csv' using ($1):($7)   with lines title 'PacMat \@ Packgn'
set term png size 800,600
set out 'Safety-consumption.png'
replot


set term aqua 1

set title 'Level of safety stocks' 
plot \
'ApiProduction.safety.RawMaterial.csv' using ($1):($2)   with lines title 'RM \@ ApiProd', \
'DrugProduction.safety.Api.csv' using ($1):($2)   with lines title 'API \@ DrugProd', \
'DrugProduction.safety.Excipient.csv' using ($1):($2)   with lines title 'Excipient \@ DrugProd', \
'Packaging.safety.BulkDrug.csv' using ($1):($2)   with lines title 'Bulk drug \@ Packgn', \
'Packaging.safety.PackagingMaterial.csv' using ($1):($2)   with lines title 'PacMat \@ Packgn'
set term png size 800,600
set out 'Safety-consumption.png'
replot
