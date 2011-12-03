# run it as ". ./deploy.sh"
./gradlew clean assemble

# be sure to update POM as well
version=1.3.6.1-jenkins-2
v=1.3.6-v1

animal-sniffer -t Java5 svnkit/build/libs/svnkit-$v.jar
if [ $? != 0 ]; then
    echo "Incompatible classes"
    exit 1
fi

#cmd=install:install-file
cmd=deploy:deploy-file
mvn $cmd -Dfile=svnkit/build/libs/svnkit-$v.jar    -DrepositoryId=maven.jenkins-ci.org -Durl=http://maven.jenkins-ci.org:8081/content/repositories/releases -DpomFile=pom.xml
mvn $cmd -Dfile=svnkit/build/libs/svnkit-$v-sources.jar -DrepositoryId=maven.jenkins-ci.org -Durl=http://maven.jenkins-ci.org:8081/content/repositories/releases -DpomFile=pom.xml -Dclassifier=sources
