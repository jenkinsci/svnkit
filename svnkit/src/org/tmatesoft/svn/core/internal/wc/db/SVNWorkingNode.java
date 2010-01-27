/*
 * ====================================================================
 * Copyright (c) 2004-2010 TMate Software Ltd.  All rights reserved.
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

import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNProperties;
import org.tmatesoft.svn.core.internal.wc.SVNChecksum;


/**
 * @version 1.3
 * @author  TMate Software Ltd.
 */
public class SVNWorkingNode {
    private long myWCId;
    private String myLocalRelPath;
    private String myParentRelPath;
    private SVNWCDbStatus myStatus;
    private SVNNodeKind myKind;
    private long myCopyFromReposId;
    private String myCopyFromReposPath;
    private long myCopyFromRevision;
    private boolean myIsMovedHere;
    private String myIsMovedTo;
    private SVNChecksum myChecksum;
    private long myTranslatedSize;
    private long myChangedRevision;
    private Date myChangedDate;
    private String myChangedAuthor;
    private String myDepth;
    private Date myLastModifiedTime;
    private SVNProperties myProperties;
    private boolean myIsKeepLocal;
    
    public static SVNWorkingNode maybeCreateWorkingNode(SVNWorkingNode workingNode) {
        if (workingNode != null) {
            return workingNode;
        }
        return new SVNWorkingNode();
    }
    
    public long getWCId() {
        return myWCId;
    }
    
    public String getLocalRelPath() {
        return myLocalRelPath;
    }
    
    public String getParentRelPath() {
        return myParentRelPath;
    }
    
    public SVNWCDbStatus getStatus() {
        return myStatus;
    }
    
    public SVNNodeKind getKind() {
        return myKind;
    }
    
    public long getCopyFromReposId() {
        return myCopyFromReposId;
    }
    
    public String getCopyFromReposPath() {
        return myCopyFromReposPath;
    }
    
    public long getCopyFromRevision() {
        return myCopyFromRevision;
    }
    
    public boolean isIsMovedHere() {
        return myIsMovedHere;
    }
    
    public String getIsMovedTo() {
        return myIsMovedTo;
    }
    
    public SVNChecksum getChecksum() {
        return myChecksum;
    }
    
    public long getTranslatedSize() {
        return myTranslatedSize;
    }
    
    public long getChangedRevision() {
        return myChangedRevision;
    }
    
    public Date getChangedDate() {
        return myChangedDate;
    }
    
    public String getChangedAuthor() {
        return myChangedAuthor;
    }
    
    public String getDepth() {
        return myDepth;
    }
    
    public Date getLastModifiedTime() {
        return myLastModifiedTime;
    }
    
    public SVNProperties getProperties() {
        return myProperties;
    }
    
    public boolean isIsKeepLocal() {
        return myIsKeepLocal;
    }

    
    public void setWCId(long wCId) {
        myWCId = wCId;
    }

    
    public void setLocalRelPath(String localRelPath) {
        myLocalRelPath = localRelPath;
    }

    
    public void setParentRelPath(String parentRelPath) {
        myParentRelPath = parentRelPath;
    }

    
    public void setStatus(SVNWCDbStatus status) {
        myStatus = status;
    }

    
    public void setKind(SVNNodeKind kind) {
        myKind = kind;
    }

    
    public void setCopyFromReposId(long copyFromReposId) {
        myCopyFromReposId = copyFromReposId;
    }

    
    public void setCopyFromReposPath(String copyFromReposPath) {
        myCopyFromReposPath = copyFromReposPath;
    }

    
    public void setCopyFromRevision(long copyFromRevision) {
        myCopyFromRevision = copyFromRevision;
    }

    
    public void setIsMovedHere(boolean isMovedHere) {
        myIsMovedHere = isMovedHere;
    }

    
    public void setIsMovedTo(String isMovedTo) {
        myIsMovedTo = isMovedTo;
    }

    
    public void setChecksum(SVNChecksum checksum) {
        myChecksum = checksum;
    }

    
    public void setTranslatedSize(long translatedSize) {
        myTranslatedSize = translatedSize;
    }

    
    public void setChangedRevision(long changedRevision) {
        myChangedRevision = changedRevision;
    }

    
    public void setChangedDate(Date changedDate) {
        myChangedDate = changedDate;
    }

    
    public void setChangedAuthor(String changedAuthor) {
        myChangedAuthor = changedAuthor;
    }

    
    public void setDepth(String depth) {
        myDepth = depth;
    }

    
    public void setLastModifiedTime(Date lastModifiedTime) {
        myLastModifiedTime = lastModifiedTime;
    }

    
    public void setProperties(SVNProperties properties) {
        myProperties = properties;
    }

    
    public void setIsKeepLocal(boolean isKeepLocal) {
        myIsKeepLocal = isKeepLocal;
    }
    
}
