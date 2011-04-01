# run it as ". ./deploy.sh"
tiger
ant clean build-src build-library

animal-sniffer -t Java5 build/lib/svnkit.jar
if [ $? != 0 ]; then
    echo "Incompatible classes"
    exit 1
fi

version=1.3.4-jenkins-4

# be sure to update pom.xml and svnkit.build.properties

cmd=install:install-file
#cmd=deploy:deploy-file
mvn $cmd -Dfile=build/lib/svnkit.jar    -DrepositoryId=maven.jenkins-ci.org -Durl=http://maven.jenkins-ci.org:8081/content/repositories/releases -DpomFile=pom.xml
mvn $cmd -Dfile=build/lib/svnkitsrc.zip -DrepositoryId=maven.jenkins-ci.org -Durl=http://maven.jenkins-ci.org:8081/content/repositories/releases -DpomFile=pom.xml -Dclassifier=sources
