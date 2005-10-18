/*
 * ====================================================================
 * Copyright (c) 2004 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://tmate.org/svn/license.html.
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
import org.tmatesoft.svn.core.SVNAuthenticationException;
import org.tmatesoft.svn.core.SVNDirEntry;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNLock;
import org.tmatesoft.svn.core.SVNLogEntry;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationManager;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.io.diff.SVNDiffWindow;

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
 * to be accessed via the <i>WebDAV</i> protocol (<code>http://</code> or 
 * <code>https://</code>) or a custom <i>svn</i> one 
 * (<code>svn://</code> or <code>svn+ssh://</code>) a user must initialize the library
 * in a proper way:
 * <pre class="javacode">
 * <span class="javacomment">//import neccessary files</span>
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
 * <span class="javacomment">//for DAV (over http and https)</span>
 * DAVRepositoryFactory.setup();
 * <span class="javacomment">//for SVN (over svn and svn+ssh)</span>
 * SVNRepositoryFactoryImpl.setup();</pre>
 * And only after these steps the client can create <i>WebDAV</i> or <i>SVN</i> 
 * implementations of the <code>SVNRepository</code> abstract class to access 
 * the repository.
 * 
 * <p>
 * This is a general way how a user creates an <b>SVNRepository</b> driver object:
 * <pre class="javacode">
 * String url=<span class="javastring">"http://svn.collab.net/svn/trunk/"</span>;
 * String name=<span class="javastring">"my name"</span>;
 * String password=<span class="javastring">"my password"</span>;
 * repository = <span class="javakeyword">null</span>;
 * <span class="javakeyword">try</span> { 
 *     repository = SVNRepositoryFactory.create(SVNURL.parseURIEncoded(url));
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
 * you can not call methods of <b>SVNRepository</b> neither from within those 
 * handlers that are passed to some of the methods, nor during committing with
 * the help of a commit editor (until the editor's {@link ISVNEditor#closeEdit() closeEdit()} 
 * method is called). 
 * 
 * <p>
 * To authenticate a user over network <b>SVNRepository</b> drivers use
 * <b>ISVNAuthenticationManager</b> auth drivers.
 * 
 * <p>
 * <b>NOTE:</b> unfortunately, at present the JavaSVN library doesn't 
 * provide an implementation for accessing a Subversion repository via the
 * <i>file:///</i> protocol (on a local machine), but in future it will be
 * certainly realized.
 * 
 * @version     1.0
 * @author      TMate Software Ltd.
 * @see         SVNRepositoryFactory
 * @see         org.tmatesoft.svn.core.auth.ISVNAuthenticationManager
 * @see         <a target="_top" href="http://tmate.org/svn/kb/examples/">Examples</a>
 */
public abstract class SVNRepository {
        
    protected String myRepositoryUUID;
    protected SVNURL myRepositoryRoot;
    private SVNURL myLocation;
    
    private int myLockCount;
    private Thread myLocker;
    private ISVNAuthenticationManager myAuthManager;
    private ISVNSession myOptions;

    protected SVNRepository(SVNURL location, ISVNSession options) {
        myLocation = location;
        myOptions = options;
    }
	
    /**
	 * Returns the repository location for which this object is 
     * created. 
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
                SVNErrorManager.error("svn: SVNRepository.setLocation could not change connection protocol '" + myLocation.getProtocol() + "' to '" + url.getProtocol() + "';\n" +
                        "svn: Create another SVNRepository instance instead");
            }
            
            if (forceReconnect || myRepositoryRoot == null) {
                closeSession();
                myRepositoryRoot = null;
                myRepositoryUUID = null;
            } else if (url.toString().startsWith(myRepositoryRoot.toString() + "/") || myRepositoryRoot.equals(url)) {
                // just do nothing
            } else if (url.getProtocol().equals(myRepositoryRoot.getProtocol()) && 
                    url.getHost().equals(myRepositoryRoot.getHost()) &&
                    url.getPort() == myRepositoryRoot.getPort()) {
                closeSession();
                myRepositoryRoot = null;
                myRepositoryUUID = null;
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
     * Gets a cached repository's Universal Unique IDentifier (UUID). 
     * According to uniqueness for different repositories UUID values 
     * (36 character strings) are different. UUID is got and cached at 
     * the time of the first successful repository access operation. 
     * Before that it's <span class="javakeyword">null</span>.
     * 
     * @return 	the UUID of a repository 
     */
    public String getRepositoryUUID() {
        return myRepositoryUUID;
    }

