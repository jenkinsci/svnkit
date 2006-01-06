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


/**
 * The <b>SVNCommitInfo</b> class represents information about a committed 
 * revision. Commit information includes:
 * <ol>
 * <li>a revision number;
 * <li>a datestamp when the revision was committed;
 * <li>the name of the revision author.
 * </ol>
 * In addition, this class provides anexception that, if a commit has failed,
 * has got a description of a failure reason.
 * 
 * @version 1.0
 * @author 	TMate Software Ltd.
 */
public class SVNCommitInfo {

    public static final SVNCommitInfo NULL = new SVNCommitInfo(-1, null, null, null);
    
    private long myNewRevision;
    private Date myDate;
    private String myAuthor;
    private SVNErrorMessage myErrorMessage;

    /**
     * 
     * Constructs an <b>SVNCommitInfo</b> object.
     * 
     * @param revision 		a revision number 
     * @param author 		the name of the author who committed the revision
     * @param date 			the datestamp when the revision was committed
     */
    public SVNCommitInfo(long revision, String author, Date date) {
        this(revision, author, date, null);        
    }

    /**
     * Constructs an <b>SVNCommitInfo</b> object.
     * 
     * @param revision      a revision number 
     * @param author        the name of the author who committed the revision
     * @param date          the datestamp when the revision was committed
     * @param error         if a commit failed - this is an exception containing
     *                      an error description 
     */
    public SVNCommitInfo(long revision, String author, Date date, SVNErrorMessage error) {
        myNewRevision = revision;
        myAuthor = author;
        myDate = date;
        myErrorMessage = error;
    }

    /**
     * Gets the revision number the repository was committed to.
     * 
     * @return 	a revision number
     */
    public long getNewRevision() {
        return myNewRevision;
    }

    /**
     * Gets the name of the revision author
     * 
     * @return 	a revision author's name
     */
    public String getAuthor() {
        return myAuthor;
    }
    
    /**
     * Gets the datestamp when the revision was committed.
     *
     * @return 	a revision datestamp
     */
    public Date getDate() {
        return myDate;
    }
    
    /**
     * Gets an array of error messages that occurred (if occurred) while committing 
     * a new revision.
     * 
     * @return an array of error messages or null. 
     */
    public SVNErrorMessage getErrorMessage() {
        return myErrorMessage;
    }

    /**
     * @deprecated use {@link #getErrorMessage() } instead
     */
    public SVNException getError() {
        if (myErrorMessage != null) {
            return new SVNException(getErrorMessage());
        }
        return null;
    }
}
