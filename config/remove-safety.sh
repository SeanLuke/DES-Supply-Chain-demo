#!/usr/bin/perl -p

#-- This script takes a config file and comments out all lines
#-- referring to safety stocks.
#-- Usage:
#--   ./remove-safety.sh pharma3.orig.csv > tmp.csv

s/(^[^#].*\.safety)/#$1/
