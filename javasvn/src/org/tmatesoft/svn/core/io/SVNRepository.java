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

import org.tmatesoft.svn.core.diff.SVNDiffWindow;
import org.tmatesoft.svn.core.internal.io.SVNAnnotate;
import org.tmatesoft.svn.util.DebugLog;

/**
 * <p>
 * The abstract class <code>SVNRepository</code> declares all the basic
 * interface methods as well as implements commonly used ones to work with
 * a Subversion repository.  
 * </p>
 * 
 * @version 1.0
 * @author TMate Software Ltd.
 * 
 */

public abstract class SVNRepository {
        
    private OutputStream myLoggingOutput;
    private OutputStream myLoggingInput;
    private String myRepositoryUUID;
    private String myRepositoryRoot;
    private SVNRepositoryLocation myLocation;
    private int myLockCount;
    private Thread myLocker;
    private ISVNCredentialsProvider myUserCredentialsProvider;
	/**
	 * Constructs a <code>SVNRepository</code> instance (representing a session to work with
	 * a repository) given the Subversion repository location as a 
	 * {@link SVNRepositoryLocation} object.   
	 * @param location a {@link SVNRepositoryLocation} object that incapsulates
	 * the repository location (i.e. URL pointing to the repository root directory)
	 * @see SVNRepositoryLocation
	 */
    protected SVNRepository(SVNRepositoryLocation location) {
        myLocation = location;
    }
	/**
	 * <p>
	 * Returns the Subversion repository Location as a <code>SVNRepositoryLocation</code>
	 * object
	 * </p> 
	 * @return repository location
	 * @see SVNRepositoryLocation
	 */
    
    public SVNRepositoryLocation getLocation() {
        return myLocation;
    }
    /**
     * <p>
     * Sets output streams for logging out and in.??????   
     * </p>
     * @param out stream for outputting log.
     * @param in stream for writing log messages into.
     */
    public void setLoggingStreams(OutputStream out, OutputStream in) {
        myLoggingOutput = out;
        myLoggingInput = in;        
    }
    /**
     * <p>
     * The UUID is the repository's Universal Unique IDentifier. A Subversion
     * client uses this identifier to differentiate between one repository and
     * another.
     * NOTE: the UUID has the same lifetime as the current session.
     * </p>
     * @return the UUID of the repository 
     */
    public String getRepositoryUUID() {
        return myRepositoryUUID;
    }
    /**
     * <p>
     * Retrieves the repository's root URL.  The value will not include
     * a trailing '/'.  The returned URL is guaranteed to be a prefix of the
     * URL used to create the current session (i.e. the URL passed to create 
     * {@link SVNRepositoryLocation} which for its turn was used to create the 
     * current session object <code>SVNRepository</code> for working with the
     * repository).
     *
     * NOTE: the URL has the same lifetime as the current session.
     * </p>
     * @return the repository root URL
     */
    public String getRepositoryRoot() {
        return myRepositoryRoot;
    }
    /**
     * 
     * @param provider
     */
    public void setCredentialsProvider(ISVNCredentialsProvider provider) {
        myUserCredentialsProvider = provider;
    }
    
    public ISVNCredentialsProvider getCredentialsProvider() {
        return myUserCredentialsProvider;
    }
    /**
     * <p>
     * Set the following parameters to identify the current repository.
     * Every call to this routine is logged by {@link org.tmatesoft.svn.util.DebugLog
     * DebugLog}. 
     * </p>
     * @param uuid the repository's Universal Unique IDentifier used to
     *  differentiate between one repository and another. NOTE: the UUID
     *  has the same lifetime as the current session.
     * @param root the repository's root URL
     * @see #getRepositoryRoot()
     * @see #getRepositoryUUID()
     * 
     */
    protected void setRepositoryCredentials(String uuid, String root) {
        if (uuid != null && root != null) {
            myRepositoryUUID = uuid;
            myRepositoryRoot = root;
            DebugLog.log("REPOSITORY: " + uuid + ":" + root);
        }
    }
    
    /* init */
    
    public abstract void testConnection() throws SVNException; 
    
    /* simple methods */
    
    /**
     * <p>
     * Returns the latest revision number (that is the latest state of the entire
     * repository filesystem tree represented as a unique natural number). As
     * this number corresponds to the latest revision it's greater than the previous
     * revision number. 
     * </p>
     * @throws {@link SVNException}
     * @return the latest revision number
     */
    public abstract long getLatestRevision() throws SVNException;
    
