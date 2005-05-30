/*
 * Created on 12.05.2005
 *
 * TODO To change the template for this generated file go to
 * Window - Preferences - Java - Code Style - Code Templates
 */
package org.tmatesoft.svn.examples;
import java.util.Set;
import java.io.*;

import org.tmatesoft.svn.core.diff.SVNDiffWindow;
import org.tmatesoft.svn.core.io.SVNSimpleCredentialsProvider;
import org.tmatesoft.svn.core.io.*;

import org.tmatesoft.svn.core.internal.io.dav.DAVRepositoryFactory;
import org.tmatesoft.svn.core.internal.io.svn.SVNRepositoryFactoryImpl;
import org.tmatesoft.svn.core.internal.ws.fs.FSEntryFactory;
import java.util.*;
import org.tmatesoft.svn.core.internal.*;
import org.tmatesoft.svn.core.*;

public class GetFile{
	public static void main(String[] args){
		// for DAV (over http and https)
	    DAVRepositoryFactory.setup();
	    
	    //for SVN (over svn and svn+ssh)
	    SVNRepositoryFactoryImpl.setup();

	    // Working copy storage (default is file system).
	    FSEntryFactory.setup();

	    String url = "svn://localhost/materials/rep";

//	    String URL = "http://72.9.228.230:8080/svn/jsvn/trunk";
	    try { 
	        SVNRepositoryLocation location        = SVNRepositoryLocation.parseURL(url);
	        SVNRepository repository              = SVNRepositoryFactory.create(location);
	        ISVNCredentialsProvider credsProvider = new SVNSimpleCredentialsProvider("me", "me");
	        repository.setCredentialsProvider(credsProvider);
	        File myFile = new File("N:\\materials\\temp.doc");
	        myFile.createNewFile();
	        FileOutputStream fos = new FileOutputStream(myFile);
	        Map fileProperties = new HashMap();
	        repository.getFile("/MyRepos/MyDiploma/Diploma.doc", -1, fileProperties, fos);
	        Iterator iterator = fileProperties.keySet().iterator();
	        while(iterator.hasNext()){
	            String propertyName  = (String)iterator.next();
	            String propertyValue = (String)fileProperties.get(propertyName);
	            System.out.println("File property: "+propertyName+"="+propertyValue);
	        }
	    } catch (SVNException e) {
	        e.printStackTrace();
	    }catch(Exception e){
	        e.printStackTrace();
	    }
	}
}
