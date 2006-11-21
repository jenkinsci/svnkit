/*
 * ====================================================================
 * Copyright (c) 2004-2006 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.core.io;

import java.io.OutputStream;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

import org.tmatesoft.svn.core.ISVNDirEntryHandler;
import org.tmatesoft.svn.core.ISVNLogEntryHandler;
import org.tmatesoft.svn.core.SVNDirEntry;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNLock;
import org.tmatesoft.svn.core.SVNLogEntry;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationManager;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.io.diff.SVNDiffWindow;
import org.tmatesoft.svn.util.ISVNDebugLog;
import org.tmatesoft.svn.util.SVNDebugLog;

/**
 * The abstract class <b>SVNRepository</b> provides an interface for protocol
 * specific drivers used for direct working with a Subversion repository. 
 * <b>SVNRepository</b> joins all low-level API methods needed for repository
 * access operations.
 * 
 * <p>
 * In particular this low-level protocol driver is used by the high-level API 
 * (represented by the <B><A HREF="../wc/package-summary.html">org.tmatesoft.svn.core.wc</A></B> package) 
 * when an access to a repository is needed. 
 * 
 * <p>
 * It is important to say that before using the library it must be configured 
 * according to implimentations to be used. That is if a repository is assumed
 * to be accessed either via the <i>WebDAV</i> protocol (<code>http://</code> or 
 * <code>https://</code>), or a custom <i>svn</i> one (<code>svn://</code> or <code>svn+ssh://</code>) 
 * or immediately on the local machine (<code>file:///</code>) a user must initialize the library
 * in a proper way:
 * <pre class="javacode">
 * <span class="javacomment">//import neccessary classes</span>
 * <span class="javakeyword">import</span> org.tmatesoft.svn.core.internal.io.fs.FSRepositoryFactory;
 * <span class="javakeyword">import</span> org.tmatesoft.svn.core.internal.io.dav.DAVRepositoryFactory;
 * <span class="javakeyword">import</span> org.tmatesoft.svn.core.internal.io.svn.SVNRepositoryFactoryImpl;
 * <span class="javakeyword">import</span> org.tmatesoft.svn.core.SVNURL;
 * <span class="javakeyword">import</span> org.tmatesoft.svn.core.io.SVNRepository;
 * <span class="javakeyword">import</span> org.tmatesoft.svn.core.io.SVNRepositoryFactory;
 * <span class="javakeyword">import</span> import org.tmatesoft.svn.core.wc.SVNWCUtil;
 * <span class="javakeyword">import</span> import org.tmatesoft.svn.core.SVNException;
 * ...
 * 
 * <span class="javacomment">//Set up connection protocols support:</span>
 * <span class="javacomment">//http:// and https://</span>
 * DAVRepositoryFactory.setup();
 * <span class="javacomment">//svn://, svn+xxx:// (svn+ssh:// in particular)</span>
 * SVNRepositoryFactoryImpl.setup();
 * <span class="javacomment">//file:///</span>
 * FSRepositoryFactory.setup();
 * </pre>
 * <code>svn+xxx://</code> can be any tunnel scheme for tunneled working with a 
 * repository. <code>xxx</code> URL scheme is looked up in the section <code>tunnels</code> of the 
 * standard Subversion <code>config</code> file.
 * 
 * <p>
 * So, only after these setup steps the client can create http | svn | file protocol 
 * implementations of the <code>SVNRepository</code> abstract class to access 
 * the repository.
 * 
 * <p>
 * This is a general way how a user creates an <b>SVNRepository</b> driver object:
 * <pre class="javacode">
 * String url=<span class="javastring">"http://svn.collab.net/svn/trunk"</span>;
 * String name=<span class="javastring">"my name"</span>;
 * String password=<span class="javastring">"my password"</span>;
 * repository = <span class="javakeyword">null</span>;
 * <span class="javakeyword">try</span> { 
 *     repository = SVNRepositoryFactory.create(SVNURL.parseURIDecoded(url));
 *     ISVNAuthenticationManager authManager = 
 *                  SVNWCUtil.createDefaultAuthenticationManager(name, password);
 *     repository.setAuthenticationManager(authManager);
 *     ...
 * } <span class="javakeyword">catch</span> (SVNException e){
 *     e.printStackTrace();
 *     System.exit(1);
 * }
 * <span class="javacomment">//work with the repository</span>
 * ... </pre>
 * <p>
 * <b>SVNRepository</b> objects are not thread-safe, we're strongly recommend
 * you not to use one <b>SVNRepository</b> object from within multiple threads.  
 * 
 * <p>
 * Also methods of <b>SVNRepository</b> objects are not reenterable - that is, 
 * you can not call operation methods of an <b>SVNRepository</b> driver neither 
 * from within those handlers that are passed to some of the driver's methods, nor 
 * during committing with the help of a commit editor (until the editor's {@link ISVNEditor#closeEdit() closeEdit()} 
 * method is called). 
 * 
 * <p>
 * To authenticate a user over network <b>SVNRepository</b> drivers use
 * <b>ISVNAuthenticationManager</b> auth drivers.
 * 
 * @version     1.1.0
 * @author      TMate Software Ltd.
 * @see         SVNRepositoryFactory
 * @see         org.tmatesoft.svn.core.auth.ISVNAuthenticationManager
 * @see         <a target="_top" href="http://svnkit.com/kb/examples/">Examples</a>
 */
public abstract class SVNRepository {
        
    protected String myRepositoryUUID;
    protected SVNURL myRepositoryRoot;
    protected SVNURL myLocation;
    
    private int myLockCount;
    private Thread myLocker;
    private ISVNAuthenticationManager myAuthManager;
    private ISVNSession myOptions;
    private ISVNTunnelProvider myTunnelProvider;
    private ISVNDebugLog myDebugLog;

    protected SVNRepository(SVNURL location, ISVNSession options) {
        myLocation = location;
        myOptions = options;
    }
	
    /**
	 * Returns the repository location to which this object is set. 
     * It may be the location that was used to create this object
     * (see {@link SVNRepositoryFactory#create(SVNURL)}), or the recent 
     * one the object was set to.
     * 
	 * @return 	a repository location set for this driver
     * @see     #setLocation(SVNURL, boolean) 
	 * 			
	 */    
    public SVNURL getLocation() {
        return myLocation;
    }
    
    /**
     * Sets a new repository location for this object. The ability to reset
     * an old repository location to a new one (to switch the working location)
     * lets a developer to use the same <b>SVNRepository</b> object instead of
     * creating a new object per each repository location. This advantage gives
     * memory & coding efforts economy. 
     * 
     * <p>
     * But you can not specify a new repository location url with a protocol
     * different from the one used for the previous (essentially, the current) 
     * repository location, since <b>SVNRepository</b> objects are protocol dependent.    
     * 
     * <p>
     * If a new <code>url</code> is located within the same repository, this object
     * just switches to that <code>url</code> not closing the current session (i.e. 
     * not calling {@link #closeSession()}).
     * 
     * <p>
     * If either a new <code>url</code> refers to the same host (including a port
     * number), or refers to an absolutely different host, or this object has got
     * no repository root location cached (hasn't ever accessed a repository yet),
     * or <code>forceReconnect</code> is <span class="javakeyword">true</span>, then
     * the current session is closed, cached repository credentials (UUID and repository 
     * root directory location ) are reset and this object is switched to a new 
     * repository location.
     * 
     * @param  url             a new repository location url
     * @param  forceReconnect  if <span class="javakeyword">true</span> then
     *                         forces to close the current session, resets the
     *                         cached repository credentials and switches this object to 
     *                         a new location (doesn't matter whether it's on the same 
     *                         host or not)
     * @throws SVNException    if the old url and a new one has got different
     *                         protocols
     */
    public void setLocation(SVNURL url, boolean forceReconnect) throws SVNException {
        lock();
        try {
            if (url == null) {
                return;
            } else if (!url.getProtocol().equals(myLocation.getProtocol())) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.RA_NOT_IMPLEMENTED, "SVNRepository URL could not be changed from ''{0}'' to ''{1}''; create new SVNRepository instance instead", new Object[] {myLocation, url});
                SVNErrorManager.error(err);
            }
            
