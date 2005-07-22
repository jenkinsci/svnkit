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
import org.tmatesoft.svn.core.SVNRevisionProperty;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationManager;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.io.diff.SVNDiffWindow;

/**
 * The abstract class <code>SVNRepository</code> declares all the basic
 * interface methods as well as implements commonly used ones to work with
 * a Subversion repository. It is the skeleton of the low-level mechanism of 
 * accessing a repository. In the model of the Subversion distributed system of
 * versioning and sharing data this mechanism corresponds to the Repository 
 * Access (RA) Layer.
 * 
 * <p>
 * Actually, the high-level library API rests upon this basis: for example, 
 * manipulations with a working copy (which need an access to a repository), say,
 * commiting it, uses an appropriate implementation (depending on the protocol 
 * that is chosen to access the repository) of <code>SVNRepository</code> as an
 * engine that carries out the commit itself.
 * 
 * <p>
 * It is important to say that before using the library it must be configured 
 * according to implimentations to be used. That is if a repository is assumed
 * to be accessed via the <i>WebDAV</i> protocol(<code>http://</code> or 
 * <code>https://</code>) or a custom <i>SVN</i> one 
 * (<code>svn://</code> or <code>svn+ssh://</code>) a user must initialize the library
 * in this way:
 * <blockquote><pre>
 * 
 * <i>//import neccessary files</i>
 * import org.tmatesoft.svn.core.internal.io.dav.DAVRepositoryFactory;
 * import org.tmatesoft.svn.core.internal.io.svn.SVNRepositoryFactoryImpl;
 * import org.tmatesoft.svn.core.internal.ws.fs.FSEntryFactory;
 * <i>//Set up connection protocols support:</i>
 * <i>//for DAV (over http and https)</i>
 * DAVRepositoryFactory.setup();
 * <i>//for SVN (over svn and svn+ssh)</i>
 * SVNRepositoryFactoryImpl.setup();
 * 
 * </pre></blockquote>
 * And only after these steps the client can create <i>WebDAV</i> or <i>SVN</i> 
 * implementations of the <code>SVNRepository</code> abstract class to access 
 * the repository.
 * 
 * <p>
 * This is a general way how a user creates an <code>SVNRepository</code> for his work:
 * <blockquote><pre>
 * 		
 * String URL="http://svn.collab.net/svn/trunk/";
 * try { 
 * 		SVNRepositoryLocation location = SVNRepositoryLocation.parseURL(URL);
 * 		SVNRepository repository       = SVNRepositoryFactory.create(location);
 *
 * 		<i>//work with the repository</i>
 * } catch (SVNException e){
 * 		e.printStackTrace();
 * }
 * 
 * </pre></blockquote>
 *
 * <p>
 * <b>NOTE:</b> unfortunately, at present the <i>JavaSVN</i> library doesn't 
 * provide an implementation for accessing a Subversion repository via the
 * <i>file:///</i> protocol (on a local machine), but in future it will be
 * certainly realized.
 * 
 * <p>
 * For users familiar with the native Subversion source code it may be useful to
 * know that the <code>SVNRepository</code> interface is more or less similar to
 * the <code><b>include/svn_ra.h</b></code> declaration file.
 * 
 * @version 	1.0
 * @author 		TMate Software Ltd.
 * @see			SVNRepositoryFactory
 * @see 		org.tmatesoft.svn.core.internal.io.dav.DAVRepositoryFactory
 */
public abstract class SVNRepository {
        
    private String myRepositoryUUID;
    private SVNURL myRepositoryRoot;
    private SVNURL myLocation;
    
    private int myLockCount;
    private Thread myLocker;
    private ISVNAuthenticationManager myAuthManager;

