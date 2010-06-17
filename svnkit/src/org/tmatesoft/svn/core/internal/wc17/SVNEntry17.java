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
package org.tmatesoft.svn.core.internal.wc17;

import java.io.File;
import java.util.Map;

import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.internal.wc.SVNTreeConflictUtil;
import org.tmatesoft.svn.core.internal.wc.admin.SVNAdminArea;
import org.tmatesoft.svn.core.internal.wc.admin.SVNEntry;
import org.tmatesoft.svn.core.wc.SVNRevision;

/**
 * @version 1.3
 * @author TMate Software Ltd.
 */
public class SVNEntry17 extends SVNEntry {

    private String author;
    private String[] cachableProperties;
    private String changelistName;
    private String checksum;
    private String committedDate;
    private long committedRevision;
    private String conflictNew;
    private String conflictOld;
    private String conflictWorking;
    private long copyFromRevision;
    private SVNURL copyFromSVNURL;
    private String copyFromURL;
    private SVNDepth depth;
    private String externalFilePath;
    private SVNRevision externalFilePegRevision;
    private SVNRevision externalFileRevision;
    private SVNNodeKind kind;
    private String lockComment;
    private String lockCreationDate;
    private String lockOwner;
    private String lockToken;
    private String name;
    private String[] presentProperties;
    private String propRejectFile;
    private String propTime;
    private String repositoryRoot;
    private SVNURL repositoryRootURL;
    private long revision;
    private SVNURL svnUrl;
    private String schedule;
    private String textTime;
    private String treeConflictData;
    private Map treeConflicts;
    private String url;
    private String uuid;
    private long workingSize;
    private boolean absent;
    private boolean copied;
    private boolean deleted;
    private boolean hidden;
    private boolean incomplete;
    private boolean keepLocal;
    private boolean scheduledForAddition;
    private boolean scheduledForDeletion;
    private boolean scheduledForReplacement;

    private File path;

    public SVNEntry17(File path) {
        this.path = path;
    }

    public Map asMap() {
        // TODO
        return null;
    }

    public void loadProperties(Map entryProps) {
        // TODO
    }

    public SVNAdminArea getAdminArea() {
        return null;
    }

    public String getAuthor() {
        return author;
    }

    public String[] getCachableProperties() {
        return cachableProperties;
    }

    public String getChangelistName() {
        return changelistName;
    }

    public String getChecksum() {
        return checksum;
    }

    public String getCommittedDate() {
        return committedDate;
    }

    public long getCommittedRevision() {
        return committedRevision;
    }

    public String getConflictNew() {
        return conflictNew;
    }

    public String getConflictOld() {
        return conflictOld;
    }

    public String getConflictWorking() {
        return conflictWorking;
    }

    public long getCopyFromRevision() {
        return copyFromRevision;
    }

    public SVNURL getCopyFromSVNURL() throws SVNException {
        if (copyFromSVNURL == null && copyFromURL != null) {
            copyFromSVNURL = SVNURL.parseURIEncoded(copyFromURL);
        }
        return copyFromSVNURL;
    }

    public String getCopyFromURL() {
        return copyFromURL;
    }

    public SVNDepth getDepth() {
        return depth;
    }

    public String getExternalFilePath() {
        return externalFilePath;
    }

    public SVNRevision getExternalFilePegRevision() {
        return externalFilePegRevision;
    }

    public SVNRevision getExternalFileRevision() {
        return externalFileRevision;
    }

    public SVNNodeKind getKind() {
        return kind;
    }

    public String getLockComment() {
        return lockComment;
    }

    public String getLockCreationDate() {
        return lockCreationDate;
    }

    public String getLockOwner() {
        return lockOwner;
    }

    public String getLockToken() {
        return lockToken;
    }

    public String getName() {
        return name;
    }

    public String[] getPresentProperties() {
        return presentProperties;
    }

    public String getPropRejectFile() {
        return propRejectFile;
    }

    public String getPropTime() {
        return propTime;
    }

    public String getRepositoryRoot() {
        return repositoryRoot;
    }

    public SVNURL getRepositoryRootURL() throws SVNException {
        if (repositoryRootURL == null && repositoryRoot != null) {
            repositoryRootURL = SVNURL.parseURIEncoded(repositoryRoot);
        }
        return repositoryRootURL;
    }

    public long getRevision() {
        return revision;
    }

    public SVNURL getSVNURL() throws SVNException {
        if (svnUrl == null && url != null) {
            svnUrl = SVNURL.parseURIEncoded(url);
        }
        return svnUrl;
    }

    public String getSchedule() {
        return schedule;
    }

    public String getTextTime() {
        return textTime;
    }

    public String getTreeConflictData() {
        return treeConflictData;
    }

    public Map getTreeConflicts() throws SVNException {
        if (treeConflicts == null && treeConflictData != null) {
            treeConflicts = SVNTreeConflictUtil.readTreeConflicts(getRoot(), treeConflictData);
        }
        return treeConflicts;
    }

    public String getURL() {
        return url;
    }

    public String getUUID() {
        return uuid;
    }

    public long getWorkingSize() {
        return workingSize;
    }

    public boolean isAbsent() {
        return absent;
    }

    public boolean isCopied() {
        return copied;
    }

    public boolean isDeleted() {
        return deleted;
    }

    public boolean isDirectory() {
        return SVNNodeKind.DIR == this.kind;
    }

    public boolean isFile() {
        return SVNNodeKind.FILE == this.kind;
    }

    public boolean isHidden() {
        return hidden;
    }