    /**
     * <p>
     * Returns the recent repository revision number for the particular moment in time
     * you are interested in. Note that if you specify a single date without
     * specifying a time of the day (e.g. 2002-11-27) Subversion assumes the 
     * timestamp to be 00:00:00 and won't return any revisions for the day you
     * have specified but for the day just before it. 
     * </p>
     * 
     * @param date a <code>Date</code> instance for defining the needed moment in time
     * @throws {@link SVNException}
     * @return the recent revision for the date
     */
    
    public abstract long getDatedRevision(Date date) throws SVNException;
    /**
     * <p>
     * Returns the <code>properties</code> associated with the given <code>revision</code>
     * as a hash map (map keys are property names, map values are property values).
     * </p>
     * 
     * @param revision the number of the revision which properties will be retrieved 
     * @param properties <code>Map</code> instance to receive the revision properties
     * @throws SVNException
     * @return hash map containing unversioned revision properties
     */
    
    public abstract Map getRevisionProperties(long revision, Map properties) throws SVNException;
    
    /**
     * <p>
     * Sets <code>propertyValue</code> to the value of an unversioned
     * property <code>propertyName</code> attached to a revision identified by <code>revision</code>
     * </p>
     * <p>
     * The method is similar to the Subversion's native 
     * <code>*svn_ra_rev_prop</code> function declared in include/svn_ra.h.     
     * </p>
     * @param revision the revision number
     * @param propertyName a property name
     * @param propertyValue a property value
     * @throws SVNException
     */
    public abstract void setRevisionPropertyValue(long revision, String propertyName, String propertyValue) throws SVNException;
    
    /**
     * <p>
     * Gets the value of an unversioned
     * property <code>propertyName</code> attached to a revision identified by <code>revision</code>
     * </p>
     * @param revision the revision number
     * @param propertyName a property name
     * @return a revision property value
     * @throws SVNException
     */
    public abstract String getRevisionPropertyValue(long revision, String propertyName) throws SVNException;
    
    /* simple callback methods */
    /**
     * <p>
	 * Returns the kind of the node defined by <code>path</code> at <code>revision</code>.  
	 * If <code>path</code> does not exist under <code>revision</code>, 
	 * <code>SVNNodeKind.NONE</code> will be returned.
	 * <code>path</code> is relative to the session's parent URL.
     * </p>
     * @return the node kind for the given path at the given revision
     */
    public abstract SVNNodeKind checkPath(String path, long revision) throws SVNException;
    /**
     * <p>
	 * Fetch the contents and properties of the file located at the <code>path</code>
	 * at the <code>revision</code>.
	 * Interpret <code>path</code> relative to the URL for the current session.
	 * </p>
	 * <p>
	 * If the output stream - <code>contents</code> - is not <code>null</code>, then
	 * the contents of the file will be flown into this stream.
	 * </p>
	 * <p>
	 * If <code>properties</code> is not <code>null</code> it will receive the 
	 * properties of the file.  This means all properties: not just ones controlled by
	 * the user and stored in the repository filesystems, but non-tweakable ones
	 * generated by the SCM system itself (e.g. 'wcprops', 'entryprops',
	 * etc.)  The keys are strings, values are 
	 * {@link SVNFileRevision SVNFileRevisions}.
	 * </p>
     * @param path the file pathway in the repository.
     * @param revision the file revision number.
     * @param properties a hash map to receive the file properties.
     * @param contents <code>OutputStream</code> to write the file contents to.
     * @return the file size
     * @throws SVNException
     */
    public abstract long getFile(String path, long revision, Map properties, OutputStream contents) throws SVNException; 
    
    /**
     * <p>
     * The method is useful to get and handle all the directory entries at the definite
     * revision. 
     * </p> 
     * @param path a relative path to the repository root 
     * @param revision the interested revision 
     * @param properties a hash map to contain all the directory
     * properties (not just ones controlled by the user and stored in the repository 
     * fylesystem, but non-tweakable ones generated by the SCM system itself). Keys are
     * property names and associated values are property values. <code>properties</code>
     * Can be <code>null</code> when not interested in directory properties.
     * @param handler an {@link ISVNDirEntryHandler} object
     * @return the number of directory enries
     * @throws SVNException
     * @see #getDir(String, long, Map, Collection)
     * @see SVNDirEntry
     */
    public abstract long getDir(String path, long revision, Map properties, ISVNDirEntryHandler handler) throws SVNException; 
    
