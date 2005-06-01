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
import org.tmatesoft.svn.core.io.ISVNCredentialsProvider;
import org.tmatesoft.svn.core.io.SVNDirEntry;
import org.tmatesoft.svn.core.io.SVNException;
import org.tmatesoft.svn.core.io.SVNNodeKind;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.io.SVNRepositoryFactory;
import org.tmatesoft.svn.core.io.SVNRepositoryLocation;
import org.tmatesoft.svn.core.io.SVNSimpleCredentialsProvider;

/*
 * This example shows how to get the repository tree at the latest (HEAD) revision 
 * starting with the directory that is the path/to/repository part of the repository 
 * location URL. The main point is SVNRepository.getDir() method that is called 
 * recursively for each directory (till the end of the tree). getDir collects all 
 * entries located inside a directory and returns them as a java.util.Collection.
 * As an example here's one of the program layouts:
 * 
 * Repository Root: /svn/jsvn
 * Repository UUID: 0a862816-5deb-0310-9199-c792c6ae6c6e
 * /ISVNDirectoryContent.java (author:alex; revision:66)
 * /ISVNFileEntry.java (author:alex; revision:420)
 * /ISVNEntry.java (author:alex; revision:424)
 * /ISVNExternalsHandler.java (author:alex; revision:33)
 * /ISVNWorkspaceListener.java (author:alex; revision:33)
 * /progress (author:alex; revision:446)
 * /progress/SVNProgressCancelledException.java (author:alex; revision:363)
 * /progress/ISVNProgressViewer.java (author:alex; revision:446)
 * /progress/SVNProgressViewerIterator.java (author:alex; revision:446)
 * /progress/SVNProgressDummyViewer.java (author:alex; revision:446)
 * /progress/SVNProgressRangeViewer.java (author:alex; revision:446)
 * /ISVNDirectoryEntry.java (author:alex; revision:409)
 * /diff (author:alex; revision:550)
 * /diff/SVNDiffInstruction.java (author:alex; revision:33)
 * /diff/ISVNDeltaConsumer.java (author:alex; revision:33)
 * /diff/SVNDiffWindow.java (author:alex; revision:151)
 * /diff/delta (author:alex; revision:550)
 * ---------------------------------------------
 * Repository latest revision: 644
 */
public class DisplayRepositoryTree{
    /*
     * args parameter is used to obtain a repository location URL, user's account name
     * & password to authenticate him to the server. 
     */
    public static void main(String[] args){
        /*
         * default values:
         */
        String url      = "svn://localhost/path/to/repository";//"http://72.9.228.230:8080/svn/jsvn/trunk/javasvn";
        String name     = "user";
        String password = "password";
        
        /*
         * initializes the library (it must be done before ever using the library 
         * itself) 
         */
        setupLibrary();
        if (args != null) {
            /*
             * obtains a repository location URL
             */
            url      = (args.length>=1) ? args[0] : url; 
            /*
             * obtains an account name (will be used to authenticate the user to the 
             * server)
             */
            name     = (args.length>=2) ? args[1] : name;
            /*
             * obtains a password
             */
            password = (args.length>=3) ? args[2] : password;
        }

        try { 
            /*
             * Parses the URL string and creates an SVNRepositoryLocation which 
             * represents the repository location (you can think of this location as of 
             * a current repository session directory; it can be any versioned
             * directory inside the repository).  
             */ 
            SVNRepositoryLocation location         = SVNRepositoryLocation.parseURL(url);
            /*
             * Creates an instance of SVNRepository to work with the repository. All 
             * user's requests to the repository are relative to the repository 
             * location used to create this SVNRepository.
             * 
             */
            SVNRepository repository         	   = SVNRepositoryFactory.create(location);
            /*
             * creates a user's credentials provider
             */
            ISVNCredentialsProvider scp            = new SVNSimpleCredentialsProvider(name, password);
            /*
             * Sets the provider of the user's credentials that will be used to
             * authenticate the user to the server (if needed) during operations 
             * handled by SVNRepository
             */
            repository.setCredentialsProvider(scp);
            /*
             * Tests if the repository server can be connected
             */
            repository.testConnection();
            /*
             * getRepositoryRoot returns the actual root directory where the repository
             * was created
             */
            System.out.println("Repository Root: "+repository.getRepositoryRoot());
            /*
             * getRepositoryUUID returns Universal Unique IDentifier (UUID) - an 
             * identifier of the repository
             */
            System.out.println("Repository UUID: "+repository.getRepositoryUUID());
            
            /*
             * Displays the repository tree at the current path - "" (what means the 
             * path/to/repository directory) 
             */
            listEntries(repository, "");
            
            /*
             * Gets the latest revision number of the repository
             */
            long latestRevision = repository.getLatestRevision();
            System.out.println("---------------------------------------------");
            System.out.println("Repository latest revision: "+latestRevision);
            
        } catch (SVNException e) {
            e.printStackTrace();
        }
    }
	
    /*
	 * Initializes the library to work with a repository either via svn:// 
	 * (or svn+ssh://) or via http:// (https://)
	 */
    public static void setupLibrary(){
		/*
		 * for DAV (over http and https)
		 */
	    DAVRepositoryFactory.setup();
	    
	    /*
	     * for SVN (over svn and svn+ssh)
	     */
	    SVNRepositoryFactoryImpl.setup();
	}
    
    /*
     * Called recursively to obtain all entries that make up the repository tree
     * repository - an SVNRepository which interface is used to carry out the request,
     * in this case it's a request to get all entries in the directory located at 
     * the path parameter;
     * 
     * path is a directory path relative to the repository location path (that is a 
     * part of the URL used to create an SVNRepository instance);
     * 
     */
    public static void listEntries(SVNRepository repository, String path) throws SVNException{
        /*
         * Gets the contents of the directory specified by path at the latest revision
         * (for this purpose -1 is used here as the revision number to mean HEAD-revision)
         * getDir returns a Collection of SVNDirEntry elements. SVNDirEntry represents 
         * information about the directory entry. Here this information is used 
         * to get the entry name, the name of the person who last changed this entry, the
         * number of the revision when it was last changed and the entry type to 
         * determine whether it's a directory or a file. If it's a directory 
         * listEntries steps into a next recursion to display the contents of this
         * directory.
         * The third parameter of getDir is null and means that a user is not 
         * interested in directory properties.
         * The fourth one is null, too - the user doesn't provide its own Collection
         * instance and uses the one returned by getDir.   
         */
        Collection entries = repository.getDir(path, -1,null, (Collection)null);
        Iterator iterator = entries.iterator();
        while(iterator.hasNext()){
            SVNDirEntry entry = (SVNDirEntry)iterator.next();
            System.out.println("/"+(path.equals("") ? "":path+"/")+entry.getName()+" (author:"+entry.getAuthor()+"; revision:"+entry.getRevision()+")");
            /*
             * Checking up if the entry is a directory.
             */
            if(entry.getKind()==SVNNodeKind.DIR){
                listEntries(repository, (path.equals("")) ? entry.getName(): path+"/"+entry.getName());
            }
        }
    }
}