    public boolean isIncomplete() {
        return incomplete;
    }

    public boolean isKeepLocal() {
        return keepLocal;
    }

    public boolean isScheduledForAddition() {
        return scheduledForAddition;
    }

    public boolean isScheduledForDeletion() {
        return scheduledForDeletion;
    }

    public boolean isScheduledForReplacement() {
        return scheduledForReplacement;
    }

    public boolean isThisDir() {
        return "".equals(getName());
    }

    public void scheduleForAddition() {
        scheduledForAddition = true;
    }

    public void scheduleForDeletion() {
        scheduledForDeletion = true;
    }

    public void scheduleForReplacement() {
        scheduledForReplacement = true;
    }

    public void setAbsent(boolean absent) {
        this.absent = absent;
    }

    public boolean setAuthor(String cmtAuthor) {
        if (isChangedValue(this.author, cmtAuthor)) {
            this.author = cmtAuthor;
            return true;
        }
        return false;
    }

    public void setCachableProperties(String[] cachableProps) {
        this.cachableProperties = cachableProps;
    }

    public boolean setChangelistName(String changelistName) {
        if (isChangedValue(this.changelistName, changelistName)) {
            this.changelistName = changelistName;
            return true;
        }
        return false;
    }

    public void setChecksum(String checksum) {
        this.checksum = checksum;
    }

    public void setCommittedDate(String date) {
        this.committedDate = date;
    }

    public boolean setCommittedRevision(long cmtRevision) {
        if (this.committedRevision != cmtRevision) {
            this.committedRevision = cmtRevision;
            return true;
        }
        return false;
    }

    public void setConflictNew(String name) {
        this.conflictNew = name;
    }

    public void setConflictOld(String name) {
        this.conflictOld = name;
    }

    public void setConflictWorking(String name) {
        this.conflictWorking = name;
    }

    public void setCopied(boolean copied) {
        this.copied = copied;
    }

    public void setCopyFromRevision(long revision) {
        this.revision = revision;
    }

    public boolean setCopyFromURL(String url) {
        if (isChangedValue(this.copyFromURL, url)) {
            this.copyFromURL = url;
            this.copyFromSVNURL = null;
            return true;
        }
        return false;
    }

    public void setDeleted(boolean deleted) {
        this.deleted = deleted;
    }

    public void setDepth(SVNDepth depth) {
        this.depth = depth;
    }

    public void setIncomplete(boolean incomplete) {
        this.incomplete = incomplete;
    }

    public void setKeepLocal(boolean keepLocal) {
        this.keepLocal = keepLocal;
    }

    public void setKind(SVNNodeKind kind) {
        this.kind = kind;
    }

    public void setLockComment(String comment) {
        this.lockComment = comment;
    }

    public void setLockCreationDate(String date) {
        this.lockCreationDate = date;
    }

    public void setLockOwner(String owner) {
        this.lockOwner = owner;
    }

    public void setLockToken(String token) {
        this.lockToken = token;
    }

    public void setPropRejectFile(String name) {
        this.propRejectFile = name;
    }

    public void setPropTime(String time) {
        this.propTime = time;
    }

    public boolean setRepositoryRoot(String url) {
        if (isChangedValue(this.repositoryRoot, url)) {
            this.repositoryRoot = url;
            this.repositoryRootURL = null;
            return true;
        }
        return false;
    }

    public boolean setRepositoryRootURL(SVNURL url) {
        if (url == null) {
            this.repositoryRoot = null;
            this.repositoryRootURL = null;
        } else {
            return setRepositoryRoot(url.toString());
        }
        return false;
    }

    public boolean setRevision(long revision) {
        if (this.revision != revision) {
            this.revision = revision;
            return true;
        }
        return false;
    }

    public void setSchedule(String schedule) {
        this.schedule = schedule;
    }

    public void setTextTime(String time) {
        this.textTime = time;
    }

    public void setTreeConflictData(String conflictData) {
        this.treeConflictData = conflictData;
        this.treeConflicts = null;
    }

    public void setTreeConflicts(Map treeConflicts) throws SVNException {
        String conflictData = SVNTreeConflictUtil.getTreeConflictData(treeConflicts);
        setTreeConflictData(conflictData);
        this.treeConflicts = treeConflicts;
    }

    public boolean setURL(String url) {
        if (isChangedValue(this.url, url)) {
            this.url = url;
            this.svnUrl = null;
            return true;
        }
        return false;
    }

    public void setUUID(String uuid) {
        this.uuid = uuid;
    }

    public boolean setWorkingSize(long size) {
        if (this.workingSize != size) {
            this.workingSize = size;
            return true;
        }
        return false;
    }

    public void unschedule() {
        this.schedule = null;
    }

    private boolean isChangedValue(Object oldValue, Object newValue) {
        return (oldValue == null && newValue != null) || (oldValue != null && newValue == null) || (!oldValue.equals(newValue));
    }

    public void setName(String name) {
        this.name = name;
    }

    public File getPath() {
        return path;
    }

    public void setPath(File path) {
        this.path = path;
    }

    private File getRoot() {
        if (path == null) {
            return null;
        }
        if (isDirectory() && path.isDirectory()) {
            return path;
        }
        return SVNFileUtil.getParentFile(path);
    }

    public void setExternalFilePath(String path) {
        externalFilePath = path;
    }

    public void setExternalFilePegRevision(SVNRevision pegRevision) {
        externalFilePegRevision = pegRevision;
    }

    public void setExternalFileRevision(SVNRevision revision) {
        externalFileRevision = revision;
    }

}
