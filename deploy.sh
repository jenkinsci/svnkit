# run it as ". ./deploy.sh"
tiger
ant clean build-src build-library

version=1.2.2-hudson-1

# be sure to update pom.xml and svnkit.build.properties

#cmd=install:install-file
cmd=deploy:deploy-file
mvn $cmd -Dfile=build/lib/svnkit.jar -DrepositoryId=java.net2 -Durl=file:$(cygpath -wa ~m2repo) -DpomFile=pom.xml
mvn $cmd -Dfile=build/lib/svnkitsrc.zip -DrepositoryId=java.net2 -Durl=file:$(cygpath -wa ~m2repo) -DpomFile=pom.xml -Dclassifier=sources
