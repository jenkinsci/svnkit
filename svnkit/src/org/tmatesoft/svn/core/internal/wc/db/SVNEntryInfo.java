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

import java.io.File;
import java.util.Date;

import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.internal.wc.SVNChecksum;


/**
 * @version 1.3
 * @author  TMate Software Ltd.
 */
public class SVNEntryInfo {
    
    private String myName;
    private long myRevision;
    private String myURL;
    private String myReposURL;
    private String myUUID;
    private SVNNodeKind myNodeKind;
    private String mySchedule;
    private boolean myIsCopied;
    private boolean myIsDeleted;
    private boolean myIsAbsent;
    private boolean myIsIncomplete;
    private long myCopyFromRevision;
    private String myCopyFromURL;
    private long myCommittedRevision;
    private Date myCommittedDate;
    private String myCommittedAuthor;
    private String myConflictOld;
    private String myConflictNew;
    private String myConflictWorking;
    private String myPropertyRejectFilePath;
    private Date myLastTextTime;
    private SVNChecksum myChecksum;
    private String myLockToken;
    private Date myLockCreationDate;
    private boolean myHasProps;
    private boolean myHasPropModifications;
    private String myChangeList;
    private long myWorkingSize;
    private boolean myIsKeepLocal;
    private SVNDepth myDepth;
    private String myTreeConflictData;
    private String myFileExternalPath;
    private long myFileExternalPegRevision;
    private long myFileExternalRevision;
    
    private SVNWCDbLock myWCDBLock;
    private SVNWCDbStatus myWCDBStatus;
    private SVNWCDbKind myWCDBKind;
    private boolean myIsTextMode;
    private boolean myIsPropsMode;
    private boolean myIsBaseShadowed;
    private boolean myIsConflicted;
    private boolean myIsBaseReplaced;
    private long myOriginalRevision;
    private String myOriginalUUID;
    private String myOriginalRootURL;
    private String myOriginalReposPath;
    private String myTarget;
    private String myReposPath;
    private File myOperationRootPath;
    private File myDeletedBasePath;
    private File myMovedToPath;
    private File myDeletedWorkingPath;
    
    public String getName() {
        return myName;
    }
    
    public long getRevision() {
        return myRevision;
    }
    
    public String getURL() {
        return myURL;
    }
    
    public String getReposRootURL() {
        return myReposURL;
    }
    
    public String getUUID() {
        return myUUID;
    }
    
    public SVNNodeKind getNodeKind() {
        return myNodeKind;
    }
    
    public String getSchedule() {
        return mySchedule;
    }
    
    public boolean isCopied() {
        return myIsCopied;
    }
    
    public boolean isDeleted() {
        return myIsDeleted;
    }
    
    public boolean isAbsent() {
        return myIsAbsent;
    }
    
    public boolean isIncomplete() {
        return myIsIncomplete;
    }
    
    public long getCopyFromRevision() {
        return myCopyFromRevision;
    }
    
    public String getCopyFromURL() {
        return myCopyFromURL;
    }
    
    public long getCommittedRevision() {
        return myCommittedRevision;
    }
    
    public Date getCommittedDate() {
        return myCommittedDate;
    }
    
    public String getCommittedAuthor() {
        return myCommittedAuthor;
    }
    
    public String getConflictOld() {
        return myConflictOld;
    }
    
    public String getConflictNew() {
        return myConflictNew;
    }
    
    public String getConflictWorking() {
        return myConflictWorking;
    }
    
    public String getPropertyRejectFilePath() {
        return myPropertyRejectFilePath;
    }
    
    public Date getLastTextTime() {
        return myLastTextTime;
    }
    
    public SVNChecksum getChecksum() {
        return myChecksum;
    }
    
    public String getLockToken() {
        return myLockToken;
    }
    
    public Date getLockCreationDate() {
        return myLockCreationDate;
    }
    
    public boolean isHasProps() {
        return myHasProps;
    }
    