            if (forceReconnect) {
                closeSession();
                myRepositoryRoot = null;
                myRepositoryUUID = null;
            } else if (myRepositoryRoot == null) {
                // no way to check whether repos is the same. just compare urls
                if (!(url.toString().startsWith(myLocation.toString() + "/") || url.equals(getLocation()))) {
                    closeSession();
                    myRepositoryRoot = null;
                    myRepositoryUUID = null;
                }
            } else if (url.toString().startsWith(myRepositoryRoot.toString() + "/") || myRepositoryRoot.equals(url)) {
                // just do nothing, we are still below old root.
            } else {
                closeSession();
                myRepositoryRoot = null;
                myRepositoryUUID = null;
            }
            myLocation = url;
        } finally {
            unlock();
        }
    }

    /**
     * @return 	the UUID of a repository
     * @deprecated use {@link #getRepositoryUUID(boolean) } instead 
     */
    public String getRepositoryUUID() {
        try {
            return getRepositoryUUID(false);
        } catch (SVNException e) {
        }
        return myRepositoryUUID;
    }

    /**
     * Gets the Universal Unique IDentifier (UUID) of the repository this 
     * driver is created for.  
     *  
     * @param   forceConnection   if <span class="javakeyword">true</span> then forces
     *                            this driver to test a connection - try to access a 
     *                            repository 
     * @return  the UUID of a repository
     * @throws SVNException
     */
    public String getRepositoryUUID(boolean forceConnection) throws SVNException {
        if (forceConnection && myRepositoryUUID == null) {
            testConnection();
        }
        return myRepositoryUUID;
    }


    /**
     * @return 	the repository root directory location url
     * @see     #getRepositoryRoot(boolean)
     * 
     * @deprecated use #getRepositoryRoot(boolean) instead
     */
    public SVNURL getRepositoryRoot() {
        try {
            return getRepositoryRoot(false);
        } catch (SVNException e) {
            // will not be thrown.
        }
        return null;
    }
    
    /**
     * Gets a repository's root directory location. 
     * If this driver object is switched to a different repository location during
     * runtime (probably to an absolutely different repository, see {@link #setLocation(SVNURL, boolean) setLocation()}), 
     * the root directory location may be changed. 
     * 
     * @param   forceConnection   if <span class="javakeyword">true</span> then forces
     *                            this driver to test a connection - try to access a 
     *                            repository 
     * @return                    the repository root directory location url
     * @throws  SVNException
     * @see                       #testConnection()
     */
    public SVNURL getRepositoryRoot(boolean forceConnection) throws SVNException {
        if (forceConnection && myRepositoryRoot == null) {
            testConnection();
        }
        return myRepositoryRoot;
    }
    
    /**
     * Sets an authentication driver for this object. The auth driver
     * may be implemented to retrieve cached credentials, to prompt
     * a user for credentials or something else (actually, this is up
     * to an implementor). Also there's a default implementation - see
     * the {@link org.tmatesoft.svn.core.wc.SVNWCUtil} class for more
     * details.  
     * 
     * @param authManager an authentication driver to provide user 
     *                    credentials 
     * @see               #getAuthenticationManager()
     */
    public void setAuthenticationManager(ISVNAuthenticationManager authManager) {
        myAuthManager = authManager;
    }
    
    /**
     * Returns the authentication driver registered for this 
     * object.
     * 
     * @return an authentication driver that is used by this object 
     *         to authenticate a user over network
     */
    public ISVNAuthenticationManager getAuthenticationManager() {
        return myAuthManager;
    }
    
    /**
     * Sets a tunnel provider. Actually relevant only to  
     * <code>svn+xxx://</code> scheme cases. The provider is responsible 
     * for matching <code>xxx</code> to the tunnel command string.   
     * 
     * <p>
     * If one would like to have a standard Subversion behaviour 
     * (when tunnel commands are fetched from the <code>config</code> file 
     * beneath the section named <code>tunnels</code>), he should provide a 
     * default provider (default implementation of the {@link org.tmatesoft.svn.core.wc.ISVNOptions} 
     * interface). Refer to {@link org.tmatesoft.svn.core.wc.SVNWCUtil} class 
     * for more details on how to get a default options driver.   
     * 
     * @param tunnelProvider a tunnel provider
     * @see                  #getTunnelProvider()
     */
    public void setTunnelProvider(ISVNTunnelProvider tunnelProvider) {
        myTunnelProvider = tunnelProvider;
    }
    
    /**
     * Returns a tunnel provider.  
     * 
     * @return a tunnel provider
     * @see    #setTunnelProvider(ISVNTunnelProvider)
     */
    public ISVNTunnelProvider getTunnelProvider() {
        return myTunnelProvider;
    }
    
    /**
     * Caches identification parameters (UUID, rood directory location) 
     * of the repository with which this driver is working.
     * 
     * @param uuid 		the repository's Universal Unique IDentifier 
     * 					(UUID) 
     * @param rootURL	the repository's root directory location
     * @see 			#getRepositoryRoot(boolean)
     * @see 			#getRepositoryUUID(boolean)
     */
    protected void setRepositoryCredentials(String uuid, SVNURL rootURL) {
        if (uuid != null && rootURL != null) {
            myRepositoryUUID = uuid;
            myRepositoryRoot = rootURL;
        }
    }
    
    /**
     * Tries to access a repository. Used to check if there're no problems 
     * with accessing a repository and to cache a repository UUID and root
     * directory location.  
     * 
     * @throws SVNException if a failure occured while connecting to a repository 
     *                      or the user's authentication failed (see 
     *                      {@link org.tmatesoft.svn.core.SVNAuthenticationException})
     */
    public abstract void testConnection() throws SVNException; 
    
    /**
     * Returns the number of the latest revision of the repository this 
     * driver is working with.
     *  
     * @return 					the latest revision number
     * @throws 	SVNException	if a failure occured while connecting to a repository 
     *                          or the user's authentication failed (see 
     *                          {@link org.tmatesoft.svn.core.SVNAuthenticationException})
     */
    public abstract long getLatestRevision() throws SVNException;
    
    /**
     * Returns the recent repository revision number for the particular moment 
     * in time - the closest one before or at the specified datestamp. 
     * 
     * <p>
     * Example: if you specify a single date without specifying a time of the day 
     * (e.g. 2002-11-27) the timestamp is assumed to 00:00:00 and the method won't 
     * return any revisions for the day you have specified but for the day just 
     * before it. 
     * 
     * @param  date			a datestamp for defining the needed
     * 						moment in time
     * @return 				the revision of the repository
     *                      for that time
     * @throws SVNException if a failure occured while connecting to a repository 
     *                      or the user's authentication failed (see 
     *                      {@link org.tmatesoft.svn.core.SVNAuthenticationException})
     */
    public abstract long getDatedRevision(Date date) throws SVNException;
    
    /**
     * Returns unversioned revision properties for a particular revision.
     * Property names (keys) are mapped to their values. You may use  
     * <b>SVNRevisionProperty</b> constants to retrieve property values
     * from the map.
     *  
     * @param  revision 	a revision number 
     * @param  properties   if not <span class="javakeyword">null</span> then 	
     *                      properties will be placed in this map, otherwise 
     *                      a new map will be created  
     * @return 				a map containing unversioned revision properties
     * @throws SVNException in the following cases:
     *                      <ul>
     *                      <li><code>revision</code> number is invalid 
     * 						<li>there's no such <code>revision</code> at all
     * 						<li>a failure occured while connecting to a repository 
     *                      <li>the user authentication failed 
     *                      (see {@link org.tmatesoft.svn.core.SVNAuthenticationException})
     *                      </ul>
     * @see                 org.tmatesoft.svn.core.SVNRevisionProperty
     */
    public abstract Map getRevisionProperties(long revision, Map properties) throws SVNException;
    
    /**
     * Sets a revision property with the specified name to a new value. 
     * 
     * <p>
     * <b>NOTE:</b> revision properties are not versioned. So, the old values
     * may be lost forever.
     * 
     * @param  revision			the number of the revision which property is to
     * 							be changed
     * @param  propertyName		a revision property name
     * @param  propertyValue 	the value of the revision property  
     * @throws SVNException		in the following cases:
     *                          <ul>
     *                          <li>the repository is configured not to allow clients
     * 							to modify revision properties (e.g. a pre-revprop-change-hook
     *                          program is not found or failed)
     * 							<li><code>revision</code> is invalid or doesn't 
     * 							exist at all 
     *                          <li>a failure occured while connecting to a repository 
     *                          <li>the user authentication failed 
     *                          (see {@link org.tmatesoft.svn.core.SVNAuthenticationException})
     *                          </ul>
     * @see                     org.tmatesoft.svn.core.SVNRevisionProperty
     */
    public abstract void setRevisionPropertyValue(long revision, String propertyName, String propertyValue) throws SVNException;
    
    /**
     * Gets the value of an unversioned property. 
     * 
     * 
     * @param 	revision 		a revision number
     * @param 	propertyName 	a property name
     * @return 					a revision property value or <span class="javakeyword">null</span> 
     *                          if there's no such revision property 
     * @throws 	SVNException	in the following cases:
     *                          <ul>
     *                          <li><code>revision</code> number is invalid or
     * 							if there's no such <code>revision</code> at all.
     *                          <li>a failure occured while connecting to a repository 
     *                          <li>the user authentication failed 
     *                          (see {@link org.tmatesoft.svn.core.SVNAuthenticationException})
     *                          </ul>
     */
    public abstract String getRevisionPropertyValue(long revision, String propertyName) throws SVNException;
    
    /**
	 * Returns the kind of an item located at the specified path in
     * a particular revision. If the <code>path</code> does not exist 
     * under the specified <code>revision</code>, {@link org.tmatesoft.svn.core.SVNNodeKind#NONE SVNNodeKind.NONE} 
     * will be returned.
	 * 
     * <p>
     * The <code>path</code> arg can be both relative to the location of 
     * this driver and absolute to the repository root (starts with <code>"/"</code>).
     * 
     * @param  path				an item's path  
     * @param  revision			a revision number
     * @return 					the node kind for the given <code>path</code> at the given 
     * 							<code>revision</code>
     * @throws SVNException  	if a failure occured while connecting to a repository 
     *                          or the user's authentication failed (see 
     *                          {@link org.tmatesoft.svn.core.SVNAuthenticationException})
     */
    public abstract SVNNodeKind checkPath(String path, long revision) throws SVNException;
    
    /**
	 * Fetches the contents and/or properties of a file located at the specified path
	 * in a particular revision.
	 * 
	 * <p>
     * If <code>contents</code> arg is not <span class="javakeyword">null</span> it 
     * will be written with file contents.
     * 
     * <p>
	 * If <code>properties</code> arg is not <span class="javakeyword">null</span> it will 
     * receive the properties of the file.  This includes all properties: not just ones 
     * controlled by a user and stored in the repository filesystem, but also non-tweakable 
     * ones (e.g. 'wcprops', 'entryprops', etc.). Property names (keys) are mapped to property
     * values. 
	 *
     * <p>
     * The <code>path</code> arg can be both relative to the location of 
     * this driver and absolute to the repository root (starts with <code>"/"</code>).
     * 
     * <p>
     * If <code>revision</code> is invalid (negative), HEAD revision will be used. 
     * 
     * @param path 				a file path
     * @param revision 			a file revision
     * @param properties 		a file properties receiver map
     * @param contents 			an output stream to write the file contents to
     * @return 					the revision the file has been taken at
     * @throws SVNException		in the following cases:
     *                          <ul>
     * 							<li>there's	no such <code>path</code> in <code>revision</code>
     *                          <li>a failure occured while connecting to a repository 
     *                          <li>the user authentication failed 
     *                          (see {@link org.tmatesoft.svn.core.SVNAuthenticationException})
     *                          </ul>
     */
    public abstract long getFile(String path, long revision, Map properties, OutputStream contents) throws SVNException; 

    /**
     * Fetches the contents and/or properties of a directory located at the specified path
     * in a particular revision. 
     * 
     * <p>
     * If <code>handler</code> arg is not <span class="javakeyword">null</span> it 
     * will be dispatched information of each directory entry represented by an 
     * <b>SVNDirEntry</b> object.
     *
     * <p>
     * If <code>properties</code> arg is not <span class="javakeyword">null</span> it will 
     * receive the properties of the file.  This includes all properties: not just ones 
     * controlled by a user and stored in the repository filesystem, but also non-tweakable 
     * ones (e.g. 'wcprops', 'entryprops', etc.). Property names (keys) are mapped to property
     * values. 
     *
     * <p>
     * The <code>path</code> arg can be both relative to the location of 
     * this driver and absolute to the repository root (starts with <code>"/"</code>).
     * 
     * <p>
     * If <code>revision</code> is invalid (negative), HEAD revision will be used. 
     * 
     * <b>NOTE:</b> you may not invoke operation methods of this <b>SVNRepository</b>
     * object from within the provided <code>handler</code>.
     * 
     * @param  path 		a directory path   
     * @param  revision 	a directory revision 
     * @param  properties 	a directory properties receiver map
     * @param  handler 		a handler to process directory entries
     * @return 				the revision of the directory
     * @throws SVNException	in the following cases:
     *                      <ul>
     *                      <li><code>path</code> not found in the specified <code>revision</code>
     * 						<li><code>path</code> is not a directory
     *                      <li>a failure occured while connecting to a repository 
     *                      <li>the user authentication failed 
     *                      (see {@link org.tmatesoft.svn.core.SVNAuthenticationException})
     *                      </ul>
     * @see 				#getDir(String, long, Map, Collection)
     * @see                 #getDir(String, long, boolean, Collection)
     * @see                 org.tmatesoft.svn.core.SVNDirEntry
     */
    public abstract long getDir(String path, long revision, Map properties, ISVNDirEntryHandler handler) throws SVNException; 

    /**
     * Retrieves interesting file revisions for the specified file. 
	 * 
	 * <p>
	 * A file revision is represented by an <b>SVNFileRevision</b> object. Each
     * file revision is handled by the file revision handler provided. Only those 
     * revisions will be retrieved in which the file was changed.
	 * The iteration will begin at the first such revision starting from the 
	 * <code>startRevision</code> and so on - up to the <code>endRevision</code>.
	 * If the method succeeds, the provided <code>handler</code> will have
	 * been invoked at least once.
	 * 
	 * <p>
	 * For the first interesting revision the file contents  
     * will be provided to the <code>handler</code> as a text delta against an empty file.  
     * For the following revisions, the delta will be against the fulltext contents of the 
     * previous revision.
     *
     * <p>
     * The <code>path</code> arg can be both relative to the location of 
     * this driver and absolute to the repository root (starts with <code>"/"</code>).
	 * 
     * <p>
     * <b>NOTES:</b> 
     * <ul>
     * <li>you may not invoke methods of this <b>SVNRepository</b>
     *     object from within the provided <code>handler</code>
     * <li>this functionality is not available in pre-1.1 servers
     * </ul>
	 * 
	 * @param  path 			a file path 
	 * @param  startRevision 	a revision to start from 
	 * @param  endRevision   	a revision to stop at
	 * @param  handler 			a handler that processes file revisions passed  
	 * @return 					the number of retrieved file revisions  
	 * @throws SVNException		if a failure occured while connecting to a repository 
     *                          or the user's authentication failed (see 
     *                          {@link org.tmatesoft.svn.core.SVNAuthenticationException})
	 * @see 					#getFileRevisions(String, Collection, long, long)
	 * @see 					SVNFileRevision
	 * @since					SVN 1.1
	 */    
    public abstract int getFileRevisions(String path, long startRevision, long endRevision, ISVNFileRevisionHandler handler) throws SVNException;
    
    /**
	 * Traverses revisions history. In other words, collects per revision
     * information that includes the revision number, author, datestamp, 
     * log message and maybe a list of changed paths (optional). For each
     * revision this information is represented by an <b>SVNLogEntry</b> 
     * object. Such objects are passed to the provided <code>handler</code>. 
     * 
     * <p>
     * This method invokes <code>handler</code> on each log entry from
	 * <code>startRevision</code> to <code>endRevision</code>.
	 * <code>startRevision</code> may be greater or less than
	 * <code>endRevision</code>; this just controls whether the log messages are
	 * processed in descending or ascending revision number order.
	 * 
	 * <p>
	 * If <code>startRevision</code> or <code>endRevision</code> is invalid, it
	 * defaults to the youngest.
	 * 
	 * <p>
	 * If <code>targetPaths</code> has one or more elements, then
	 * only those revisions are processed in which at least one of <code>targetPaths</code> was
	 * changed (i.e., if a file text or properties changed; if dir properties
	 * changed or an entry was added or deleted). Each path is relative 
	 * to the repository location that this object is set to.
	 * 
	 * <p>
	 * If <code>changedPath</code> is <span class="javakeyword">true</span>, then each 
     * <b>SVNLogEntry</b> passed to the handler will contain info about all 
     * paths changed in that revision it represents. To get them call 
     * {@link org.tmatesoft.svn.core.SVNLogEntry#getChangedPaths()} that returns a map,
     * which keys are the changed paths and the values are <b>SVNLogEntryPath</b> objects.
     * If <code>changedPath</code> is <span class="javakeyword">false</span>, changed paths
     * info will not be provided. 
	 * 
	 * <p>
	 * If <code>strictNode</code> is <span class="javakeyword">true</span>, copy history will 
     * not be traversed (if any exists) when harvesting the revision logs for each path.
	 * 
     * <p>
     * Target paths can be both relative to the location of 
     * this driver and absolute to the repository root (starts with <code>"/"</code>).
	 * 
     * <p>
	 * <b>NOTE:</b> you may not invoke methods of this <b>SVNRepository</b>
     * object from within the provided <code>handler</code>.
	 * 
     * @param  targetPaths 		paths that mean only those revisions at which they were 
     * 							changed
     * @param  startRevision 	a revision to start from
     * @param  endRevision 		a revision to end at 
     * @param  changedPath 		if <span class="javakeyword">true</span> then
     *                          revision information will also include all changed paths per 
     *                          revision, otherwise not
     * @param  strictNode 		if <span class="javakeyword">true</span> then copy history (if any) is not 
     * 							to be traversed
     * @param  handler 			a caller's handler that will be dispatched log entry objects
     * @return 					the number of revisions traversed
     * @throws SVNException     if a failure occured while connecting to a repository 
     *                          or the user's authentication failed (see 
     *                          {@link org.tmatesoft.svn.core.SVNAuthenticationException})
     * @see                     #log(String[], Collection, long, long, boolean, boolean)
     * @see                     #log(String[], long, long, boolean, boolean, long, ISVNLogEntryHandler)
     * @see 					org.tmatesoft.svn.core.ISVNLogEntryHandler
     * @see 					org.tmatesoft.svn.core.SVNLogEntry
     * @see                     org.tmatesoft.svn.core.SVNLogEntryPath
     */
    public long log(String[] targetPaths, long startRevision, long endRevision, boolean changedPath, boolean strictNode,
            ISVNLogEntryHandler handler) throws SVNException {
        return log(targetPaths, startRevision, endRevision,changedPath,strictNode, 0, handler);
    }

    /**
     * Traverses revisions history. In other words, collects per revision
     * information that includes the revision number, author, datestamp, 
     * log message and maybe a list of changed paths (optional). For each
     * revision this information is represented by an <b>SVNLogEntry</b> 
     * object. Such objects are passed to the provided <code>handler</code>. 
     * 
     * <p>
     * This method invokes <code>handler</code> on each log entry from
     * <code>startRevision</code> to <code>endRevision</code>.
     * <code>startRevision</code> may be greater or less than
     * <code>endRevision</code>; this just controls whether the log messages are
     * processed in descending or ascending revision number order.
     * 
     * <p>
     * If <code>startRevision</code> or <code>endRevision</code> is invalid, it
     * defaults to the youngest.
     * 
     * <p>
     * If <code>targetPaths</code> has one or more elements, then
     * only those revisions are processed in which at least one of <code>targetPaths</code> was
     * changed (i.e., if a file text or properties changed; if dir properties
     * changed or an entry was added or deleted). Each path is relative 
     * to the repository location that this object is set to.
     * 
     * <p>
     * If <code>changedPath</code> is <span class="javakeyword">true</span>, then each 
     * <b>SVNLogEntry</b> passed to the handler will contain info about all 
     * paths changed in that revision it represents. To get them call 
     * {@link org.tmatesoft.svn.core.SVNLogEntry#getChangedPaths()} that returns a map,
     * which keys are the changed paths and the values are <b>SVNLogEntryPath</b> objects.
     * If <code>changedPath</code> is <span class="javakeyword">false</span>, changed paths
     * info will not be provided. 
     * 
     * <p>
     * If <code>strictNode</code> is <span class="javakeyword">true</span>, copy history will 
     * not be traversed (if any exists) when harvesting the revision logs for each path.
     * 
     * <p>
     * If <code>limit</code> is > 0 then only the first <code>limit</code> log entries
     * will be handled. Otherwise this number is ignored.
     * 
     * <p>
     * Target paths can be both relative to the location of 
     * this driver and absolute to the repository root (starts with <code>"/"</code>).
     * 
     * <p>
     * <b>NOTE:</b> you may not invoke methods of this <b>SVNRepository</b>
     * object from within the provided <code>handler</code>.
     * 
     * @param  targetPaths      paths that mean only those revisions at which they were 
     *                          changed
     * @param  startRevision    a revision to start from
     * @param  endRevision      a revision to end at 
     * @param  changedPath      if <span class="javakeyword">true</span> then
     *                          revision information will also include all changed paths per 
     *                          revision, otherwise not
     * @param  strictNode       if <span class="javakeyword">true</span> then copy history (if any) is not 
     *                          to be traversed
     * @param  limit            the maximum number of log entries to process
     * @param  handler          a caller's handler that will be dispatched log entry objects
     * @return                  the number of revisions traversed
     * @throws SVNException     if a failure occured while connecting to a repository 
     *                          or the user's authentication failed (see 
     *                          {@link org.tmatesoft.svn.core.SVNAuthenticationException})
     * @see                     #log(String[], Collection, long, long, boolean, boolean)
     * @see                     #log(String[], long, long, boolean, boolean, long, ISVNLogEntryHandler)
     * @see                     org.tmatesoft.svn.core.ISVNLogEntryHandler
     * @see                     org.tmatesoft.svn.core.SVNLogEntry
     * @see                     org.tmatesoft.svn.core.SVNLogEntryPath
     */
    public abstract long log(String[] targetPaths, long startRevision, long endRevision, boolean changedPath, boolean strictNode, long limit,
            ISVNLogEntryHandler handler) throws SVNException;
    
    /**
	 * Gets entry locations in time. The location of an entry in a repository
     * may change from revision to revision. This method allows to trace entry locations 
     * in different revisions. 
     * 
     * <p>
     * For each interesting revision (taken from <code>revisions</code>) an entry location
     * is represented by an <b>SVNLocationEntry</b> object which is passed to the provided
     * <code>handler</code>. Each <b>SVNLocationEntry</b> object represents a repository path 
     * in a definite revision.
	 * 
     * <p>
     * The <code>path</code> arg can be both relative to the location of 
     * this driver and absolute to the repository root (starts with <code>"/"</code>).
     * 
     * <p>
     * <b>NOTES:</b> 
     * <ul>
     * <li>you may not invoke methods of this <b>SVNRepository</b>
     *     object from within the provided <code>handler</code>
     * <li>this functionality is not available in pre-1.1 servers
     * </ul>
     * 
     * @param  path			an item's path 
     * @param  pegRevision 	a revision in which <code>path</code> is first 
     *                      looked up 
     * @param  revisions 	an array of numbers of interesting revisions in which
     *                      locations are looked up. If <code>path</code> 
     * 						doesn't exist in an interesting revision, that revision 
     *                      will be ignored. 
     * @param  handler 		a location entry handler that will handle all found entry
     * 						locations
     * @return 				the number of the entry locations found 
     * @throws SVNException in the following cases:
     *                      <ul>
     *                      <li><code>path</code> not found in the specified <code>pegRevision</code>
     *                      <li><code>pegRevision</code> is not valid
     *                      <li>a failure occured while connecting to a repository 
     *                      <li>the user authentication failed 
     *                      (see {@link org.tmatesoft.svn.core.SVNAuthenticationException})
     *                      </ul>
     * @see                 #getLocations(String, Collection, long, long[])
     * @see                 #getLocations(String, Map, long, long[])                      
     * @see 				ISVNLocationEntryHandler
     * @see 				SVNLocationEntry
     * @since               SVN 1.1
     */
    public abstract int getLocations(String path, long pegRevision, long[] revisions, ISVNLocationEntryHandler handler) throws SVNException;
    
    /**
     * Retrieves and returns interesting file revisions for the specified file. 
     * 
     * <p>
     * A file revision is represented by an <b>SVNFileRevision</b> object. 
     * Only those revisions will be retrieved in which the file was changed.
     * The iteration will begin at the first such revision starting from the 
     * <code>startRevision</code> and so on - up to the <code>endRevision</code>.
     * 
     * <p>
     * The <code>path</code> arg can be both relative to the location of 
     * this driver and absolute to the repository root (starts with <code>"/"</code>).
     * 
     * <p>
     * <b>NOTE:</b> this functionality is not available in pre-1.1 servers
     *   
     * @param  path         a file path 
     * @param  revisions 	if not <span class="javakeyword">null</span> this collection
     *                      will receive all the fetched file revisions   
     * @param  sRevision 	a revision to start from
     * @param  eRevision 	a revision to stop at
     * @return 				a collection that keeps	file revisions - {@link SVNFileRevision} instances 
     * @throws SVNException if a failure occured while connecting to a repository 
     *                      or the user's authentication failed (see 
     *                      {@link org.tmatesoft.svn.core.SVNAuthenticationException})
     * @see                 #getFileRevisions(String, long, long, ISVNFileRevisionHandler)
     * @see                 SVNFileRevision
     * @since               SVN 1.1
     */
    public Collection getFileRevisions(String path, Collection revisions, long sRevision, long eRevision) throws SVNException {
        final Collection result = revisions != null ? revisions : new LinkedList();
        ISVNFileRevisionHandler handler = new ISVNFileRevisionHandler() {
            public void openRevision(SVNFileRevision fileRevision) throws SVNException {
                result.add(fileRevision);
            }
            public void applyTextDelta(String path, String baseChecksum) throws SVNException {
            }
            public OutputStream textDeltaChunk(String token, SVNDiffWindow diffWindow) throws SVNException {
                return SVNFileUtil.DUMMY_OUT;
            }
            public void textDeltaEnd(String token) throws SVNException {
            }
            public void closeRevision(String token) throws SVNException {
            }
        };
        getFileRevisions(path, sRevision, eRevision, handler);
        return result;
    }
    
    /**
     * Fetches the contents and properties of a directory located at the specified path
     * in a particular revision. Information of each directory 
     * entry is represented by a single <b>SVNDirEntry</b> object.
     *
     * <p>
     * The <code>path</code> arg can be both relative to the location of 
     * this driver and absolute to the repository root (starts with <code>"/"</code>).
     * 
     * @param  path         a directory path   
     * @param  revision     a revision number 
     * @param  properties   if not <span class="javakeyword">null</span> then all
     *                      directory properties (including non-tweakable ones)
     *                      will be put into this map (where keys are property names
     *                      and mappings are property values)
     * @param  dirEntries 	if not <span class="javakeyword">null</span> then this
     *                      collection receives fetched dir entries (<b>SVNDirEntry</b> objects)
     * @return 				a collection containing fetched directory entries (<b>SVNDirEntry</b> objects)
     * @throws SVNException in the following cases:
     *                      <ul>
     *                      <li><code>path</code> not found in the specified <code>revision</code>
     *                      <li><code>path</code> is not a directory
     *                      <li>a failure occured while connecting to a repository 
     *                      <li>the user authentication failed 
     *                      (see {@link org.tmatesoft.svn.core.SVNAuthenticationException})
     *                      </ul>
     * @see                 #getDir(String, long, Map, ISVNDirEntryHandler)
     * @see                 #getDir(String, long, boolean, Collection)
     * @see                 org.tmatesoft.svn.core.SVNDirEntry
     */
    public Collection getDir(String path, long revision, Map properties, Collection dirEntries) throws SVNException {
        final Collection result = dirEntries != null ? dirEntries : new LinkedList();
        ISVNDirEntryHandler handler;
        handler = new ISVNDirEntryHandler() {
            public void handleDirEntry(SVNDirEntry dirEntry) {
                result.add(dirEntry);
            }
        };
        getDir(path, revision, properties, handler);
        return result;
    }
    
    /**
     * Fetches the contents of a directory into the provided 
     * collection object and returns the directory entry itself. 
     * 
     * <p>
     * If <code>entries</code> arg is not <span class="javakeyword">null</span> it 
     * receives the directory entries. Information of each directory entry is 
     * represented by an <b>SVNDirEntry</b> object.
     * 
     * <p>
     * The <code>path</code> arg can be both relative to the location of 
     * this driver and absolute to the repository root (starts with <code>"/"</code>).
     * 
     * @param  path                    a directory path   
     * @param  revision                a revision number 
     * @param  includeCommitMessages   if <span class="javakeyword">true</span> then
     *                                 dir entries (<b>SVNDirEntry</b> objects) will be supplied 
     *                                 with commit log messages, otherwise not
     * @param  entries                 a collection that receives fetched dir entries                 
     * @return                         the directory entry itself which contents
     *                                 are fetched into <code>entries</code>
     * @throws SVNException            in the following cases:
     *                                 <ul>
     *                                 <li><code>path</code> not found in the specified <code>revision</code>
     *                                 <li><code>path</code> is not a directory
     *                                 <li>a failure occured while connecting to a repository 
     *                                 <li>the user authentication failed 
     *                                 (see {@link org.tmatesoft.svn.core.SVNAuthenticationException})
     *                                 </ul>
     * @see                            #getDir(String, long, Map, ISVNDirEntryHandler)
     * @see                            #getDir(String, long, Map, Collection)
     * @see                            org.tmatesoft.svn.core.SVNDirEntry
     */
    public abstract SVNDirEntry getDir(String path, long revision, boolean includeCommitMessages, Collection entries) throws SVNException;

    /**
     * Traverses revisions history and returns a collection of log entries. In 
     * other words, collects per revision information that includes the revision number, 
     * author, datestamp, log message and maybe a list of changed paths (optional). For each
     * revision this information is represented by an <b>SVNLogEntry</b>. 
     * object. 
     * 
     * <p>
     * <code>startRevision</code> may be greater or less than
     * <code>endRevision</code>; this just controls whether the log messages are
     * processed in descending or ascending revision number order.
     * 
     * <p>
     * If <code>startRevision</code> or <code>endRevision</code> is invalid, it
     * defaults to the youngest.
     * 
     * <p>
     * If <code>targetPaths</code> has one or more elements, then
     * only those revisions are processed in which at least one of <code>targetPaths</code> was
     * changed (i.e., if a file text or properties changed; if dir properties
     * changed or an entry was added or deleted). Each path is relative 
     * to the repository location that this object is set to.
     * 
     * <p>
     * If <code>changedPath</code> is <span class="javakeyword">true</span>, then each 
     * <b>SVNLogEntry</b> object is supplied with info about all 
     * paths changed in that revision it represents. To get them call 
     * {@link org.tmatesoft.svn.core.SVNLogEntry#getChangedPaths()} that returns a map,
     * which keys are the changed paths and the mappings are <b>SVNLogEntryPath</b> objects.
     * If <code>changedPath</code> is <span class="javakeyword">false</span>, changed paths
     * info will not be provided. 
     * 
     * <p>
     * If <code>strictNode</code> is <span class="javakeyword">true</span>, copy history will 
     * not be traversed (if any exists) when harvesting the revision logs for each path.
     * 
     * <p>
     * Target paths can be both relative to the location of 
     * this driver and absolute to the repository root (starts with <code>"/"</code>).
     * 
     * @param  targetPaths      paths that mean only those revisions at which they were 
     *                          changed
     * @param  entries 			if not <span class="javakeyword">null</span> then this collection
     *                          will receive log entries
     * @param  startRevision    a revision to start from
     * @param  endRevision      a revision to end at 
     * @param  changedPath      if <span class="javakeyword">true</span> then
     *                          revision information will also include all changed paths per 
     *                          revision, otherwise not
     * @param  strictNode       if <span class="javakeyword">true</span> then copy history (if any) is not 
     *                          to be traversed
     * @return 					a collection with log entries
     * @throws SVNException     if a failure occured while connecting to a repository 
     *                          or the user's authentication failed (see 
     *                          {@link org.tmatesoft.svn.core.SVNAuthenticationException})
     * @see                     #log(String[], long, long, boolean, boolean, ISVNLogEntryHandler)
     * @see                     #log(String[], long, long, boolean, boolean, long, ISVNLogEntryHandler)
     * @see                     org.tmatesoft.svn.core.ISVNLogEntryHandler
     * @see                     org.tmatesoft.svn.core.SVNLogEntry
     * @see                     org.tmatesoft.svn.core.SVNLogEntryPath
     */
    public Collection log(String[] targetPaths, Collection entries, long startRevision, long endRevision, boolean changedPath, boolean strictNode) throws SVNException {
        final Collection result = entries != null ? entries : new LinkedList();
        log(targetPaths, startRevision, endRevision, changedPath, strictNode, new ISVNLogEntryHandler() {
            public void handleLogEntry(SVNLogEntry logEntry) {
                result.add(logEntry);
            }        
        });
        return result;
    }
    
    /**
     * Gets entry locations in time. The location of an entry in a repository
     * may change from revision to revision. This method allows to trace entry locations 
     * in different revisions. 
     * 
     * <p>
     * For each interesting revision (taken from <code>revisions</code>) an entry location
     * is represented by an <b>SVNLocationEntry</b> object. Each <b>SVNLocationEntry</b> 
     * object represents a repository path in a definite revision.
     * 
     * <p>
     * The <code>path</code> arg can be both relative to the location of 
     * this driver and absolute to the repository root (starts with <code>"/"</code>).
     * 
     * <p>
     * <b>NOTE:</b> this functionality is not available in pre-1.1 servers
     * 
     * @param  path         an item's path
     * @param  entries 		if not <span class="javakeyword">null</span> then this 
     *                      collection object receives entry locations
     * @param  pegRevision  a revision in which <code>path</code> is first 
     *                      looked up 
     * @param  revisions    an array of numbers of interesting revisions in which
     *                      locations are looked up. If <code>path</code> 
     *                      doesn't exist in an interesting revision, that revision 
     *                      will be ignored. 
     * @return 				a collection with retrieved entry locations
     * @throws SVNException in the following cases:
     *                      <ul>
     *                      <li><code>path</code> not found in the specified <code>pegRevision</code>
     *                      <li><code>pegRevision</code> is not valid
     *                      <li>a failure occured while connecting to a repository 
     *                      <li>the user authentication failed 
     *                      (see {@link org.tmatesoft.svn.core.SVNAuthenticationException})
     *                      </ul>
     * @see                 #getLocations(String, long, long[], ISVNLocationEntryHandler)
     * @see                 #getLocations(String, Map, long, long[])
     * @see 				SVNLocationEntry
     * @see 				ISVNLocationEntryHandler
     * @since               SVN 1.1
     */
    public Collection getLocations(String path, Collection entries, long pegRevision, long[] revisions) throws SVNException {
        final Collection result = entries != null ? entries : new LinkedList();
        getLocations(path, pegRevision, revisions, new ISVNLocationEntryHandler() {
            public void handleLocationEntry(SVNLocationEntry locationEntry) {
                result.add(locationEntry);
            } 
        });
        return result;        
    }

    /**
     * Gets entry locations in time. The location of an entry in a repository
     * may change from revision to revision. This method allows to trace entry locations 
     * in different revisions. 
     * 
     * <p>
     * For each interesting revision (taken from <code>revisions</code>) an entry location
     * is represented by an <b>SVNLocationEntry</b> object. Each <b>SVNLocationEntry</b> 
     * object represents a repository path in a definite revision.
     * 
     * <p>
     * The <code>path</code> arg can be both relative to the location of 
     * this driver and absolute to the repository root (starts with <code>"/"</code>).
     * 
     * <p>
     * <b>NOTE:</b> this functionality is not available in pre-1.1 servers
     * 
     * @param  path         an item's path
     * @param  entries      if not <span class="javakeyword">null</span> then this 
     *                      map object receives entry locations (which keys are revision
     *                      numbers as Longs and mappings are entry locations objects)
     * @param  pegRevision  a revision in which <code>path</code> is first 
     *                      looked up 
     * @param  revisions    an array of numbers of interesting revisions in which
     *                      locations are looked up. If <code>path</code> 
     *                      doesn't exist in an interesting revision, that revision 
     *                      will be ignored. 
     * @return              a map (which keys are revision numbers as Longs and mappings 
     *                      are entry locations objects) with collected entry locations            
     * @throws SVNException in the following cases:
     *                      <ul>
     *                      <li><code>path</code> not found in the specified <code>pegRevision</code>
     *                      <li><code>pegRevision</code> is not valid
     *                      <li>a failure occured while connecting to a repository 
     *                      <li>the user authentication failed 
     *                      (see {@link org.tmatesoft.svn.core.SVNAuthenticationException})
     *                      </ul>
     * @see                 #getLocations(String, long, long[], ISVNLocationEntryHandler)
     * @see                 #getLocations(String, Collection, long, long[])
     * @see                 SVNLocationEntry
     * @see                 ISVNLocationEntryHandler
     * @since               SVN 1.1
     */
    public Map getLocations(String path, Map entries, long pegRevision, long[] revisions) throws SVNException {
        final Map result = entries != null ? entries : new HashMap();
        getLocations(path, pegRevision, revisions, new ISVNLocationEntryHandler() {
            public void handleLocationEntry(SVNLocationEntry locationEntry) {
                result.put(new Long(locationEntry.getRevision()), locationEntry);
            } 
        });
        return result;        
    }
	
	/**
	 * Calculates the differences between two items. 
	 *
     * <p>
     * <code>target</code> is the name (one-level path component) of an entry that will restrict
     * the scope of the diff operation to this entry. In other words <code>target</code> is a child entry of the 
     * directory represented by the repository location to which this object is set. For
     * example, if we have something like <code>"/dirA/dirB"</code> in a repository, then
     * this object's repository location may be set to <code>"svn://host:port/path/to/repos/dirA"</code>,
     * and <code>target</code> may be <code>"dirB"</code>.
     * 
     * <p>
     * If <code>target</code> is <span class="javakeyword">null</span> or empty (<code>""</code>)
	 * then the scope of the diff operation is the repository location to which
     * this object is set.
     * 
     * <p>
     * The <code>reporter</code> is used to describe the state of the target item(s) (i.e. 
     * items' revision numbers). All the paths described by the <code>reporter</code>
     * should be relative to the repository location to which this object is set. 
     * 
     * <p>
     * After that the <code>editor</code> is used to carry out all the work on 
     * evaluating differences against <code>url</code>. This <code>editor</code> contains 
     * knowledge of where the change will begin (when {@link ISVNEditor#openRoot(long) ISVNEditor.openRoot()} 
     * is called).
	 * 
	 * <p>
	 * If <code>ignoreAncestry</code> is <span class="javakeyword">false</span> then
     * the ancestry of the paths being diffed is taken into consideration - they
     * are treated as related. In this case, for example, if calculating differences between 
     * two files with identical contents but different ancestry, 
     * the entire contents of the target file is considered as having been removed and 
     * added again. 
     * 
     * <p>
     * If <code>ignoreAncestry</code> is <span class="javakeyword">true</span>
     * then the two paths are merely compared ignoring the ancestry.
	 * 
     * <p>
     * <b>NOTE:</b> you may not invoke methods of this <b>SVNRepository</b>
     * object from within the provided <code>reporter</code> and <code>editor</code>.
	 * 
	 * @param  url 				a repository location of the entry against which 
     *                          differences are calculated 
	 * @param  targetRevision   a revision number of the entry located at the 
     *                          specified <code>url</code>; defaults to the
     *                          latest revision (HEAD) if this arg is invalid
     * @param  revision         a revision number of the repository location to which 
     *                          this driver object is set
	 * @param  target 			a target entry name (optional)
	 * @param  ignoreAncestry 	if <span class="javakeyword">true</span> then
     *                          the ancestry of the two entries to be diffed is 
     *                          ignored, otherwise not 
     * @param  recursive        if <span class="javakeyword">true</span> and the diff scope
     *                          is a directory, descends recursively, otherwise not 
     * @param  getContents      if <span class="javakeyword">false</span> contents (diff windows) will not be sent ot 
     *                          the editor. 
     * @param  reporter 		a caller's reporter
     * @param  editor 			a caller's editor
     * @throws SVNException     in the following cases:
     *                          <ul>
     *                          <li><code>url</code> not found neither in the specified 
     *                          <code>revision</code> nor in the HEAD revision
     *                          <li>a failure occured while connecting to a repository 
     *                          <li>the user authentication failed 
     *                          (see {@link org.tmatesoft.svn.core.SVNAuthenticationException})
     *                          </ul>
     * @see 					ISVNReporterBaton
     * @see 					ISVNReporter
     * @see 					ISVNEditor
	 */
    public abstract void diff(SVNURL url, long targetRevision, long revision, String target, boolean ignoreAncestry, boolean recursive, boolean getContents, ISVNReporterBaton reporter, ISVNEditor editor) throws SVNException;

    /**
     * @deprecated
     */
    public abstract void diff(SVNURL url, long targetRevision, long revision, String target, boolean ignoreAncestry, boolean recursive, ISVNReporterBaton reporter, ISVNEditor editor) throws SVNException;

    
    /**
     * Calculates the differences between two items. 
     *
     * <p>
     * <code>target</code> is the name (one-level path component) of an entry that will restrict
     * the scope of the diff operation to this entry. In other words <code>target</code> is a child entry of the 
     * directory represented by the repository location to which this object is set. For
     * example, if we have something like <code>"/dirA/dirB"</code> in a repository, then
     * this object's repository location may be set to <code>"svn://host:port/path/to/repos/dirA"</code>,
     * and <code>target</code> may be <code>"dirB"</code>.
     * 
     * <p>
     * If <code>target</code> is <span class="javakeyword">null</span> or empty (<code>""</code>)
     * then the scope of the diff operation is the repository location to which
     * this object is set.
     * 
     * <p>
     * The <code>reporter</code> is used to describe the state of the target item(s) (i.e. 
     * items' revision numbers). All the paths described by the <code>reporter</code>
     * should be relative to the repository location to which this object is set. 
     * 
     * <p>
     * After that the <code>editor</code> is used to carry out all the work on 
     * evaluating differences against <code>url</code>. This <code>editor</code> contains 
     * knowledge of where the change will begin (when {@link ISVNEditor#openRoot(long) ISVNEditor.openRoot()} 
     * is called).
     * 
     * <p>
     * If <code>ignoreAncestry</code> is <span class="javakeyword">false</span> then
     * the ancestry of the paths being diffed is taken into consideration - they
     * are treated as related. In this case, for example, if calculating differences between 
     * two files with identical contents but different ancestry, 
     * the entire contents of the target file is considered as having been removed and 
     * added again. 
     * 
     * <p>
     * If <code>ignoreAncestry</code> is <span class="javakeyword">true</span>
     * then the two paths are merely compared ignoring the ancestry.
     * 
     * <p>
     * <b>NOTE:</b> you may not invoke methods of this <b>SVNRepository</b>
     * object from within the provided <code>reporter</code> and <code>editor</code>.
     * 
     * @param  url              a repository location of the entry against which 
     *                          differences are calculated 
     * @param  revision         a revision number of the repository location to which 
     *                          this driver object is set
     * @param  target           a target entry name (optional)
     * @param  ignoreAncestry   if <span class="javakeyword">true</span> then
     *                          the ancestry of the two entries to be diffed is 
     *                          ignored, otherwise not 
     * @param  recursive        if <span class="javakeyword">true</span> and the diff scope
     *                          is a directory, descends recursively, otherwise not 
     * @param  reporter         a caller's reporter
     * @param  editor           a caller's editor
     * @throws SVNException     in the following cases:
     *                          <ul>
     *                          <li><code>url</code> not found neither in the specified 
     *                          <code>revision</code> nor in the HEAD revision
     *                          <li>a failure occured while connecting to a repository 
     *                          <li>the user authentication failed 
     *                          (see {@link org.tmatesoft.svn.core.SVNAuthenticationException})
     *                          </ul>
     * @deprecated              use {@link #diff(SVNURL, long, long, String, boolean, boolean, ISVNReporterBaton, ISVNEditor)} instead 
     * @see                     ISVNReporterBaton
     * @see                     ISVNReporter
     * @see                     ISVNEditor
     */
    public abstract void diff(SVNURL url, long revision, String target, boolean ignoreAncestry, boolean recursive, ISVNReporterBaton reporter, ISVNEditor editor) throws SVNException;
    
    /**
     * Updates a path receiving changes from a repository.
     *
     * <p>
     * <code>target</code> is the name (one-level path component) of an entry that will 
     * restrict the scope of the update to this entry. In other words <code>target</code> is a child entry of the 
     * directory represented by the repository location to which this object is set. For
     * example, if we have something like <code>"/dirA/dirB"</code> in a repository, then
     * this object's repository location may be set to <code>"svn://host:port/path/to/repos/dirA"</code>,
     * and <code>target</code> may be <code>"dirB"</code>.
     * 
     * <p>
     * If <code>target</code> is <span class="javakeyword">null</span> or empty (<code>""</code>)
     * then the scope of the update operation is the repository location to which
     * this object is set.
     * 
     * <p>
     * The <code>reporter</code> is used to describe the state of the local item(s) (i.e. 
     * items' revision numbers, deleted, switched items). All the paths described by the 
     * <code>reporter</code> should be relative to the repository location to which this 
     * object is set. 
     * 
     * <p>
     * After that the <code>editor</code> is used to carry out all the work on 
     * updating. This <code>editor</code> contains 
     * knowledge of where the change will begin (when {@link ISVNEditor#openRoot(long) ISVNEditor.openRoot()} 
     * is called).
	 * 
     * <p>
     * <b>NOTE:</b> you may not invoke methods of this <b>SVNRepository</b>
     * object from within the provided <code>reporter</code> and <code>editor</code>.
	 * 
     * @param  revision 		a desired revision to make update to; defaults to
     *                          the latest revision (HEAD)
     * @param  target 			an entry name (optional)  
     * @param  recursive        if <span class="javakeyword">true</span> and the update scope
     *                          is a directory, descends recursively, otherwise not 
     * @param  reporter         a caller's reporter
     * @param  editor           a caller's editor
     * @throws SVNException     in the following cases:
     *                          <ul>
     *                          <li>a failure occured while connecting to a repository 
     *                          <li>the user authentication failed 
     *                          (see {@link org.tmatesoft.svn.core.SVNAuthenticationException})
     *                          </ul>
     * @see                     #update(SVNURL, long, String, boolean, ISVNReporterBaton, ISVNEditor)
     * @see 					ISVNReporterBaton
     * @see 					ISVNReporter
     * @see 					ISVNEditor
     * @see                     <a href="http://svnkit.com/kb/dev-guide-update-operation.html">Using ISVNReporter/ISVNEditor in update-related operations</a>
     */
    public abstract void update(long revision, String target, boolean recursive, ISVNReporterBaton reporter, ISVNEditor editor) throws SVNException;
    
    /**
     * Gets status of a path.
     *
     * <p>
     * <code>target</code> is the name (one-level path component) of an entry that will 
     * restrict the scope of the status to this entry. In other words <code>target</code> is a child entry of the 
     * directory represented by the repository location to which this object is set. For
     * example, if we have something like <code>"/dirA/dirB"</code> in a repository, then
     * this object's repository location may be set to <code>"svn://host:port/path/to/repos/dirA"</code>,
     * and <code>target</code> may be <code>"dirB"</code>.
     * 
     * <p>
     * If <code>target</code> is <span class="javakeyword">null</span> or empty (<code>""</code>)
     * then the scope of the update operation is the repository location to which
     * this object is set.
     * 
     * <p>
     * The <code>reporter</code> is used to describe the state of the local item(s) (i.e. 
     * items' revision numbers, deleted, switched items). All the paths described by the 
     * <code>reporter</code> should be relative to the repository location to which this 
     * object is set. 
     * 
     * <p>
     * After that the <code>editor</code> is used to carry out all the work on 
     * performing status. This <code>editor</code> contains 
     * knowledge of where the change will begin (when {@link ISVNEditor#openRoot(long) ISVNEditor.openRoot()} 
     * is called).
     * 
     * <p>
     * <b>NOTE:</b> you may not invoke methods of this <b>SVNRepository</b>
     * object from within the provided <code>reporter</code> and <code>editor</code>.
     * 
     * @param  revision         a desired revision to get status against; defaults to
     *                          the latest revision (HEAD)
     * @param  target           an entry name (optional)  
     * @param  recursive        if <span class="javakeyword">true</span> and the status scope
     *                          is a directory, descends recursively, otherwise not 
     * @param  reporter 		a client's reporter-baton
     * @param  editor 			a client's status editor
     * @throws SVNException     in the following cases:
     *                          <ul>
     *                          <li>a failure occured while connecting to a repository 
     *                          <li>the user authentication failed 
     *                          (see {@link org.tmatesoft.svn.core.SVNAuthenticationException})
     *                          </ul>
     * @see 					ISVNReporterBaton
     * @see 					ISVNEditor
     */
    public abstract void status(long revision, String target, boolean recursive, ISVNReporterBaton reporter, ISVNEditor editor) throws SVNException;
    
    /**
     * Updates a path switching it to a new repository location.  
     * 
     * <p>
     * Updates a path as it's described for the {@link #update(long, String, boolean, ISVNReporterBaton, ISVNEditor) update()}
     * method using the provided <code>reporter</code> and <code>editor</code>, and switching
     * it to a new repository location. 
     * 
     * <p>
     * <b>NOTE:</b> you may not invoke methods of this <b>SVNRepository</b>
     * object from within the provided <code>reporter</code> and <code>editor</code>.
     * 
     * @param  url 				a new location in the repository to switch to
     * @param  revision         a desired revision to make update to; defaults
     *                          to the latest revision (HEAD)
     * @param  target           an entry name (optional)  
     * @param  recursive        if <span class="javakeyword">true</span> and the switch scope
     *                          is a directory, descends recursively, otherwise not 
     * @param  reporter         a caller's reporter
     * @param  editor           a caller's editor
     * @throws SVNException     in the following cases:
     *                          <ul>
     *                          <li>a failure occured while connecting to a repository 
     *                          <li>the user authentication failed 
     *                          (see {@link org.tmatesoft.svn.core.SVNAuthenticationException})
     *                          </ul>
     * @see                     #update(long, String, boolean, ISVNReporterBaton, ISVNEditor)
     * @see 					ISVNReporterBaton
     * @see 					ISVNReporter
     * @see 					ISVNEditor
     * @see                     <a href="http://svnkit.com/kb/dev-guide-update-operation.html">Using ISVNReporter/ISVNEditor in update-related operations</a>
     */
    public abstract void update(SVNURL url, long revision, String target, boolean recursive, ISVNReporterBaton reporter, ISVNEditor editor) throws SVNException;
    
    /**
     * Checks out a directory from a repository .
     *
     * <p>
     * <code>target</code> is the name (one-level path component) of an entry that will 
     * restrict the scope of the checkout to this entry. In other words <code>target</code> is a child entry of the 
     * directory represented by the repository location to which this object is set. For
     * example, if we have something like <code>"/dirA/dirB"</code> in a repository, then
     * this object's repository location may be set to <code>"svn://host:port/path/to/repos/dirA"</code>,
     * and <code>target</code> may be <code>"dirB"</code>.
     * 
     * <p>
     * If <code>target</code> is <span class="javakeyword">null</span> or empty (<code>""</code>)
     * then the scope of the checkout operation is the repository location to which
     * this object is set.
     * 
     * <p>
     * The provided <code>editor</code> is used to carry out all the work on 
     * building a local tree of dirs and files being checked out. 
     * 
     * <p>
     * <b>NOTE:</b> you may not invoke methods of this <b>SVNRepository</b>
     * object from within the provided <code>editor</code>.
     * 
     * @param  revision     a desired revision of a dir to check out; defaults
     *                      to the latest revision (HEAD)
     * @param  target       an entry name (optional)  
     * @param  recursive    if <span class="javakeyword">true</span> and the checkout 
     *                      scope is a directory, descends recursively, otherwise not 
     * @param  editor 		a caller's checkout editor
     * @throws SVNException	in the following cases:
     *                      <ul>
     *                      <li>the checkout scope is not a directory (only dirs can
     *                      be checked out)
     *                      <li>a failure occured while connecting to a repository 
     *                      <li>the user authentication failed 
     *                      (see {@link org.tmatesoft.svn.core.SVNAuthenticationException})
     *                      </ul>
     * @see                 #update(long, String, boolean, ISVNReporterBaton, ISVNEditor)
     * @see    				ISVNEditor
     * 
     */
    public void checkout(long revision, String target, boolean recursive, ISVNEditor editor) throws SVNException {
        final long lastRev = revision >= 0 ? revision : getLatestRevision();
        // check path?
        SVNNodeKind nodeKind = checkPath("", revision);
        if (nodeKind == SVNNodeKind.FILE) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.RA_ILLEGAL_URL, "URL ''{0}'' refers to a file, not a directory", getLocation());
            SVNErrorManager.error(err);
        } else if (nodeKind == SVNNodeKind.NONE) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.RA_ILLEGAL_URL, "URL ''{0}'' doesn't exist", getLocation());
            SVNErrorManager.error(err);
        }
        update(revision, target, recursive, new ISVNReporterBaton() {
                    public void report(ISVNReporter reporter) throws SVNException {
                        reporter.setPath("", null, lastRev, true);
                        reporter.finishReport();
                    }            
                }, editor);
    }
    
    /**
     * Replays the changes from the specified revision through the given editor.
     * 
     * <p>
     * Changes will be limited to those that occur under a session's URL, and
     * the server will assume that the client has no knowledge of revisions
     * prior to a <code>lowRevision</code>.  These two limiting factors define the portion
     * of the tree that the server will assume the client already has knowledge of,
     * and thus any copies of data from outside that part of the tree will be
     * sent in their entirety, not as simple copies or deltas against a previous
     * version.
     * 
     * <p>
     * If <code>sendDeltas</code> is <span class="javakeyword">null</span>, the actual text 
     * and property changes in the revision will be sent, otherwise no text deltas and 
     * <span class="javakeyword">null</span> property changes will be sent instead.
     * 
     * <p>
     * If <code>lowRevision</code> is invalid, it defaults to 0.
     * 
     * @param  lowRevision     a low revision point beyond which a client has no
     *                         knowledge of paths history        
     * @param  revision        a revision to replay
     * @param  sendDeltas      controls whether text and property changes are to be
     *                         sent
     * @param  editor          a commit editor to receive changes 
     * @throws SVNException
     * @since  1.1, new in SVN 1.4
     */
    public abstract void replay(long lowRevision, long revision, boolean sendDeltas, ISVNEditor editor) throws SVNException;

    /* write methods */
    /**
     * Gets an editor for committing changes to a repository. Having got the editor
     * traverse a local tree of dirs and/or files to be committed, handling them    
     * with corresponding methods of the editor. 
     * 
     * <p>
     * <code>mediator</code> is used for temporary delta data storage allocations. 
     * 
     * <p>
     * The root path of the commit is the current repository location to which
     * this object is set.
     * 
     * <p>
     * After the commit has succeeded {@link ISVNEditor#closeEdit() 
     * ISVNEditor.closeEdit()} returns an <b>SVNCommitInfo</b> object
     * that contains a new revision number, the commit date, commit author.
     * 
     * <p>
     * This method should be rather used with pre-1.2 repositories.
     * 
     * <p>
     * <b>NOTE:</b> you may not invoke methods of this <b>SVNRepository</b>
     * object from within the returned commit editor.
	 * 
     * @param  logMessage       a commit log message
     * @param  mediator         temp delta storage provider; used also to cache
     *                          wcprops while committing  
     * @return                  an editor to commit a local tree of dirs and/or files 
     * @throws SVNException     in the following cases:
     *                          <ul>
     *                          <li>the repository location this object is set to is not a 
     *                          directory
     *                          <li>a failure occured while connecting to a repository 
     *                          <li>the user authentication failed 
     *                          (see {@link org.tmatesoft.svn.core.SVNAuthenticationException})
     *                          </ul>
	 * @see 					ISVNEditor
	 * @see 					ISVNWorkspaceMediator
     * @see                     <a href="http://svnkit.com/kb/dev-guide-commit-operation.html">Using ISVNEditor in commit operations</a>
     */
    public ISVNEditor getCommitEditor(String logMessage, final ISVNWorkspaceMediator mediator) throws SVNException {
        return getCommitEditor(logMessage, null, false, mediator);
    }
    
    /**
     * Gives information about an entry located at the specified path in a particular 
     * revision. 
     * 
     * <p>
     * The <code>path</code> arg can be both relative to the location of 
     * this driver and absolute to the repository root (starts with <code>"/"</code>).
     * 
     * @param  path			an item's path
     * @param  revision		a revision of the entry; defaults to the latest 
     *                      revision (HEAD)  
     * @return				an <b>SVNDirEntry</b> containing information about
     * 						the entry or <span class="javakeyword">null</span> if 
     *                      there's no entry with at the specified <code>path</code> 
     *                      under the specified <code>revision</code>
     * @throws SVNException in the following cases:
     *                      <ul>
     *                      <li>a failure occured while connecting to a repository 
     *                      <li>the user authentication failed 
     *                      (see {@link org.tmatesoft.svn.core.SVNAuthenticationException})
     *                      </ul>
     */
    public abstract SVNDirEntry info(String path, long revision) throws SVNException;
    
    /**
	 * Gets an editor for committing changes to a repository. Having got the editor
     * traverse a local tree of dirs and/or files to be committed, handling them    
     * with corresponding methods of the editor. 
     * 
	 * <p>
	 * <code>locks</code> is a map used to provide lock tokens on the locked paths. 
     * Keys are locked paths in a local tree, and each value for a key is a lock 
 	 * token. <code>locks</code> must live during the whole commit operation.
 	 * 
 	 * <p>
 	 * If <code>keepLocks</code> is <span class="javakeyword">true</span>, then the locked 
     * paths won't be unlocked after a successful commit. Otherwise, if 
     * <span class="javakeyword">false</span>, locks will be automatically released.
	 * 
     * <p>
	 * <code>mediator</code> is used for temporary delta data storage allocations. 
     * 
     * <p>
	 * The root path of the commit is the current repository location to which
     * this object is set.
	 * 
	 * <p>
	 * After the commit has succeeded {@link ISVNEditor#closeEdit() 
	 * ISVNEditor.closeEdit()} returns an <b>SVNCommitInfo</b> object
	 * that contains a new revision number, the commit date, commit author.
	 * 
     * <p>
     * <b>NOTE:</b> you may not invoke methods of this <b>SVNRepository</b>
     * object from within the returned commit editor.
     * 
     * @param  logMessage		a commit log message
     * @param  locks			a map containing locked paths mapped to lock 
     *                          tokens
     * @param  keepLocks		<span class="javakeyword">true</span> to keep 
     *                          existing locks;	<span class="javakeyword">false</span> 
     *                          to release locks after the commit
     * @param  mediator			temp delta storage provider; used also to cache
     *                          wcprops while committing  
     * @return					an editor to commit a local tree of dirs and/or files 
     * @throws SVNException     in the following cases:
     *                          <ul>
     *                          <li>the repository location this object is set to is not a 
     *                          directory
     *                          <li>a failure occured while connecting to a repository 
     *                          <li>the user authentication failed 
     *                          (see {@link org.tmatesoft.svn.core.SVNAuthenticationException})
     *                          </ul>
     * @see                     #getCommitEditor(String, ISVNWorkspaceMediator)
     * @see                     <a href="http://svnkit.com/kb/dev-guide-commit-operation.html">Using ISVNEditor in commit operations</a>
     */    
    public abstract ISVNEditor getCommitEditor(String logMessage, Map locks, boolean keepLocks, final ISVNWorkspaceMediator mediator) throws SVNException;
    
    /**
     * Gets the lock for the file located at the specified path.
     * If the file has no lock the method returns <span class="javakeyword">null</span>.
     * 
     * <p>
     * The <code>path</code> arg can be both relative to the location of 
     * this driver and absolute to the repository root (starts with <code>"/"</code>).
     * 
     * @param  path		     a file path  
     * @return			     an <b>SVNLock</b> instance (representing the lock) or 
     * 					     <span class="javakeyword">null</span> if there's no lock
     * @throws SVNException  in the following cases:
     *                       <ul>
     *                       <li>a failure occured while connecting to a repository 
     *                       <li>the user authentication failed 
     *                       (see {@link org.tmatesoft.svn.core.SVNAuthenticationException})
     *                       </ul>
     * @see				     #lock(Map, String, boolean, ISVNLockHandler)
     * @see				     #unlock(Map, boolean, ISVNLockHandler)
     * @see				     #getLocks(String)
     * @see				     org.tmatesoft.svn.core.SVNLock
     * @since			     SVN 1.2
     */
    public abstract SVNLock getLock(String path) throws SVNException;

    /**
	 * Gets all locks on or below the <code>path</code>, that is if the repository 
	 * entry (located at the <code>path</code>) is a directory then the method 
	 * returns locks of all locked files (if any) in it.
     * 
     * <p>
     * The <code>path</code> arg can be both relative to the location of 
     * this driver and absolute to the repository root (starts with <code>"/"</code>).
     * 
     * @param  path 		a path under which locks are to be retrieved
     * @return 				an array of <b>SVNLock</b> objects (representing locks)
     * @throws SVNException in the following cases:
     *                      <ul>
     *                      <li>a failure occured while connecting to a repository 
     *                      <li>the user authentication failed 
     *                      (see {@link org.tmatesoft.svn.core.SVNAuthenticationException})
     *                      </ul>
     * @see					#lock(Map, String, boolean, ISVNLockHandler)
     * @see					#unlock(Map, boolean, ISVNLockHandler)
     * @see					#getLock(String)
     * @see                 org.tmatesoft.svn.core.SVNLock
     * @since				SVN 1.2
     */
    public abstract SVNLock[] getLocks(String path) throws SVNException;
    
    /**
	 * Locks path(s) at definite revision(s).
	 * 
	 * <p>
	 * Note that locking is never anonymous, so any server implementing
	 * this function will have to "pull" a username from the client, if
	 * it hasn't done so already.
	 * 
     * <p>
	 * Each path to be locked is handled with the provided <code>handler</code>.
     * If a path was successfully locked, the <code>handler</code>'s 
     * {@link ISVNLockHandler#handleLock(String, SVNLock, SVNErrorMessage) handleLock()}
     * is called that receives the path and either a lock object (representing the lock
     * that was set on the path) or an error exception, if locking failed for that path.
     * 
     * <p>
	 * If any path is already locked by a different user and the
	 * <code>force</code> flag is <span class="javakeyword">false</span>, then this call fails
	 * with throwing an <b>SVNException</b>. But if <code>force</code> is
	 * <span class="javakeyword">true</span>, then the existing lock(s) will be "stolen" anyway,
	 * even if the user name does not match the current lock's owner.
	 * 
     * <p>
     * Paths can be both relative to the location of this driver and absolute to 
     * the repository root (starting with <code>"/"</code>).
     * 
     * @param  pathsToRevisions		a map which keys are paths and values are 
     * 								revision numbers (as Longs); paths are strings and revision 
     * 								numbers are Long objects 
     * @param  comment				a comment string for the lock (optional)
     * @param  force				<span class="javakeyword">true</span> if the file is to be 
     *                              locked in any way (even if it's already locked by someone else)
     * @param  handler              if not <span class="javakeyword">null</span>, the lock
     *                              handler is invoked on each path to be locked  
     * @throws SVNException         in the following cases:
     *                              <ul>
     *                              <li><code>force</code> is <span class="javakeyword">false</span>
     *                              and a path is already locked by someone else
     *                              <li>a revision of a path is less than its last changed revision
     *                              <li>a path does not exist in the latest revision 
     *                              <li>a failure occured while connecting to a repository 
     *                              <li>the user authentication failed 
     *                              (see {@link org.tmatesoft.svn.core.SVNAuthenticationException})
     *                              </ul>
     * @see							#unlock(Map, boolean, ISVNLockHandler)
     * @see							#getLocks(String)
     * @see							#getLock(String)
     * @see                         org.tmatesoft.svn.core.SVNLock
     * @since 						SVN 1.2
     */
    public abstract void lock(Map pathsToRevisions, String comment, boolean force, ISVNLockHandler handler) throws SVNException;
    
    /**
	 * Removes lock(s) from the file(s). 
	 * 
	 * <p>
	 * Note that unlocking is never anonymous, so any server
	 * implementing this function will have to "pull" a username from
	 * the client, if it hasn't done so already.
	 * 
     * <p>
     * Each path to be unlocked is handled with the provided <code>handler</code>.
     * If a path was successfully unlocked, the <code>handler</code>'s 
     * {@link ISVNLockHandler#handleUnlock(String, SVNLock, SVNErrorMessage) handleUnlock()}
     * is called that receives the path and either a lock object (representing the lock
     * that was removed from the path) or an error exception, if unlocking failed for 
     * that path.
     * 
     * <p>
	 * If the username doesn't match the lock's owner and <code>force</code> is 
	 * <span class="javakeyword">false</span>, this method call fails with
	 * throwing an <b>SVNException</b>.  But if the <code>force</code>
	 * flag is <span class="javakeyword">true</span>, the lock will be "broken" 
	 * by the current user.
	 * 
	 * <p>
	 * Also if the lock token is incorrect or <span class="javakeyword">null</span>
	 * and <code>force</code> is <span class="javakeyword">false</span>, the method 
	 * fails with throwing a <b>SVNException</b>. However, if <code>force</code> is 
	 * <span class="javakeyword">true</span> the lock will be removed anyway.
	 *
     * <p>
     * Paths can be both relative to the location of this driver and absolute to 
     * the repository root (starting with <code>"/"</code>).
     * 
     * @param  pathToTokens	a map which keys are file paths and values are file lock
     * 						tokens (both keys and values are strings)
     * @param  force		<span class="javakeyword">true</span> to remove the 
     * 						lock in any case - i.e. to "break" the lock
     * @param  handler      if not <span class="javakeyword">null</span>, the lock
     *                      handler is invoked on each path to be unlocked
     * @throws SVNException in the following cases:
     *                      <ul>
     *                      <li><code>force</code> is <span class="javakeyword">false</span>
     *                      and the name of the user who tries to unlock a path does not match
     *                      the lock owner
     *                      <li>a lock token is incorrect for a path
     *                      <li>a failure occured while connecting to a repository 
     *                      <li>the user authentication failed 
     *                      (see {@link org.tmatesoft.svn.core.SVNAuthenticationException})
     *                      </ul>
     * @see 				#lock(Map, String, boolean, ISVNLockHandler)
     * @see					#getLocks(String)
     * @see					#getLock(String)
     * @see                 org.tmatesoft.svn.core.SVNLock
     * @since				SVN 1.2
     */
    public abstract void unlock(Map pathToTokens, boolean force, ISVNLockHandler handler) throws SVNException;
    
    /**
     * Closes the current session closing a socket connection used by
     * this object.    
     * If this driver object keeps a single connection for 
     * all the data i/o, this method helps to reset the connection.
     * 
     * @throws SVNException  if some i/o error has occurred
     */
    public abstract void closeSession() throws SVNException;
    
    /**
     * Returns the session options object this driver is using.
     * If no options object was provided to create this driver then 
     * it uses a default one - {@link ISVNSession#DEFAULT}. 
     * 
     * @return a session options object
     */
    public ISVNSession getOptions() {
        if (myOptions == null) {
            myOptions = ISVNSession.DEFAULT;
        }
        return myOptions;
    }
    
    protected synchronized void lock() {
        try {
            synchronized(this) {
                while ((myLockCount > 0) || (myLocker != null)) {
                    if (Thread.currentThread() == myLocker) {
                        throw new Error("SVNRepository methods are not reenterable");
                    }
                    wait();
                }
                myLocker = Thread.currentThread();
                myLockCount = 1;
            }
    	} catch (InterruptedException e) {
    	    throw new Error("Interrupted attempt to aquire write lock");
    	}
    }
    
    protected synchronized void unlock() {
        synchronized(this) {
            if (--myLockCount <= 0) {
                myLockCount = 0;
                myLocker = null;
                notifyAll();
            }
        }
    }
    
    protected static boolean isInvalidRevision(long revision) {
        return revision < 0;
    }    
    
    protected static boolean isValidRevision(long revision) {
        return revision >= 0;
    }
    
    protected static Long getRevisionObject(long revision) {
        return isValidRevision(revision) ? new Long(revision) : null;
    }
    
    protected static void assertValidRevision(long revision) throws SVNException {
        if (!isValidRevision(revision)) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CLIENT_BAD_REVISION, "Invalid revision number ''{0}''", new Long(revision));
            SVNErrorManager.error(err);
        }
    }

    // all paths are uri-decoded.
    //
    // get repository path (path starting with /, relative to repository root).
    // get full path (path starting with /, relative to host).
    // get relative path (repository path, now relative to repository location, not starting with '/').

    /**
     * Returns a path relative to the repository root directory given
     * a path relative to the location to which this driver object is set.
     * 
     * @param  relativePath a path relative to the location to which
     *                      this <b>SVNRepository</b> is set
     * @return              a path relative to the repository root
     * @throws SVNException             
     */
    public String getRepositoryPath(String relativePath) throws SVNException {
        if (relativePath == null) {
            return "/";
        }
        if (relativePath.length() > 0 && relativePath.charAt(0) == '/') {
            return relativePath;
        }
        String fullPath = SVNPathUtil.append(getLocation().getPath(), relativePath);
        String repositoryPath = fullPath.substring(getRepositoryRoot(true).getPath().length());
        if ("".equals(repositoryPath)) {
            return "/";
        }
        return repositoryPath;
    }
    
    /**
     * Resolves a path, relative either to the location to which this 
     * driver object is set or to the repository root directory, to a
     * path, relative to the host.
     * 
     * @param  relativeOrRepositoryPath a relative path within the
     *                                  repository 
     * @return                          a path relative to the host
     * @throws SVNException
     */
    public String getFullPath(String relativeOrRepositoryPath) throws SVNException {
        if (relativeOrRepositoryPath == null) {
            return getFullPath("/");
        }
        String fullPath;
        if (relativeOrRepositoryPath.length() > 0 && relativeOrRepositoryPath.charAt(0) == '/') {
            fullPath = SVNPathUtil.append(getRepositoryRoot(true).getPath(), relativeOrRepositoryPath);
        } else {
            fullPath = SVNPathUtil.append(getLocation().getPath(), relativeOrRepositoryPath);
        }
        if (!fullPath.startsWith("/")) {
            fullPath = "/" + fullPath;
        }
        return fullPath;
    }
    
    /**
     * Sets a logger to write debug log information to.
     * 
     * @param log a debug logger
     */
    public void setDebugLog(ISVNDebugLog log) {
        myDebugLog = log;
    }
    
    /**
     * Returns the debug logger currently in use.  
     * 
     * <p>
     * If no debug logger has been specified by the time this call occurs, 
     * a default one (returned by <code>org.tmatesoft.svn.util.SVNDebugLog.getDefaultLog()</code>) 
     * will be created and used.
     * 
     * @return a debug logger
     */
    public ISVNDebugLog getDebugLog() {
        if (myDebugLog == null) {
            return SVNDebugLog.getDefaultLog();
        }
        return myDebugLog;
    }
}