    /**
     * Constructs an <code>SVNRepository</code> instance (representing a
     * Repository Access Layer session to work with a repository)
     * given the Subversion repository location as an
     * <code>SVNRepositoryLocation</code> object.
     *
     * @param location 		an <code>SVNRepositoryLocation</code> object that
     * 						incapsulates the repository location (that is
     * 						a <code>URL</code> pointing to a repository
     * 						tree node - not necessarily the repository root
     * 						directory which it was installed to).
     */
    protected SVNRepository(SVNURL location) {
        myLocation = location;
    }
	
    /**
	 * Gets the Subversion repository location as an 
	 * <code>SVNRepositoryLocation</code> object (which incapsulates
	 * the <code>URL</code> that is used to establish a connection
	 * to the repository).
	 *  
	 * @return 		repository location as a 
	 * 				<code>SVNRepositoryLocation</code> instance
	 */    
    public SVNURL getLocation() {
        return myLocation;
    }

    /**
     * Gets a repository's Universal Unique IDentifier (<code>UUID</code>).
     * 
     * <p>
     * A Subversion client uses this identifier to differentiate
     * between one repository and another.
     * 
     * <p>
     * <b>NOTE:</b> the <code>UUID</code> has the same lifetime as the current 
     * session (represented by this <code>SVNRepository</code> object).
     * 
     * @return 	the <code>UUID</code> string of a repository 
     */
    public String getRepositoryUUID() {
        return myRepositoryUUID;
    }

    /**
     * Retrieves the repository's root <code>URL</code>.
     * 
     * <p>
     * This value will not include a trailing '/'.  The returned <code>URL</code>
     * is guaranteed to be a prefix of the <code>URL</code> used to create the 
     * current session (that is the <code>URL</code> passed to create 
     * which for its turn was used to create the 
     * current session object <code>SVNRepository</code> for working with the
     * repository).
     * 
     * <p>
     * <b>NOTE:</b> the <code>URL</code> has the same lifetime as the current
     * session.
     * 
     * @return 	the repository root <code>URL</code>
     */
    public SVNURL getRepositoryRoot() {
        try {
            return getRepositoryRoot(false);
        } catch (SVNException e) {
            // will not be thrown.
        }
        return null;
    }

    public SVNURL getRepositoryRoot(boolean forceConnection) throws SVNException {
        if (forceConnection && myRepositoryRoot == null) {
            testConnection();
        }
        return myRepositoryRoot;
    }

    public void setAuthenticationManager(ISVNAuthenticationManager authManager) {
        myAuthManager = authManager;
    }

    public ISVNAuthenticationManager getAuthenticationManager() {
        return myAuthManager;
    }
    
    /**
     * Sets the following parameters to identify the current repository.
     * Every call to this routine is logged by 
     * {@link org.tmatesoft.svn.util.DebugLog DebugLog}. 
     * 
     * @param uuid 		the repository's Universal Unique IDentifier 
     * 					(<code>UUID</code>) used to differentiate between one
     * 					repository and another. 
     * @param root 		the repository's root <code>URL</code>.
     * @see 			#getRepositoryRoot()
     * @see 			#getRepositoryUUID()
     * @see				org.tmatesoft.svn.util.DebugLog
     */
    protected void setRepositoryCredentials(String uuid, String rootURL) {
        if (uuid != null && rootURL != null) {
            myRepositoryUUID = uuid;
            try {
                myRepositoryRoot = SVNURL.parse(rootURL);
            } catch (SVNException e) {
                //
            }
        }
    }
    
    /* init */
    /**
     * Establishes a socket connection to a repository.
     * 
     * @throws SVNException if a connection establishment attempt fails, - 
     * 						it could be a failure in creating a socket (maybe
     * 						an invalid Subversion repository server's host/port).
     * 						Also if the user authentication failed (see
     * 						{@link SVNAuthenticationException}).
     */
    public abstract void testConnection() throws SVNException; 
    
    /* simple methods */
    
