/*
 * Created on 27.05.2005
 *
 * TODO To change the template for this generated file go to
 * Window - Preferences - Java - Code Style - Code Templates
 */

import java.io.PrintStream;
import java.util.Collection;
import java.util.Iterator;

import org.tmatesoft.svn.core.internal.io.dav.DAVRepositoryFactory;
import org.tmatesoft.svn.core.internal.io.svn.SVNRepositoryFactoryImpl;
import org.tmatesoft.svn.core.internal.ws.fs.FSEntryFactory;
import org.tmatesoft.svn.core.io.ISVNCredentialsProvider;
import org.tmatesoft.svn.core.io.SVNDirEntry;
import org.tmatesoft.svn.core.io.SVNException;
import org.tmatesoft.svn.core.io.SVNNodeKind;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.io.SVNRepositoryFactory;
import org.tmatesoft.svn.core.io.SVNRepositoryLocation;
import org.tmatesoft.svn.core.io.SVNSimpleCredentialsProvider;

/**
 * @author sinyushkin
 *
 * TODO To change the template for this generated type comment go to
 * Window - Preferences - Java - Code Style - Code Templates
 */
public class GetRepositoryTree{
    
    public static void main(String[] args){
        // for DAV (over http and https)
        DAVRepositoryFactory.setup();
            	    
        //for SVN (over svn and svn+ssh)
        SVNRepositoryFactoryImpl.setup();

        // Working copy storage (default is file system).
        FSEntryFactory.setup();
     
        String url = "svn://localhost/materials/rep";

//  	    String url = "http://72.9.228.230:8080/svn/jsvn/trunk/javasvn";
        try { 
            //
            SVNRepositoryLocation location         = SVNRepositoryLocation.parseURL(url);
            //
            SVNRepository repository         	   = SVNRepositoryFactory.create(location);
            //
            ISVNCredentialsProvider scp            = new SVNSimpleCredentialsProvider("sa", "apollo13");
            //
            repository.setCredentialsProvider(scp);
            
            PrintStream ps = new PrintStream(System.out);
            listEntries(repository, "", ps);
            
            //Get the latest revision of the repository
            long latestRevision = repository.getLatestRevision();
            ps.println("---------------------------------------------");

            ps.println("Repository latest revision: "+latestRevision);
            
        } catch (SVNException e) {
            e.printStackTrace();
        }
    }
    
    public static void listEntries(SVNRepository repository, String path, PrintStream ps) throws SVNException{
        Collection entries = repository.getDir(path, -1,null, (Collection)null);
        Iterator iterator = entries.iterator();
        while(iterator.hasNext()){
            SVNDirEntry entry = (SVNDirEntry)iterator.next();
            ps.println("/"+(path.equals("") ? "":path+"/")+entry.getName()+" (author:"+entry.getAuthor()+"; revision:"+entry.getRevision()+")");
            if(entry.getKind()==SVNNodeKind.DIR){
                listEntries(repository, (path.equals("")) ? entry.getName(): path+"/"+entry.getName(), ps);
            }
        }
    }

}
