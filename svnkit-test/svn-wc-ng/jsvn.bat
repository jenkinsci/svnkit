@echo off
setlocal enabledelayedexpansion
set IS_STATUS=false
if "%1"=="status" (set IS_STATUS=true)
if "%2"=="-u" (set IS_STATUS=false)
if "!IS_STATUS!"=="false" (
if "%1"=="add" (
bash -c '%path.svn%/svn.exe %1 "$(cygpath -au %2)" %3 %4 %5 %6 %7 %8 %9'
exit
) else (
%path.svn%/svn.exe %*
exit
)
exit
)

set DEFAULT_SVNKIT_HOME=%~dp0

if "%SVNKIT_HOME%"=="" set SVNKIT_HOME=%DEFAULT_SVNKIT_HOME%

set SVNKIT_CLASSPATH= "%SVNKIT_HOME%svnkit.jar";"%SVNKIT_HOME%svnkit-cli.jar";"%SVNKIT_HOME%trilead.jar";"%SVNKIT_HOME%jna.jar";"%SVNKIT_HOME%sqljet.%sqljet.version%.jar";"%SVNKIT_HOME%antlr-runtime-%antlr.version%.jar"
set SVNKIT_MAINCLASS=org.tmatesoft.svn.cli.svn.SVN
set SVNKIT_OPTIONS=%SVNKIT_OPTIONS% -Djava.util.logging.config.file="%SVNKIT_HOME%/logging.properties"

"%JAVA_HOME%\bin\java" %SVNKIT_OPTIONS% -cp %SVNKIT_CLASSPATH% %SVNKIT_MAINCLASS% %*
