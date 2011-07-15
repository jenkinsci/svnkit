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
import java.util.Date;

import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNLock;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.internal.wc17.db.ISVNWCDb;
import org.tmatesoft.svn.core.internal.wc17.db.ISVNWCDb.SVNWCDbStatus;
import org.tmatesoft.svn.core.internal.wc17.db.ISVNWCDb.WCDbBaseInfo.BaseInfoField;
import org.tmatesoft.svn.core.internal.wc17.db.ISVNWCDb.WCDbInfo.InfoField;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNStatus;
import org.tmatesoft.svn.core.wc.SVNStatusType;
import org.tmatesoft.svn.core.wc.SVNTreeConflictDescription;

/**
 * Structure for holding the "status" of a working copy item.
 *
 * The item's entry data is in entry, augmented and possibly shadowed by the
 * other fields. entry is NULL if this item is not under version control.
 *
 * Fields may be added to the end of this structure in future versions.
 * Therefore, to preserve binary compatibility, users should not directly
 * allocate structures of this type.
 *
 * @since New in SVN1.7.
 * @author TMate Software Ltd.
 */
public class SVNStatus17 {

    public static class ConflictInfo {
        public boolean textConflicted;
        public boolean propConflicted;
        public boolean treeConflicted;
        public File baseFile;
        public File repositoryFile;
        public File localFile;
        public File propRejectFile;
        public SVNTreeConflictDescription treeConflict;
    }

    private final SVNWCContext context;

    private File localAbsPath;

    /** The kind of node as recorded in the working copy */
    private SVNNodeKind kind;

    /**
     * The depth of the node as recorded in the working copy (#svn_depth_unknown
     * for files or when no depth is set)
     */
    private SVNDepth depth;

    /**
     * If the path is under version control, versioned is TRUE, otherwise FALSE.
     */
    private boolean versioned;

    /** Set to TRUE if the item is the victim of a conflict. */
    private boolean conflicted;

    /**
     * The status of the node itself. In order of precendence: Tree conflicts,
     * structural changes, text changes (including text conflicts).
     */
    private SVNStatusType nodeStatus;

    /** The status of the entry itself, including its text if it is a file. */
    private SVNStatusType textStatus;

    /** The status of the entry's properties. */
    private SVNStatusType propStatus;

    /**
     * a file or directory can be 'copied' if it's scheduled for
     * addition-with-history (or part of a subtree that is scheduled as such.).
     */
    private boolean copied;

    /** Base revision. */
    private long revision;

    /** Last revision this was changed */
    private long changedRev;

    /** Date of last commit. */
    private Date changedDate;

    /** Last commit author of this item */
    private String changedAuthor;

    /** The URL of the repository */
    private SVNURL reposRootUrl;

    /**
     * The in-repository path relative to the repository root. Use
     * svn_path_url_component2() to join this value to the repos_root_url to get
     * the full URL.
     */
    private File reposRelpath;

    /**
     * a file or directory can be 'switched' if the switch command has been
     * used. If this is TRUE, then file_external will be FALSE.
     */
    private boolean switched;

    /**
     * The locally present lock. (Values of path, token, owner, comment and are
     * available if a lock is present)
     */
    private SVNLock lock;

    /** Which changelist this item is part of, or NULL if not part of any. */
    private String changelist;

    /**
     * WC out-of-date info from the repository
     *
     * When the working copy item is out-of-date compared to the repository, the
     * following fields represent the state of the youngest revision of the item
     * in the repository. If the working copy is not out of date, the fields are
     * initialized as described below.
     */

    /**
     * Set to the node kind of the youngest commit, or #svn_node_none if not out
     * of date.
     */
    private SVNNodeKind oodKind;

    /**
     * The status of the node, based on the text status if the node has no
     * restructuring changes
     */
    private SVNStatusType reposNodeStatus;

    /** The entry's text status in the repository. */
    private SVNStatusType reposTextStatus;

    /** The entry's property status in the repository. */
    private SVNStatusType reposPropStatus;

    /** The entry's lock in the repository, if any. */
    private SVNLock reposLock;

    /**
     * Set to the youngest committed revision, or #SVN_INVALID_REVNUM if not out
     * of date.
     */
    private long oodChangedRev;

    /** Set to the most recent commit date, or @c 0 if not out of date. */
    private Date oodChangedDate;

    /**
     * Set to the user name of the youngest commit, or @c NULL if not out of
     * date or non-existent. Because a non-existent @c svn:author property has
     * the same behavior as an out-of-date working copy, examine @c
     * ood_last_cmt_rev to determine whether the working copy is out of date.
     */
    private String oodChangedAuthor;

    private String reposUuid;
    
    private boolean locked;


    public SVNStatus17(SVNWCContext context) {
        this.context = context;
    }

    public void setLocalAbsPath(File localAbsPath) {
        this.localAbsPath = localAbsPath;
    }

    public File getLocalAbsPath() {
        return localAbsPath;
    }

    public SVNNodeKind getKind() {
        return kind;
    }

    public void setKind(SVNNodeKind kind) {
        this.kind = kind;
    }

