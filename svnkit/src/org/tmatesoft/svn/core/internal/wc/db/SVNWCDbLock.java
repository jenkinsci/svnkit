/*
 * ====================================================================
 * Copyright (c) 2004-2009 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html.
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.core.internal.wc.db;

import java.util.Date;


/**
 * @version 1.3
 * @author  TMate Software Ltd.
 */
public class SVNWCDbLock {
    private String myToken;
    private String myOwner;
    private String myComment;
    private Date myDate;

    public SVNWCDbLock(String token, String owner, String comment, Date date) {
        myToken = token;
        myOwner = owner;
        myComment = comment;
        myDate = date;
    }
    
    public String getToken() {
        return myToken;
    }
    
    public String getOwner() {
        return myOwner;
    }
    
    public String getComment() {
        return myComment;
    }
    
    public Date getDate() {
        return myDate;
    }
    
}
