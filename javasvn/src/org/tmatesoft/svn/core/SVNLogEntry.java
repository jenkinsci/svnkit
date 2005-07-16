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

package org.tmatesoft.svn.core;

import java.util.Date;
import java.util.Map;

import org.tmatesoft.svn.core.io.SVNRepository;

/**
 * The class <code>SVNLogEntry</code> incapsulates log information provided for every
 * commit operation. This information includes:
 * <ul>
 * <li>revision number the repository was committed to;
 * <li>person who made the commit;
 * <li>date (generally moment in time) when the commit was performed;
 * <li>client's log message that accompanied the commit;
 * <li>map collection that contains all the paths of the entries which were
 * changed at the commit. Map keys are the paths themselves and values are
 * <code>SVNLogEntryPath</code> instances. 
 * </ul>
 * 
 * <p>
 * Instances of <code>SVNLogEntry</code> are passed to
 * <code>ISVNLogEntryHandler</code> during the progress of the 
 * {@link SVNRepository#log(String[], long, long, boolean, boolean, ISVNLogEntryHandler)
 * log} operation. {@link ISVNLogEntryHandler#handleLogEntry(SVNLogEntry)
 * ISVNLogEntryHandlerhandleLogEntry(SVNLogEntry)} then performs handling
 * the passed log entry.
 * 
 * @version 1.0
 * @author 	TMate Software Ltd.
 * @see 	SVNLogEntryPath
 * @see 	SVNRepository
 */
public class SVNLogEntry {
    
    private long myRevision;
    private String myAuthor;
    private Date myDate;
    private String myMessage;
    private Map myChangedPaths;
    
    /**
     * <p>
     * Constructs an <code>SVNLogEntry</code> object. 
     * 
     * @param changedPaths 	a map collection which keys should be
     * 						all the paths of the entries that were changed in the
     * 						<code>revision</code>. And values are 
     * 						<code>SVNLogEntryPath</code> instances.
     * @param revision 		a revision revision number
     * @param author 		the person who committed the repository to 
     * 						<code>revision</code>
     * @param date 			the moment in time when changes were committed to the 
     * 						repository
     * @param message 		an obligatory log message provided for committing.
     * @see 				SVNLogEntryPath
     */
    public SVNLogEntry(Map changedPaths, long revision, String author, Date date, String message) {
        myRevision = revision;
        myAuthor = author;
        myDate = date;
        myMessage = message;
        myChangedPaths = changedPaths;
    }
    
    /**
     * Gets a map collection containing all the paths of the entries that
     * were changed in the revision.
     * 
     * <p>
     * <b>NOTE:</b> if the <code>changedPath</code> flag is <code>false</code> in 
     * {@link SVNRepository#log(String[], long, long, boolean, boolean, ISVNLogEntryHandler)
     * SVNRepository.log()} a call to {@link #getChangedPaths()}
     * will return <code>null</code>. 
     * 
     * @return 		a <code>Map</code> instance which keys are all the paths 
     * 				of the entries that were changed and values are 
     * 				<code>SVNLogEntryPath</code> instances
     */
    public Map getChangedPaths() {
        return myChangedPaths;
    }
    
    /**
     * Gets the commit author name.
     * 
     * @return the name of the person who did the commit
     */
    public String getAuthor() {
        return myAuthor;
    }
    
    /**
     * Gets the moment in time when the commit was performed.
     * 
     * @return 		the time moment of the commit
     */
    public Date getDate() {
        return myDate;
    }
    
    /**
     * Gets the log message attached to the commit.
     * 
     * @return 		the commit log message
     */
    public String getMessage() {
        return myMessage;
    }
    
    /**
     * Gets the revision number of the repository after the commit.
     * 
     * @return 		the revision number the repository was commited to
     */
    public long getRevision() {
        return myRevision;
    }
}