    public SVNDepth getDepth() {
        return depth;
    }

    public void setDepth(SVNDepth depth) {
        this.depth = depth;
    }

    public boolean isVersioned() {
        return versioned;
    }

    public void setVersioned(boolean versioned) {
        this.versioned = versioned;
    }

    public boolean isConflicted() {
        return conflicted;
    }

    public void setConflicted(boolean conflicted) {
        this.conflicted = conflicted;
    }

    public SVNStatusType getNodeStatus() {
        return nodeStatus;
    }

    public void setNodeStatus(SVNStatusType nodeStatus) {
        this.nodeStatus = nodeStatus;
    }

    public SVNStatusType getTextStatus() {
        return textStatus;
    }

    public void setTextStatus(SVNStatusType textStatus) {
        this.textStatus = textStatus;
    }

    public SVNStatusType getPropStatus() {
        return propStatus;
    }

    public void setPropStatus(SVNStatusType propStatus) {
        this.propStatus = propStatus;
    }

    public boolean isCopied() {
        return copied;
    }

    public void setCopied(boolean copied) {
        this.copied = copied;
    }

    public long getRevision() {
        return revision;
    }

    public void setRevision(long revision) {
        this.revision = revision;
    }

    public long getChangedRev() {
        return changedRev;
    }

    public void setChangedRev(long changedRev) {
        this.changedRev = changedRev;
    }

    public Date getChangedDate() {
        return changedDate;
    }

    public void setChangedDate(Date changedDate) {
        this.changedDate = changedDate;
    }

    public String getChangedAuthor() {
        return changedAuthor;
    }

    public void setChangedAuthor(String changedAuthor) {
        this.changedAuthor = changedAuthor;
    }

    public SVNURL getReposRootUrl() {
        return reposRootUrl;
    }

    public void setReposRootUrl(SVNURL reposRootUrl) {
        this.reposRootUrl = reposRootUrl;
    }

    public File getReposRelpath() {
        return reposRelpath;
    }

    public void setReposRelpath(File reposRelpath) {
        this.reposRelpath = reposRelpath;
    }

    public boolean isSwitched() {
        return switched;
    }

    public void setSwitched(boolean switched) {
        this.switched = switched;
    }

    public SVNLock getLock() {
        return lock;
    }

    public void setLock(SVNLock lock) {
        this.lock = lock;
    }

    public String getChangelist() {
        return changelist;
    }

    public void setChangelist(String changelist) {
        this.changelist = changelist;
    }

    public SVNNodeKind getOodKind() {
        return oodKind;
    }

    public void setOodKind(SVNNodeKind oodKind) {
        this.oodKind = oodKind;
    }

    public SVNStatusType getReposNodeStatus() {
        return reposNodeStatus;
    }

    public void setReposNodeStatus(SVNStatusType reposNodeStatus) {
        this.reposNodeStatus = reposNodeStatus;
    }

    public SVNStatusType getReposTextStatus() {
        return reposTextStatus;
    }

    public void setReposTextStatus(SVNStatusType reposTextStatus) {
        this.reposTextStatus = reposTextStatus;
    }

    public SVNStatusType getReposPropStatus() {
        return reposPropStatus;
    }

    public void setReposPropStatus(SVNStatusType reposPropStatus) {
        this.reposPropStatus = reposPropStatus;
    }

    public SVNLock getReposLock() {
        return reposLock;
    }

    public void setReposLock(SVNLock reposLock) {
        this.reposLock = reposLock;
    }

    public long getOodChangedRev() {
        return oodChangedRev;
    }

    public void setOodChangedRev(long oodChangedRev) {
        this.oodChangedRev = oodChangedRev;
    }

    public Date getOodChangedDate() {
        return oodChangedDate;
    }

    public void setOodChangedDate(Date oodChangedDate) {
        this.oodChangedDate = oodChangedDate;
    }

    public String getOodChangedAuthor() {
        return oodChangedAuthor;
    }
    
    public void setLocked(boolean locked) {
        this.locked = locked;
    }

    public void setOodChangedAuthor(String oodChangedAuthor) {
        this.oodChangedAuthor = oodChangedAuthor;
    }

    public void setReposUUID(String uuid) {
        this.reposUuid = uuid;
    }
    
    public String getReposUUID() {
        return this.reposUuid;
    }

