@echo off

set DEFAULT_JAVASVN_HOME=%~dp0

if "%JAVASVN_HOME%"=="" set JAVASVN_HOME=%DEFAULT_JAVASVN_HOME%

set JAVASVN_CLASSPATH= "%JAVASVN_HOME%javasvn.jar";"%JAVASVN_HOME%javasvn-cli.jar";"%JAVASVN_HOME%ganymed.jar"
set JAVASVN_MAINCLASS=org.tmatesoft.svn.cli.SVN
set JAVASVN_OPTIONS=-Djava.util.logging.config.file="%JAVASVN_HOME%/logging.properties"

"%JAVA_HOME%\bin\java" %JAVASVN_OPTIONS% -cp %JAVASVN_CLASSPATH% %JAVASVN_MAINCLASS% %*