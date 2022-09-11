
set term qt 0

set title 'Inventory at CMO production steps' 
plot \
'CmoTrackA.csv' using ($1):($4)   with lines title 'A', \
'CmoTrackB.csv'  using ($1):($4)  with lines title 'B', \
'CmoTrackC.csv'  using ($1):($4)  with lines title 'C', \
'CmoTrackD.csv'  using ($1):($4)  with lines title 'D'

