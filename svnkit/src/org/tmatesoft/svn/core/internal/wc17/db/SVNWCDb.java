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
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNProperties;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.internal.util.SVNSkel;
import org.tmatesoft.svn.core.internal.wc.SVNChecksum;
import org.tmatesoft.svn.core.internal.wc.SVNConfigFile;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.internal.wc17.db.ISVNWCDb.WCDbOpenMode;
import org.tmatesoft.svn.core.internal.wc17.db.ISVNWCDb.WCDbStatus;
import org.tmatesoft.svn.core.internal.wc17.db.ISVNWCDb.WCDbAdditionInfo.AdditionInfoField;
import org.tmatesoft.svn.core.internal.wc17.db.ISVNWCDb.WCDbBaseInfo.BaseInfoField;
import org.tmatesoft.svn.core.internal.wc17.db.ISVNWCDb.WCDbDeletionInfo.DeletionInfoField;
import org.tmatesoft.svn.core.internal.wc17.db.ISVNWCDb.WCDbInfo.InfoField;
import org.tmatesoft.svn.core.internal.wc17.db.ISVNWCDb.WCDbRepositoryInfo.RepositoryInfoField;
import org.tmatesoft.svn.core.wc.ISVNOptions;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNTreeConflictDescription;

/**
 * @version 1.3
 * @author TMate Software Ltd.
 */
public class SVNWCDb implements ISVNWCDb {

    private static final EnumMap<WCDbKind, String> kindMap = new EnumMap<WCDbKind, String>(WCDbKind.class);
    static {
        kindMap.put(WCDbKind.File, "file");
        kindMap.put(WCDbKind.Dir, "dir");
        kindMap.put(WCDbKind.Symlink, "symlink");
        kindMap.put(WCDbKind.Subdir, "subdir");
        kindMap.put(WCDbKind.Unknown, "unknown");
    };

    /*
     * Note: we only decode presence values from the database. These are a
     * subset of all the status values.
     */
    private static final EnumMap<WCDbStatus, String> presenceMap = new EnumMap<WCDbStatus, String>(WCDbStatus.class);
    static {
        presenceMap.put(WCDbStatus.Normal, "normal");
        presenceMap.put(WCDbStatus.Absent, "absent");
        presenceMap.put(WCDbStatus.Excluded, "excluded");
        presenceMap.put(WCDbStatus.NotPresent, "not-present");
        presenceMap.put(WCDbStatus.Incomplete, "incomplete");
        presenceMap.put(WCDbStatus.BaseDeleted, "base-deleted");
    };

    private static String depthToWord(SVNDepth depth) {
        if (depth == SVNDepth.EXCLUDE) {
            return "exclude";
        } else if (depth == SVNDepth.UNKNOWN) {
            return "unknown";
        } else if (depth == SVNDepth.EMPTY) {
            return "empty";
        } else if (depth == SVNDepth.FILES) {
            return "files";
        } else if (depth == SVNDepth.IMMEDIATES) {
            return "immediates";
        } else if (depth == SVNDepth.INFINITY) {
            return "infinity";
        } else {
            return "INVALID-DEPTH";
        }
    }

    private WCDbOpenMode mode;
    private ISVNOptions config;
    private boolean autoUpgrade;
    private boolean enforceEmptyWQ;
    private HashMap<File, SVNWCDbDir> dirData;

    public void open(final WCDbOpenMode mode, final ISVNOptions config, final boolean autoUpgrade, final boolean enforceEmptyWQ) throws SVNException {
        this.mode = mode;
        this.config = config;
        this.autoUpgrade = autoUpgrade;
        this.enforceEmptyWQ = enforceEmptyWQ;
        this.dirData = new HashMap<File, SVNWCDbDir>();
    }

    public void close() throws SVNException {
        final Set<SVNWCDbRoot> roots = new HashSet<SVNWCDbRoot>();
        /* Collect all the unique WCROOT structures, and empty out DIR_DATA. */
        for (Map.Entry<File, SVNWCDbDir> entry : dirData.entrySet()) {
            final File key = entry.getKey();
            final SVNWCDbDir pdh = entry.getValue();
            if (pdh.getWCRoot() != null && pdh.getWCRoot().getSDb() != null) {
                roots.add(pdh.getWCRoot());
            }
            dirData.remove(key);
        }
        /* Run the cleanup for each WCROOT. */
        closeManyWCRoots(roots);
    }

