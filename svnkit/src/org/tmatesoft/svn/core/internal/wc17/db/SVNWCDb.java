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
package org.tmatesoft.svn.core.internal.wc17.db;

import java.io.File;
import java.io.InputStream;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNProperties;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.util.SVNSkel;
import org.tmatesoft.svn.core.internal.wc.SVNChecksum;
import org.tmatesoft.svn.core.internal.wc.SVNConfigFile;
import org.tmatesoft.svn.core.internal.wc17.db.ISVNWCDb.WCDbAdditionInfo.AdditionInfoField;
import org.tmatesoft.svn.core.internal.wc17.db.ISVNWCDb.WCDbBaseInfo.BaseInfoField;
import org.tmatesoft.svn.core.internal.wc17.db.ISVNWCDb.WCDbDeletionInfo.DeletionInfoField;
import org.tmatesoft.svn.core.internal.wc17.db.ISVNWCDb.WCDbInfo.InfoField;
import org.tmatesoft.svn.core.internal.wc17.db.ISVNWCDb.WCDbRepositoryInfo.RepositoryInfoField;
import org.tmatesoft.svn.core.wc.ISVNOptions;
import org.tmatesoft.svn.core.wc.SVNTreeConflictDescription;

/**
 * @version 1.3
 * @author TMate Software Ltd.
 */
public class SVNWCDb implements ISVNWCDb {

    public void open(WCDbOpenMode mode, ISVNOptions config, boolean autoUpgrade, boolean enforceEmptyWQ) throws SVNException {
    }

    public void addBaseAbsentNode(File localAbsPath, File reposRelPath, SVNURL reposRootUrl, String reposUuid, long revision, WCDbKind kind, WCDbStatus status, SVNSkel conflict, SVNSkel workItems)
            throws SVNException {
    }

    public void addBaseDirectory(File localAbsPath, File reposRelPath, SVNURL reposRootUrl, String reposUuid, long revision, SVNProperties props, long changedRev, Date changedDate,
            String changedAuthor, List<File> children, SVNDepth depth, SVNSkel conflict, SVNSkel workItems) throws SVNException {
    }

    public void addBaseFile(File localAbspath, File reposRelpath, SVNURL reposRootUrl, String reposUuid, long revision, SVNProperties props, long changedRev, Date changedDate, String changedAuthor,
            SVNChecksum checksum, long translatedSize, SVNSkel conflict, SVNSkel workItems) throws SVNException {
    }

    public void addBaseSymlink(File localAbsPath, File reposRelPath, SVNURL reposRootUrl, String reposUuid, long revision, SVNProperties props, long changedRev, Date changedDate,
            String changedAuthor, File target, SVNSkel conflict, SVNSkel workItem) throws SVNException {
    }

    public void addLock(File localAbsPath, WCDbLock lock) throws SVNException {
    }

    public void addWorkQueue(File wcRootAbsPath, SVNSkel workItem) throws SVNException {
    }

    public boolean checkPristine(File wcRootAbsPath, SVNChecksum sha1Checksum, WCDbCheckMode mode) throws SVNException {
        return false;
    }

    public void close() throws SVNException {
    }

    public void completedWorkQueue(File wcRootAbsPath, long id) throws SVNException {
    }

    public long ensureRepository(File localAbsPath, SVNURL reposRootUrl, String reposUuid) throws SVNException {
        return 0;
    }

    public WCDbWorkQueueInfo fetchWorkQueue(File wcRootAbsPath) throws SVNException {
        return null;
    }

    public File fromRelPath(File wcRootAbsPath, File localRelPath) throws SVNException {
        return null;
    }

    public List<String> getBaseChildren(File localAbsPath) throws SVNException {
        return null;
    }

    public SVNProperties getBaseDavCache(File localAbsPath) throws SVNException {
        return null;
    }

    public WCDbBaseInfo getBaseInfo(File localAbsPath, BaseInfoField... fields) throws SVNException {
        return null;
    }

    public String getBaseProp(File localAbsPath, String propName) throws SVNException {
        return null;
    }

    public Map<String, String> getBaseProps(File localAbsPath) throws SVNException {
        return null;
    }

    public int getFormatTemp(File localDirAbsPath) throws SVNException {
        return 0;
    }

    public SVNChecksum getPristineMD5(File wcRootAbsPath, SVNChecksum sha1Checksum) throws SVNException {
        return null;
    }

    public File getPristinePath(File wcRootAbsPath, SVNChecksum sha1Checksum) throws SVNException {
        return null;
    }

    public SVNChecksum getPristineSHA1(File wcRootAbsPath, SVNChecksum md5Checksum) throws SVNException {
        return null;
    }

    public File getPristineTempDir(File wcRootAbsPath) throws SVNException {
        return null;
    }

    public void globalCommit(File localAbspath, long newRevision, Date newDate, String newAuthor, SVNChecksum newChecksum, List<File> newChildren, SVNProperties newDavCache, boolean keepChangelist,
            SVNSkel workItems) throws SVNException {
    }

    public void globalRecordFileinfo(File localAbspath, long translatedSize, Date lastModTime) throws SVNException {
    }

    public void globalRelocate(File localDirAbspath, SVNURL reposRootUrl, boolean singleDb) throws SVNException {
    }

    public void globalUpdate(File localAbsPath, WCDbKind newKind, File newReposRelpath, long newRevision, SVNProperties newProps, long newChangedRev, Date newChangedDate, String newChangedAuthor,
            List<File> newChildren, SVNChecksum newChecksum, File newTarget, SVNProperties newDavCache, SVNSkel conflict, SVNSkel workItems) throws SVNException {
    }

    public void init(File localAbsPath, File reposRelPath, SVNURL reposRootUrl, String reposUuid, long initialRev, SVNDepth depth) throws SVNException {
    }

