package org.tmatesoft.svn.core.wc2;

import java.io.File;

import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNLock;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.util.SVNDate;
import org.tmatesoft.svn.core.wc.SVNStatusType;

public class SvnStatus extends SvnObject {

    private SVNNodeKind kind;
    private File path;
    private long fileSize;
    private boolean versioned;
    private boolean conflicted;
    
    private SVNStatusType nodeStatus;
    private SVNStatusType textStatus;
    private SVNStatusType propertiesStatus;
    
    private boolean wcLocked;
    private boolean copied;
    
    private SVNURL repositoryRootUrl;
    private String repositoryUuid;
    
    private String repositoryRelativePath;
    
    private long revision;
    private long changedRevision;
    private SVNDate changedDate;
    private String changedAuthor;
    
    private boolean switched;
    private boolean fileExternal;
    
    private SVNLock lock;
    private String changelist;
    private SVNDepth depth;
    
    private SVNNodeKind repositoryKind;
    private SVNStatusType repositoryNodeStatus;
    private SVNStatusType repositoryTextStatus;
    private SVNStatusType repositoryPropertiesStatus;    
    private SVNLock repositoryLock;
    
    private long repositoryChangedRevision;
    private SVNDate repositoryChangedDate;
    private String repositoryChangedAuthor;    
        
    public SVNNodeKind getKind() {
        return kind;
    }
    public File getPath() {
        return path;
    }
    public long getFileSize() {
        return fileSize;
    }
    public boolean isVersioned() {
        return versioned;
    }
    public boolean isConflicted() {
        return conflicted;
    }
    public SVNStatusType getNodeStatus() {
        return nodeStatus;
    }
    public SVNStatusType getTextStatus() {
        return textStatus;
    }
    public SVNStatusType getPropertiesStatus() {
        return propertiesStatus;
    }
    public boolean isWcLocked() {
        return wcLocked;
    }
    public boolean isCopied() {
        return copied;
    }
    public SVNURL getRepositoryRootUrl() {
        return repositoryRootUrl;
    }
    public String getRepositoryUuid() {
        return repositoryUuid;
    }
    public String getRepositoryRelativePath() {
        return repositoryRelativePath;
    }
    public long getRevision() {
        return revision;
    }
    public long getChangedRevision() {
        return changedRevision;
    }
    public SVNDate getChangedDate() {
        return changedDate;
    }
    public String getChangedAuthor() {
        return changedAuthor;
    }
    public boolean isSwitched() {
        return switched;
    }
    public boolean isFileExternal() {
        return fileExternal;
    }
    public SVNLock getLock() {
        return lock;
    }
    public SVNDepth getDepth() {
        return depth;
    }
    public SVNNodeKind getRepositoryKind() {
        return repositoryKind;
    }
    public SVNStatusType getRepositoryNodeStatus() {
        return repositoryNodeStatus;
    }
    public SVNStatusType getRepositoryTextStatus() {
        return repositoryTextStatus;
    }
    public SVNStatusType getRepositoryPropertiesStatus() {
        return repositoryPropertiesStatus;
    }
    public SVNLock getRepositoryLock() {
        return repositoryLock;
    }
    public long getRepositoryChangedRevision() {
        return repositoryChangedRevision;
    }
    public SVNDate getRepositoryChangedDate() {
        return repositoryChangedDate;
    }
    public String getRepositoryChangedAuthor() {
        return repositoryChangedAuthor;
    }
    public void setKind(SVNNodeKind kind) {
        this.kind = kind;
    }
    public void setPath(File path) {
        this.path = path;
    }
    public void setFileSize(long fileSize) {
        this.fileSize = fileSize;
    }
    public void setVersioned(boolean versioned) {
        this.versioned = versioned;
    }
    public void setConflicted(boolean conflicted) {
        this.conflicted = conflicted;
    }
    public void setNodeStatus(SVNStatusType nodeStatus) {
        this.nodeStatus = nodeStatus;
    }
    public void setTextStatus(SVNStatusType textStatus) {
        this.textStatus = textStatus;
    }
    public void setPropertiesStatus(SVNStatusType propertiesStatus) {
        this.propertiesStatus = propertiesStatus;
    }
    public void setWcLocked(boolean wcLocked) {
        this.wcLocked = wcLocked;
    }
    public void setCopied(boolean copied) {
        this.copied = copied;
    }
    public void setRepositoryRootUrl(SVNURL repositoryRootUrl) {
        this.repositoryRootUrl = repositoryRootUrl;
    }
    public void setRepositoryUuid(String repositoryUuid) {
        this.repositoryUuid = repositoryUuid;
    }
    public void setRepositoryRelativePath(String repositoryRelativePath) {
        this.repositoryRelativePath = repositoryRelativePath;
    }
    public void setRevision(long revision) {
        this.revision = revision;
    }
    public void setChangedRevision(long changedRevision) {
        this.changedRevision = changedRevision;
    }
    public void setChangedDate(SVNDate changedDate) {
        this.changedDate = changedDate;
    }
    public void setChangedAuthor(String changedAuthor) {
        this.changedAuthor = changedAuthor;
    }
    public void setSwitched(boolean switched) {
        this.switched = switched;
    }
    public void setFileExternal(boolean fileExternal) {
        this.fileExternal = fileExternal;
    }
    public void setLock(SVNLock lock) {
        this.lock = lock;
    }
    public void setDepth(SVNDepth depth) {
        this.depth = depth;
    }
    public void setRepositoryKind(SVNNodeKind repositoryKind) {
        this.repositoryKind = repositoryKind;
    }
    public void setRepositoryNodeStatus(SVNStatusType repositoryNodeStatus) {
        this.repositoryNodeStatus = repositoryNodeStatus;
    }
    public void setRepositoryTextStatus(SVNStatusType repositoryTextStatus) {
        this.repositoryTextStatus = repositoryTextStatus;
    }
    public void setRepositoryPropertiesStatus(SVNStatusType repositoryPropertiesStatus) {
        this.repositoryPropertiesStatus = repositoryPropertiesStatus;
    }
    public void setRepositoryLock(SVNLock repositoryLock) {
        this.repositoryLock = repositoryLock;
    }
    public void setRepositoryChangedRevision(long repositoryChangedRevision) {
        this.repositoryChangedRevision = repositoryChangedRevision;
    }
    public void setRepositoryChangedDate(SVNDate repositoryChangedDate) {
        this.repositoryChangedDate = repositoryChangedDate;
    }
    public void setRepositoryChangedAuthor(String repositoryChangedAuthor) {
        this.repositoryChangedAuthor = repositoryChangedAuthor;
    }
    public String getChangelist() {
        return changelist;
    }
    public void setChangelist(String changelist) {
        this.changelist = changelist;
    }
}