    /**
     * <p>
     * This method retrieves all the matched revisions for the specified (by the 
     * <code>path</code> argument) file and passes them to {@link ISVNFileRevisionHandler}
     * for further processing. The method is similar to the Subversion's native 
     * <code>*svn_ra_get_file_revs</code> function declared in include/svn_ra.h.
	 * </p>
	 * <p>
	 * If there is an interesting revision of the file that is less than or
	 * equal to startRevision, the iteration will begin at that revision.
	 * Else, the iteration will begin at the first revision of the file in
	 * the repository, which has to be less than or equal to endRevision.  Note
	 * that if the function succeeds, ISVNFileRevisionHandler  will have been invoked at
	 * least once.
	 * </p>
	 * <p>
	 * In a series of calls to {@link ISVNFileRevisionHandler#hanldeFileRevision(SVNFileRevision)},
	 * the file contents for the first interesting revision will be provided as a text delta against the
	 * empty file.  In the following calls, the delta will be against the
	 * fulltext contents for the previous call.
	 * </p>
	 * <p>
	 * NOTE: This functionality is not available in pre-1.1 servers.
	 * </p>
	 * @param path a file path in the repository
	 * @param startRevision the revision to start from 
	 * @param endRevision   the revision to end at
	 * @param handler {@link ISVNFileRevisionHandler} object that handles file revisions passed  
	 * @see #getFileRevisions(String, Collection, long, long)
	 * @see SVNFileRevision
	 * @return the actual number of file revisions existing in [startRevision,endRevision]
	 * range 
	 * @throws SVNException
	 */    
    public abstract int getFileRevisions(String path, long startRevision, long endRevision, ISVNFileRevisionHandler handler) throws SVNException;

    /**
	 * <p>
	 * Invoke <code>ISVNLogEntryHandler</code> on each log entry from
	 * <code>startRevision</code> to <code>endRevision</code>.
	 * <code>startRevision</code> may be greater or less than
	 * <code>endRevision</code>; this just controls whether the log messages are
	 * processed in descending or ascending revision number order.
	 * </p>
	 * <p>
	 * If <code>startRevision</code> or <code>endRevision</code> is invalid, it
	 * defaults to the youngest.
	 * </p>
	 * <p>
	 * If <code>targetPaths</code> is non-null and has one or more elements, then
	 * only those revisions are processed in which at least one of <code>targetPaths</code> was
	 * changed (i.e., if a file text or properties changed; if a dir properties
	 * changed or an entry was added or deleted). Each path is relative 
	 * to the session's common parent.
	 * </p>
	 * <p>
	 * If <code>changedPath</code> is set, then each call to
	 * {@link ISVNLogEntryHandler#handleLogEntry(SVNLogEntry) 
	 * ISVNLogEntryHandler.handleLogEntry(SVNLogEntry)} passes a 
	 * <code>SVNLogEntry</code> with the set hash map of changed paths;
	 * the hash's keys are all the paths committed in that revision.
	 * Otherwise, <code>SVNLogEntry</code> will not contain that hash map
	 * for the changed paths.
	 * </p>
	 * <p>
	 * If <code>strictNode</code> is set, copy history will not be traversed
	 * (if any exists) when harvesting the revision logs for each path.
	 * </p>
	 * <p>
	 * If <code>startRevision</code> or <code>endRevision</code> is a non-existent
	 * revision, <code>SVNException</code> will be thrown, without ever invoking
	 * <code>ISVNLogEntryHandler</code>.
	 * </p>
	 * <p>
	 * The caller may not invoke any RA operations using the current 
	 * <code>SVNRepository</code> from within <code>ISVNLogEntryHandler</code>.
	 * </p>
     * @param targetPaths paths that mean only those revisions at which they were 
     * changed.
     * @param startRevision the start revision to get the log entry of.
     * @param endRevision the end revision to get the log entry of.
     * @param changedPath true if log entries are to have the changed paths hash map
     * set;
     * false - otherwise.
     * @param strictNode true if a copy history (if any) is not to be traversed.
     * @param handler <code>ISVNLogEntryHandler</code> to handle log entries.
     * @return the number of log entries handled.
     * @throws SVNException
     * @see ISVNLogEntryHandler
     * @see SVNLogEntry
     */
    public abstract int log(String[] targetPaths, long startRevision, long endRevision, boolean changedPath, boolean strictNode,
            ISVNLogEntryHandler handler) throws SVNException;
    /**
     * <p>
	 * Used to get locations of the file identified by <code>path</code> (which 
	 * it has in <code>pegRevision</code>) at the interested repository <code>revisions</code>.
	 * <code>path</code> is relative to the URL for which the current session
	 * (<code>SVNRepository</code>) was created.
	 * </p>
	 * <p>
	 * NOTE: This functionality is not available in pre-1.1 servers.
     * </p>
     * @param path the file pathway in the repository
     * @param pegRevision that is the revision in which the file has the given
     *  <code>path</code> 
     * @param revisions an array of interested revision numbers. If the file doesn't
     *  exist in a location revision, that revision will be ignored. 
     * @param handler a location entry handler that will handle all found file locations
     * @return the number of the file locations existing in <code>revisions</code>
     * @throws SVNException
     * @see ISVNLocationEntryHandler
     * @see SVNLocationEntry
     */
    public abstract int getLocations(String path, long pegRevision, long[] revisions, ISVNLocationEntryHandler handler) throws SVNException;