    /**
     * Returns the latest revision number (that is the latest state of the entire
     * repository filesystem tree represented as a unique natural number). As
     * this revision number corresponds to the latest revision it's greater than 
     * all the previous ones.
     *  
     * @return 					the latest revision number
     * @throws 	SVNException	if a failure in connection occured or the user's
     * 							authentication failed (see 
     * 							{@link SVNAuthenticationException}).
     */
    public abstract long getLatestRevision() throws SVNException;
    
    /**
     * Returns the recent repository revision number for the particular moment 
     * in time the user is interested in. 
     * 
     * <p>
     * <b>Note</b> that if you specify a single date
     * without specifying a time of the day (e.g. 2002-11-27) Subversion assumes
     * the timestamp to be 00:00:00 and won't return any revisions for the day you
     * have specified but for the day just before it. 
     * 
     * @param  date			a <code>Date</code> instance for defining the needed
     * 						moment in time
     * @return 				the recent revision for the date
     * @throws SVNException if a failure in connection occured or the date format
     * 						can't be processed. Also if the user authentication failed
     * 						(see {@link SVNAuthenticationException}). 
     */
    public abstract long getDatedRevision(Date date) throws SVNException;
    
    /**
     * Returns unversioned <code>properties</code> associated with the given 
     * <code>revision</code> as a map collection (map keys are property names, map 
     * values are property values).
     * 
     * @param  revision 	the number of the revision which properties will be
     * 						retrieved 
     * @param  properties 	a <code>Map</code> instance to receive the revision 
     * 						properties
     * @return 				a hash map containing unversioned revision properties
     * @throws SVNException if the <code>revision</code> number is invalid (&lt0) or
     * 						if there's no such <code>revision</code> at all.
     * 						Also if a failure in connection occured or the user 
     * 						authentication failed (see 
     * 						{@link SVNAuthenticationException}).
     */
    public abstract Map getRevisionProperties(long revision, Map properties) throws SVNException;
    
    /**
     * Sets the revision property with the specified name 
     * (<code>propertyName</code>) to the value of <code>propertyValue</code>.
     * 
     * <p>
     * <b>NOTE:</b> revision properties are out of versioning. So, the old values
     * will be forgotten.
     * 
     * @param  revision			the number of the revision which properties are to
     * 							be changed
     * @param  propertyName		a revision property name
     * @param  propertyValue 	the value of the revision property  
     * @throws SVNException		if the repository is configured not to allow clients
     * 							to modify revision properties (it's done by default
     * 							when a repository is created) or an appropriate 
     * 							provided hook program (if any) failed;
     * 							if <code>revision</code> is invalid (&lt0) or doesn't 
     * 							exist at all; if a failure in connection occured or the
     * 							user's authentification failed
     * 							(see {@link SVNAuthenticationException}).
     * 
     * @see    SVNRevisionProperty
     */
    public abstract void setRevisionPropertyValue(long revision, String propertyName, String propertyValue) throws SVNException;
    
    /**
     * Gets the value of an unversioned
     * property <code>propertyName</code> attached to the revision identified by
     * <code>revision</code>.
     * 
     * @param 	revision 		the revision number
     * @param 	propertyName 	a property name
     * @return 					a revision property value or <code>null</code> if 
     * 							there's no such value
     * @throws 	SVNException	if the <code>revision</code> number is invalid (&lt0) or
     * 							if there's no such <code>revision</code> at all.
     * 							Also if a failure in connection occured or the user's 
     * 							authentication failed (see 
     * 							{@link SVNAuthenticationException}).
     */
    public abstract String getRevisionPropertyValue(long revision, String propertyName) throws SVNException;
    
    /* simple callback methods */
    /**
	 * Returns the kind of a node defined by <code>path</code> at a <code>revision</code>.  
	 * If the <code>path</code> does not exist under the <code>revision</code>, 
	 * <code>SVNNodeKind.NONE</code> will be returned.
	 * The <code>path</code> is relative to this session's parent <code>URL</code>.
     * 
     * @param  path				the path of a node in a repsitory which is to be inspected 
     * @param  revision			a revision number which the <code>path</code> is under
     * @return 					the node kind for the given <code>path</code> at the given 
     * 							<code>revision</code>
     * @throws SVNException  	if a failure in connection occured or the user's 
     * 							authentication failed (see 
     * 							{@link SVNAuthenticationException}).
     */
    public abstract SVNNodeKind checkPath(String path, long revision) throws SVNException;
    
