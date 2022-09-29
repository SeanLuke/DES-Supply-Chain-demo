#!/usr/bin/perl -p

#-- Processes the output of graph-analysis.sh for "prettier" inclusion into HTML files.

s/(alpha|beta|gamma)/&$1;/g