    private void closeManyWCRoots(final Set<SVNWCDbRoot> roots) {
        for (final SVNWCDbRoot wcRoot : roots) {
            wcRoot.close();
        }
    }

    public ISVNOptions getConfig() {
        return config;
    }

    public void init(File localAbsPath, File reposRelPath, SVNURL reposRootUrl, String reposUuid, long initialRev, SVNDepth depth) throws SVNException {

        assert (SVNFileUtil.isAbsolute(localAbsPath));
        assert (reposRelPath != null);
        assert (depth == SVNDepth.EMPTY || depth == SVNDepth.FILES || depth == SVNDepth.IMMEDIATES || depth == SVNDepth.INFINITY);

        /* ### REPOS_ROOT_URL and REPOS_UUID may be NULL. ... more doc: tbd */

        /* Create the SDB and insert the basic rows. */
        CreateDbInfo createDb = createDb(localAbsPath, reposRootUrl, reposUuid, SDB_FILE);

        /* Begin construction of the PDH. */
        SVNWCDbDir pdh = new SVNWCDbDir();
        pdh.setLocalAbsPath(localAbsPath);

        /* Create the WCROOT for this directory. */
        pdh.setWCRoot(new SVNWCDbRoot(localAbsPath, createDb.sDb, createDb.wcId, false, false));

        /* The PDH is complete. Stash it into DB. */
        dirData.put(localAbsPath, pdh);

        InsertBaseInfo ibb = new InsertBaseInfo();

        if (initialRev > 0)
            ibb.status = WCDbStatus.Incomplete;
        else
            ibb.status = WCDbStatus.Normal;
        ibb.kind = WCDbKind.Dir;
        ibb.wcId = createDb.wcId;
        ibb.localRelPath = new File("");
        ibb.reposId = createDb.reposId;
        ibb.reposRelPath = reposRelPath;
        ibb.revision = initialRev;

        /* ### what about the children? */
        ibb.children = null;
        ibb.depth = depth;

        /* ### no children, conflicts, or work items to install in a txn... */

        insertBaseNode(ibb, createDb.sDb);
    }

    private static class CreateDbInfo {

        public SVNSqlJetDb sDb;
        public long reposId;
        public long wcId;
    }

    private CreateDbInfo createDb(File dirAbsPath, SVNURL reposRootUrl, String reposUuid, String sdbFileName) {

        CreateDbInfo info = new CreateDbInfo();

        info.sDb = SVNSqlJetDb.open(dirAbsPath, sdbFileName, SVNSqlJetDb.Mode.RWCreate);

        /* Create the database's schema. */
        info.sDb.execStatement(SVNWCDbStatements.CREATE_SCHEMA);

        /* Insert the repository. */
        info.reposId = createReposId(info.sDb, reposRootUrl, reposUuid);

        /* Insert the wcroot. */
        /* ### Right now, this just assumes wc metadata is being stored locally. */
        final SVNSqlJetStatement statement = info.sDb.getStatement(SVNWCDbStatements.INSERT_WCROOT);
        statement.insert(info.wcId);

        return info;
    }

    /**
     * For a given REPOS_ROOT_URL/REPOS_UUID pair, return the existing REPOS_ID
     * value. If one does not exist, then create a new one.
     */
    private long createReposId(SVNSqlJetDb sDb, SVNURL reposRootUrl, String reposUuid) {

        final SVNSqlJetStatement getStmt = sDb.getStatement(SVNWCDbStatements.SELECT_REPOSITORY);
        try {
            getStmt.bindf("s", reposRootUrl);
            boolean haveRow = getStmt.next();
            if (haveRow) {
                return getStmt.getLong(0);
            }
        } finally {
            getStmt.reset();
        }

        /*
         * NOTE: strictly speaking, there is a race condition between the above
         * query and the insertion below. We're simply going to ignore that, as
         * it means two processes are *modifying* the working copy at the same
         * time, *and* new repositores are becoming visible. This is rare
         * enough, let alone the miniscule chance of hitting this race
         * condition. Further, simply failing out will leave the database in a
         * consistent state, and the user can just re-run the failed operation.
         */

        final SVNSqlJetStatement insertStmt = sDb.getStatement(SVNWCDbStatements.INSERT_REPOSITORY);
        insertStmt.bindf("ss", reposRootUrl, reposUuid);
        return insertStmt.insert();
    }

    private static class InsertBaseInfo {

