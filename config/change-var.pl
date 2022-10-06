#!/usr/bin/perl -p
s|0.25/|0.999/|;
s|1.75/|1.001/|;
# s/faulty,Triangular,0,0.02,0.04/faulty,Triangular,0.019,0.02,0.021/;

s/(faulty,Triangular),([0-9\.]+),([0-9\.]+),([0-9\.]+)/$1,$3,$3,$3/;
