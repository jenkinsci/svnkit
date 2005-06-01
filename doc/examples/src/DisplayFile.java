/*
 * Created on 12.05.2005
 *
 * TODO To change the template for this generated file go to
 * Window - Preferences - Java - Code Style - Code Templates
 */

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.tmatesoft.svn.core.internal.io.dav.DAVRepositoryFactory;
import org.tmatesoft.svn.core.internal.io.svn.SVNRepositoryFactoryImpl;
import org.tmatesoft.svn.core.io.ISVNCredentialsProvider;
import org.tmatesoft.svn.core.io.SVNException;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.io.SVNRepositoryFactory;
import org.tmatesoft.svn.core.io.SVNRepositoryLocation;
import org.tmatesoft.svn.core.io.SVNSimpleCredentialsProvider;
import org.tmatesoft.svn.core.SVNProperty;
/*
 * This example shows how to fetch a file and its properties from the repository at the
 * latest (HEAD) revision . If the file is a text (either it has no svn:mime-type 
 * property at all or if has and the property value is text/-like) its contents as well 
 * as properties will be displayed in the console, otherwise - only properties.
 * As an example here's one of the program layouts:
 * 
 * File property: svn:entry:checksum=d823f4a4eccbb16535031aaccf871880
 * File property: svn:entry:revision=16
 * File property: svn:entry:last-author=me
 * File property: svn:entry:uuid=466bc291-b22d-3743-ba76-018ba5011628
 * File property: svn:entry:committed-date=2005-06-01T08:29:47.859375Z
 * File property: svn:entry:committed-rev=16
 * File contents:
 *
 * propset (pset, ps): Set PROPNAME to PROPVAL on files, dirs, or revisions.
 * usage: 1. propset PROPNAME [PROPVAL | -F VALFILE] PATH...
 *        2. propset PROPNAME --revprop -r REV [PROPVAL | -F VALFILE] [URL]
 *
 * 1. Creates a versioned, local propchange in working copy.
 * 2. Creates an unversioned, remote propchange on repos revision.
 *
 * Note: svn recognizes the following special versioned properties
 * but will store any arbitrary properties set:
 *   svn:ignore     - A newline separated list of file patterns to ignore.
 *   svn:keywords   - Keywords to be expanded.  Valid keywords are:
 *     URL, HeadURL             - The URL for the head version of the object.
 *     Author, LastChangedBy    - The last person to modify the file.
 *     Date, LastChangedDate    - The date/time the object was last modified.
 *     Rev, Revision,           - The last revision the object changed.
 *     LastChangedRevision
 *     Id                       - A compressed summary of the previous
 *                                  4 keywords.
 *   svn:executable - If present, make the file executable.
 *   svn:eol-style  - One of 'native', 'LF', 'CR', 'CRLF'.
 *   svn:mime-type  - The mimetype of the file.  Used to determine
 *     whether to merge the file, and how to serve it from Apache.
 *     A mimetype beginning with 'text/' (or an absent mimetype) is
 *     treated as text.  Anything else is treated as binary.
 *   svn:externals  - A newline separated list of module specifiers,
 *     each of which consists of a relative directory path, optional
 *     revision flags, and an URL.  For example
 *       foo             http://example.com/repos/zig
 *       foo/bar -r 1234 http://example.com/repos/zag
 *   svn:needs-lock - If present, indicates that the file should be locked
 *     before it is modified.  Makes the working copy file read-only
 *     when it is not locked.
 * The svn:keywords, svn:executable, svn:eol-style, svn:mime-type and
 * svn:needs-lock properties cannot be set on a directory.  A non-recursive
 * attempt will fail, and a recursive attempt will set the property
 * only on the file children of the directory.
 * 
 * Valid options:
 * -F [--file] arg          : read data from file ARG
 * --encoding arg           : treat value as being in charset encoding ARG
 * -q [--quiet]             : print as little as possible
 * -r [--revision] arg      : ARG (some commands also take ARG1:ARG2 range)
 *                            A revision argument can be one of:
 *                               NUMBER       revision number
 *                               "{" DATE "}" revision at start of the date
 *                               "HEAD"       latest in repository
 *                               "BASE"       base rev of item's working copy
 *                               "COMMITTED"  last commit at or before BASE
 *                               "PREV"       revision just before COMMITTED
 * --targets arg            : pass contents of file ARG as additional args
 * -R [--recursive]         : descend recursively
 * --revprop                : operate on a revision property (use with -r)
 * --username arg           : specify a username ARG
 * --password arg           : specify a password ARG
 * --no-auth-cache          : do not cache authentication tokens
 * --non-interactive        : do no interactive prompting
 * --force                  : force operation to run
 * --config-dir arg         : read user configuration files from directory ARG
 *
 * ---------------------------------------------
 * Repository latest revision: 16
 */