    public boolean isHasPropModifications() {
        return myHasPropModifications;
    }
    
    public String getChangeList() {
        return myChangeList;
    }
    
    public long getWorkingSize() {
        return myWorkingSize;
    }
    
    public boolean isKeepLocal() {
        return myIsKeepLocal;
    }
    
    public SVNDepth getDepth() {
        return myDepth;
    }
    
    public String getTreeConflictData() {
        return myTreeConflictData;
    }
    
    public String getFileExternalPath() {
        return myFileExternalPath;
    }
    
    public long getFileExternalPegRevision() {
        return myFileExternalPegRevision;
    }
    
    public long getFileExternalRevision() {
        return myFileExternalRevision;
    }
    
    public SVNWCDbLock getWCDBLock() {
        return myWCDBLock;
    }
    
    public SVNWCDbStatus getWCDBStatus() {
        return myWCDBStatus;
    }
    
    public SVNWCDbKind getWCDBKind() {
        return myWCDBKind;
    }
    
    public boolean isTextMode() {
        return myIsTextMode;
    }
    
    public boolean isPropsMode() {
        return myIsPropsMode;
    }
    
    public boolean isBaseShadowed() {
        return myIsBaseShadowed;
    }
    
    public boolean isConflicted() {
        return myIsConflicted;
    }
    
    public long getOriginalRevision() {
        return myOriginalRevision;
    }
    
    public String getOriginalUUID() {
        return myOriginalUUID;
    }
    
    public String getOriginalRootURL() {
        return myOriginalRootURL;
    }
    
    public String getOriginalReposPath() {
        return myOriginalReposPath;
    }
    
    public String getTarget() {
        return myTarget;
    }
    
    public String getReposPath() {
        return myReposPath;
    }

    public File getOperationRootPath() {
        return myOperationRootPath;
    }
    
    public File getDeletedBasePath() {
        return myDeletedBasePath;
    }
    
    public boolean isBaseReplaced() {
        return myIsBaseReplaced;
    }
    
    public File getMovedToPath() {
        return myMovedToPath;
    }
    
    public File getDeletedWorkingPath() {
        return myDeletedWorkingPath;
    }
    
    public void setDeletedWorkingPath(File deletedWorkingPath) {
        myDeletedWorkingPath = deletedWorkingPath;
    }

    public void setMovedToPath(File movedToPath) {
        myMovedToPath = movedToPath;
    }

    public void setIsBaseReplaced(boolean isBaseReplaced) {
        myIsBaseReplaced = isBaseReplaced;
    }

    public void setDeletedBasePath(File deletedBasePath) {
        myDeletedBasePath = deletedBasePath;
    }

    public void setOperationRootPath(File operationRootPath) {
        myOperationRootPath = operationRootPath;
    }
    
    public void setReposPath(String reposPath) {
        myReposPath = reposPath;
    }

    public void setTarget(String target) {
        myTarget = target;
    }

    public void setOriginalRevision(long originalRevision) {
        myOriginalRevision = originalRevision;
    }
    
    public void setOriginalUUID(String originalUUID) {
        myOriginalUUID = originalUUID;
    }
    
    public void setOriginalRootURL(String originalRootURL) {
        myOriginalRootURL = originalRootURL;
    }
    
    public void setOriginalReposPath(String originalReposRelPath) {
        myOriginalReposPath = originalReposRelPath;
    }
    
    public void setName(String name) {
        myName = name;
    }
    
    public void setRevision(long revision) {
        myRevision = revision;
    }
    
    public void setURL(String uRL) {
        myURL = uRL;
    }
    
    public void setReposRootURL(String reposURL) {
        myReposURL = reposURL;
    }
    
    public void setUUID(String uUID) {
        myUUID = uUID;
    }
    
    public void setNodeKind(SVNNodeKind nodeKind) {
        myNodeKind = nodeKind;
    }
    
