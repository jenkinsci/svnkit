@echo off
set NG_MAINCLASS=%mainclass%
set NG_PORT=%port%

%NG% %NG_MAINCLASS% --nailgun-port %NG_PORT% %name% %*