    /**
	 * Fetches the contents and properties of the file located at the <code>path</code>
	 * under the specified <code>revision</code>.
	 * The <code>path</code> is relative to the <code>URL</code> of the current session.
	 * 
	 * <p>
	 * If the output stream - <code>contents</code> - is not <code>null</code>, then
	 * the contents of the file will be flown into this stream.
	 * 
	 * <p>
	 * If <code>properties</code> is not <code>null</code> it will receive the 
	 * properties of the file.  This means all properties: not just ones controlled by
	 * the user and stored in the repository filesystems, but non-tweakable ones
	 * generated by the SCM system itself (e.g. 'wcprops', 'entryprops',
	 * etc.)  The keys are strings, values are 
	 * {@link SVNFileRevision SVNFileRevision instances}.
	 * 
     * @param path 				the file pathway in the repository
     * @param revision 			the file revision number
     * @param properties 		a map collection to receive the file properties
     * @param contents 			<code>OutputStream</code> to write the file contents to
     * @return 					the size of the file in bytes
     * @throws SVNException		if the <code>revision</code> number is invalid (&lt0) or
     * 							if there's no such <code>revision</code> at all. If 
     * 							there's	no such <code>path</code> in that 
     * 							<code>revision</code>. Also if a failure in connection 
     * 							occured or the user's authentication failed (see 
     * 							{@link SVNAuthenticationException}).
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
	 * handler.handleFileRevision()}, the file contents for the first interesting 
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
     * @param  revisions 	a caller's {@link Collection} reference to get  file 
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
	
    /* edit-mode methods */
	/**
	 * Asks the Repository Access (RA) Layer to 'diff' a working copy against
	 * a <code>url</code>; it's another form of 
	 * {@link #update(long, String, boolean, ISVNReporterBaton, ISVNEditor) update()}.
	 * 
	 * <p>
	 * <b>
	 * Please note: this method cannot be used to diff a single
	 * file, only a working copy directory.  See the {@link #update(String, long, String, boolean, ISVNReporterBaton, ISVNEditor)
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
	 * @param  url 				a <code>URL</code> path to the entry to be diffed 
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
    public abstract void diff(String url, long revision, String target, boolean ignoreAncestry, boolean recursive, ISVNReporterBaton reporter, ISVNEditor editor) throws SVNException;
    public abstract void diff(String url, long targetRevision, long revision, String target, boolean ignoreAncestry, boolean recursive, ISVNReporterBaton reporter, ISVNEditor editor) throws SVNException;
    
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
    public abstract void update(String url, long revision, String target, boolean recursive, ISVNReporterBaton reporter, ISVNEditor editor) throws SVNException;
    
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
     * Ask the Repository Access Layer to inform about an entry located at a 
     * <code>path</code> under a specified <code>revision</code>. If the 
     * <code>revision</code> is invalid (&lt0) it is assigned to the HEAD-revision 
     * (the latest revision of the repository).
     * 
     * @param  path			an entry path (relative to a repository location path)
     * @param  revision		a revision of the entry 
     * @return				an <code>SVNDirEntry</code> containing information about
     * 						the entry or <code>null</code> if there's no an entry with
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
	 * <code>locks</code>, if non-<code>null</code>, is a <code>Map<code> which
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
     * @return			a <code>SVNLock</code> instance (the lock itself) or 
     * 					<code>null</code> if there's no lock.
     * @throws 			SVNException
     * @see				#setLock(String, String, boolean, long)
     * @see				#removeLock(String, String, boolean)
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
     * 						repository root directory).
     * @return 				an array of <code>SVNLock</code> instances 
     * 						(locks themselves).
     * @throws 				SVNException
     * @see					#setLock(String, String, boolean, long)
     * @see					#removeLock(String, String, boolean)
     * @see					#getLock(String)
     * @see					SVNLock
     * @since				SVN 1.2
     */
    public abstract SVNLock[] getLocks(String path) throws SVNException;
    