    /**
     * Gets a cached repository's root directory location. The root directory
     * is evaluated and cached at the time of the first successful repository
     * access operation. Before that it's <span class="javakeyword">null</span>.
     * If this driver object is switched to a different repository location during
     * runtime (probably to an absolutely different repository, see {@link #setLocation(SVNURL, boolean) setLocation()}), 
     * the root directory location may be changed. 
     * <p>
     * This method does not force this <b>SVNRepository</b> driver to
     * test a connection.
     * 
     * @return 	the repository root directory location url
     * @see     #getRepositoryRoot(boolean)
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
     * Gets a cached repository's root directory location. The root directory
     * is evaluated and cached at the time of the first successful repository
     * access operation. Before that it's <span class="javakeyword">null</span>.
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
     * Caches identification parameters (UUID, rood directory location) 
     * of the repository with which this driver is working.
     * 
     * @param uuid 		the repository's Universal Unique IDentifier 
     * 					(UUID) 
     * @param rootURL	the repository's root directory location
     * @see 			#getRepositoryRoot()
     * @see 			#getRepositoryUUID()
     */
    protected void setRepositoryCredentials(String uuid, SVNURL rootURL) {
        if (uuid != null && rootURL != null) {
            myRepositoryUUID = uuid;
            myRepositoryRoot = rootURL;
        }
    }
    
    /* init */
    /**
     * Tries to access a repository. Used to check if there're no problems 
     * with accessing a repository and to cache a repository UUID and root
     * dir location.  
     * 
     * @throws SVNException if a failure occured while connecting to a repository 
     *                      or the user's authentication failed (see 
     *                      {@link org.tmatesoft.svn.core.SVNAuthenticationException})
     */
    public abstract void testConnection() throws SVNException; 
    
    /* simple methods */
    
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
    
    /* simple callback methods */
    /**
	 * Returns the kind of an item located at the specified path in
     * a particular revision. If the <code>path</code> does not exist 
     * under the specified <code>revision</code>, {@link org.tmatesoft.svn.core.SVNNodeKind#NONE SVNNodeKind.NONE} 
     * will be returned.
	 * The <code>path</code> is relative to the repository location to which
     * this driver object is set.
     * 
     * @param  path				a path of a node in a repsitory which is to be inspected 
     * @param  revision			a revision number
     * @return 					the node kind for the given <code>path</code> at the given 
     * 							<code>revision</code>
     * @throws SVNException  	if a failure occured while connecting to a repository 
     *                          or the user's authentication failed (see 
     *                          {@link org.tmatesoft.svn.core.SVNAuthenticationException})
     */
    public abstract SVNNodeKind checkPath(String path, long revision) throws SVNException;
    
    /**
	 * Fetches the contents and properties of a file located at the specified path
	 * in a particular revision.
     * <p>
     * The <code>path</code> is relative to the repository location to which
     * this driver object is set.
	 * 
	 * <p>
	 * If <code>properties</code> is not <span class="javakeyword">null</span> it will 
     * receive the properties of the file.  This includes all properties: not just ones 
     * controlled by a user and stored in the repository filesystem, but also non-tweakable 
     * ones (e.g. 'wcprops', 'entryprops', etc.). Property names (keys) are mapped to property
     * values. 
	 * 
     * @param path 				a file path
     * @param revision 			a revision number
     * @param properties 		if not <span class="javakeyword">null</span> then    
     *                          properties will be fetched into this map
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
     * Gets and handles all directory entries under a <code>path</code> at a specified 
     * <code>revision</code>. A found directory entry is passed to a 
     * <code>handler</code> for further processing. 
     *  
     * @param  path 		a directory path relative to a repository location  
     * @param  revision 	an interested revision 
     * @param  properties 	a <code>Map</code> to contain all the directory
     * 						properties (not just ones controlled by the user and 
     * 						stored in the repository fylesystem, but non-tweakable ones).
     * 						Keys are property names and associated values are property 
     * 						values. <code>properties</code> can be <code>null</code> 
     * 						when not interested in directory properties.
     * @param  handler 		a handler to process a next found directory entry
     * @return 				the revision of the directory
     * @throws SVNException	If there's no such <code>path</code> in that <code>revision</code>.
     * 						Also if a failure in connection occured or the user's 
     * 						authentication failed (see 
     * 						{@link SVNAuthenticationException}).
     * @see 				#getDir(String, long, Map, Collection)
     * @see 				SVNDirEntry
     */
    public abstract long getDir(String path, long revision, Map properties, ISVNDirEntryHandler handler) throws SVNException; 

    /**
     * Retrieves all the matched revisions for the specified (by the 
     * <code>path</code> argument) file and passes them to a <code>handler</code>
     * for further processing. 
	 * 
	 * <p>
	 * Only those revisions will be handled where the file was changed at.
	 * The iteration will begin at the first such revision starting from the 
	 * <code>startRevision</code> and so on - up to the <code>endRevision</code>.
	 * Note that if the method succeeds, <code>ISVNFileRevisionHandler</code> will have
	 * been invoked at least once.
	 * 
	 * <p>
	 * In a series of calls to {@link ISVNFileRevisionHandler#handleFileRevision(SVNFileRevision)
	 * handler.handleFileRevision(..)}, the file contents for the first interesting 
	 * revision will be provided as a text delta against the empty file.  In the 
	 * following calls, the delta will be against the fulltext contents for the 
	 * previous call.
	 * 
	 * <p>
	 * <b>NOTE:</b> This functionality is not available in pre-1.1 servers.
	 * 
	 * @param  path 			a file path relative to a repository location
	 * @param  startRevision 	the revision to start from 
	 * @param  endRevision   	the revision to stop at
	 * @param  handler 			a handler that processes file revisions passed  
	 * @return 					the actual number of file revisions existing in
	 * 							[<code>startRevision</code>,<code>endRevision</code>]
	 * 							range 
	 * @throws SVNException		if a failure in connection occured or the user's
     * 							authentication failed (see 
     * 							{@link SVNAuthenticationException}).
	 * @see 					#getFileRevisions(String, Collection, long, long)
	 * @see 					SVNFileRevision
	 * @since					SVN 1.1
	 */    
    public abstract int getFileRevisions(String path, long startRevision, long endRevision, ISVNFileRevisionHandler handler) throws SVNException;
    
