
set term qt 0

set title 'Inventory at CMO production tracks' 
plot \
'CmoTrackA.csv' using ($1):($4)   with lines title 'A', \
'CmoTrackB.csv'  using ($1):($4)  with lines title 'B', \
'CmoTrackC.csv'  using ($1):($4)  with lines title 'C', \
'CmoTrackD.csv'  using ($1):($4)  with lines title 'D'



set term qt 1

set title 'Use of safety stocks' 
plot \
'ApiProduction.safety.RawMaterial.csv' using ($1):($7)   with lines title 'RM \@ ApiProd', \
'DrugProduction.safety.Api.csv' using ($1):($7)   with lines title 'API \@ DrugProd', \
'DrugProduction.safety.Excipient.csv' using ($1):($7)   with lines title 'Excipient \@ DrugProd', \
'Packaging.safety.BulkDrug.csv' using ($1):($7)   with lines title 'Bulk drug \@ Packgn', \
'Packaging.safety.PackagingMaterial.csv' using ($1):($7)   with lines title 'PacMat \@ Packgn', \
