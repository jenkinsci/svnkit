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

import org.tmatesoft.svn.core.io.SVNLock;

/**
 * @author TMate Software Ltd.
 */
public class SVNStatus {
    
    public static final int NOT_MODIFIED = 0x00;
    public static final int ADDED = 0x01;
    public static final int CONFLICTED = 0x02;
    public static final int DELETED = 0x03;
    public static final int MERGED = 0x04;
    public static final int IGNORED = 0x05;
    public static final int MODIFIED = 0x06;
    public static final int REPLACED = 0x07;
    public static final int UNVERSIONED = 0x08;
    public static final int MISSING = 0x09;
    public static final int OBSTRUCTED = 0x0A;
    
    public static final int REVERTED = 0x0B;
    public static final int RESOLVED = 0x0C;
    public static final int COPIED = 0x0D;
    public static final int MOVED = 0x0E;
    public static final int RESTORED = 0x0F;
    public static final int UPDATED = 0x10;

    public static final int EXTERNAL = 0x12;
    public static final int CORRUPTED = 0x13;
    public static final int NOT_REVERTED = 0x14;

    private boolean myIsSwitched;
    private boolean myIsAddedWithHistory;
    private long myRemoteRevision;
    private long myWCRevision;
    private long myRevision;
    private int myContentsStatus;
    private int myPropertiesStatus;
    private int myRemoteContentsStatus;
    private int myRemotePropertiesStatus;
    private String myPath;
    private boolean myIsDirectory;
    private String myAuthor;
    private SVNLock myLock;
    
    public SVNStatus(String path, int propStatus, int contentsStatus, long revision, 
            long wcRevision, boolean addedWithHistory, boolean switched, boolean isDirectory, String author,
            SVNLock lock) {
        this(path, propStatus, contentsStatus, revision, wcRevision, -1, 0, 0, addedWithHistory, switched, isDirectory, author, lock);
        
    }

    public SVNStatus(String path, int propStatus, int contentsStatus, long revision, 
            long wcRevision, long remoteRevision, int remoteContentsStatus, int remotePropsStatus,
            boolean addedWithHistory, boolean switched, boolean isDirectory, String author) {
        this(path, propStatus, contentsStatus, revision, wcRevision, remoteRevision, 
                remoteContentsStatus, remotePropsStatus, addedWithHistory, switched, isDirectory,
                author, null);
    }

    public SVNStatus(String path, int propStatus, int contentsStatus, long revision, 
            long wcRevision, long remoteRevision, int remoteContentsStatus, int remotePropsStatus,
            boolean addedWithHistory, boolean switched, boolean isDirectory, String author,
            SVNLock lock) {
        myPath = path;
        myPropertiesStatus = propStatus;
        myContentsStatus = contentsStatus;
        myRevision = revision;
        myWCRevision = wcRevision;
        myRemoteRevision = remoteRevision;
        myIsAddedWithHistory = addedWithHistory;
        myIsSwitched = switched;        
        myRemotePropertiesStatus = remotePropsStatus;
        myRemoteContentsStatus = remoteContentsStatus;
        myIsDirectory = isDirectory;
        myAuthor = author;
        myLock = lock;
    }
    
    public String getAuthor() {
        return myAuthor;
    }
    
    public boolean isDirectory() {
        return myIsDirectory;
    }
    
    public String getPath() {
        return myPath;
    }
    
    public int getPropertiesStatus() {
        return myPropertiesStatus;
    }
    
    public int getContentsStatus() {
        return myContentsStatus;
    }
    
    public long getRevision() {
        return myRevision;
    }
    
    public long getWorkingCopyRevision() {
        return myWCRevision;
    }
    
    public long getRepositoryRevision() {
        return myRemoteRevision;
    }
    
    public int getRepositoryContentsStatus() {
        return myRemoteContentsStatus;
    }

    public int getRepositoryPropertiesStatus() {
        return myRemotePropertiesStatus;
    }
    
    public boolean isAddedWithHistory() {
        return myIsAddedWithHistory;
    }
    
    public boolean isSwitched() {
        return myIsSwitched;
    }
    
    public boolean isManaged() {
        return getContentsStatus() != SVNStatus.UNVERSIONED &&
            getContentsStatus() != SVNStatus.IGNORED;
    }
    
    public void setRemoteStatus(long revision, int propStatus, int contentsStatus) {
        myRemotePropertiesStatus = propStatus;
        myRemoteContentsStatus = contentsStatus;
        myRemoteRevision = revision;
    }
    
    public void setPath(String path) {
        myPath = path;
    }
    
    public SVNLock getLock() {
        return myLock;
    }

}
