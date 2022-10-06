#!/usr/bin/perl -s
use strict;

# A02 LEN  (days)
# A03 DEC  (*0.1) (range [0..10])
# A04 QTY  (*1e7) (range [0.1 ... 4] for one day's worth; up  to 120 for one month's worth)

#  ./transform.pl ../set-level-1 70 3 1 0.5


my ($dir, $day,$len,$dec,$qty) = @ARGV;
if (! defined $qty) { die "Usage: $0 dir day len qty\n"; }
my $naive = (defined $::naive)? 1 : 0;
print "naive=$naive\n";



($day>=0 && $len>=0 && $day+$len<=360) or die "Invalid day=$day or len=$len\n";
($dec>=0 && $dec<=100) or die "Invalid dec=$dec\n";
($qty>=0 && $qty<=100) or die "Invalid qty=$qty\n";


my $readme = "$dir/README";
open( G, ">$readme")  or die "Cannot write to $readme\n";

print G "The dis.*.csv files in this directory have been produced by $0, with arguments ". join(" ",@ARGV)+", naive=$naive, from the files in ".`pwd` . "\n";
print G "Disruptions will start on DAY=$day\n";
if ($naive) {
    print G "LEN type disruptions will continue for LEN=$len days, others for 1 day\n";
} else {
    print G "Disruptions will continue for LEN=$len days\n";
}
print G "For percentage-wise disruptions, the level will be DEC=$dec * 10%\n";
print G "For quantity-based disruptions, the daily depletion amonut will be QTY=$qty * 1e7\n";

# `cp ~/mason/work/charts.gnu $dir`;
# `cp ~/mason/work/config/pharma3.orig.csv $dir`;
# `echo exit >> $dir/charts.gnu`;



my @all = ();
my $head;

foreach my $f (`ls dis.A??.csv`) {
    $f =~ s/\s*$//;
    my $g = "$dir/$f";
    print "$f to $g\n";

    open(F, "<$f") or die "Cannot read $f\n";
    open(G, ">$g") or die "Cannot write to $g\n";
    $head=<F>;
    print G $head;
    my $body=<F>;
    (defined $body) or die "No data in $f\n";
    my $n = $naive?1 : ($body =~ /LEN/)? 1: $len;
    for(my $j=0; $j<$n; $j++) {
	my $s = $body;
	my $t = $day + $j;
	$s =~ s/DAY/$t/;
	$s =~ s/LEN/$len/;
	$s =~ s/DEC/$dec/;
	$s =~ s/QTY/$qty/;
	print G $s;
	@all = (@all, $s);
    }    
    close(F);
    close(G);

    my $g = "$dir/dis.all.csv";
    open(G, ">$g") or die "Cannot write to $g\n";
    print G $head;
    foreach my $s (@all) {
	print G $s;
    }
	
    close(G);
    
}

    

    
