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

import java.util.Date;

/**
 * <code>SVNCommitInfo</code> represents a class that incapsulates the necessary 
 * information related with every committing to the repository. Commit information
 * includes:
 * <ol>
 * <li>a new repository revision number the repository assigns after successfull 
 * committing;
 * <li>the date this commit was done at (including a timestamp, not just a day);
 * <li>its author's name.
 * </ol>
 * The class provides necessary public methods to obtain that information. 
 * 
 * @version 1.0
 * @author 	TMate Software Ltd.
 * @see 	ISVNEditor
 */
public class SVNCommitInfo {

    public static final SVNCommitInfo NULL = new SVNCommitInfo(-1, null, null);
    
    private long myNewRevision;
    private Date myDate;
    private String myAuthor;

    /**
     * 
     * Constructs an <code>SVNCommitInfo</code> object.
     * 
     * @param revision 		new revision number the repository was committed to.
     * @param author 		the author who performed the commit.
     * @param date 			time moment the commit was done.
     */
    public SVNCommitInfo(long revision, String author, Date date) {
        myNewRevision = revision;
        myAuthor = author;
        myDate = date;
    }

    /**
     * Gets the revision number the repository was committed to.
     * 
     * @return 	a new assigned revision number after the commit
     */
    public long getNewRevision() {
        return myNewRevision;
    }

    /**
     * Gets the author who did the commit.
     * 
     * @return 	the commit author's name.
     */
    public String getAuthor() {
        return myAuthor;
    }
    
    /**
     * Gets the moment in time when the commit was done.
     *
     * @return 	the time moment when the commit was performed
     */
    public Date getDate() {
        return myDate;
    }
}