    /**
     * <p>
     * This overloaded method uses {@link #getFileRevisions(String, long, long, ISVNFileRevisionHandler)}.
     * Its specialization is just to retrieve all the revisions of the specified file 
     * starting from sRevision (including it) and ending at eRevision (also including).
     * </p>  
     * @param path a file path in the repository
     * @param revisions a caller's {@link Collection} reference to get  file revisions 
     * (keeps references to {@link SVNFileRevision} instances). This parameter can be
     *  set to <code>null</code>.  
     * @param sRevision the revision to start from
     * @param eRevision the revision to end at
     * @return a reference to a <code>Collection</code> instance that keeps {@link SVNFileRevision} instances 
     * @throws {@link SVNException}
     * @see #getFileRevisions(String, long, long, ISVNFileRevisionHandler)
     */
    public Collection getFileRevisions(String path, Collection revisions, long sRevision, long eRevision) throws SVNException {
        final Collection result = revisions != null ? revisions : new LinkedList();
        ISVNFileRevisionHandler handler = new ISVNFileRevisionHandler() {
            public void hanldeFileRevision(SVNFileRevision fileRevision) {
                result.add(fileRevision);
            }
            public OutputStream handleDiffWindow(String token, SVNDiffWindow delta) {
            	return null;
            }
            public void hanldeDiffWindowClosed(String token) {
            }
        };
        getFileRevisions(path, sRevision, eRevision, handler);
        return result;
    }
    /**
     * <p>
     * This overloaded method gets and handles all the directory entries ({@link SVNDirEntry}) at the definite
     * revision and returns them as a <code>Collection</code> instance.
     * It uses {@link #getDir(String, long, Map, ISVNDirEntryHandler)} with a pre-implemented
     * {@link ISVNDirEntryHandler}. 
     * </p>
     * @param path a relative path to the repository root 
     * @param revision the interested revision 
     * @param properties a hash map to contain all the directory
     * properties (not just ones controlled by the user and stored in the repository 
     * fylesystem, but non-tweakable ones generated by the SCM system itself). Keys are
     * property names and associated values are property values. <code>properties</code>
     * Can be <code>null</code> when not interested in directory properties.
     * @param dirEntries a collection to get the directory entries.
     * Can be <code>null</code>
     * @return a collection containing the directory entries
     * @throws SVNException
     * @see #getDir(String, long, Map, ISVNDirEntryHandler)
     */
    public Collection getDir(String path, long revision, Map properties, Collection dirEntries) throws SVNException {
        final Collection result = dirEntries != null ? dirEntries : new LinkedList();
        ISVNDirEntryHandler handler = null;        
        handler = new ISVNDirEntryHandler() {
            public void handleDirEntry(SVNDirEntry dirEntry) {
                result.add(dirEntry);
            }
        };
        getDir(path, revision, properties, handler);
        return result;
    }
    /**
     * <p>
     * The same as {@link SVNRepository#log(String[], long, long, boolean, boolean, ISVNLogEntryHandler)},
     * but as a result it returns a <code>Collection</code> of retrieved log entries.
     * </p>
     * @param targetPaths paths that mean only those revisions at which they were 
     * changed.
     * @param entries <code>Collection</code> instance to receive log entries
     * @param startRevision the start revision to get the log entry of.
     * @param endRevision the end revision to get the log entry of.
     * @param changedPath true if log entries are to have the changed paths hash map
     * set; false - otherwise.
     * @param strictNode true if a copy history (if any) is not to be traversed.
     * @return <code>Collection</code> instance containing log entries.
     * @throws SVNException
     * @see ISVNLogEntryHandler
     * @see SVNLogEntry
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
     * <p>
     * The same as {@link #getLocations(String, long, long[], ISVNLocationEntryHandler)},
     * but it returns a <code>Collection</code> containing {@link SVNLocationEntry 
     * location entries}.
     * </p>
     * 
     * @param path the file pathway in the repository
     * @param entries a <code>Collection</code> reference to receive entries. Can be
     * <code>null</code>
     * @param pegRevision that is the revision in which the file has the given
     *  <code>path</code>
     * @param revisions an array of interested revision numbers. If the file doesn't
     *  exist in a location revision, that revision will be ignored.
     * @return <code>Collection</code> instance containing retrieved entries.
     * @throws SVNException
     * @see SVNLocationEntry
     * @see ISVNLocationEntryHandler
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
	 * <p>
	 * Make annotations for the entry at <code>path</code>.........
	 * </p>
	 * @param path
	 * @param startRevision
	 * @param endRevision
	 * @param handler
	 * @throws SVNException
	 */
	public void annotate(String path, long startRevision, long endRevision, ISVNAnnotateHandler handler) throws SVNException {
		if (handler == null) {
			return;
		}
		if (endRevision < 0 || endRevision < 0) {
			long lastRevision = getLatestRevision();
			startRevision = startRevision < 0 ? lastRevision : startRevision;
			endRevision = endRevision < 0 ? lastRevision : endRevision;
		} 
		SVNAnnotate annotate = new SVNAnnotate();
		annotate.setAnnotateHandler(handler);
		try {
			getFileRevisions(path, startRevision, endRevision, annotate);
		} finally {
			annotate.dispose();
		}
	}
    
