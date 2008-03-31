@echo off

set DEFAULT_SVNKIT_HOME=%~dp0

if "%SVNKIT_HOME%"=="" set SVNKIT_HOME=%DEFAULT_SVNKIT_HOME%

set SVNKIT_CLASSPATH= "%SVNKIT_HOME%svnkit.jar";"%SVNKIT_HOME%svnkit-cli.jar";"%SVNKIT_HOME%trilead.jar";"%SVNKIT_HOME%jna.jar"
set SVNKIT_MAINCLASS=org.tmatesoft.svn.cli.SVNAdmin
set SVNKIT_OPTIONS=-Djava.util.logging.config.file="%SVNKIT_HOME%/logging.properties"
set PATH=%PATH%;%SVNKIT_HOME%

"%JAVA_HOME%\bin\java" %SVNKIT_OPTIONS% -cp %SVNKIT_CLASSPATH% %SVNKIT_MAINCLASS% %*