    /**
	 * Lock a file at the <code>path</code> in the <code>revision</code>.
	 * 
	 * <p>
	 * Note that locking is never anonymous, so any server implementing
	 * this function will have to "pull" a username from the client, if
	 * it hasn't done so already.
	 * 
	 * <p>
	 * The <code>comment</code> is optional: it's either a string
	 * which describes the lock, or it is <code>null</code>.
	 * 
	 * <p>
	 * If any path is already locked by a different user and the
	 * <code>force</code> flag is <code>false</code>, then this call fails
	 * with throwing a <code>SVNException</code>. But if <code>force</code> is
	 * <code>true</code>, then the existing lock(s) will be "stolen" anyway,
	 * even if the user name (who is trying to lock) does not match the current
	 * lock's owner. (That's just the way how user can delete any lock on
	 * the path, and unconditionally create a new lock.)
	 * 
	 * <p>
	 * If the <code>revision</code> is less than the last-changed-revision of
	 * the file to be locked (or if the file <code>path</code> doesn't exist
	 * in HEAD-revision), this call also fails with throwing a 
	 * <code>SVNException</code>.
     * 
     * @param path 					the path of a file in a repository that 
     * 								will be locked (relative to the repository
     * 								root directory). 
     * @param comment				a comment string for the lock. It's optional.
     * @param force					<code>true</code> if the file is to be locked
     * 								in any way (even if it's already locked by
     * 								someone else).
     * @param revision				a revision number in which the file is 
     * 								considered to have the specified <code>path</code>. 
     * @return						a <code>SVNLock</code> instance if the lock was
     * 								created.
     * @throws 						SVNException
     * @see							#removeLock(String, String, boolean)
     * @see							#getLocks(String)
     * @see							#getLock(String)
     * @see							SVNLock
     * @since 						SVN 1.2
     */
    public abstract SVNLock setLock(String path, String comment, boolean force, long revision) throws SVNException;
    
    /**
	 * Removes the repository lock for the file located at the <code>path</code>.
	 * The lock is identified by its token.
	 * 
	 * <p>
	 * Note that unlocking is never anonymous, so any server
	 * implementing this function will have to "pull" a username from
	 * the client, if it hasn't done so already.
	 * 
	 * <p>
	 * If the username doesn't match the lock's owner, this method call fails with
	 * throwing a <code>SVNException</code>.  But if the <code>force</code> flag
	 * is <code>true</code>, the lock will be "broken" by the current user.
	 * 
	 * <p>
	 * Also if the <code>id</code> is incorrect or missing and <code>force</code>
	 * is <code>null</code>, the method fails with throwing a 
	 * <code>SVNException</code>. (However, if <code>force</code> is 
	 * <code>true</code> the lock will be removed anyway.)
	 * 
     * @param path			the path of the file to be unlocked (relative to the 
     * 						repository root directory).
     * @param id			a specific token that identifies the lock to be removed.
     * @param force			<code>true</code> to remove the lock in any case (in 
     * 						spite of missmatching the lock owner's name or an 
     * 						incorrect <code>id</code>).
     * @throws 				SVNException
     * @see 				#setLock(String, String, boolean, long)
     * @see					#getLocks(String)
     * @see					#getLock(String)
     * @see 				SVNLock
     * @since				SVN 1.2
     */
    public abstract void removeLock(String path, String id, boolean force) throws SVNException;
    