    /* edit-mode methods */
	
	/**
	 * <p>
	 * Ask the RA layer to 'diff' a working copy against <code>url</code>;
	 * it's another form of 
	 * {@link #update(long, String, boolean, ISVNReporterBaton, ISVNEditor) update()}.
	 * </p>
	 * <p>
	 * <b>
	 * Please note: this method cannot be used to diff a single
	 * file, only a working copy directory.  See the {@link #update(String, long, String, boolean, ISVNReporterBaton, ISVNEditor)
	 * update()} for more details.
	 * </b>
	 * </p>
	 * <p>
	 * The client initially provides a diff editor (<code>ISVNEditor</code>) to the RA
	 * layer; this editor contains knowledge of where the common diff
	 * root is in the working copy (when {@link ISVNEditor#openRoot(long)
	 * ISVNEditor.openRoot(long)} is called). 
	 * </p>
	 * <p>
	 * The {@link ISVNReporterBaton reporter-baton} is used to 
	 * describe client's working-copy revision numbers by calling the methods of
	 * {@link ISVNReporter}; the RA layer assumes that all
	 * paths are relative to the URL used to open the current repository session.	 * </p>
	 * </p>
	 * <p>
	 * When finished, the client calls {@link ISVNReporter#finishReport() 
	 * ISVNReporter.finishReport()}. The RA layer then does a complete drive of the
	 * diff editor, ending with {@link ISVNEditor#closeEdit() ISVNEditor.closeEdit()},
	 * to transmit the diff.
	 * </p>
	 * <p>
	 * <code>target</code> is an optional single path component will restrict
	 * the scope of the diff to an entry in the directory represented by
	 * the current session's URL, or empty if the entire directory is meant to be
	 * one of the diff paths.
	 * </p>
	 * <p>
	 * The working copy will be diffed against <code>url</code> as it exists
	 * in <code>revision</code>, or as it is in head if <code>revision</code> is
	 * invalid.
	 * </p>
	 * <p>
	 * Use <code>ignoreAncestry</code> to control whether or not items being
	 * diffed will be checked for relatedness first.  Unrelated items
	 * are typically transmitted to the editor as a deletion of one thing
	 * and the addition of another, but if this flag is <code>TRUE</code>,
	 * unrelated items will be diffed as if they were related.
	 * </p>
	 * <p>
	 * If <code>recursive</code> is true and the target is a directory, diff
	 * recursively; otherwise, diff just target and its immediate entries,
	 * but not its child directories (if any).
	 * </p>
	 * <p>
	 * The caller may not perform any RA operations using the current
	 * <code>SVNRepository</code> object before finishing the report, and may not
	 * perform any RA operations using <code>SVNRepository</code> from within
	 * the editing operations of the diff editor.
	 * </p>
	 * @param url the URL path to the entry to be diffed against.
	 * @param revision revision number of the entry located at <code>url</code>
	 * @param target relative to the current session URL path of the entry to
	 * be diffed against <code>url</code>.
	 * @param ignoreAncestry set true to control relatedness.
	 * @param recursive true to diff recursively, false - otherwise.
     * @param reporter client's reporter-baton
     * @param editor client's update editor
     * @throws SVNException
     * @see ISVNReporterBaton
     * @see ISVNReporter
     * @see ISVNEditor
	 */
	public abstract void diff(String url, long revision, String target, boolean ignoreAncestry, boolean recursive, ISVNReporterBaton reporter, ISVNEditor editor) throws SVNException;
    
