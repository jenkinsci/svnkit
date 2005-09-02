@echo off

set DEFAULT_JAVASVN_HOME=%~dp0

if "%JAVASVN_HOME%"=="" set JAVASVN_HOME=%DEFAULT_JAVASVN_HOME%

set JAVASVN_CLASSPATH= "%JAVASVN_HOME%javasvn.jar";"%JAVASVN_HOME%javasvn-cli.jar";"%JAVASVN_HOME%jsch.jar";"%JAVASVN_HOME%commons-codec-1.3.jar";"%JAVASVN_HOME%commons-httpclient-3.0-rc3.jar";"%JAVASVN_HOME%commons-logging.jar"
set JAVASVN_MAINCLASS=org.tmatesoft.svn.cli.SVN
set JAVASVN_OPTIONS=-Djava.util.logging.config.file=%JAVASVN_HOME%/logging.properties

REM
REM uncomment the line below to make JavaSVN use jakarta httpclient instead of built in http implementation.
REM
REM set JAVASVN_OPTIONS=%JAVASVN_OPTIONS% -Djavasvn.httpclient=jakarta
REM

"%JAVA_HOME%\bin\java" %JAVASVN_OPTIONS% -cp %JAVASVN_CLASSPATH% %JAVASVN_MAINCLASS% %*