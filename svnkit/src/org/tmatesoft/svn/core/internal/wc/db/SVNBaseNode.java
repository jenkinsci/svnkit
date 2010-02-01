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
import java.util.List;

import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNProperties;
import org.tmatesoft.svn.core.internal.wc.SVNChecksum;
import org.tmatesoft.svn.core.io.SVNRepository;


/**
 * @version 1.3
 * @author  TMate Software Ltd.
 */
public class SVNBaseNode {

    private SVNWCDbStatus myStatus;
    private SVNWCDbKind myWCDbKind;
    private SVNNodeKind myNodeKind;
    private long myWCId;
    private long myReposId = -1;
    private String myReposPath;
    private String myLocalRelativePath;
    private long myRevision = SVNRepository.INVALID_REVISION;
    private SVNProperties myProps;
    private long myChangedRevision = SVNRepository.INVALID_REVISION;
    private Date myChangedDate;
    private String myChangedAuthor;
    private SVNDepth myDepth;

    private List myChildren;
    private SVNChecksum myChecksum;
    private long myTranslatedSize = -1;
    private String myTarget;
    private String myParentRelPath; 
    private Date myLastModifiedTime;
    
    
    public Date getLastModifiedTime() {
        return myLastModifiedTime;
    }

    
    public void setLastModifiedTime(Date lastModifiedTime) {
        myLastModifiedTime = lastModifiedTime;
    }

    public String getParentRelPath() {
        return myParentRelPath;
    }
    
    public void setParentRelPath(String parentRelPath) {
        myParentRelPath = parentRelPath;
    }

    public static SVNBaseNode maybeCreateNewInstance(SVNBaseNode baseNode) {
        if (baseNode != null) {
            return baseNode;
        }
        return new SVNBaseNode();
    }
    
    public SVNWCDbStatus getStatus() {
        return myStatus;
    }
    
    public SVNWCDbKind getWCDbKind() {
        return myWCDbKind;
    }
    
    public long getWCId() {
        return myWCId;
    }
    
    public long getReposId() {
        return myReposId;
    }
    
    public String getReposPath() {
        return myReposPath;
    }
    
    public long getRevision() {
        return myRevision;
    }
    
    public SVNProperties getProperties() {
        return myProps;
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
    
    public List getChildren() {
        return myChildren;
    }
    
    public SVNChecksum getChecksum() {
        return myChecksum;
    }
    
    public long getTranslatedSize() {
        return myTranslatedSize;
    }
    
    public String getTarget() {
        return myTarget;
    }

    public String getLocalRelativePath() {
        return myLocalRelativePath;
    }
    
    public SVNDepth getDepth() {
        return myDepth;
    }
    
    public boolean hasChildren() {
        return myChildren != null && !myChildren.isEmpty();
    }

    
    public SVNNodeKind getNodeKind() {
        return myNodeKind;
    }

    
    public void setNodeKind(SVNNodeKind nodeKind) {
        myNodeKind = nodeKind;
    }

    public void setStatus(SVNWCDbStatus status) {
        myStatus = status;
    }

    
    public void setWCDbKind(SVNWCDbKind kind) {
        myWCDbKind = kind;
    }

    
    public void setWCId(long wCId) {
        myWCId = wCId;
    }

    
    public void setReposId(long reposId) {
        myReposId = reposId;
    }

    
    public void setReposPath(String reposPath) {
        myReposPath = reposPath;
    }

    
    public void setLocalRelativePath(String localRelativePath) {
        myLocalRelativePath = localRelativePath;
    }

    
    public void setRevision(long revision) {
        myRevision = revision;
    }

    
    public void setProps(SVNProperties props) {
        myProps = props;
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

    
    public void setDepth(SVNDepth depth) {
        myDepth = depth;
    }

    
    public void setChildren(List children) {
        myChildren = children;
    }

    
    public void setChecksum(SVNChecksum checksum) {
        myChecksum = checksum;
    }

    
    public void setTranslatedSize(long translatedSize) {
        myTranslatedSize = translatedSize;
    }

    
    public void setTarget(String target) {
        myTarget = target;
    }
}