    /**
	 * Invokes <code>ISVNLogEntryHandler</code> on each log entry from
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
	 * If <code>targetPaths</code>  has one or more elements, then
	 * only those revisions are processed in which at least one of <code>targetPaths</code> was
	 * changed (i.e., if a file text or properties changed; if a dir properties
	 * changed or an entry was added or deleted). Each path is relative 
	 * to the session's common parent.
	 * 
	 * <p>
	 * If <code>changedPath</code> is set, then each call to
	 * {@link ISVNLogEntryHandler#handleLogEntry(SVNLogEntry) 
	 * ISVNLogEntryHandler.handleLogEntry(SVNLogEntry)} passes a 
	 * <code>SVNLogEntry</code> with the set hash map of changed paths;
	 * the hash's keys are all the paths committed in that revision, and its
	 * values are <code>SVNLogEntryPath</code> instances.
	 * Otherwise, <code>SVNLogEntry</code> will not contain that hash map
	 * for the changed paths.
	 * 
	 * <p>
	 * If <code>strictNode</code> is set, copy history will not be traversed
	 * (if any exists) when harvesting the revision logs for each path.
	 * 
	 * <p>
	 * If <code>startRevision</code> or <code>endRevision</code> is a non-existent
	 * revision, <code>SVNException</code> will be thrown, without ever invoking
	 * <code>ISVNLogEntryHandler</code>.
	 * 
	 * <p>
	 * The caller may not invoke any Repository Access operations using the current 
	 * <code>SVNRepository</code> from within <code>ISVNLogEntryHandler</code>.
	 * 
     * @param  targetPaths 		paths that mean only those revisions at which they were 
     * 							changed. If such a behaviour is not needed simply
     * 							provide <code>String[] {""}</code>.
     * @param  startRevision 	the start revision to get the log entry of.
     * @param  endRevision 		the end revision to get the log entry of.
     * @param  changedPath 		<code>true</code> if log entries are to have a map of 
     * 							the changed paths set; <code>false</code> - otherwise.
     * @param  strictNode 		<code>true</code> if a copy history (if any) is not 
     * 							to be traversed.
     * @param  handler 			<code>ISVNLogEntryHandler</code> to handle log entries.
     * @return 					the number of log entries handled.
     * @throws SVNException
     * @see 					ISVNLogEntryHandler
     * @see 					SVNLogEntry
     */
    public long log(String[] targetPaths, long startRevision, long endRevision, boolean changedPath, boolean strictNode,
            ISVNLogEntryHandler handler) throws SVNException {
        return log(targetPaths, startRevision, endRevision,changedPath,strictNode, 0, handler);
    }
    
    public abstract long log(String[] targetPaths, long startRevision, long endRevision, boolean changedPath, boolean strictNode, long limit,
            ISVNLogEntryHandler handler) throws SVNException;
    
    /**
	 * Gets locations of the file identified by <code>path</code> (which 
	 * it has in <code>pegRevision</code>) at the interested repository <code>revisions</code>.
	 * <code>path</code> is relative to the URL for which the current session
	 * (<code>SVNRepository</code>) was created.
	 * 
	 * <p>
	 * <b>NOTE:</b> This functionality is not available in pre-1.1 servers.
     * 
     * @param  path			the file pathway in the repository
     * @param  pegRevision 	that is the revision in which the file has the given
     *  					<code>path</code> 
     * @param  revisions 	an array of interested revision numbers. If the file 
     * 						doesn't exist in a location revision, that revision will
     * 						be ignored. 
     * @param  handler 		a location entry handler that will handle all found file
     * 						locations
     * @return 				the number of the file locations existing in a 
     * 						<code>revisions</code>
     * @throws SVNException
     * @see 				ISVNLocationEntryHandler
     * @see 				SVNLocationEntry
     */
    public abstract int getLocations(String path, long pegRevision, long[] revisions, ISVNLocationEntryHandler handler) throws SVNException;
    
