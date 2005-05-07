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
 * <p>
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
 * </p>
 * @version 1.0
 * @author TMate Software Ltd.
 * @see ISVNEditor
 */
public class SVNCommitInfo {
    
    private long myNewRevision;
    private Date myDate;
    private String myAuthor;
    /**
     * <p>
     * The constructor.
     * </p>
     * @param revision new revision number the repository was committed to.
     * @param author the author who performed the commit.
     * @param date time moment the commit was done.
     */
    public SVNCommitInfo(long revision, String author, Date date) {
        myNewRevision = revision;
        myAuthor = author;
        myDate = date;
    }
    /**
     * <p>
     * Gets the revision number the repository was committed to.
     * </p>
     * @return revision number.
     */
    public long getNewRevision() {
        return myNewRevision;
    }
    /**
     * <p>
     * Gets the author who did the commit.
     * </p>
     * @return author's name.
     */
    public String getAuthor() {
        return myAuthor;
    }
    /**
     * <p>
     * Gets the moment in time when the commit was done.
     * </p>
     * @return time moment.
     */
    public Date getDate() {
        return myDate;
    }
}