    /**
     * <p>
     * Ask the Repository Access Layer to update a working copy.
	 * The client initially provides an update editor ({@link ISVNEditor});
	 * this editor contains knowledge of where the change will
	 * begin in the working copy (when {@link ISVNEditor#openRoot(long)
	 * ISVNEditor.openRoot(long)} is called).
	 * </p>
	 * 
	 * <p>
	 * The {@link ISVNReporterBaton reporter-baton} is used to 
	 * describe client's working-copy revision numbers by calling the methods of
	 * {@link ISVNReporter}; the RA layer assumes that all
	 * paths are relative to the URL used to open the current repository session.
	 * </p>
	 * 
	 * <p>
	 * When finished, the client calls {@link ISVNReporter#finishReport() ISVNReporter.finishReport()} 
	 * from within the <code>reporter</code>.  The RA layer then does a complete drive
	 * of the <code>editor</code>, ending with {@link ISVNEditor#closeEdit() ISVNEditor.closeEdit()},
	 * to update the working copy. 
	 * </p>
	 * 
	 * <p>
	 * <code>target</code> is an optional single path component to restrict
	 * the scope of the update to just that entry in the directory represented by
	 * the current session' URL.
	 * If the <code>target</code> is <code>null</code>,
	 * the entire directory is updated.
	 * </p>
	 * 
	 * <p>
	 * If <code>recursive</code> is true and the <code>target</code> is a directory,
	 * update recursively; otherwise, update just the <code>target</code> and its
	 * immediate entries, but not its child directories (if any).
	 * </p>
	 * 
	 * <p>
	 * The working copy will be updated to <code>revision</code>, or the
	 * "latest" revision if this arg is invalid.
	 * </p>
	 * <p>
	 * The caller may not perform any RA operations using the current
	 * <code>SVNRepository</code>
	 * before finishing the report, and may not perform any RA operations
	 * using <code>SVNRepository</code> from within the editing operations
	 * of update <code>ISVNEditor</code>.
	 * </p>
     * @param revision the desired update revision
     * @param target the update scope pathway
     * @param recursive to update the working copy recursively or not 
     * @param reporter client's reporter-baton
     * @param editor client's update editor
     * @throws SVNException
     * @see ISVNReporterBaton
     * @see ISVNReporter
     * @see ISVNEditor
     */
    public abstract void update(long revision, String target, boolean recursive, ISVNReporterBaton reporter, ISVNEditor editor) throws SVNException;
    /**
	 * <p>
	 * Ask the Repository Access (RA) Layer to describe the status of a working copy with
	 * respect to  <code>revision</code> of the repository (or HEAD - the latest
	 * revision, if <code>revision</code> is invalid).
	 * </p>
	 *
	 * <p>
	 * The client initially provides an editor {@link ISVNEditor}  to the RA
	 * layer; this editor contains knowledge of where the change will
	 * begin in the working copy (when {@link ISVNEditor#openRoot(long)
	 * ISVNEditor.openRoot(long)} is called).
	 * </p>
	 * 
	 * <p>
	 * The {@link ISVNReporterBaton reporter} is used to 
	 * describe client's working-copy revision numbers by calling the methods of
	 * {@link ISVNReporter}; the RA layer assumes that all
	 * paths are relative to the URL used to open the current repository session.
	 * </p>
	 * 
	 * <p>
	 * When finished, the client calls {@link ISVNReporter#finishReport()} 
	 * from within the <code>reporter</code>. The RA
	 * layer then does a complete drive of the status <code>editor</code>, ending with
	 * {@link ISVNEditor#closeEdit()}, to report, essentially, what would be modified in
	 * the working copy were the client to call {@link #update(long, String, boolean, ISVNReporterBaton, ISVNEditor)
	 * update()}.
	 * </p>
	 * <p>
	 * <code>target</code> is an optional single path component to restrict
	 * the scope of the status report to an entry in the directory represented by
	 * the current session's URL, or empty if the entire
	 * directory is meant to be examined.
	 * </p>
	 * <p>
	 * If <code>recursive</code> is true and the target is a directory, get status
	 * recursively; otherwise, get status for just the target and its
	 * immediate entries, but not its child directories (if any).
	 * </p>
	 * <p>
	 * The caller may not perform any RA operations using the current
	 * <code>SVNRepository</code>
	 * before finishing the report, and may not perform any RA operations
	 * using <code>SVNRepository</code> from within the editing operations
	 * of status <code>ISVNEditor</code>.
     * </p>
     * @param revision the revision number for which the working copy status will
     * be described
     * @param target that is the pathway (relative to the current session URL) to
     * the entry to be examined. It can be <code>null</code> to check out the 
     * entire session root directory.
     * @param recursive to get status for the working copy recursively or not
     * @param reporter client's reporter-baton
     * @param editor client's status editor
     * @throws SVNException
     * @see ISVNReporterBaton
     * @see ISVNEditor
     */
    public abstract void status(long revision, String target, boolean recursive, ISVNReporterBaton reporter, ISVNEditor editor) throws SVNException;