    public void installPristine(File tempfileAbspath, SVNChecksum sha1Checksum, SVNChecksum md5Checksum) throws SVNException {
    }

    public boolean isNodeHidden(File localAbspath) throws SVNException {
        return false;
    }

    public boolean isWCLocked(File localAbspath) throws SVNException {
        return false;
    }

    public void opAddDirectory(File localAbsPath, SVNSkel workItems) throws SVNException {
    }

    public void opAddFile(File localAbsPath, SVNSkel workItems) throws SVNException {
    }

    public void opAddSymlink(File localAbsPath, File target, SVNSkel workItems) throws SVNException {
    }

    public void opCopy(File srcAbsPath, File dstAbspath, SVNSkel workItems) throws SVNException {
    }

    public void opCopyDir(File localAbsPath, SVNProperties props, long changedRev, Date changedDate, String changedAuthor, File originalReposRelPath, SVNURL originalRootUrl, String originalUuid,
            long originalRevision, List<File> children, SVNDepth depth, SVNSkel conflict, SVNSkel workItems) throws SVNException {
    }

    public void opCopyFile(File localAbsPath, SVNProperties props, long changedRev, Date changedDate, String changedAuthor, File originalReposRelPath, SVNURL originalRootUrl, String originalUuid,
            long originalRevision, SVNChecksum checksum, SVNSkel conflict, SVNSkel workItems) throws SVNException {
    }

    public void opCopySymlink(File localAbsPath, SVNProperties props, long changedRev, Date changedDate, String changedAuthor, File originalReposRelPath, SVNURL originalRootUrl, String originalUuid,
            long originalRevision, File target, SVNSkel conflict, SVNSkel workItems) throws SVNException {
    }

    public void opDelete(File localAbsPath) throws SVNException {
    }

    public void opMarkConflict(File localAbsPath) throws SVNException {
    }

    public void opMarkResolved(File localAbspath, boolean resolvedText, boolean resolvedProps, boolean resolvedTree) throws SVNException {
    }

    public void opModified(File localAbsPath) throws SVNException {
    }

    public void opMove(File srcAbsPath, File dstAbsPath) throws SVNException {
    }

    public Map<File, SVNTreeConflictDescription> opReadAllTreeConflicts(File localAbsPath) throws SVNException {
        return null;
    }

    public SVNTreeConflictDescription opReadTreeConflict(File localAbspath) throws SVNException {
        return null;
    }

    public void opRevert(File localAbspath, SVNDepth depth) throws SVNException {
    }

    public void opSetChangelist(File localAbsPath, String changelist) throws SVNException {
    }

    public void opSetProps(File localAbsPath, SVNProperties props, SVNSkel conflict, SVNSkel workItems) throws SVNException {
    }

    public void opSetTreeConflict(File localAbspath, SVNTreeConflictDescription treeConflict) throws SVNException {
    }

    public void open(WCDbOpenMode mode, SVNConfigFile config, boolean autoUpgrade, boolean enforceEmptyWQ) throws SVNException {
    }

    public List<File> readChildren(File localAbspath) throws SVNException {
        return null;
    }

    public List<File> readConflictVictims(File localAbspath) throws SVNException {
        return null;
    }

    public List<SVNTreeConflictDescription> readConflicts(File localAbspath) throws SVNException {
        return null;
    }

    public WCDbInfo readInfo(File localAbsPath, InfoField... fields) throws SVNException {
        return null;
    }

    public WCDbKind readKind(File localAbspath, boolean allowMissing) throws SVNException {
        return null;
    }

    public InputStream readPristine(File wcRootAbsPath, SVNChecksum sha1Checksum) throws SVNException {
        return null;
    }

    public SVNProperties readPristineProperties(File localAbspath) throws SVNException {
        return null;
    }

    public String readProperty(File localAbsPath, String propname) throws SVNException {
        return null;
    }

    public SVNProperties readProperties(File localAbsPath) throws SVNException {
        return null;
    }

    public void removeBase(File localAbsPath) throws SVNException {
    }

    public void removeLock(File localAbsPath) throws SVNException {
    }

    public void removePristine(File wcRootAbsPath, SVNChecksum sha1Checksum) throws SVNException {
    }

    public void removeWCLock(File localAbspath) throws SVNException {
    }

    public void repairPristine(File wcRootAbsPath, SVNChecksum sha1Checksum) throws SVNException {
    }

    public WCDbAdditionInfo scanAddition(File localAbsPath, AdditionInfoField... fields) throws SVNException {
        return null;
    }

    public WCDbRepositoryInfo scanBaseRepository(File localAbsPath, RepositoryInfoField... fields) throws SVNException {
        return null;
    }

    public WCDbDeletionInfo scanDeletion(File localAbsPath, DeletionInfoField... fields) throws SVNException {
        return null;
    }

    public void setBaseDavCache(File localAbsPath, SVNProperties props) throws SVNException {
    }

    public void setBasePropsTemp(File localAbsPath, SVNProperties props) throws SVNException {
    }

    public void setWCLock(File localAbspath, int levelsToLock) throws SVNException {
    }

    public void setWorkingPropsTemp(File localAbsPath, SVNProperties props) throws SVNException {
    }

    public File toRelPath(File localAbsPath) throws SVNException {
        return null;
    }

    public ISVNOptions getConfig(){
        return null;
    }

    public String getFileExternalTemp(File path) throws SVNException {
        return null;
    }

    public boolean determineKeepLocalTemp(File localAbsPath) throws SVNException {
        return false;
    }

    public WCDbDirDeletedInfo isDirDeletedTem(File entryAbspath) throws SVNException {
        return null;
    }

}
