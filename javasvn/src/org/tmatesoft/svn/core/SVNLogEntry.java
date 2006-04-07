/*
 * ====================================================================
 * Copyright (c) 2004-2006 TMate Software Ltd.  All rights reserved.
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


/**
 * The <b>SVNLogEntry</b> class encapsulates such per revision information as: 
 * a revision number, the datestamp when the revision was committed, the author 
 * of the revision, a commit log message and all paths changed in that revision. 
 * 
 * @version 1.0
 * @author 	TMate Software Ltd.
 * @see 	SVNLogEntryPath
 * @see     ISVNLogEntryHandler
 * @see     <a target="_top" href="http://tmate.org/svn/kb/examples/">Examples</a>
 */
public class SVNLogEntry {
    
    private long myRevision;
    private String myAuthor;
    private Date myDate;
    private String myMessage;
    private Map myChangedPaths;
    
    /**
     * Constructs an <b>SVNLogEntry</b> object. 
     * 
     * @param changedPaths 	a map collection which keys are
     * 						all the paths that were changed in   
     *                      <code>revision</code>, and values are 
     * 						<b>SVNLogEntryPath</b> representation objects
     * @param revision 		a revision number
     * @param author 		the author of <code>revision</code>
     * @param date 			the datestamp when the revision was committed
     * @param message 		an commit log message for <code>revision</code>
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
     * Gets a map containing all the paths that were changed in the 
     * revision that this object represents.
     * 
     * @return 		a map which keys are all the paths 
     * 				that were changed and values are 
     * 				<b>SVNLogEntryPath</b> objects
     * 
     */
    public Map getChangedPaths() {
        return myChangedPaths;
    }
    
    /**
     * Returns the author of the revision that this object represents.
     * 
     * @return the author of the revision
     */
    public String getAuthor() {
        return myAuthor;
    }
    
    /**
     * Gets the datestamp when the revision was committed.
     * 
     * @return 	the moment in time when the revision was committed
     */
    public Date getDate() {
        return myDate;
    }
    
    /**
     * Gets the log message attached to the revision.
     * 
     * @return 		the commit log message
     */
    public String getMessage() {
        return myMessage;
    }
    
    /**
     * Gets the number of the revision that this object represents.
     * 
     * @return 	a revision number 
     */
    public long getRevision() {
        return myRevision;
    }
}
