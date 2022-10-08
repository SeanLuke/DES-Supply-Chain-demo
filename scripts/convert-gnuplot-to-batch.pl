#!/usr/bin/perl

#-------------------------------------------------------------------
#-- This scripts converts a gnuplot command file (such as charts.gnu)
#-- to a "batch" (non-interactive) format. While the input file
#-- produces both the screen and PNG output, the output file
#-- will only produce PNG output. Thus, if the input file was
#-- designed for use at a home computer, the output file can also
#-- be used on a remote server.
#----------------------------------------------------------------
#-- A section of a typical input file
#
# set term qt 0
# set grid mxtics mytics
# set title 'Hospital/Pharmacy Pool'
# plot 'HospitalPool.csv'  using ($1):($2)  with lines title 'HP.Stock', \
# 'HospitalPool.csv'  using ($1):($3)  with impulses title 'HP.Ordered', \
# 'HospitalPool.csv'  using  ($1):($4)  with lines title 'HP.Received'
# set term png size 800,600
# set out 'HospitalPool.png'
# replot
#----------------------------------------------------------------------

use strict;

my ($in) = @ARGV;

open( F, "<$in") or die "Cannot read file $in\n";

my @lines = <F>;

close(F);

my $saving = 0;
my @saved = ();

for my $line (@lines) {
    my $s = $line;
    $s =~ s/^\s*//;
      
    if ($saving) {  #-- "save" these lines to print them later, in place of "replot"	
	if ($s =~ /^set term png/) {
	    $saving = 0;
	    print $line;
	} else {
	    @saved = (@saved, $line);
	}	    
    } else {
	if ($s =~ /^set term qt/) {
	    $saving = 1;
	} elsif ($s =~ /^replot/) {
	    print join("", @saved);
	    @saved = ();
	    $saving = 0;
	} else {
	    print $line;
	}
    }
    
}

print "\nexit\n";