    public void setSchedule(String schedule) {
        mySchedule = schedule;
    }
    
    public void setIsCopied(boolean isCopied) {
        myIsCopied = isCopied;
    }
    
    public void setIsDeleted(boolean isDeleted) {
        myIsDeleted = isDeleted;
    }
    
    public void setIsAbsent(boolean isAbsent) {
        myIsAbsent = isAbsent;
    }
    
    public void setIsIncomplete(boolean isIncomplete) {
        myIsIncomplete = isIncomplete;
    }
    
    public void setCopyFromRevision(long copyFromRevision) {
        myCopyFromRevision = copyFromRevision;
    }
    
    public void setCopyFromURL(String copyFromURL) {
        myCopyFromURL = copyFromURL;
    }
    
    public void setCommittedRevision(long committedRevision) {
        myCommittedRevision = committedRevision;
    }
    
    public void setCommittedDate(Date committedDate) {
        myCommittedDate = committedDate;
    }
    
    public void setCommittedAuthor(String committedAuthr) {
        myCommittedAuthor = committedAuthr;
    }
    
    public void setConflictOld(String conflictOld) {
        myConflictOld = conflictOld;
    }
    
    public void setConflictNew(String conflictNew) {
        myConflictNew = conflictNew;
    }
    
    public void setConflictWorking(String conflictWorking) {
        myConflictWorking = conflictWorking;
    }
    
    public void setPropertyRejectFilePath(String propertyRejectFilePath) {
        myPropertyRejectFilePath = propertyRejectFilePath;
    }
    
    public void setLastTextTime(Date lastTextTime) {
        myLastTextTime = lastTextTime;
    }
    
    public void setChecksum(SVNChecksum checksum) {
        myChecksum = checksum;
    }
    
    public void setLockToken(String lockToken) {
        myLockToken = lockToken;
    }
    
    public void setLockCreationDate(Date lockCreationDate) {
        myLockCreationDate = lockCreationDate;
    }
    
    public void setHasProps(boolean hasProps) {
        myHasProps = hasProps;
    }
    
    public void setHasPropModifications(boolean hasPropModifications) {
        myHasPropModifications = hasPropModifications;
    }
    
    public void setChangeList(String changeList) {
        myChangeList = changeList;
    }
    
    public void setWorkingSize(long workingSize) {
        myWorkingSize = workingSize;
    }
    
    public void setIsKeepLocal(boolean isKeepLocal) {
        myIsKeepLocal = isKeepLocal;
    }
    
    public void setDepth(SVNDepth depth) {
        myDepth = depth;
    }
    
    public void setTreeConflictData(String treeConflictData) {
        myTreeConflictData = treeConflictData;
    }
    
    public void setFileExternalPath(String fileExternalPath) {
        myFileExternalPath = fileExternalPath;
    }
    
    public void setFileExternalPegRevision(long fileExternalPegRevision) {
        myFileExternalPegRevision = fileExternalPegRevision;
    }
    
    public void setFileExternalRevision(long fileExternalRevision) {
        myFileExternalRevision = fileExternalRevision;
    }
    
    public void setWCDBLock(SVNWCDbLock wcDBLock) {
        myWCDBLock = wcDBLock;
    }
    
    public void setWCDBStatus(SVNWCDbStatus wcDBStatus) {
        myWCDBStatus = wcDBStatus;
    }
    
    public void setWCDBKind(SVNWCDbKind wcDBKind) {
        myWCDBKind = wcDBKind;
    }
    
    public void setIsTextMode(boolean isTextMode) {
        myIsTextMode = isTextMode;
    }
    
    public void setIsPropsMode(boolean isPropsMode) {
        myIsPropsMode = isPropsMode;
    }
    
    public void setIsBaseShadowed(boolean isBaseShadowed) {
        myIsBaseShadowed = isBaseShadowed;
    }
    
    public void setIsConflicted(boolean isConflicted) {
        myIsConflicted = isConflicted;
    }
    
}
