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
 * @author Alexander Kitaev
 */
public class SVNDirEntry {

    private String myName;
    private SVNNodeKind myKind;
    private long mySize;
    private boolean myHasProperties;
    private long myFirstRevision;
    private Date myCreatedDate;
    private String myLastAuthor;
    
    public SVNDirEntry(String name, SVNNodeKind kind, long size,
            boolean hasProperties, long firstRevision, Date createdDate,
            String lastAuthor) {
        myName = name;
        myKind = kind;
        mySize = size;
        myHasProperties = hasProperties;
        myFirstRevision = firstRevision;
        myCreatedDate = createdDate;
        myLastAuthor = lastAuthor;
    }
    
    public String getName() {
        return myName;
    }
    public long size() {
        return mySize;
    }
    public boolean hasProperties() {
        return myHasProperties;
    }
    public SVNNodeKind getKind() {
        return myKind;
    }
    public Date getDate() {
        return myCreatedDate;
    }
    public long getRevision() {
        return myFirstRevision;
    }

    public String getAuthor() {
        return myLastAuthor;
    }
    
    public void setName(String name) {
        myName = name;
    }

    public String toString() {
        StringBuffer result = new StringBuffer();
        result.append("name=");
        result.append(myName);
        result.append(", kind=");
        result.append(myKind);
        result.append(", size=");
        result.append(mySize);
        result.append(", hasProps=");
        result.append(myHasProperties);
        result.append(", creation-rev=");
        result.append(myFirstRevision);
        if (myLastAuthor != null) {
            result.append(", lastAuthor=");
            result.append(myLastAuthor);
        }
        if (myCreatedDate != null) {
            result.append(", creation-date=");
            result.append(myCreatedDate);
        }
        return result.toString();
    }
    
}
