#!/usr/bin/perl
#-------------------------------------------------------
# Creates a ToC page for a directory
# Sample usage:
# /bin/ls *.zip | mk-index2.pl >> index.html
#-------------------------------------------------------

use strict;

my $dir = `pwd`;
$dir =~ s|\s+$||;
my $dirLast = $dir;
$dirLast  =~ s|.*/||;

print "<html>\n";
print "<head><title>$dirLast</title></head>\n";

print "<body>\n";
print "<h1>Directory of <tt>$dir</tt></h1>\n";
print "<ul>\n";

my @img = ();

foreach my $line (<>) {
    # print "L=$line";
    $line =~ s|\s+$||;
    #-- strip any trailng slash
    $line =~ s|/$||;
    $line =~ s|\*$||;
    if ($line =~ /\@$/) {
	#-- skip symlinks
    } else {
	print "<li><a href=\"$line\">$line</a>\n";
	if ($line =~ /\.png$/) {
	    @img = (@img, $line);
	}
    }
}

print "</ul>\n";

if (scalar(@img)>0) {
    print "<table border='1'>\n";
    foreach my $im (@img) {
	print "<tr><td align='center'>$im<br>\n";
	print "<img src=\"$im\">\n";
	print "</td></tr>\n";
	
    }
    print "</table>\n";
}


print "</body>\n";
print "</html>\n";