    /**
     * <p>
     * This is another form of update. It updates the client's working copy to
     * mirror a new URL (switching a working copy to a new branch). As for the rest,
     * see the description for
     * {@link #update(long, String, boolean, ISVNReporterBaton, ISVNEditor)}
     * </p>
     * @param url new location in the repository to switch to
     * @param revision the desired update revision
     * @param target the update scope pathway
     * @param recursive to update the working copy recursively or not
     * @param reporter client's reporter-baton
     * @param editor client's editor
     * @throws SVNException
     * @see ISVNReporterBaton
     * @see ISVNReporter
     * @see ISVNEditor
     */
    public abstract void update(String url, long revision, String target, boolean recursive, ISVNReporterBaton reporter, ISVNEditor editor) throws SVNException;
    /**
     * <p>
     * Ask the Repository Access Layer to checkout a working copy. This method is a
     * kind of the update operation.   
     * </p>
     * <p>
     * To checkout a working copy the current session should have been set to a
     * directory, not a file - i.e. that URL that was used to create 
     * {@link SVNRepositoryLocation} that in its turn was used to create the current
     * <code>SVNRepository</code> object, that URL should point to a directory; otherwise
     * a SVNException will be thrown. 
     * </p>
     * @param revision
     * @param target that is the pathway (relative to the current session URL) to
     * the entry to be checked out. It can be <code>null</code> to check out the 
     * entire session root directory.
     * @param recursive true if the working copy is to be checked out recursively or
     * false - otherwise.
     * @param editor client's checkout editor
     * @throws SVNException
     * @see SVNRepository#update(long, String, boolean, ISVNReporterBaton, ISVNEditor)
     * @see ISVNEditor
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
     * <p>
	 * Gets an {@link ISVNEditor editor} for committing changes
	 * to the repository of the current session, using <code>logMessage</code> as
	 * the log message.  The revisions being committed against are passed to the
	 * editor methods, starting with the <code>revision</code> argument to 
	 * {@link ISVNEditor#openRoot(long) ISVNEditor.openRoot(revision)}.
	 * </p>
	 * <p>
	 * After the commit has succeeded {@link ISVNEditor#closeEdit() 
	 * ISVNEditor.closeEdit()} returns an instance of <code>SVNCommitInfo</code>
	 * that contains a new revision number, the commit date, commit author.
	 * </p>
	 * <p>
	 * The caller may not perform any RA operations using the current
	 * <code>SVNRepository</code> before finishing the edit.     
	 * </p>
	 * @param logMessage log message 
	 * @param mediator
	 * @return commit editor
	 * @throws SVNException
	 * @see ISVNEditor
	 * @see ISVNWorkspaceMediator
     */
    public ISVNEditor getCommitEditor(String logMessage, final ISVNWorkspaceMediator mediator) throws SVNException {
        return getCommitEditor(logMessage, null, false, mediator);
    }
    
