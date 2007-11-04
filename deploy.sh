# run it as ". ./deploy.sh"
tiger
ant clean build-src build-library

dir=/kohsuke/projects/hudson/hudson/main/lib/org.tmatesoft
version=1.1.4-hudson-4

# be sure to update pom.xml and svnkit.build.properties

mvn deploy:deploy-file -Dfile=build/lib/svnkit.jar -DrepositoryId=java.net2 -Durl=file:$(cygpath -wa ~m2repo) -DpomFile=pom.xml
mvn deploy:deploy-file -Dfile=build/lib/svnkitsrc.zip -DrepositoryId=java.net2 -Durl=file:$(cygpath -wa ~m2repo) -DpomFile=pom.xml -Dclassifier=sources