    /**
     * The same as 
     * {@link #getFileRevisions(String, long, long, ISVNFileRevisionHandler)} except
     * for it just collects all the file revisions found and returns them in a 
     * <code>Collection</code>.
     *   
     * @param  path 		a file path in the repository
     * @param  revisions 	a caller's Collection reference to get  file 
     * 						revisions (keeps references to {@link SVNFileRevision} 
     * 						instances). This parameter can be set to <code>null</code>.  
     * @param  sRevision 	the revision to start from
     * @param  eRevision 	the revision to stop at
     * @return 				a <code>Collection</code> instance that keeps 
     * 						{@link SVNFileRevision} instances 
     * @throws SVNException 
     */
    public Collection getFileRevisions(String path, Collection revisions, long sRevision, long eRevision) throws SVNException {
        final Collection result = revisions != null ? revisions : new LinkedList();
        ISVNFileRevisionHandler handler = new ISVNFileRevisionHandler() {
            public void handleFileRevision(SVNFileRevision fileRevision) {
                result.add(fileRevision);
            }
            public OutputStream handleDiffWindow(String token, SVNDiffWindow delta) {
            	return new OutputStream() {
                    public void write(byte[] b, int o, int l) {
                    }
                    public void write(int r) {
                    }
                };
            }
            public void handleDiffWindowClosed(String token) {
            }
        };
        getFileRevisions(path, sRevision, eRevision, handler);
        return result;
    }
    
    /**
     * The same as {@link #getDir(String, long, Map, ISVNDirEntryHandler)} except for
     * it just collects all the directory entries found and returns them in a 
     * <code>Collection</code>.  
     * 
     * @param  path			a relative path to the repository root 
     * @param  revision 	an interested revision 
     * @param  properties 	a <code>Map</code> to contain all the directory properties 
     * 						(not just ones controlled by the user and stored in the 
     * 						repository fylesystem, but non-tweakable ones, too). Keys
     * 						are property names and associated values are property 
     * 						values. <code>properties</code> can be <code>null</code>
     * 						when not interested in directory properties.
     * @param  dirEntries 	a <code>Collection</code> to get directory entries. Can be
     * 						<code>null</code>.
     * @return 				a <code>Collection</code> containing the directory entries
     * @throws SVNException
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
     * @return Collection of SVNDirEntry objects with commit messages.
     */
    public abstract SVNDirEntry getDir(String path, long revision, boolean includeCommitMessages, Collection entries) throws SVNException;

    /**
     * The same as {@link SVNRepository#log(String[], long, long, boolean, boolean, ISVNLogEntryHandler)},
     * but as a result it returns a <code>Collection</code> of retrieved log entries.
     * 
     * @param  targetPaths 		paths that mean only those revisions at which they were 
     * 							changed.
     * @param  entries 			<code>Collection</code> instance to receive log entries
     * @param  startRevision 	the start revision to get the log entry of.
     * @param  endRevision 		the end revision to get the log entry of.
     * @param  changedPath 		<code>true</code> if log entries are to have the changed
     * 							paths <code>Map</code> set; <code>false</code> - 
     * 							otherwise.
     * @param  strictNode 		<code>true</code> if a copy history (if any) is not to
     * 							be traversed.
     * @return 					a <code>Collection</code> containing log entries.
     * @throws SVNException
     * @see 					ISVNLogEntryHandler
     * @see 					SVNLogEntry
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
     * The same as {@link #getLocations(String, long, long[], ISVNLocationEntryHandler)},
     * but it returns a <code>Collection</code> containing {@link SVNLocationEntry 
     * location entries}.
     * 
     * @param  path 		a file path relative to a repository location
     * @param  entries 		a <code>Collection</code> to receive entries. Can be
     * 						<code>null</code>
     * @param  pegRevision  that is the revision in which the file has the given
     *  					<code>path</code>
     * @param  revisions 	an array of interested revision numbers. If the file 
     * 						doesn't exist in a location revision, that revision will 
     * 						be ignored.
     * @return 				a <code>Collection</code> containing retrieved entries.
     * @throws SVNException 
     * @see 				SVNLocationEntry
     * @see 				ISVNLocationEntryHandler
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

    public Map getLocations(String path, Map entries, long pegRevision, long[] revisions) throws SVNException {
        final Map result = entries != null ? entries : new HashMap();
        getLocations(path, pegRevision, revisions, new ISVNLocationEntryHandler() {
            public void handleLocationEntry(SVNLocationEntry locationEntry) {
                result.put(new Long(locationEntry.getRevision()), locationEntry);
            } 
        });
        return result;        
    }
	
    /* edit-mode methods */
	/**
	 * Gets differences between two repository items - one specified as a <code>target</code> 
	 * and another as <code>url</code>. 
	 * It's another form of 
	 * {@link #update(long, String, boolean, ISVNReporterBaton, ISVNEditor) update()}.
	 * 
	 * <p>
	 * <b>
	 * Please note: this method cannot be used to diff a single
	 * file, only a working copy directory.  See the {@link #update(SVNURL, long, String, boolean, ISVNReporterBaton, ISVNEditor)
	 * update()} for more details.
	 * </b>
	 * 
	 * <p>
	 * The client initially provides a diff editor (<code>ISVNEditor</code>) to the RA
	 * layer; this editor contains knowledge of where the common diff
	 * root is in the working copy (when {@link ISVNEditor#openRoot(long)
	 * ISVNEditor.openRoot(long)} is called). 
	 * 
	 * <p>
	 * The {@link ISVNReporterBaton reporter-baton} is used to 
	 * describe client's working-copy revision numbers by calling the methods of
	 * {@link ISVNReporter}; the RA layer assumes that all
	 * paths are relative to the <code>URL</code> used to open the current repository session.	 * </p>
	 * 
	 * <p>
	 * When finished, the client calls {@link ISVNReporter#finishReport() 
	 * ISVNReporter.finishReport()}. The RA layer then does a complete drive of the
	 * diff editor, ending with {@link ISVNEditor#closeEdit() ISVNEditor.closeEdit()},
	 * to transmit the diff.
	 * 
	 * <p>
	 * <code>target</code> is an optional single path component that will restrict
	 * the scope of the diff to an entry in the directory represented by
	 * the current session's <code>URL</code>, or empty if the entire directory is 
	 * meant to be one of the diff paths.
	 * 
	 * <p>
	 * The working copy will be diffed against <code>url</code> as it exists
	 * in <code>revision</code>, or as it is in head if <code>revision</code> is
	 * invalid.
	 * 
	 * <p>
	 * Use <code>ignoreAncestry</code> to control whether or not items being
	 * diffed will be checked for relatedness first.  Unrelated items
	 * are typically transmitted to the editor as a deletion of one thing
	 * and the addition of another, but if this flag is <code>true</code>,
	 * unrelated items will be diffed as if they were related.
	 * 
	 * <p>
	 * If <code>recursive</code> is true and the target is a directory, diff
	 * recursively; otherwise, diff just target and its immediate entries,
	 * but not its child directories (if any).
	 * 
	 * <p>
	 * The caller may not perform any RA operations using the current
	 * <code>SVNRepository</code> object before finishing the report, and may not
	 * perform any RA operations using <code>SVNRepository</code> from within
	 * the editing operations of the diff editor.
	 * 
	 * @param  url 				a URL to the entry to be diffed 
	 * 							against.
	 * @param  revision 		a revision number of the entry located at the 
	 * 							<code>url</code>
	 * @param  target 			relative to the current repository location path of the
	 * 							entry to be diffed against <code>url</code>.
	 * @param  ignoreAncestry 	set <code>true</code> to control relatedness.
	 * @param  recursive 		<code>true</code> to diff recursively, 
	 * 							<code>false</code> - otherwise.
     * @param  reporter 		a client's reporter-baton
     * @param  editor 			a client's update editor
     * @throws SVNException
     * @see 					ISVNReporterBaton
     * @see 					ISVNReporter
     * @see 					ISVNEditor
	 */
    public abstract void diff(SVNURL url, long targetRevision, long revision, String target, boolean ignoreAncestry, boolean recursive, ISVNReporterBaton reporter, ISVNEditor editor) throws SVNException;
    
