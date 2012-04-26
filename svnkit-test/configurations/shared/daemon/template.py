#!/usr/bin/env python

import sys, string, re, os, getopt, stat, subprocess

line = string.join(sys.argv[1:])
p = re.compile('%pattern%')
m = p.match(line)

allargs=[]

if (m):
	allargs.append('ng.exe')
	allargs.append('%mainclass%')
	allargs.append('--nailgun-port')
	allargs.append('%port%')
	allargs.append('%name%')
else:
	allargs.append('%svn_home%/bin/%name%')

allargs.extend(sys.argv[1:])

p = subprocess.Popen(allargs, shell=True, close_fds=True)
p.communicate()

sys.exit(p.returncode)