    public abstract SVNDirEntry info(String path, long revision) throws SVNException;
        
    public abstract ISVNEditor getCommitEditor(String logMessage, Map locks, boolean keepLocks, final ISVNWorkspaceMediator mediator) throws SVNException;
    
    public abstract SVNLock getLock(String path) throws SVNException;

    public abstract SVNLock[] getLocks(String path) throws SVNException;
    
    public abstract SVNLock setLock(String path, String comment, boolean force, long revision) throws SVNException;

    public abstract void removeLock(String path, String id, boolean force) throws SVNException;
    
    /**
     * <p>
     * Locks the current session <code>SVNRepository</code> object. It prevents
     * from using non-reenterable methods of this object (e.g. while having not
     * finished updating the working copy yet, the client can not call the 
     * <code>status</code> method from within a reporter baton). If the client
     * tries to lock the object that has been already locked, this method throws
     * a non catchable <code>Error</code> exception that immediately terminates
     * the application. 
     * </p>
     * @see #unlock()
     */
    protected synchronized void lock() {
    	try {
    	    while ((myLockCount > 0) || (myLocker != null)) {
	    		if (Thread.currentThread() == myLocker) {
	    			throw new Error("SVNRerpository methods are not reenterable");
	            }
	    		wait();
    	    }
    	    myLocker = Thread.currentThread();
            myLockCount = 1;
    	} catch (InterruptedException e) {
    	    throw new Error("Interrupted attempt to aquire write lock");
    	}
    }
    /**
     * <p>
     * Unlocks the current session <code>SVNRepository</code> object making it free
     * for using.
     * </p>
     * @see #lock()
     */
    protected synchronized void unlock() {
        if (--myLockCount <= 0) {
            myLockCount = 0;
            myLocker = null;
            notifyAll();
        }
    }
    /**
     * <p>
     * Gets <code>OutputSream</code> used for getting out log messages and writing
     * into it.????? 
     * </p>
     * @return <code>OutputStream</code> instance
     */
    protected OutputStream getOutputLoggingStream() {
        return myLoggingOutput;
    }
    /**
     * <p>
     * Gets <code>OutputSream</code> used for writing log messages.?????? 
     * </p>
     * @return <code>OutputStream</code> instance
     */
    protected OutputStream getInputLoggingStream() {
        return myLoggingInput;
    }
    /**
     * <p>
     * Says if the <code>revision</code> number is invalid (i.e. < 0); 
     * </p>
     * @param revision the revision number to be checked for invalidity
     * @return true if invalid, false otherwise
     */
    protected static boolean isInvalidRevision(long revision) {
        return revision < 0;
    }    
    /**
     * <p>
     * Says if the <code>revision</code> number is valid (i.e. > or == 0); 
     * </p>
     * @param revision the revision number to be checked for validity
     * @return true if valid, false otherwise
     * 
     */
    protected static boolean isValidRevision(long revision) {
        return revision >= 0;
    }
    /**
     * <p>
     * Checks the passed revision number for validity and if valid returns
     * it in <code>Long</code> representation (simply a wrapper object for the
     * <code>long</code> number). 
     * </p>
     * @param revision a revision number
     * @return <code>Long</code> representation of the revision number or null if
     * the passed revision number is invalid 
     * @see #isInvalidRevision(long)
     */
    protected static Long getRevisionObject(long revision) {
        return isValidRevision(revision) ? new Long(revision) : null;
    }
    
    /**
     * <p>
     * This assertion method checks if the revision number can be assumed as valid.
     * Note that only numbers > or = 0 can be applied for revisioning! 
     * </p>
     * @param revision the revision number to be checked for validity  
     * @throws SVNException
     * @see #isValidRevision(long)
     */
    protected static void assertValidRevision(long revision) throws SVNException {
        if (!isValidRevision(revision)) {
            throw new SVNException("only valid revisions (>=0) are accepted in this method");
        }
    }
    /**
     * <p>
     * Return a canonical representation of the given URL string.
     * </p>
     * @param url URL string
     * @return URL string transformed to the canonical representation 
     * @throws SVNException
     * @see SVNRepositoryLocation#toCanonicalForm()
     */
    protected static String getCanonicalURL(String url) throws SVNException {
        if (url == null) {
            return null;
        }
        SVNRepositoryLocation location = SVNRepositoryLocation.parseURL(url);
        if (location != null) {
            return location.toString();
        }
        return null;
    }
}