    /**
     * @deprecated use diff with 'targetRevision' parameter instead
     */
    public abstract void diff(SVNURL url, long revision, String target, boolean ignoreAncestry, boolean recursive, ISVNReporterBaton reporter, ISVNEditor editor) throws SVNException;
    
    /**
     * Asks the Repository Access (RA) Layer to update a working copy.
	 * The client initially provides an update editor ({@link ISVNEditor});
	 * this editor contains knowledge of where the change will
	 * begin in the working copy (when {@link ISVNEditor#openRoot(long)
	 * ISVNEditor.openRoot(long)} is called).
	 * 
	 * <p>
	 * The {@link ISVNReporterBaton reporter-baton} is used to 
	 * describe client's working-copy revision numbers by calling the methods of
	 * {@link ISVNReporter}; the RA layer assumes that all
	 * paths are relative to the URL used to open the current repository session.
	 * 
	 * <p>
	 * When finished, the client calls {@link ISVNReporter#finishReport() ISVNReporter.finishReport()} 
	 * from within the <code>reporter</code>.  The RA layer then does a complete drive
	 * of the <code>editor</code>, ending with {@link ISVNEditor#closeEdit() ISVNEditor.closeEdit()},
	 * to update the working copy. 
	 * 
	 * <p>
	 * <code>target</code> is an optional single path component to restrict
	 * the scope of the update to just that entry in the directory represented by
	 * the current session' URL.
	 * If the <code>target</code> is <code>null</code>, the entire directory is 
	 * updated.
	 * 
	 * <p>
	 * If <code>recursive</code> is true and the <code>target</code> is a directory,
	 * update recursively; otherwise, update just the <code>target</code> and its
	 * immediate entries, but not its child directories (if any).
	 * 
	 * 
	 * <p>
	 * The working copy will be updated to <code>revision</code>, or the
	 * "latest" revision if this parameter is invalid.
	 * 
	 * <p>
	 * The caller may not perform any RA operations using the current
	 * <code>SVNRepository</code>
	 * before finishing the report, and may not perform any RA operations
	 * using <code>SVNRepository</code> from within the editing operations
	 * of update <code>ISVNEditor</code>.
	 * 
     * @param  revision 		a desired update revision
     * @param  target 			a path to restrict the update scope 
     * @param  recursive 		<code>true</code> to update the working copy recursively
     * @param  reporter 		a client's reporter-baton
     * @param  editor 			a client's update editor
     * @throws SVNException
     * @see 					ISVNReporterBaton
     * @see 					ISVNReporter
     * @see 					ISVNEditor
     */
    public abstract void update(long revision, String target, boolean recursive, ISVNReporterBaton reporter, ISVNEditor editor) throws SVNException;
    
