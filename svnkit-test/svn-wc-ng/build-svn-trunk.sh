#/bin/bash

cd ./svn-trunk
make clean
sh ./autogen.sh && ./configure --enable-maintainer-mode --disable-shared CFLAGS=-DSVN_EXPERIMENTAL_PRISTINE && make