    public SVNStatus getStatus16() throws SVNException {
        SVNStatus status = new SVNStatus();
        status.setFile(localAbsPath);
        status.setKind(kind);
        // TODO filesize
        status.setIsVersioned(versioned);
        status.setIsConflicted(conflicted);
        
        status.setNodeStatus(nodeStatus);
        status.setContentsStatus(textStatus);
        status.setPropertiesStatus(propStatus);

        if (kind == SVNNodeKind.DIR) {
            status.setIsLocked(locked);
        }
        status.setIsCopied(copied);
        status.setRevision(SVNRevision.create(revision));
        
        status.setCommittedRevision(SVNRevision.create(changedRev));
        status.setAuthor(changedAuthor);
        status.setCommittedDate(changedDate);
        
        status.setRepositoryRootURL(reposRootUrl);
        status.setRepositoryRelativePath(SVNFileUtil.getFilePath(reposRelpath));
        status.setRepositoryUUID(reposUuid);
        
        status.setIsSwitched(switched);
        if (versioned && switched && kind == SVNNodeKind.FILE) {
           // TODO fileExternal
        }
        status.setLocalLock(lock);
        status.setChangelistName(changelist);
        status.setDepth(depth);
        
        status.setRemoteKind(oodKind);
        status.setRemoteNodeStatus(reposNodeStatus);
        status.setRemoteContentsStatus(reposTextStatus);
        status.setRemotePropertiesStatus(reposPropStatus);
        status.setRemoteLock(reposLock);
        
        status.setRemoteAuthor(oodChangedAuthor);
        status.setRemoteRevision(SVNRevision.create(oodChangedRev));
        status.setRemoteDate(oodChangedDate);
        
        // do all that on demand in SVNStatus class later.
        // compose URL on demand in SVNStatus
        
        if (versioned && conflicted) {
            ConflictInfo info = context.getConflicted(localAbsPath, true, true, true);
            if (info.textConflicted) {
                status.setContentsStatus(SVNStatusType.STATUS_CONFLICTED);
            }
            if (info.propConflicted) {
                status.setPropertiesStatus(SVNStatusType.STATUS_CONFLICTED);
            }
            if (info.textConflicted || info.propConflicted) {
                status.setNodeStatus(SVNStatusType.STATUS_CONFLICTED);
            }
        }
        
        if (reposRootUrl != null && reposRelpath != null) {
            // TODO ?
            status.setURL(SVNWCUtils.join(reposRootUrl, reposRelpath));
            status.setRemoteURL(SVNWCUtils.join(reposRootUrl, reposRelpath));
        }
        
        // fetch missing info on revisions for some statuses.
        if (context != null && versioned && revision < 0 && !copied) {
            
            if (nodeStatus == SVNStatusType.STATUS_REPLACED) {
                fetchStatusRevision(status);
            } else if (nodeStatus == SVNStatusType.STATUS_DELETED ) {
                fetchStatusRevision(status);
            }
        }
        
        // fetch conflict info and tree conflict description.
        if (conflicted && context != null) {
            boolean hasTreeConflict = false;
            ConflictInfo conflictedInfo = null;
            if (versioned) {
                if (status.isVersioned()) {
                    try {
                        conflictedInfo = context.getConflicted(localAbsPath, true, true, true);
                    } catch (SVNException e) {
                        if (e.getErrorMessage().getErrorCode() == SVNErrorCode.WC_UPGRADE_REQUIRED) {
                        } else {
                            throw e;
                        }
                    }
                    hasTreeConflict = conflictedInfo != null && conflictedInfo.treeConflicted;
                } else {
                    hasTreeConflict = true;
                }                
            }
            if (hasTreeConflict) {
                SVNTreeConflictDescription treeConflictDescription = context.getTreeConflict(localAbsPath);
                status.setTreeConflict(treeConflictDescription);
            }
            
            if (conflictedInfo != null) {
                status.setConflictWrkFile(conflictedInfo.localFile);
                status.setConflictOldFile(conflictedInfo.baseFile);
                status.setConflictNewFile(conflictedInfo.repositoryFile);                    
                status.setPropRejectFile(conflictedInfo.propRejectFile);                    
            }
        }
        
        status.setWorkingCopyFormat(ISVNWCDb.WC_FORMAT_17);
        
        return status;
    }
    
    private void fetchStatusRevision(SVNStatus status) throws SVNException {        
        ISVNWCDb.WCDbInfo info = context.getDb().readInfo(localAbsPath, InfoField.revision, InfoField.changedAuthor, InfoField.changedDate, InfoField.changedRev,
                InfoField.haveBase, InfoField.haveWork, InfoField.haveMoreWork, InfoField.status);
        if (nodeStatus == SVNStatusType.STATUS_DELETED) {
            status.setAuthor(info.changedAuthor);
            status.setCommittedDate(info.changedDate);
            status.setCommittedRevision(SVNRevision.create(info.changedRev));
        }
        status.setRevision(SVNRevision.create(info.revision));
        
        if (info.haveWork || info.revision < 0 || (nodeStatus == SVNStatusType.STATUS_DELETED && info.changedRev < 0) || 
                (info.status != SVNWCDbStatus.Added && info.status != SVNWCDbStatus.Deleted)) {
            ISVNWCDb.WCDbBaseInfo binfo = context.getDb().getBaseInfo(localAbsPath, BaseInfoField.revision, BaseInfoField.changedRev, BaseInfoField.changedAuthor, BaseInfoField.changedDate);
            if (nodeStatus == SVNStatusType.STATUS_DELETED) {
                status.setAuthor(binfo.changedAuthor);
                status.setCommittedDate(binfo.changedDate);
                status.setCommittedRevision(SVNRevision.create(binfo.changedRev));
            }
            status.setRevision(SVNRevision.create(binfo.revision));
        }
    }
        

    
}