        /* common to all insertions into BASE */
        public WCDbStatus status;
        public WCDbKind kind;
        public long wcId;
        public File localRelPath;
        public long reposId;
        public File reposRelPath;
        public long revision;

        /* common to all "normal" presence insertions */
        public SVNProperties props;
        public long changedRev;
        public Date changedDate;
        public String changedAuthor;

        /* for inserting directories */
        public List<File> children;
        public SVNDepth depth;

        /* for inserting files */
        public SVNChecksum checksum;
        public long translatedSize;

        /* for inserting symlinks */
        public String target;

        /* may need to insert/update ACTUAL to record a conflict */
        public SVNSkel conflict;

        /* may have work items to queue in this transaction */
        public SVNSkel workItems;

        public InsertBaseInfo() {
            this.revision = INVALID_REVNUM;
            this.changedRev = INVALID_REVNUM;
            this.depth = SVNDepth.INFINITY;
            this.translatedSize = INVALID_FILESIZE;
        }

    };

    private void insertBaseNode(InsertBaseInfo ibb, SVNSqlJetDb sDb) throws SVNException {
        /* ### we can't handle this right now */
        assert (ibb.conflict == null);

        SVNSqlJetStatement stmt = sDb.getStatement(SVNWCDbStatements.INSERT_BASE_NODE);
        stmt.bindf("is", ibb.wcId, ibb.localRelPath);

        if (true /* maybe_bind_repos() */) {
            stmt.bindLong(3, ibb.reposId);
            stmt.bindString(4, ibb.reposRelPath.toString());
        }

        /*
         * The directory at the WCROOT has a NULL parent_relpath. Otherwise,
         * bind the appropriate parent_relpath.
         */
        if (ibb.localRelPath != null && !"".equals(ibb.localRelPath.toString()))
            stmt.bindString(5, SVNFileUtil.getParentFile(ibb.localRelPath).toString());

        stmt.bindString(6, presenceMap.get(ibb.status));
        stmt.bindString(7, kindMap.get(ibb.kind));
        stmt.bindLong(8, ibb.revision);
        stmt.bindProperties(9, ibb.props);

        if (SVNRevision.isValidRevisionNumber(ibb.changedRev))
            stmt.bindLong(10, ibb.changedRev);
        if (ibb.changedDate != null)
            stmt.bindLong(11, ibb.changedDate.getTime());
        if (ibb.changedAuthor != null)
            stmt.bindString(12, ibb.changedAuthor);

        if (ibb.kind == WCDbKind.Dir) {
            stmt.bindString(13, depthToWord(ibb.depth));
        } else if (ibb.kind == WCDbKind.File) {
            stmt.bindChecksumm(14, ibb.checksum);
            if (ibb.translatedSize != INVALID_FILESIZE)
                stmt.bindLong(15, ibb.translatedSize);
        } else if (ibb.kind == WCDbKind.Symlink) {
            /* Note: incomplete nodes may have a NULL target. */
            if (ibb.target != null)
                stmt.bindString(16, ibb.target);
        }

        stmt.insert();

        if (ibb.kind == WCDbKind.Dir && ibb.children != null) {

            stmt = sDb.getStatement(SVNWCDbStatements.INSERT_BASE_NODE_INCOMPLETE);

            for (File name : ibb.children) {
                stmt.bindf("issi", ibb.wcId, SVNPathUtil.append(ibb.localRelPath.toString(), name.toString()), ibb.localRelPath, ibb.revision);

                stmt.insert();
            }
        }

        addWorkItems(sDb, ibb.workItems);

    }

    private void addWorkItems(SVNSqlJetDb sDb, SVNSkel skel) throws SVNException {
        /* Maybe there are no work items to insert. */
        if (skel == null) {
            return;
        }

        /* Is the list a single work item? Or a list of work items? */
        if (skel.isAtom()) {
            addSingleWorkItem(sDb, skel);
        } else {
            /* SKEL is a list-of-lists, aka list of work items. */
            for (int i = 0; i < skel.getListSize(); i++) {
                addSingleWorkItem(sDb, skel.getChild(i));
            }
        }

    }

    private void addSingleWorkItem(SVNSqlJetDb sDb, SVNSkel workItem) throws SVNException {        
        final byte[] serialized = workItem.unparse();
        final SVNSqlJetStatement stmt = sDb.getStatement(SVNWCDbStatements.INSERT_WORK_ITEM);
        stmt.bindBlob(1, serialized);
        stmt.insert();
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
