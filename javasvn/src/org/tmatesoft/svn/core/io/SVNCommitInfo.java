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
 * @author TMate Software Ltd.
 */
public class SVNCommitInfo {
    
    private long myNewRevision;
    private Date myDate;
    private String myAuthor;
    
    public SVNCommitInfo(long revision, String author, Date date) {
        myNewRevision = revision;
        myAuthor = author;
        myDate = date;
    }

    public long getNewRevision() {
        return myNewRevision;
    }
    
    public String getAuthor() {
        return myAuthor;
    }
    
    public Date getDate() {
        return myDate;
    }
}