    /**
	 * Ask the Repository Access (RA) Layer to describe the status of a working copy 
	 * with respect to  <code>revision</code> of the repository (or HEAD - the latest
	 * revision, if <code>revision</code> is invalid).
	 * 
	 * <p>
	 * The client initially provides an editor {@link ISVNEditor}  to the RA
	 * layer; this editor contains knowledge of where the change will
	 * begin in the working copy (when {@link ISVNEditor#openRoot(long)
	 * ISVNEditor.openRoot(long)} is called).
	 * 
	 * <p>
	 * The {@link ISVNReporterBaton reporter} is used to 
	 * describe client's working-copy revision numbers by calling the methods of
	 * {@link ISVNReporter}; the RA layer assumes that all
	 * paths are relative to the URL used to open the current repository session.
	 * 
	 * <p>
	 * When finished, the client calls {@link ISVNReporter#finishReport()} 
	 * from within the <code>reporter</code>. The RA
	 * layer then does a complete drive of the status <code>editor</code>, ending with
	 * {@link ISVNEditor#closeEdit()}, to report, essentially, what would be modified in
	 * the working copy were the client to call {@link #update(long, String, boolean, ISVNReporterBaton, ISVNEditor)
	 * update()}.
	 * 
	 * <p>
	 * <code>target</code> is an optional single path component to restrict
	 * the scope of the status report to an entry in the directory represented by
	 * the current session's URL, or empty if the entire
	 * directory is meant to be examined.
	 * 
	 * <p>
	 * If <code>recursive</code> is true and the target is a directory, get status
	 * recursively; otherwise, get status for just the target and its
	 * immediate entries, but not its child directories (if any).
	 * 
	 * <p>
	 * The caller may not perform any RA operations using the current
	 * <code>SVNRepository</code>
	 * before finishing the report, and may not perform any RA operations
	 * using <code>SVNRepository</code> from within the editing operations
	 * of status <code>ISVNEditor</code>.
     * 
     * @param  revision 		a revision number for which the working copy status 
     * 							will be described
     * @param  target 			that is the path (relative to the current session
     * 							URL) to an entry to be examined. It can be
     * 							<code>null</code> to check out the entire session root
     * 							directory.
     * @param  recursive 		<code>true</code> to get status for the working copy 
     * 							recursively or not
     * @param  reporter 		a client's reporter-baton
     * @param  editor 			a client's status editor
     * @throws SVNException
     * @see 					ISVNReporterBaton
     * @see 					ISVNEditor
     */
    public abstract void status(long revision, String target, boolean recursive, ISVNReporterBaton reporter, ISVNEditor editor) throws SVNException;
    
    /**
     * Asks the Repository Access Layer to switch a workin copy to a new URL. 
     * 
     * <p>
     * The same as {@link #update(long, String, boolean, ISVNReporterBaton, ISVNEditor)}
     * except for this version updates the current directory (or a <code>target</code>
     * if it's not <code>null</code>) comparing it with the provided <code>url</code>
     * and resetting its origin URL to that one.
     * 
     * <p> 
     * This is another form of an update. Namely, it updates the client's working copy 
     * to mirror a new URL (switching a working copy to a new branch). As for 
     * the rest, see the description for
     * {@link #update(long, String, boolean, ISVNReporterBaton, ISVNEditor)}
     * 
     * @param  url 				a new location in the repository to switch to
     * @param  revision 		a desired update revision
     * @param  target 			a path to restrict the update scope
     * @param  recursive 		<code>true</code> to update the working copy 
     * 							recursively
     * @param  reporter 		a client's reporter-baton
     * @param  editor 			a client's editor
     * @throws SVNException
     * @see 					ISVNReporterBaton
     * @see 					ISVNReporter
     * @see 					ISVNEditor
     */
    public abstract void update(SVNURL url, long revision, String target, boolean recursive, ISVNReporterBaton reporter, ISVNEditor editor) throws SVNException;
    