public class DisplayFile{
    /*
     * args parameter is used to obtain
     * a repository location URL, user's account name & password to authenticate him
     * to the server, the file path in the rpository (the file path should be relative 
     * to the the path/to/repository part of the repository location URL). 
     */
   public static void main(String[] args){
       /*
        * Default values:
        */ 
       String url      = "svn://localhost/path/to/repository";//"http://72.9.228.230:8080/svn/jsvn/trunk/javasvn";
       String name     = "user";
       String password = "password";
       String filePath = "/path/to/file";
       /*
        * Initializes the library (it must be done before ever using the library 
        * itself) 
        */
       setupLibrary();
       
       if (args != null) {
           /*
            * Obtains a repository location URL
            */
           url      = (args.length>=1) ? args[0] : url; 
           /*
            * Obtains an account name (will be used to authenticate the user to 
            * the server)
            */
           name     = (args.length>=2) ? args[1] : name;
           /*
            * Obtains a password
            */
           password = (args.length>=3) ? args[2] : password;
           /*
            * Obtains a file path 
            */
           filePath = (args.length>=4) ? args[3] : filePath;
       }

       try { 
           /*
            * Parses the URL string and creates an SVNRepositoryLocation which 
            * represents the repository location (you can think of this location as of 
            * a current repository session directory; it can be any versioned
            * directory inside the repository).  
            */ 
           SVNRepositoryLocation location = SVNRepositoryLocation.parseURL(url);
           /*
            * Creates an instance of SVNRepository to work with the repository. All 
            * user's requests to the repository are relative to the repository 
            * location used to create this SVNRepository.
            * 
            */
           SVNRepository repository = SVNRepositoryFactory.create(location);
           /*
            * Creates a usre's credentials provider
            */
           ISVNCredentialsProvider scp = new SVNSimpleCredentialsProvider(name, password);
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
	        * This Map will be used to get the file properties. Each Map key is a 
	        * property name and the value associated with the key is the property value.
	        * 
	        */
           Map fileProperties = new HashMap();
	       /*
	        * Gets only properties of the file located at filePath in the repository
	        * at the latest revision (which is meant by a negative revision number).
	        */
           repository.getFile(filePath, -1, fileProperties, null);
	       
           boolean isTextType = true;
	       
           Iterator iterator = fileProperties.keySet().iterator();
	       /*
	        * Displays file properties.
	        */
           while(iterator.hasNext()){
               String propertyName  = (String)iterator.next();
               String propertyValue = (String)fileProperties.get(propertyName);
               System.out.println("File property: "+propertyName+"="+propertyValue);
               /*
                * Here the SVNProperty class is used to find out if the current 
                * property name is svn:-like.  SVNProperty is used to facilitate
                * the  work with versioned properties. 
                */
               if(SVNProperty.isSVNProperty(propertyName)){
                   /*
                    * Checking up if the property is the mime-type property
                    */
                   if(SVNProperty.MIME_TYPE.equals(propertyName)){
                       /*
                        * The static SVNProperty.isTextType method checks up the 
                        * value of the mime-type file property and says if 
                        * the file is a text (true) or not (false).
                        */
                       isTextType = SVNProperty.isTextType(propertyValue);
                   }
               }
	        }

           if(isTextType){
               System.out.println("File contents:");
               System.out.println();
               /*
                * Now calling getFile once more not for the file properties
                * but for its contents to display them in the console. 
                */
               repository.getFile(filePath, -1, null, System.out);
           }else{
               System.out.println("File contents can not be displayed in the console since the mime-type property says that it's not a kind of a text file.");
           }
           /*
            * Gets the latest revision number of the repository
            */
           long latestRevision = repository.getLatestRevision();
           System.out.println("---------------------------------------------");
           System.out.println("Repository latest revision: "+latestRevision);
       }catch (SVNException e) {
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
}
