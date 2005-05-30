/*
 * Created on 29.05.2005
 *
 * TODO To change the template for this generated file go to
 * Window - Preferences - Java - Code Style - Code Templates
 */
package org.tmatesoft.svn.examples;

import java.io.FileOutputStream;
import java.util.Collection;
import java.util.Iterator;
import java.io.*;
import org.tmatesoft.svn.core.internal.io.dav.DAVRepositoryFactory;
import org.tmatesoft.svn.core.internal.io.svn.SVNRepositoryFactoryImpl;
import org.tmatesoft.svn.core.io.*;

/**
 * @author sinyushkin
 *
 * TODO To change the template for this generated type comment go to
 * Window - Preferences - Java - Code Style - Code Templates
 */
public class Export{

    public static void main(String[] args) {
        //for DAV (over http and https)
        DAVRepositoryFactory.setup();
        //for SVN (over svn and svn+ssh)
        SVNRepositoryFactoryImpl.setup();
        //the URL to the repository 
        String url = "svn://localhost/materials/rep";
        String exportPath = "N:\\materials\\exportDir\\";
//  	    String url = "http://72.9.228.230:8080/svn/jsvn/trunk/javasvn";
        try { 
            File exportDir = new File(exportPath);
            if(!exportDir.isDirectory()){
                throw new SVNException("The path is not a directory!");            
            }
            if(!exportDir.exists()){
                if(!exportDir.mkdir()){
                    throw new SVNException("The export directory can not be created!");
                }
            }
            //
            SVNRepositoryLocation location         = SVNRepositoryLocation.parseURL(url);
            //
            SVNRepository repository         	   = SVNRepositoryFactory.create(location);
            //
            ISVNCredentialsProvider scp            = new SVNSimpleCredentialsProvider("sa", "apollo13");
            //
            repository.setCredentialsProvider(scp);
            long exportRevision = -10;
            listEntries(repository, exportRevision, "", exportPath);
            System.out.println("Absolute path: "+exportDir.getAbsolutePath());
            if(exportRevision<0){
                //Get the latest revision of the repository
                System.out.println("Exported revision: "+repository.getLatestRevision());
            }else{
                System.out.println("Exported revision: "+exportRevision);
            }
        } catch (SVNException e) {
            e.printStackTrace();
        }
    }
    
    public static void listEntries(SVNRepository repository, long revision, String path, String exportPath) throws SVNException{
        Collection entries = repository.getDir(path, revision, null, (Collection)null);
        Iterator iterator = entries.iterator();
        while(iterator.hasNext()){
            SVNDirEntry entry = (SVNDirEntry)iterator.next();
            if(entry.getKind()==SVNNodeKind.DIR){
                File newDir = new File((exportPath.endsWith("\\") ? exportPath : exportPath+"\\")+entry.getName());
                if(!newDir.exists()){
                    if(!newDir.mkdir()){
                        throw new SVNException("The export subdirectory can not be created!");    
                    }
                }
                System.out.println("A   "+ (exportPath.endsWith("\\") ? exportPath : exportPath+"\\")+entry.getName());
                listEntries(repository, revision, (path.equals("")) ? entry.getName(): path+"/"+entry.getName(), (exportPath.endsWith("\\") ? exportPath : exportPath+"\\")+entry.getName());
            }else if(entry.getKind()==SVNNodeKind.FILE){
                File newFile = new File((exportPath.equals("") || exportPath.endsWith("\\") ? exportPath : exportPath+"\\")+entry.getName());
                try{
	                newFile.createNewFile();
	    	        FileOutputStream fos = new FileOutputStream(newFile);
	    	        repository.getFile((path.equals("")) ? entry.getName(): path+"/"+entry.getName(), -1, null, fos);
                    System.out.println("A   "+ (exportPath.endsWith("\\") ? exportPath : exportPath+"\\")+entry.getName());
                }catch(IOException ioe){
    	            throw new SVNException(ioe);
    	        }
            }
        }
    }
    
}