    /**
     * Asks the Repository Access Layer to checkout a working copy. This method is a
     * kind of an update operation.   
     * 
     * <p>
     * To checkout a working copy the current session should have been set to a
     * directory, not a file - i.e. that URL that was used to create 
     * that in its turn was used to create the current
     * <code>SVNRepository</code> object, that URL should point to a 
     * directory; otherwise a SVNException will be thrown. 
     * 
     * <p>
     * For more details see description of {@link #update(long, String, boolean, ISVNReporterBaton, ISVNEditor)
     * update()}.
     * 
     * @param  revision		a revision of the scope that is to be checked out
     * @param  target 		that is a path (relative to the current repository
     * 						location URL) to restrict the checkout scope to. It can be 
     * 						<code>null</code> to check out the entire session root 
     * 						directory.
     * @param  recursive 	<code>true</code> if the working copy is to be checked out 
     * 						recursively; <code>false</code> - to check out just the 
     * 						entries under the repository location path.
     * @param  editor 		a client's checkout editor
     * @throws SVNException	
     * @see    				ISVNEditor
     */
    public void checkout(long revision, String target, boolean recursive, ISVNEditor editor) throws SVNException {
        final long lastRev = revision >= 0 ? revision : getLatestRevision();
        // check path?
        SVNNodeKind nodeKind = checkPath("", revision);
        if (nodeKind == SVNNodeKind.FILE) {
            throw new SVNException("svn: URL '" + getLocation().toString() + "' refers to a file, not a directory");
        }
        update(revision, target, recursive, new ISVNReporterBaton() {
                    public void report(ISVNReporter reporter) throws SVNException {
                        reporter.setPath("", null, lastRev, true);
                        reporter.finishReport();
                    }            
                }, editor);
    }
    
    /* write methods */
    /**
	 * The same as {@link #getCommitEditor(String, Map, boolean, ISVNWorkspaceMediator)}
	 * except for it's used when there're no any locks on the working copy files. 
	 * Also this version is used when working with Subversion servers that do not 
	 * support file locking (earlier than SVN 1.2).   
	 * 
     * @param  logMessage		a message to be set as the value of the "svn:log"
	 * 							revision property
     * @param  mediator			a temp files and working copy properties manager
     * 							for the needs of a commit
     * @return					an editor to describe a server changes done in the 
     * 							working copy against its BASE-revision
	 * @throws SVNException
	 * @see 					ISVNEditor
	 * @see 					ISVNWorkspaceMediator
     */
    public ISVNEditor getCommitEditor(String logMessage, final ISVNWorkspaceMediator mediator) throws SVNException {
        return getCommitEditor(logMessage, null, false, mediator);
    }
    
    /**
     * Gives information about an entry located at a 
     * <code>path</code> under a specified <code>revision</code>. If the 
     * <code>revision</code> is invalid (&lt0) it is assigned to the HEAD-revision 
     * (the latest revision of the repository).
     * 
     * @param  path			an entry path (relative to a repository location path)
     * @param  revision		a revision of the entry 
     * @return				an <b>SVNDirEntry</b> containing information about
     * 						the entry or <span class="javakeyword">null</span> if there's no entry with
     * 						such <code>path</code> at the <code>revision</code>
     * @throws SVNException if the <code>revision</code> doesn't exist at all
     */
    public abstract SVNDirEntry info(String path, long revision) throws SVNException;
    
    /**
	 * Gets an editor for committing changes to a repository using 
	 * <code>logMessage</code> as a log message for this commit (it 
	 * will be  the value for the <i>"svn:log"</i> property of a new revision). 
	 * 
	 * <p>
	 * The revisions being committed against are passed to 
	 * the editor methods, starting with a revision argument to 
	 * {@link ISVNEditor#openRoot(long) ISVNEditor.openRoot(revision)}.
	 * 
	 * <p>
	 * <code>locks</code>, if non-<code>null</code>, is a <code>Map</code> which
 	 * keys are locked paths in a working copy and each value for a key is a lock 
 	 * token (its identifier, in other words).  The server checks that the
 	 * correct token is provided for each committed, locked path. <code>locks</code> 
 	 * must live during the whole commit operation.
 	 * 
 	 * <p>
 	 * If <code>keepLocks</code> is <code>true</code>, then locks on
 	 * committed objects won't be released.  Otherwise, if <code>false</code>, 
 	 * they will be automatically released.
	 * 
	 * <p>
	 * The path root of the commit is in the current repository location URL.
	 * 
	 * <p>
	 * After the commit has succeeded {@link ISVNEditor#closeEdit() 
	 * ISVNEditor.closeEdit()} returns an instance of <code>SVNCommitInfo</code>
	 * that contains a new revision number, the commit date, commit author.
	 * 
	 * <p>
	 * The caller may not perform any Repository Access operations using the current
	 * <code>SVNRepository</code> before finishing the edit.     
     * 
     * @param  logMessage		a message to be set as the value of the "svn:log"
	 * 							revision property
     * @param  locks			a <code>Map</code> containing locked paths (keys)
     * 							and lock tokens (values)
     * @param  keepLocks		<code>true</code> to keep existing locks; 
     * 							<code>false</code> to release all of them
     * @param  mediator			a temp files and working copy properties manager
     * 							for the needs of a commit
     * @return					an editor to describe a server changes done in the 
     * 							working copy against its BASE-revision
     * @throws SVNException
     */    
    public abstract ISVNEditor getCommitEditor(String logMessage, Map locks, boolean keepLocks, final ISVNWorkspaceMediator mediator) throws SVNException;
    
