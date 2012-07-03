#!/usr/bin/perl

my $command_line = "";

foreach $argnum (0 .. $#ARGV) {
   $command_line .= "'@ARGV[$argnum]' ";
}
if ($command_line =~ m/%pattern%/) {
   exit 0;
}
exit 1;