    /**
     * Locks the current session <code>SVNRepository</code> object. It prevents
     * from using non-reenterable methods of this object (for example, while having
     * not finished updating the working copy yet, the client can not call the 
     * <code>status</code> method from within a reporter baton). If the client
     * tries to lock the object that has been already locked, this method throws
     * a non catchable <code>Error</code> exception that immediately terminates
     * the application. 
     * 
     * @see 	#unlock()
     */
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
    
    /**
     * Unlocks the current <code>SVNRepository</code> object making it free
     * for using. 
     * 
     * <p>
     * <b>NOTE:</b> while a current Repository Access operation is not completed,
     * this <code>SVNRepository</code> object must remain locked to be guaranteed 
     * that no other Repository Access operation will be started while a current one
     * finishes.
     *   
     * @see 	#lock()
     */
    protected synchronized void unlock() {
        synchronized(this) {
            if (--myLockCount <= 0) {
                myLockCount = 0;
                myLocker = null;
                notifyAll();
            }
        }
    }
    
    /**
     * Checks if the <code>revision</code> number is invalid (that is &lt 0); 
     * 
     * @param revision 		the revision number to be checked for invalidity.
     * @return 				<code>true</code> if <code>revision</code> is invalid,
     * 						<code>false</code> otherwise.
     */
    protected static boolean isInvalidRevision(long revision) {
        return revision < 0;
    }    
    
    /**
     * Says if the <code>revision</code> number is valid (i.e. &gt or == 0); 
     * 
     * @param revision 	the revision number to be checked for validity
     * @return 			<code>true</code> if valid, <code>false</code> otherwise
     * 
     */
    protected static boolean isValidRevision(long revision) {
        return revision >= 0;
    }
    
    /**
     * Checks the passed revision number for validity and if valid returns
     * it in the <code>Long</code> representation.
     *  
     * @param revision 		a revision number
     * @return 				a <code>Long</code> representation of the revision 
     * 						number or <code>null</code> if the passed revision
     * 						number is invalid. 
     * @see 				#isInvalidRevision(long)
     */
    protected static Long getRevisionObject(long revision) {
        return isValidRevision(revision) ? new Long(revision) : null;
    }
    
    /**
     * This assertion method checks if the revision number can be assumed as valid.
     * Note that only numbers &gt or = 0 can be applied for revisioning! 
     * 
     * @param  revision 		the revision number to be checked for validity.  
     * @throws SVNException		
     * @see 					#isValidRevision(long)
     */
    protected static void assertValidRevision(long revision) throws SVNException {
        if (!isValidRevision(revision)) {
            SVNErrorManager.error("svn: Invalid revision number '" + revision + "'");
        }
    }
    
    /**
     * Return a canonical representation of the given URL string (that is a URL
     * in the form of <i>protocol://host:port/path/to/repos</i>).
     * 
     * @param  url 				a URL string to be represented in the canonical 
     * 							form.
     * @return URL 				a URL string in the canonical representation.
     * 							 
     * @throws SVNException
     */
    protected static String getCanonicalURL(String url) throws SVNException {
        if (url == null) {
            return null;
        }
        return SVNURL.parse(url).toString();
    }

    // all paths are uri-decoded.
    //
    // get repository path (path starting with /, relative to repository root).
    // get full path (path starting with /, relative to host).
    // get relative path (repository path, now relative to repository location, not starting with '/').
    
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
    
    public String getFullPath(String relativeOrRepositoryPath) {
        if (relativeOrRepositoryPath == null) {
            return getFullPath("/");
        }
        if (relativeOrRepositoryPath.length() > 0 && relativeOrRepositoryPath.charAt(0) == '/') {
            return SVNPathUtil.append(getRepositoryRoot().getPath(), relativeOrRepositoryPath);
        }
        relativeOrRepositoryPath = getRepositoryPath(relativeOrRepositoryPath);
        return SVNPathUtil.append(getRepositoryRoot().getPath(), relativeOrRepositoryPath);
    }
}