    /**
     * Gets the lock for the repository file located at the <code>path</code>.
     * If there's no lock on it the method is to return <code>null</code>.
     * 
     * @param path		a file path in the repository (relative to the repository
     *  				root directory) for which the lock is returned (if it
     * 					exists). 
     * @return			an <b>SVNLock</b> instance (representing the lock) or 
     * 					<span class="javakeyword">null</span> if there's no lock
     * @throws 			SVNException
     * @see				#lock(Map, String, boolean, ISVNLockHandler)
     * @see				#unlock(Map, boolean, ISVNLockHandler)
     * @see				#getLocks(String)
     * @see				SVNLock
     * @since			SVN 1.2
     */
    public abstract SVNLock getLock(String path) throws SVNException;

    /**
	 * Gets all locks on or below the <code>path</code>, that is if the repository 
	 * entry (located at the <code>path</code>) is a directory then the method 
	 * returns locks of all locked files (if any) in it.
     * 
     * @param path 			a path in the repository for (or just beyond) which
     * 						all locks are retrieved (the path is relative to the 
     * 						repository root directory)
     * @return 				an array of <b>SVNLock</b> objects (representing locks)
     * @throws 				SVNException
     * @see					#lock(Map, String, boolean, ISVNLockHandler)
     * @see					#unlock(Map, boolean, ISVNLockHandler)
     * @see					#getLock(String)
     * @see					SVNLock
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
	 * The <code>comment</code> is optional: it's either a string
	 * which describes the lock, or it is <span class="javakeyword">null</span>.
	 * 
	 * <p>
	 * If any path is already locked by a different user and the
	 * <code>force</code> flag is <span class="javakeyword">false</span>, then this call fails
	 * with throwing a <b>SVNException</b>. But if <code>force</code> is
	 * <span class="javakeyword">true</span>, then the existing lock(s) will be "stolen" anyway,
	 * even if the user name (who is trying to lock) does not match the current
	 * lock's owner. (That's just the way how user can delete any lock on
	 * the path, and unconditionally create a new lock.)
	 * 
	 * <p>
	 * If a revision is less than the last-changed-revision of
	 * the file to be locked (or if the file path doesn't exist
	 * in HEAD-revision), this call also fails with throwing an 
	 * <b>SVNException</b>.
     * 
     * @param pathsToRevisions		a map which keys are paths and values are 
     * 								revision numbers; paths are strings and revision 
     * 								numbers are Long objects; all paths are assumed to
     * 								be relative to the repository location for which
     * 								the current <b>SVNRepository</b> object was instantiated. 
     * @param comment				a comment string for the lock. It's optional.
     * @param force					<span class="javakeyword">true</span> if the file is to be 
     *                              locked in any way (even if it's already locked by someone else)
     * @param handler
     * @throws 						SVNException
     * @see							#unlock(Map, boolean, ISVNLockHandler)
     * @see							#getLocks(String)
     * @see							#getLock(String)
     * @see							SVNLock
     * @since 						SVN 1.2
     */
    public abstract void lock(Map pathsToRevisions, String comment, boolean force, ISVNLockHandler handler) throws SVNException;
    
    /**
	 * Removes the repository lock(s) for the file(s) located at the path(s) which 
	 * are provided as key(s) of <code>pathToTokens</code>. Keys are mapped to values
	 * of the file lock token(s).
	 * 
	 * <p>
	 * Note that unlocking is never anonymous, so any server
	 * implementing this function will have to "pull" a username from
	 * the client, if it hasn't done so already.
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
     * @param pathToTokens	a map which keys are file paths and values are file lock
     * 						tokens (both keys and values are strings)
     * @param force			<span class="javakeyword">true</span> to remove the 
     * 						lock in any case (in spite of missmatching the lock owner's 
     * 						name or an incorrect lock token).
     * @throws 				SVNException
     * @see 				#lock(Map, String, boolean, ISVNLockHandler)
     * @see					#getLocks(String)
     * @see					#getLock(String)
     * @see 				SVNLock
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
    
    protected ISVNSession getOptions() {
        if (myOptions == null) {
            return ISVNSession.DEFAULT;
        }
        return myOptions;
    }
    
    protected synchronized void lock() {
        try {
            synchronized(this) {
                while ((myLockCount > 0) || (myLocker != null)) {
                    if (Thread.currentThread() == myLocker) {
                        throw new Error("SVNRerpository methods are not reenterable");
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
            SVNErrorManager.error("svn: Invalid revision number '" + revision + "'");
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
     */
    public String getRepositoryPath(String relativePath) {
        if (relativePath == null) {
            return "/";
        }
        if (relativePath.length() > 0 && relativePath.charAt(0) == '/') {
            return relativePath;
        }
        String fullPath = SVNPathUtil.append(getLocation().getPath(), relativePath);
        String repositoryPath = fullPath.substring(getRepositoryRoot().getPath().length());
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
     */
    public String getFullPath(String relativeOrRepositoryPath) {
        if (relativeOrRepositoryPath == null) {
            return getFullPath("/");
        }
        if (relativeOrRepositoryPath.length() > 0 && relativeOrRepositoryPath.charAt(0) == '/') {
            return SVNPathUtil.append(getRepositoryRoot().getPath(), relativeOrRepositoryPath);
        }
        return SVNPathUtil.append(getLocation().getPath(), relativeOrRepositoryPath);
    }
}
