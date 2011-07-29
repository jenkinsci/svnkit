package org.tmatesoft.svn.core.wc2;

import org.tmatesoft.svn.core.SVNLock;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.util.SVNDate;

public class SvnInfo extends SvnObject {
    
    private SVNURL url;
    private long revision;
    private SVNURL repositoryRootURL;
    private String repositoryUuid;
    
    private SVNNodeKind kind;
    private long size;
    
    private long lastChangedRevision;
    private SVNDate lastChangedDate;
    private String lastChangedAuthor;
    
    private SVNLock lock;
    
    private SvnWorkingCopyInfo wcInfo;

    public SVNURL getUrl() {
        return url;
    }

    public long getRevision() {
        return revision;
    }

    public SVNURL getRepositoryRootUrl() {
        return repositoryRootURL;
    }

    public String getRepositoryUuid() {
        return repositoryUuid;
    }

    public SVNNodeKind getKind() {
        return kind;
    }

    public long getSize() {
        return size;
    }

    public long getLastChangedRevision() {
        return lastChangedRevision;
    }

    public SVNDate getLastChangedDate() {
        return lastChangedDate;
    }

    public String getLastChangedAuthor() {
        return lastChangedAuthor;
    }

    public SVNLock getLock() {
        return lock;
    }

    public SvnWorkingCopyInfo getWcInfo() {
        return wcInfo;
    }

    public void setUrl(SVNURL url) {
        this.url = url;
    }

    public void setRevision(long revision) {
        this.revision = revision;
    }

    public void setRepositoryRootURL(SVNURL repositoryRootURL) {
        this.repositoryRootURL = repositoryRootURL;
    }

    public void setRepositoryUuid(String repositoryUUID) {
        this.repositoryUuid = repositoryUUID;
    }

    public void setKind(SVNNodeKind kind) {
        this.kind = kind;
    }

    public void setSize(long size) {
        this.size = size;
    }

    public void setLastChangedRevision(long lastChangedRevision) {
        this.lastChangedRevision = lastChangedRevision;
    }

    public void setLastChangedDate(SVNDate lastChangedDate) {
        this.lastChangedDate = lastChangedDate;
    }

    public void setLastChangedAuthor(String lastChangedAuthor) {
        this.lastChangedAuthor = lastChangedAuthor;
    }

    public void setLock(SVNLock lock) {
        this.lock = lock;
    }

    public void setWcInfo(SvnWorkingCopyInfo wcInfo) {
        this.wcInfo = wcInfo;
    }
}
