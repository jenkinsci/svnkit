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
import java.util.ArrayList;
import java.util.Date;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.tmatesoft.sqljet.core.SqlJetException;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNProperties;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.db.SVNSqlJetDb;
import org.tmatesoft.svn.core.internal.db.SVNSqlJetStatement;
import org.tmatesoft.svn.core.internal.db.SVNSqlJetDb.Mode;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.internal.util.SVNSkel;
import org.tmatesoft.svn.core.internal.wc.SVNAdminUtil;
import org.tmatesoft.svn.core.internal.wc.SVNChecksum;
import org.tmatesoft.svn.core.internal.wc.SVNChecksumKind;
import org.tmatesoft.svn.core.internal.wc.SVNConfigFile;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.wc.SVNFileType;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.internal.wc.SVNTreeConflictUtil;
import org.tmatesoft.svn.core.internal.wc17.db.ISVNWCDb.WCDbAdditionInfo.AdditionInfoField;
import org.tmatesoft.svn.core.internal.wc17.db.ISVNWCDb.WCDbBaseInfo.BaseInfoField;
import org.tmatesoft.svn.core.internal.wc17.db.ISVNWCDb.WCDbDeletionInfo.DeletionInfoField;
import org.tmatesoft.svn.core.internal.wc17.db.ISVNWCDb.WCDbInfo.InfoField;
import org.tmatesoft.svn.core.internal.wc17.db.ISVNWCDb.WCDbRepositoryInfo.RepositoryInfoField;
import org.tmatesoft.svn.core.internal.wc17.db.statement.SVNWCDbSchema;
import org.tmatesoft.svn.core.internal.wc17.db.statement.SVNWCDbStatements;
import org.tmatesoft.svn.core.wc.ISVNOptions;
import org.tmatesoft.svn.core.wc.SVNConflictDescription;
import org.tmatesoft.svn.core.wc.SVNMergeFileSet;
import org.tmatesoft.svn.core.wc.SVNPropertyConflictDescription;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNTextConflictDescription;
import org.tmatesoft.svn.core.wc.SVNTreeConflictDescription;
import org.tmatesoft.svn.util.SVNLogType;

/**
 *
 * Note: svn-trunk's last seen revision is 960988.
 *
 * @author TMate Software Ltd.
 */
public class SVNWCDb implements ISVNWCDb {

    public static final int FORMAT_FROM_SDB = -1;
    public static final long UNKNOWN_WC_ID = -1;

    private static final EnumMap<SVNWCDbKind, String> kindMap = new EnumMap<SVNWCDbKind, String>(SVNWCDbKind.class);
    private static final HashMap<String, SVNWCDbKind> kindMap2 = new HashMap<String, SVNWCDbKind>();
    static {
        kindMap.put(SVNWCDbKind.File, "file");
        kindMap.put(SVNWCDbKind.Dir, "dir");
        kindMap.put(SVNWCDbKind.Symlink, "symlink");
        kindMap.put(SVNWCDbKind.Subdir, "subdir");
        kindMap.put(SVNWCDbKind.Unknown, "unknown");

        kindMap2.put("file", SVNWCDbKind.File);
        kindMap2.put("dir", SVNWCDbKind.Dir);
        kindMap2.put("symlink", SVNWCDbKind.Symlink);
        kindMap2.put("subdir", SVNWCDbKind.Subdir);
        kindMap2.put("unknown", SVNWCDbKind.Unknown);
    };

    /*
     * Note: we only decode presence values from the database. These are a
     * subset of all the status values.
     */
    private static final EnumMap<SVNWCDbStatus, String> presenceMap = new EnumMap<SVNWCDbStatus, String>(SVNWCDbStatus.class);
    private static final HashMap<String, SVNWCDbStatus> presenceMap2 = new HashMap<String, SVNWCDbStatus>();
    static {
        presenceMap.put(SVNWCDbStatus.Normal, "normal");
        presenceMap.put(SVNWCDbStatus.Absent, "absent");
        presenceMap.put(SVNWCDbStatus.Excluded, "excluded");
        presenceMap.put(SVNWCDbStatus.NotPresent, "not-present");
        presenceMap.put(SVNWCDbStatus.Incomplete, "incomplete");
        presenceMap.put(SVNWCDbStatus.BaseDeleted, "base-deleted");

        presenceMap2.put("normal", SVNWCDbStatus.Normal);
        presenceMap2.put("absent", SVNWCDbStatus.Absent);
        presenceMap2.put("excluded", SVNWCDbStatus.Excluded);
        presenceMap2.put("not-present", SVNWCDbStatus.NotPresent);
        presenceMap2.put("incomplete", SVNWCDbStatus.Incomplete);
        presenceMap2.put("base-deleted", SVNWCDbStatus.BaseDeleted);

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

    private SVNDepth depthFromWord(String depthStr) {
        if (depthStr.equals("exclude")) {
            return SVNDepth.EXCLUDE;
        } else if (depthStr.equals("unknown")) {
            return SVNDepth.UNKNOWN;
        } else if (depthStr.equals("empty")) {
            return SVNDepth.EMPTY;
        } else if (depthStr.equals("files")) {
            return SVNDepth.FILES;
        } else if (depthStr.equals("immediates")) {
            return SVNDepth.IMMEDIATES;
        } else if (depthStr.equals("infinity")) {
            return SVNDepth.INFINITY;
        } else {
            return SVNDepth.UNKNOWN;
        }
    }

    public static boolean isAbsolute(File localAbsPath) {
        return localAbsPath != null && localAbsPath.isAbsolute();
    }

    public static <E extends Enum<E>> EnumSet<E> getInfoFields(Class<E> clazz, E... fields) {
        final EnumSet<E> set = EnumSet.noneOf(clazz);
        for (E f : fields) {
            set.add(f);
        }
        return set;
    }

    private SVNWCDbOpenMode mode;
    private ISVNOptions config;
    private boolean autoUpgrade;
    private boolean enforceEmptyWQ;
    private HashMap<File, SVNWCDbDir> dirData;

    public void open(final SVNWCDbOpenMode mode, final ISVNOptions config, final boolean autoUpgrade, final boolean enforceEmptyWQ) throws SVNException {
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
        }
        dirData.clear();
        /* Run the cleanup for each WCROOT. */
        closeManyWCRoots(roots);
    }

    private void closeManyWCRoots(final Set<SVNWCDbRoot> roots) {
        for (final SVNWCDbRoot wcRoot : roots) {
            try {
                wcRoot.close();
            } catch (SVNException e) {
                // TODO SVNException closeManyWCRoots()
            }
        }
    }

    public ISVNOptions getConfig() {
        return config;
    }

    public void init(File localAbsPath, File reposRelPath, SVNURL reposRootUrl, String reposUuid, long initialRev, SVNDepth depth) throws SVNException, SqlJetException {

        assert (SVNFileUtil.isAbsolute(localAbsPath));
        assert (reposRelPath != null);
        assert (depth == SVNDepth.EMPTY || depth == SVNDepth.FILES || depth == SVNDepth.IMMEDIATES || depth == SVNDepth.INFINITY);

        /* ### REPOS_ROOT_URL and REPOS_UUID may be NULL. ... more doc: tbd */

        /* Create the SDB and insert the basic rows. */
        CreateDbInfo createDb = createDb(localAbsPath, reposRootUrl, reposUuid, SDB_FILE);

        /* Begin construction of the PDH. */
        SVNWCDbDir pdh = new SVNWCDbDir(localAbsPath);

        /* Create the WCROOT for this directory. */
        pdh.setWCRoot(new SVNWCDbRoot(localAbsPath, createDb.sDb, createDb.wcId, WC_FORMAT_17, false, false));

        /* The PDH is complete. Stash it into DB. */
        dirData.put(localAbsPath, pdh);

        InsertBaseInfo ibb = new InsertBaseInfo();

        if (initialRev > 0)
            ibb.status = SVNWCDbStatus.Incomplete;
        else
            ibb.status = SVNWCDbStatus.Normal;
        ibb.kind = SVNWCDbKind.Dir;
        ibb.wcId = createDb.wcId;
        ibb.localRelPath = null;
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

    private CreateDbInfo createDb(File dirAbsPath, SVNURL reposRootUrl, String reposUuid, String sdbFileName) throws SVNException, SqlJetException {

        CreateDbInfo info = new CreateDbInfo();

        info.sDb = openDb(dirAbsPath, sdbFileName, SVNSqlJetDb.Mode.RWCreate);

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
     *
     * @throws SVNException
     */
    private long createReposId(SVNSqlJetDb sDb, SVNURL reposRootUrl, String reposUuid) throws SVNException {

        final SVNSqlJetStatement getStmt = sDb.getStatement(SVNWCDbStatements.SELECT_REPOSITORY);
        try {
            getStmt.bindf("s", reposRootUrl);
            boolean haveRow = getStmt.next();
            if (haveRow) {
                return getColumnInt64(getStmt, SVNWCDbSchema.WCROOT__Fields.id);
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
        public SVNWCDbStatus status;
        public SVNWCDbKind kind;
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
            stmt.bindString(5, ibb.localRelPath.getParent());

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

        if (ibb.kind == SVNWCDbKind.Dir) {
            stmt.bindString(13, depthToWord(ibb.depth));
        } else if (ibb.kind == SVNWCDbKind.File) {
            stmt.bindChecksum(14, ibb.checksum);
            if (ibb.translatedSize != INVALID_FILESIZE)
                stmt.bindLong(15, ibb.translatedSize);
        } else if (ibb.kind == SVNWCDbKind.Symlink) {
            /* Note: incomplete nodes may have a NULL target. */
            if (ibb.target != null)
                stmt.bindString(16, ibb.target);
        }

        stmt.insert();

        if (ibb.kind == SVNWCDbKind.Dir && ibb.children != null) {
            stmt = sDb.getStatement(SVNWCDbStatements.INSERT_BASE_NODE_INCOMPLETE);
            for (File name : ibb.children) {
                stmt.bindf("issi", ibb.wcId, SVNFileUtil.getFilePath(SVNFileUtil.createFilePath(ibb.localRelPath, name.toString())), SVNFileUtil.getFilePath(ibb.localRelPath), ibb.revision);
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

    public void addBaseAbsentNode(File localAbsPath, File reposRelPath, SVNURL reposRootUrl, String reposUuid, long revision, SVNWCDbKind kind, SVNWCDbStatus status, SVNSkel conflict,
            SVNSkel workItems) throws SVNException {
        // TODO
        throw new UnsupportedOperationException();
    }

    public void addBaseDirectory(File localAbsPath, File reposRelPath, SVNURL reposRootUrl, String reposUuid, long revision, SVNProperties props, long changedRev, Date changedDate,
            String changedAuthor, List<File> children, SVNDepth depth, SVNSkel conflict, SVNSkel workItems) throws SVNException {
        // TODO
        throw new UnsupportedOperationException();
    }

    public void addBaseFile(File localAbspath, File reposRelpath, SVNURL reposRootUrl, String reposUuid, long revision, SVNProperties props, long changedRev, Date changedDate, String changedAuthor,
            SVNChecksum checksum, long translatedSize, SVNSkel conflict, SVNSkel workItems) throws SVNException {
        // TODO
        throw new UnsupportedOperationException();
    }

    public void addBaseSymlink(File localAbsPath, File reposRelPath, SVNURL reposRootUrl, String reposUuid, long revision, SVNProperties props, long changedRev, Date changedDate,
            String changedAuthor, File target, SVNSkel conflict, SVNSkel workItem) throws SVNException {
        // TODO
        throw new UnsupportedOperationException();
    }

    public void addLock(File localAbsPath, SVNWCDbLock lock) throws SVNException {
        // TODO
        throw new UnsupportedOperationException();
    }

    public void addWorkQueue(File wcRootAbsPath, SVNSkel workItem) throws SVNException {
        // TODO
        throw new UnsupportedOperationException();
    }

    public boolean checkPristine(File wcRootAbsPath, SVNChecksum sha1Checksum, SVNWCDbCheckMode mode) throws SVNException {
        // TODO
        throw new UnsupportedOperationException();
    }

    public void completedWorkQueue(File wcRootAbsPath, long id) throws SVNException {
        // TODO
        throw new UnsupportedOperationException();
    }

    public long ensureRepository(File localAbsPath, SVNURL reposRootUrl, String reposUuid) throws SVNException {
        // TODO
        throw new UnsupportedOperationException();
    }

    public WCDbWorkQueueInfo fetchWorkQueue(File wcRootAbsPath) throws SVNException {
        // TODO
        throw new UnsupportedOperationException();
    }

    public File fromRelPath(File wcRootAbsPath, File localRelPath) throws SVNException {
        // TODO
        throw new UnsupportedOperationException();
    }

    public List<String> getBaseChildren(File localAbsPath) throws SVNException {
        return gatherChildren(localAbsPath, true);
    }

    public SVNProperties getBaseDavCache(File localAbsPath) throws SVNException {
        // TODO
        throw new UnsupportedOperationException();
    }

    public WCDbBaseInfo getBaseInfo(File localAbsPath, BaseInfoField... fields) throws SVNException {
        assert (isAbsolute(localAbsPath));

        final EnumSet<BaseInfoField> f = getInfoFields(BaseInfoField.class, fields);
        WCDbBaseInfo info = new WCDbBaseInfo();

        boolean have_row;

        final DirParsedInfo dir = parseDir(localAbsPath, Mode.ReadOnly);
        final SVNWCDbDir pdh = dir.wcDbDir;
        final File localRelPath = dir.localRelPath;

        verifyDirUsable(pdh);

        SVNSqlJetStatement stmt = pdh.getWCRoot().getSDb().getStatement(f.contains(BaseInfoField.lock) ? SVNWCDbStatements.SELECT_BASE_NODE_WITH_LOCK : SVNWCDbStatements.SELECT_BASE_NODE);
        try {
            stmt.bindf("is", pdh.getWCRoot().getWcId(), SVNFileUtil.getFilePath(localRelPath));
            have_row = stmt.next();

            if (have_row) {
                SVNWCDbKind node_kind = getColumnToken(stmt, SVNWCDbSchema.BASE_NODE__Fields.kind, kindMap2);

                if (f.contains(BaseInfoField.kind)) {
                    if (node_kind == SVNWCDbKind.Subdir)
                        info.kind = SVNWCDbKind.Dir;
                    else
                        info.kind = node_kind;
                }
                if (f.contains(BaseInfoField.status)) {
                    info.status = getColumnToken(stmt, SVNWCDbSchema.BASE_NODE__Fields.presence, presenceMap2);

                    if (node_kind == SVNWCDbKind.Subdir && info.status == SVNWCDbStatus.Normal) {
                        /*
                         * We're looking at the subdir record in the *parent*
                         * directory, which implies per-dir .svn subdirs. We
                         * should be looking at the subdir itself; therefore, it
                         * is missing or obstructed in some way. Inform the
                         * caller.
                         */
                        info.status = SVNWCDbStatus.Obstructed;
                    }
                }
                if (f.contains(BaseInfoField.revision)) {
                    info.revision = getColumnRevNum(stmt, SVNWCDbSchema.BASE_NODE__Fields.revnum);
                }
                if (f.contains(BaseInfoField.reposRelPath)) {
                    info.reposRelPath = SVNFileUtil.createFilePath(getColumnText(stmt, SVNWCDbSchema.BASE_NODE__Fields.repos_relpath));
                }
                if (f.contains(BaseInfoField.lock)) {
                    final SVNSqlJetStatement lockStmt = stmt.getJoinedStatement(SVNWCDbSchema.LOCK);
                    if (isColumnNull(lockStmt, SVNWCDbSchema.LOCK__Fields.lock_token)) {
                        info.lock = null;
                    } else {
                        info.lock = new SVNWCDbLock();
                        info.lock.token = getColumnText(lockStmt, SVNWCDbSchema.LOCK__Fields.lock_token);
                        if (!isColumnNull(lockStmt, SVNWCDbSchema.LOCK__Fields.lock_owner))
                            info.lock.owner = getColumnText(lockStmt, SVNWCDbSchema.LOCK__Fields.lock_owner);
                        if (!isColumnNull(lockStmt, SVNWCDbSchema.LOCK__Fields.lock_comment))
                            info.lock.comment = getColumnText(lockStmt, SVNWCDbSchema.LOCK__Fields.lock_comment);
                        if (!isColumnNull(lockStmt, SVNWCDbSchema.LOCK__Fields.lock_date))
                            info.lock.date = new Date(getColumnInt64(lockStmt, SVNWCDbSchema.LOCK__Fields.lock_date));
                    }
                }
                if (f.contains(BaseInfoField.reposRootUrl) || f.contains(BaseInfoField.reposUuid)) {
                    /* Fetch repository information via REPOS_ID. */
                    if (isColumnNull(stmt, SVNWCDbSchema.BASE_NODE__Fields.repos_id)) {
                        if (f.contains(BaseInfoField.reposRootUrl))
                            info.reposRootUrl = null;
                        if (f.contains(BaseInfoField.reposUuid))
                            info.reposUuid = null;
                    } else {
                        final ReposInfo reposInfo = fetchReposInfo(pdh.getWCRoot().getSDb(), getColumnInt64(stmt, SVNWCDbSchema.BASE_NODE__Fields.repos_id));
                        info.reposRootUrl = SVNURL.parseURIEncoded(reposInfo.reposRootUrl);
                    }
                }
                if (f.contains(BaseInfoField.changedRev)) {
                    info.changedRev = getColumnRevNum(stmt, SVNWCDbSchema.BASE_NODE__Fields.changed_rev);
                }
                if (f.contains(BaseInfoField.changedDate)) {
                    info.changedDate = new Date(getColumnInt64(stmt, SVNWCDbSchema.BASE_NODE__Fields.changed_date));
                }
                if (f.contains(BaseInfoField.changedAuthor)) {
                    /* Result may be NULL. */
                    info.changedAuthor = getColumnText(stmt, SVNWCDbSchema.BASE_NODE__Fields.changed_author);
                }
                if (f.contains(BaseInfoField.lastModTime)) {
                    info.lastModTime = new Date(getColumnInt64(stmt, SVNWCDbSchema.BASE_NODE__Fields.last_mod_time));
                }
                if (f.contains(BaseInfoField.depth)) {
                    if (node_kind != SVNWCDbKind.Dir) {
                        info.depth = SVNDepth.UNKNOWN;
                    } else {
                        String depth_str = getColumnText(stmt, SVNWCDbSchema.BASE_NODE__Fields.depth);

                        if (depth_str == null)
                            info.depth = SVNDepth.UNKNOWN;
                        else
                            info.depth = depthFromWord(depth_str);
                    }
                }
                if (f.contains(BaseInfoField.checksum)) {
                    if (node_kind != SVNWCDbKind.File) {
                        info.checksum = null;
                    } else {
                        try {
                            info.checksum = getColumnChecksum(stmt, SVNWCDbSchema.BASE_NODE__Fields.checksum);
                        } catch (SVNException e) {
                            SVNErrorMessage err = SVNErrorMessage.create(e.getErrorMessage().getErrorCode(), "The node ''{0}'' has a corrupt checksum value.", localAbsPath);
                            SVNErrorManager.error(err, SVNLogType.WC);
                        }
                    }
                }
                if (f.contains(BaseInfoField.translatedSize)) {
                    info.translatedSize = getTranslatedSize(stmt, SVNWCDbSchema.BASE_NODE__Fields.translated_size);
                }
                if (f.contains(BaseInfoField.target)) {
                    if (node_kind != SVNWCDbKind.Symlink)
                        info.target = null;
                    else
                        info.target = SVNFileUtil.createFilePath(getColumnText(stmt, SVNWCDbSchema.BASE_NODE__Fields.symlink_target));
                }
            } else {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_PATH_NOT_FOUND, "The node ''{0}'' was not found.", localAbsPath);
                SVNErrorManager.error(err, SVNLogType.WC);
            }

        } finally {
            stmt.reset();
        }

        return info;

    }

    public String getBaseProp(File localAbsPath, String propName) throws SVNException {
        // TODO
        throw new UnsupportedOperationException();
    }

    public SVNProperties getBaseProps(File localAbsPath) throws SVNException {
        SVNSqlJetStatement stmt = getStatementForPath(localAbsPath, SVNWCDbStatements.SELECT_BASE_PROPS);
        try {
            boolean have_row = stmt.next();
            if (!have_row) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_PATH_NOT_FOUND, "The node ''{0}''  was not found.", localAbsPath);
                SVNErrorManager.error(err, SVNLogType.WC);
            }

            SVNProperties props = getColumnProperties(stmt, 0);
            if (props == null) {
                /*
                 * ### is this a DB constraint violation? the column "probably"
                 * should ### never be null.
                 */
                return new SVNProperties();
            }
            return props;
        } finally {
            stmt.reset();
        }
    }

    public int getFormatTemp(File localDirAbsPath) throws SVNException {
        // TODO
        throw new UnsupportedOperationException();
    }

    public SVNChecksum getPristineMD5(File wcRootAbsPath, SVNChecksum sha1Checksum) throws SVNException {
        assert (isAbsolute(wcRootAbsPath));
        assert (sha1Checksum != null);
        assert (sha1Checksum.getKind() == SVNChecksumKind.SHA1);

        final DirParsedInfo parsed = parseDir(wcRootAbsPath, Mode.ReadOnly);

        final SVNWCDbDir pdh = parsed.wcDbDir;

        verifyDirUsable(pdh);

        final SVNSqlJetStatement stmt = pdh.getWCRoot().getSDb().getStatement(SVNWCDbStatements.SELECT_PRISTINE_MD5_CHECKSUM);

        try {
            stmt.bindChecksum(1, sha1Checksum);
            boolean have_row = stmt.next();
            if (!have_row) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_DB_ERROR, "The pristine text with checksum ''{0}'' not found", sha1Checksum.toString());
                SVNErrorManager.error(err, SVNLogType.WC);
                return null;
            }
            final SVNChecksum md5Checksum = getColumnChecksum(stmt, 0);
            assert (md5Checksum.getKind() == SVNChecksumKind.MD5);
            return md5Checksum;
        } finally {
            stmt.reset();
        }
    }

    public File getPristinePath(File wcRootAbsPath, SVNChecksum sha1Checksum) throws SVNException {
        // TODO
        throw new UnsupportedOperationException();
    }

    public SVNChecksum getPristineSHA1(File wcRootAbsPath, SVNChecksum md5Checksum) throws SVNException {
        assert (isAbsolute(wcRootAbsPath));
        assert (md5Checksum.getKind() == SVNChecksumKind.MD5);

        final DirParsedInfo parsed = parseDir(wcRootAbsPath, Mode.ReadOnly);
        SVNWCDbDir pdh = parsed.wcDbDir;
        verifyDirUsable(pdh);

        SVNSqlJetStatement stmt = pdh.getWCRoot().getSDb().getStatement(SVNWCDbStatements.SELECT_PRISTINE_SHA1_CHECKSUM);
        try {
            stmt.bindChecksum(1, md5Checksum);
            boolean have_row = stmt.next();
            if (!have_row) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_DB_ERROR, "The pristine text with MD5 checksum ''{0}'' not found", md5Checksum.toString());
                SVNErrorManager.error(err, SVNLogType.WC);
                return null;
            }
            SVNChecksum sha1_checksum = getColumnChecksum(stmt, 0);
            assert (sha1_checksum.getKind() == SVNChecksumKind.SHA1);
            return sha1_checksum;
        } finally {
            stmt.reset();
        }
    }

    public File getPristineTempDir(File wcRootAbsPath) throws SVNException {
        // TODO
        throw new UnsupportedOperationException();
    }

    public void globalCommit(File localAbspath, long newRevision, Date newDate, String newAuthor, SVNChecksum newChecksum, List<File> newChildren, SVNProperties newDavCache, boolean keepChangelist,
            SVNSkel workItems) throws SVNException {
        // TODO
        throw new UnsupportedOperationException();
    }

    public void globalRecordFileinfo(File localAbspath, long translatedSize, Date lastModTime) throws SVNException {
        // TODO
        throw new UnsupportedOperationException();
    }

    public void globalRelocate(File localDirAbspath, SVNURL reposRootUrl, boolean singleDb) throws SVNException {
        // TODO
        throw new UnsupportedOperationException();
    }

    public void globalUpdate(File localAbsPath, SVNWCDbKind newKind, File newReposRelpath, long newRevision, SVNProperties newProps, long newChangedRev, Date newChangedDate, String newChangedAuthor,
            List<File> newChildren, SVNChecksum newChecksum, File newTarget, SVNProperties newDavCache, SVNSkel conflict, SVNSkel workItems) throws SVNException {
        // TODO
        throw new UnsupportedOperationException();
    }

    public void installPristine(File tempfileAbspath, SVNChecksum sha1Checksum, SVNChecksum md5Checksum) throws SVNException {
        // TODO
        throw new UnsupportedOperationException();
    }

    public boolean isNodeHidden(File localAbsPath) throws SVNException {
        assert (isAbsolute(localAbsPath));

        /*
         * This uses an optimisation that first reads the working node and then
         * may read the base node. It could call svn_wc__db_read_info but that
         * would always read both nodes.
         */
        final DirParsedInfo parsedInfo = parseDir(localAbsPath, Mode.ReadWrite);
        SVNWCDbDir pdh = parsedInfo.wcDbDir;
        File localRelPath = parsedInfo.localRelPath;

        verifyDirUsable(pdh);

        /* First check the working node. */
        SVNSqlJetStatement stmt = pdh.getWCRoot().getSDb().getStatement(SVNWCDbStatements.SELECT_WORKING_NODE);
        try {
            stmt.bindf("is", pdh.getWCRoot().getWcId(), SVNFileUtil.getFilePath(localRelPath));
            boolean have_row = stmt.next();
            if (have_row) {
                /*
                 * Note: this can ONLY be an add/copy-here/move-here. It is not
                 * possible to delete a "hidden" node.
                 */
                SVNWCDbStatus work_status = presenceMap2.get(getColumnText(stmt, SVNWCDbSchema.WORKING_NODE__Fields.presence));
                return (work_status == SVNWCDbStatus.Excluded);
            }
        } finally {
            stmt.reset();
        }

        /* Now check the BASE node's status. */
        final WCDbBaseInfo baseInfo = getBaseInfo(localAbsPath, BaseInfoField.status);
        SVNWCDbStatus base_status = baseInfo.status;
        return (base_status == SVNWCDbStatus.Absent || base_status == SVNWCDbStatus.NotPresent || base_status == SVNWCDbStatus.Excluded);
    }

    public static class DirParsedInfo {

        public SVNWCDbDir wcDbDir;
        public File localRelPath;
    }

    public DirParsedInfo parseDir(File localAbsPath, Mode sMode) throws SVNException {

        DirParsedInfo info = new DirParsedInfo();
        String buildRelPath;
        boolean always_check = false;
        boolean obstruction_possible = false;

        /*
         * ### we need more logic for finding the database (if it is located ###
         * outside of the wcroot) and then managing all of that within DB. ###
         * for now: play quick & dirty.
         */

        /*
         * ### for now, overwrite the provided mode. We currently cache the ###
         * sdb handles, which is great but for the occasion where we ###
         * initially open the sdb in readonly mode and then later want ### to
         * write to it. The solution is to reopen the db in readwrite ### mode,
         * but that assumes we can track the fact that it was ### originally
         * opened readonly. So for now, just punt and open ### everything in
         * readwrite mode.
         */
        sMode = Mode.ReadWrite;

        info.wcDbDir = dirData.get(localAbsPath);
        if (info.wcDbDir != null && info.wcDbDir.getWCRoot() != null) {
            /* We got lucky. Just return the thing BEFORE performing any I/O. */
            /*
             * ### validate SMODE against how we opened wcroot->sdb? and against
             * ### DB->mode? (will we record per-dir mode?)
             */
            info.localRelPath = info.wcDbDir.computeRelPath();
            return info;
        }

        /*
         * ### at some point in the future, we may need to find a way to get ###
         * rid of this stat() call. it is going to happen for EVERY call ###
         * into wc_db which references a file. calls for directories could ###
         * get an early-exit in the hash lookup just above.
         */
        SVNNodeKind kind = SVNFileType.getNodeKind(SVNFileType.getType(localAbsPath));
        if (kind != SVNNodeKind.DIR) {
            /*
             * If the node specified by the path is NOT present, then it cannot
             * possibly be a directory containing ".svn/wc.db".
             *
             * If it is a file, then it cannot contain ".svn/wc.db".
             *
             * For both of these cases, strip the basename off of the path and
             * move up one level. Keep record of what we strip, though, since
             * we'll need it later to construct local_relpath.
             */
            buildRelPath = SVNFileUtil.getFileName(localAbsPath);
            localAbsPath = SVNFileUtil.getFileDir(localAbsPath);

            /*
             * ### if *pdh != NULL (from further above), then there is (quite
             * ### probably) a bogus value in the DIR_DATA hash table. maybe ###
             * clear it out? but what if there is an access baton?
             */

            /* Is this directory in our hash? */
            info.wcDbDir = info.wcDbDir = dirData.get(localAbsPath);
            if (info.wcDbDir != null && info.wcDbDir.getWCRoot() != null) {
                /* Stashed directory's local_relpath + basename. */
                info.localRelPath = SVNFileUtil.createFilePath(info.wcDbDir.computeRelPath(), buildRelPath);
                return info;
            }

            /*
             * If the requested path is not on the disk, then we don't know how
             * many ancestors need to be scanned until we start hitting content
             * on the disk. Set ALWAYS_CHECK to keep looking for .svn/entries
             * rather than bailing out after the first check.
             */
            if (kind == SVNNodeKind.NONE)
                always_check = true;
        } else {
            /*
             * Start the local_relpath empty. If *this* directory contains the
             * wc.db, then relpath will be the empty string.
             */
            buildRelPath = "";

            /*
             * It is possible that LOCAL_ABSPATH was *intended* to be a file,
             * but we just found a directory in its place. After we build the
             * PDH, then we'll examine the parent to see how it describes this
             * particular path.
             *
             * ### this is only possible with per-dir wc.db databases.
             */
            obstruction_possible = true;
        }

        /*
         * LOCAL_ABSPATH refers to a directory at this point. The PDH
         * corresponding to that directory is what we need to return. At this
         * point, we've determined that a PDH with a discovered WCROOT is NOT in
         * the DB's hash table of wcdirs. Let's fill in an existing one, or
         * create one. Then go figure out where the WCROOT is.
         */

        if (info.wcDbDir == null) {
            info.wcDbDir = new SVNWCDbDir(localAbsPath);
        } else {
            /* The PDH should have been built correctly (so far). */
            assert (localAbsPath.equals(info.wcDbDir.getLocalAbsPath()));
        }

        /*
         * Assume that LOCAL_ABSPATH is a directory, and look for the SQLite
         * database in the right place. If we find it... great! If not, then
         * peel off some components, and try again.
         */

        File original_abspath = localAbsPath;
        SVNWCDbDir found_pdh = null;
        SVNWCDbDir child_pdh;
        SVNSqlJetDb sDb = null;
        boolean moved_upwards = false;
        int wc_format = 0;

        while (true) {

            try {
                sDb = openDb(localAbsPath, SDB_FILE, sMode);
                break;
            } catch (SVNException e) {
                if (!isErrorNOENT(e.getErrorMessage().getErrorCode()))
                    throw e;
            } catch (SqlJetException e) {
            }

            /*
             * If we have not moved upwards, then check for a wc-1 working copy.
             * Since wc-1 has a .svn in every directory, and we didn't find one
             * in the original directory, then we aren't looking at a wc-1.
             *
             * If the original path is not present, then we have to check on
             * every iteration. The content may be the immediate parent, or
             * possibly five ancetors higher. We don't test for directory
             * presence (just for the presence of subdirs/files), so we don't
             * know when we can stop checking ... so just check always.
             */
            if (!moved_upwards || always_check) {
                wc_format = getOldVersion(localAbsPath);
                if (wc_format != 0)
                    break;
            }

            /*
             * We couldn't open the SDB within the specified directory, so move
             * up one more directory.
             */
            localAbsPath = SVNFileUtil.getFileDir(localAbsPath);
            if (localAbsPath == null) {
                /* Hit the root without finding a wcroot. */
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_NOT_WORKING_COPY, "''{0}'' is not a working copy", original_abspath);
                SVNErrorManager.error(err, SVNLogType.WC);
            }

            moved_upwards = true;

            /*
             * An obstruction is no longer possible.
             *
             * Example: we were given "/some/file" and "file" turned out to be a
             * directory. We did not find an SDB at "/some/file/.svn/wc.db", so
             * we are now going to look at "/some/.svn/wc.db". That SDB will
             * contain the correct information for "file".
             *
             * ### obstruction is only possible with per-dir wc.db databases.
             */
            obstruction_possible = false;

            /* Is the parent directory recorded in our hash? */
            found_pdh = dirData.get(localAbsPath);
            if (found_pdh != null) {
                if (found_pdh.getWCRoot() != null)
                    break;
                found_pdh = null;
            }
        }

        if (found_pdh != null) {
            /*
             * We found a PDH with data in it. We can now construct the child
             * from this, rather than continuing to scan upwards.
             */

            /* The subdirectory uses the same WCROOT as the parent dir. */
            info.wcDbDir.setWCRoot(found_pdh.getWCRoot());
        } else if (wc_format == 0) {
            /* We finally found the database. Construct the PDH record. */

            long wcId = UNKNOWN_WC_ID;

            try {
                wcId = fetchWCId(sDb);
            } catch (SVNException e) {
                if (e.getErrorMessage().getErrorCode() == SVNErrorCode.WC_CORRUPT) {
                    SVNErrorMessage err = e.getErrorMessage().wrap("Missing a row in WCROOT for '{0}'.", original_abspath);
                    SVNErrorManager.error(err, SVNLogType.WC);
                }
            }

            /*
             * WCROOT.local_abspath may be NULL when the database is stored
             * inside the wcroot, but we know the abspath is this directory (ie.
             * where we found it).
             */
            info.wcDbDir.setWCRoot(new SVNWCDbRoot(localAbsPath, sDb, wcId, FORMAT_FROM_SDB, autoUpgrade, enforceEmptyWQ));

        } else {
            /* We found a wc-1 working copy directory. */
            info.wcDbDir.setWCRoot(new SVNWCDbRoot(localAbsPath, null, UNKNOWN_WC_ID, wc_format, autoUpgrade, enforceEmptyWQ));

            /*
             * Don't test for a directory obstructing a versioned file. The wc-1
             * code can manage that itself.
             */
            obstruction_possible = false;

            SVNErrorMessage err = SVNErrorMessage.create( SVNErrorCode.WC_UNSUPPORTED_FORMAT );
            SVNErrorManager.error(err, SVNLogType.WC);

        }

        {
            /*
             * The subdirectory's relpath is easily computed relative to the
             * wcroot that we just found.
             */
            File dirRelPath = info.wcDbDir.computeRelPath();

            /* And the result local_relpath may include a filename. */
            info.localRelPath = SVNFileUtil.createFilePath(dirRelPath, buildRelPath);
        }

        /*
         * Check to see if this (versioned) directory is obstructing what should
         * be a file in the parent directory.
         *
         * ### obstruction is only possible with per-dir wc.db databases.
         */
        if (obstruction_possible) {
            /* We should NOT have moved up a directory. */
            assert (!moved_upwards);

            /* Get/make a PDH for the parent. */
            File parent_dir = SVNFileUtil.getFileDir(localAbsPath);
            SVNWCDbDir parent_pdh = dirData.get(parent_dir);
            if (parent_pdh == null || parent_pdh.getWCRoot() == null) {
                boolean err = false;
                try {
                    sDb = openDb(parent_dir, SDB_FILE, sMode);
                } catch (SVNException e) {
                    err = true;
                    if (!isErrorNOENT(e.getErrorMessage().getErrorCode()))
                        throw e;
                } catch (SqlJetException e) {
                    err = true;
                }

                if (err) {
                    /*
                     * No parent, so we're at a wcroot apparently. An
                     * obstruction is (therefore) not possible.
                     */
                    parent_pdh = null;
                } else {
                    /* ### construct this according to per-dir semantics. */
                    if (parent_pdh == null) {
                        parent_pdh = new SVNWCDbDir(parent_dir);
                    } else {
                        /* The PDH should have been built correctly (so far). */
                        assert (parent_dir.equals(parent_pdh.getLocalAbsPath()));
                    }
                    /*
                     * wcID = 1 ## # it is hack .
                     */
                    parent_pdh.setWCRoot(new SVNWCDbRoot(parent_pdh.getLocalAbsPath(), sDb, 1, FORMAT_FROM_SDB, autoUpgrade, enforceEmptyWQ));

                    dirData.put(parent_pdh.getLocalAbsPath(), parent_pdh);

                    info.wcDbDir.setParent(parent_pdh);
                }
            }

            if (parent_pdh != null) {
                String lookfor_relpath = SVNFileUtil.getFileName(localAbsPath);

                /* Was there supposed to be a file sitting here? */
                info.wcDbDir.setObstructedFile(parent_pdh.getWCRoot().determineObstructedFile(lookfor_relpath));

                /*
                 * If we determined that a file was supposed to be at the
                 * LOCAL_ABSPATH requested, then return the PDH and
                 * LOCAL_RELPATH which describes that file.
                 */
                if (info.wcDbDir.isObstructedFile()) {
                    info.wcDbDir = parent_pdh;
                    info.localRelPath = SVNFileUtil.createFilePath(lookfor_relpath);
                    return info;
                }
            }
        }

        /* The PDH is complete. Stash it into DB. */
        dirData.put(info.wcDbDir.getLocalAbsPath(), info.wcDbDir);

        /* Did we traverse up to parent directories? */
        if (!moved_upwards) {
            /*
             * We did NOT move to a parent of the original requested directory.
             * We've constructed and filled in a PDH for the request, so we are
             * done.
             */
            return info;
        }

        /*
         * The PDH that we just built was for the LOCAL_ABSPATH originally
         * passed into this function. We stepped *at least* one directory above
         * that. We should now create PDH records for each parent directory that
         * does not (yet) have one.
         */

        child_pdh = info.wcDbDir;

        do {
            File parent_dir = SVNFileUtil.getFileDir(child_pdh.getLocalAbsPath());
            SVNWCDbDir parent_pdh;

            parent_pdh = dirData.get(parent_dir);

            if (parent_pdh == null) {
                parent_pdh = new SVNWCDbDir(parent_dir);

                /* All the PDHs have the same wcroot. */
                parent_pdh.setWCRoot(info.wcDbDir.getWCRoot());

                dirData.put(parent_pdh.getLocalAbsPath(), parent_pdh);

            } else if (parent_pdh.getWCRoot() == null) {
                parent_pdh.setWCRoot(info.wcDbDir.getWCRoot());
            }

            /*
             * Point the child PDH at this (new) parent PDH. This will allow for
             * easy traversals without path munging.
             */
            child_pdh.setParent(parent_pdh);
            child_pdh = parent_pdh;

            /*
             * Loop if we haven't reached the PDH we found, or the abspath where
             * we terminated the search (when we found wc.db). Note that if we
             * never located a PDH in our ancestry, then FOUND_PDH will be NULL
             * and that portion of the test will always be TRUE.
             */
        } while (child_pdh != found_pdh && !localAbsPath.equals(child_pdh.getLocalAbsPath()));

        return info;
    }

    private int getOldVersion(File localAbsPath) {
        try {
            return SVNAdminUtil.getVersion(localAbsPath);
        } catch (SVNException e) {
            return 0;
        }
    }

    public boolean isWCLocked(File localAbspath) throws SVNException {
        return isWCLocked(localAbspath, 0);
    }

    private boolean isWCLocked(File localAbspath, long recurseDepth) throws SVNException {
        final SVNSqlJetStatement stmt = getStatementForPath(localAbspath, SVNWCDbStatements.SELECT_WC_LOCK);
        try {
            boolean have_row = stmt.next();
            if (have_row) {
                long locked_levels = getColumnInt64(stmt, 0);
                /*
                 * The directory in question is considered locked if we find a
                 * lock with depth -1 or the depth of the lock is greater than
                 * or equal to the depth we've recursed.
                 */
                return (locked_levels == -1 || locked_levels >= recurseDepth);
            }
        } finally {
            stmt.reset();
        }
        final File parentFile = SVNFileUtil.getFileDir(localAbspath);
        if (parentFile == null) {
            return false;
        }
        try {
            return isWCLocked(parentFile, recurseDepth + 1);
        } catch (SVNException e) {
            if (e.getErrorMessage().getErrorCode() == SVNErrorCode.WC_NOT_WORKING_COPY) {
                return false;
            }
        }
        return false;
    }

    public void opAddDirectory(File localAbsPath, SVNSkel workItems) throws SVNException {
        // TODO
        throw new UnsupportedOperationException();
    }

    public void opAddFile(File localAbsPath, SVNSkel workItems) throws SVNException {
        // TODO
        throw new UnsupportedOperationException();
    }

    public void opAddSymlink(File localAbsPath, File target, SVNSkel workItems) throws SVNException {
        // TODO
        throw new UnsupportedOperationException();
    }

    public void opCopy(File srcAbsPath, File dstAbspath, SVNSkel workItems) throws SVNException {
        // TODO
        throw new UnsupportedOperationException();
    }

    public void opCopyDir(File localAbsPath, SVNProperties props, long changedRev, Date changedDate, String changedAuthor, File originalReposRelPath, SVNURL originalRootUrl, String originalUuid,
            long originalRevision, List<File> children, SVNDepth depth, SVNSkel conflict, SVNSkel workItems) throws SVNException {
        // TODO
        throw new UnsupportedOperationException();
    }

    public void opCopyFile(File localAbsPath, SVNProperties props, long changedRev, Date changedDate, String changedAuthor, File originalReposRelPath, SVNURL originalRootUrl, String originalUuid,
            long originalRevision, SVNChecksum checksum, SVNSkel conflict, SVNSkel workItems) throws SVNException {
        // TODO
        throw new UnsupportedOperationException();
    }

    public void opCopySymlink(File localAbsPath, SVNProperties props, long changedRev, Date changedDate, String changedAuthor, File originalReposRelPath, SVNURL originalRootUrl, String originalUuid,
            long originalRevision, File target, SVNSkel conflict, SVNSkel workItems) throws SVNException {
        // TODO
        throw new UnsupportedOperationException();
    }

    public void opDelete(File localAbsPath) throws SVNException {
        // TODO
        throw new UnsupportedOperationException();
    }

    public void opMarkConflict(File localAbsPath) throws SVNException {
        // TODO
        throw new UnsupportedOperationException();
    }

    public void opMarkResolved(File localAbspath, boolean resolvedText, boolean resolvedProps, boolean resolvedTree) throws SVNException {
        // TODO
        throw new UnsupportedOperationException();
    }

    public void opModified(File localAbsPath) throws SVNException {
        // TODO
        throw new UnsupportedOperationException();
    }

    public void opMove(File srcAbsPath, File dstAbsPath) throws SVNException {
        // TODO
        throw new UnsupportedOperationException();
    }

    public Map<String, SVNTreeConflictDescription> opReadAllTreeConflicts(File localAbsPath) throws SVNException {
        assert (isAbsolute(localAbsPath));

        final DirParsedInfo parsed = parseDir(localAbsPath, Mode.ReadWrite);
        final SVNWCDbDir pdh = parsed.wcDbDir;
        final File local_relpath = parsed.localRelPath;
        verifyDirUsable(pdh);

        String tree_conflict_data;

        /* Get the conflict information for the parent of LOCAL_ABSPATH. */

        SVNSqlJetStatement stmt = pdh.getWCRoot().getSDb().getStatement(SVNWCDbStatements.SELECT_ACTUAL_NODE);
        try {
            stmt.bindf("is", pdh.getWCRoot().getWcId(), SVNFileUtil.getFilePath(local_relpath));
            boolean have_row = stmt.next();

            /* No ACTUAL node, no conflict info, no problem. */
            if (!have_row) {
                return null;
            }

            tree_conflict_data = getColumnText(stmt, 5);
        } finally {
            stmt.reset();
        }

        /* No tree conflict data? no problem. */
        if (tree_conflict_data == null) {
            return null;
        }

        return SVNTreeConflictUtil.readTreeConflicts(localAbsPath, tree_conflict_data);

    }

    public SVNTreeConflictDescription opReadTreeConflict(File localAbsPath) throws SVNException {
        assert (isAbsolute(localAbsPath));
        File parentAbsPath = SVNFileUtil.getFileDir(localAbsPath);
        try {
            Map<String, SVNTreeConflictDescription> tree_conflicts = opReadAllTreeConflicts(parentAbsPath);
            if (tree_conflicts != null)
                return tree_conflicts.get(SVNFileUtil.getFileName(localAbsPath));
            return null;
        } catch (SVNException e) {
            if (e.getErrorMessage().getErrorCode() == SVNErrorCode.WC_NOT_WORKING_COPY) {
                /* We walked off the top of a working copy. */
                return null;
            }
            throw e;
        }
    }

    public void opRevert(File localAbspath, SVNDepth depth) throws SVNException {
        // TODO
        throw new UnsupportedOperationException();
    }

    public void opSetChangelist(File localAbsPath, String changelist) throws SVNException {
        // TODO
        throw new UnsupportedOperationException();
    }

    public void opSetProps(File localAbsPath, SVNProperties props, SVNSkel conflict, SVNSkel workItems) throws SVNException {
        // TODO
        throw new UnsupportedOperationException();
    }

    public void opSetTreeConflict(File localAbspath, SVNTreeConflictDescription treeConflict) throws SVNException {
        // TODO
        throw new UnsupportedOperationException();
    }

    public void open(SVNWCDbOpenMode mode, SVNConfigFile config, boolean autoUpgrade, boolean enforceEmptyWQ) throws SVNException {
        // TODO
        throw new UnsupportedOperationException();
    }

    public List<String> readChildren(File localAbsPath) throws SVNException {
        return gatherChildren(localAbsPath, false);
    }

    private List<String> gatherChildren(File localAbsPath, boolean baseOnly) throws SVNException {
        assert (isAbsolute(localAbsPath));

        final DirParsedInfo parsed = parseDir(localAbsPath, Mode.ReadOnly);
        SVNWCDbDir pdh = parsed.wcDbDir;
        File localRelPath = parsed.localRelPath;

        verifyDirUsable(pdh);

        final long wcId = pdh.getWCRoot().getWcId();
        final SVNSqlJetDb sDb = pdh.getWCRoot().getSDb();

        final List<String> names = new ArrayList<String>();

        final SVNSqlJetStatement base_stmt = sDb.getStatement(SVNWCDbStatements.SELECT_BASE_NODE_CHILDREN);
        base_stmt.bindf("is", wcId, SVNFileUtil.getFilePath(localRelPath));
        addChildren(names, base_stmt);

        if (!baseOnly) {
            final SVNSqlJetStatement work_stmt = sDb.getStatement(SVNWCDbStatements.SELECT_WORKING_NODE_CHILDREN);
            work_stmt.bindf("is", wcId, SVNFileUtil.getFilePath(localRelPath));
            addChildren(names, work_stmt);
        }

        return names;
    }

    private void addChildren(List<String> children, SVNSqlJetStatement stmt) throws SVNException {
        try {
            while (stmt.next()) {
                String child_relpath = getColumnText(stmt, SVNWCDbSchema.BASE_NODE__Fields.local_relpath);
                String name = SVNFileUtil.getFileName(SVNFileUtil.createFilePath(child_relpath));
                children.add(name);
            }
        } finally {
            stmt.reset();
        }
    }

    public List<String> readConflictVictims(File localAbsPath) throws SVNException {
        assert (isAbsolute(localAbsPath));

        /* The parent should be a working copy directory. */
        final DirParsedInfo parsed = parseDir(localAbsPath, Mode.ReadOnly);
        SVNWCDbDir pdh = parsed.wcDbDir;
        File localRelPath = parsed.localRelPath;

        verifyDirUsable(pdh);

        final SVNSqlJetDb sDb = pdh.getWCRoot().getSDb();

        SVNSqlJetStatement stmt;
        String tree_conflict_data;

        List<String> victims = new ArrayList<String>();

        /*
         * ### This will be much easier once we have all conflicts in one field
         * of actual
         */

        Set<String> found = new HashSet<String>();

        /* First look for text and property conflicts in ACTUAL */
        stmt = sDb.getStatement(SVNWCDbStatements.SELECT_ACTUAL_CONFLICT_VICTIMS);
        try {
            stmt.bindf("is", pdh.getWCRoot().getWcId(), SVNFileUtil.getFilePath(localRelPath));
            while (stmt.next()) {
                String child_relpath = getColumnText(stmt, SVNWCDbSchema.ACTUAL_NODE__Fields.local_relpath);
                String child_name = SVNFileUtil.getFileName(SVNFileUtil.createFilePath(child_relpath));
                found.add(child_name);
            }
        } finally {
            stmt.reset();
        }

        /* And add tree conflicts */
        stmt = sDb.getStatement(SVNWCDbStatements.SELECT_ACTUAL_TREE_CONFLICT);
        try {
            stmt.bindf("is", pdh.getWCRoot().getWcId(), SVNFileUtil.getFilePath(localRelPath));
            if (stmt.next())
                tree_conflict_data = getColumnText(stmt, SVNWCDbSchema.ACTUAL_NODE__Fields.tree_conflict_data);
            else
                tree_conflict_data = null;
        } finally {
            stmt.reset();
        }

        if (tree_conflict_data != null) {
            Map<String, SVNTreeConflictDescription> conflict_items = SVNTreeConflictUtil.readTreeConflicts(localAbsPath, tree_conflict_data);
            for (String conflict : conflict_items.keySet()) {
                String child_name = SVNFileUtil.getFileName(SVNFileUtil.createFilePath(conflict));
                /* Using a hash avoids duplicates */
                found.add(child_name);
            }
        }

        victims.addAll(found);
        return victims;
    }

    public List<SVNConflictDescription> readConflicts(File localAbsPath) throws SVNException {
        List<SVNConflictDescription> conflicts = new ArrayList<SVNConflictDescription>();

        /* The parent should be a working copy directory. */
        DirParsedInfo parseDir = parseDir(localAbsPath, Mode.ReadOnly);
        SVNWCDbDir pdh = parseDir.wcDbDir;
        File localRelPath = parseDir.localRelPath;
        verifyDirUsable(pdh);

        /*
         * ### This will be much easier once we have all conflicts in one field
         * of actual.
         */

        /* First look for text and property conflicts in ACTUAL */
        SVNSqlJetStatement stmt = pdh.getWCRoot().getSDb().getStatement(SVNWCDbStatements.SELECT_CONFLICT_DETAILS);
        try {

            stmt.bindf("is", pdh.getWCRoot().getWcId(), localRelPath);

            boolean have_row = stmt.next();

            if (have_row) {
                /* ### Store in description! */
                String prop_reject = getColumnText(stmt, 0);
                if (prop_reject != null) {
                    SVNMergeFileSet mergeFiles = new SVNMergeFileSet(null, null, null, localAbsPath, null, new File(prop_reject), null, null, null);
                    SVNPropertyConflictDescription desc = new SVNPropertyConflictDescription(mergeFiles, SVNNodeKind.UNKNOWN, "", null, null);
                    conflicts.add(desc);
                }

                String conflict_old = getColumnText(stmt, 1);
                String conflict_new = getColumnText(stmt, 2);
                String conflict_working = getColumnText(stmt, 3);

                if (conflict_old != null || conflict_new != null || conflict_working != null) {
                    File baseFile = conflict_old != null ? new File(conflict_old) : null;
                    File theirFile = conflict_new != null ? new File(conflict_new) : null;
                    File myFile = conflict_working != null ? new File(conflict_working) : null;
                    File mergedFile = new File(SVNFileUtil.getFileName(localAbsPath));
                    SVNMergeFileSet mergeFiles = new SVNMergeFileSet(null, null, baseFile, myFile, null, theirFile, mergedFile, null, null);
                    SVNTextConflictDescription desc = new SVNTextConflictDescription(mergeFiles, SVNNodeKind.UNKNOWN, null, null);
                    conflicts.add(desc);
                }
            }
        } finally {
            stmt.reset();
        }

        /* ### Tree conflicts are still stored on the directory */
        {
            SVNTreeConflictDescription desc = opReadTreeConflict(localAbsPath);
            if (desc != null) {
                conflicts.add(desc);
            }
        }

        return conflicts;
    }

    public WCDbInfo readInfo(File localAbsPath, InfoField... fields) throws SVNException {
        assert (isAbsolute(localAbsPath));

        final DirParsedInfo parseDir = parseDir(localAbsPath, Mode.ReadOnly);
        SVNWCDbDir pdh = parseDir.wcDbDir;
        File localRelPath = parseDir.localRelPath;

        verifyDirUsable(pdh);

        WCDbInfo info = new WCDbInfo();

        final EnumSet<InfoField> f = getInfoFields(InfoField.class, fields);

        SVNSqlJetStatement stmt_base = pdh.getWCRoot().getSDb().getStatement(f.contains(InfoField.lock) ? SVNWCDbStatements.SELECT_BASE_NODE_WITH_LOCK : SVNWCDbStatements.SELECT_BASE_NODE);
        SVNSqlJetStatement stmt_work = pdh.getWCRoot().getSDb().getStatement(SVNWCDbStatements.SELECT_WORKING_NODE);
        SVNSqlJetStatement stmt_act = pdh.getWCRoot().getSDb().getStatement(SVNWCDbStatements.SELECT_ACTUAL_NODE);

        try {

            stmt_base.bindf("is", pdh.getWCRoot().getWcId(), SVNFileUtil.getFilePath(localRelPath));
            boolean have_base = stmt_base.next();

            stmt_work.bindf("is", pdh.getWCRoot().getWcId(), SVNFileUtil.getFilePath(localRelPath));
            boolean have_work = stmt_work.next();

            stmt_act.bindf("is", pdh.getWCRoot().getWcId(), SVNFileUtil.getFilePath(localRelPath));
            boolean have_act = stmt_act.next();

            if (f.contains(InfoField.haveBase))
                info.haveBase = have_base;

            if (f.contains(InfoField.haveWork))
                info.haveWork = have_work;

            if (have_base || have_work) {
                SVNWCDbKind node_kind;

                if (have_work)
                    node_kind = getColumnToken(stmt_work, SVNWCDbSchema.WORKING_NODE__Fields.kind, kindMap2);
                else
                    node_kind = getColumnToken(stmt_base, SVNWCDbSchema.BASE_NODE__Fields.kind, kindMap2);

                if (f.contains(InfoField.status)) {
                    if (have_base) {
                        info.status = getColumnToken(stmt_base, SVNWCDbSchema.BASE_NODE__Fields.presence, presenceMap2);

                        /*
                         * We have a presence that allows a WORKING_NODE
                         * override (normal or not-present), or we don't have an
                         * override.
                         */
                        /*
                         * ### for now, allow an override of an incomplete
                         * BASE_NODE ### row. it appears possible to get rows in
                         * BASE/WORKING ### both set to 'incomplete'.
                         */
                        assert ((info.status != SVNWCDbStatus.Absent && info.status != SVNWCDbStatus.Excluded
                        /* && info.status != WCDbStatus.Incomplete */) || !have_work);

                        if (node_kind == SVNWCDbKind.Subdir && info.status == SVNWCDbStatus.Normal) {
                            /*
                             * We should have read a row from the subdir wc.db.
                             * It must be obstructed in some way.
                             *
                             * It is also possible that a WORKING node will
                             * override this value with a proper status.
                             */
                            info.status = SVNWCDbStatus.Obstructed;
                        }
                    }

                    if (have_work) {
                        SVNWCDbStatus work_status;

                        work_status = getColumnToken(stmt_work, SVNWCDbSchema.WORKING_NODE__Fields.presence, presenceMap2);
                        assert (work_status == SVNWCDbStatus.Normal || work_status == SVNWCDbStatus.Excluded || work_status == SVNWCDbStatus.NotPresent || work_status == SVNWCDbStatus.BaseDeleted || work_status == SVNWCDbStatus.Incomplete);

                        if (work_status == SVNWCDbStatus.Incomplete) {
                            info.status = SVNWCDbStatus.Incomplete;
                        } else if (work_status == SVNWCDbStatus.Excluded) {
                            info.status = SVNWCDbStatus.Excluded;
                        } else if (work_status == SVNWCDbStatus.NotPresent || work_status == SVNWCDbStatus.BaseDeleted) {
                            /*
                             * The caller should scan upwards to detect whether
                             * this deletion has occurred because this node has
                             * been moved away, or it is a regular deletion.
                             * Also note that the deletion could be of the BASE
                             * tree, or a child of something that has been
                             * copied/moved here.
                             *
                             * If we're looking at the data in the parent, then
                             * something has obstructed the child data. Inform
                             * the caller.
                             */
                            if (node_kind == SVNWCDbKind.Subdir)
                                info.status = SVNWCDbStatus.ObstructedDelete;
                            else
                                info.status = SVNWCDbStatus.Deleted;
                        } else { /* normal */

                            /*
                             * The caller should scan upwards to detect whether
                             * this addition has occurred because of a simple
                             * addition, a copy, or is the destination of a
                             * move.
                             *
                             * If we're looking at the data in the parent, then
                             * something has obstructed the child data. Inform
                             * the caller.
                             */
                            if (node_kind == SVNWCDbKind.Subdir)
                                info.status = SVNWCDbStatus.ObstructedAdd;
                            else
                                info.status = SVNWCDbStatus.Added;
                        }
                    }
                }
                if (f.contains(InfoField.kind)) {
                    if (node_kind == SVNWCDbKind.Subdir)
                        info.kind = SVNWCDbKind.Dir;
                    else
                        info.kind = node_kind;
                }
                if (f.contains(InfoField.revision)) {
                    if (have_work)
                        info.revision = INVALID_REVNUM;
                    else
                        info.revision = getColumnRevNum(stmt_base, SVNWCDbSchema.BASE_NODE__Fields.revnum);
                }
                if (f.contains(InfoField.reposRelPath)) {
                    if (have_work) {
                        /*
                         * Our path is implied by our parent somewhere up the
                         * tree. With the NULL value and status, the caller will
                         * know to search up the tree for the base of our path.
                         */
                        info.reposRelPath = null;
                    } else
                        info.reposRelPath = SVNFileUtil.createFilePath(getColumnText(stmt_base, SVNWCDbSchema.BASE_NODE__Fields.repos_relpath));
                }
                if (f.contains(InfoField.reposRootUrl) || f.contains(InfoField.reposUuid)) {
                    /*
                     * Fetch repository information via REPOS_ID. If we have a
                     * WORKING_NODE (and have been added), then the repository
                     * we're being added to will be dependent upon a parent. The
                     * caller can scan upwards to locate the repository.
                     */
                    if (have_work || isColumnNull(stmt_base, SVNWCDbSchema.BASE_NODE__Fields.repos_id)) {
                        if (f.contains(InfoField.reposRootUrl))
                            info.reposRootUrl = null;
                        if (f.contains(InfoField.reposUuid))
                            info.reposUuid = null;
                    } else {
                        final ReposInfo reposInfo = fetchReposInfo(pdh.getWCRoot().getSDb(), getColumnInt64(stmt_base, SVNWCDbSchema.BASE_NODE__Fields.repos_id));
                        info.reposRootUrl = SVNURL.parseURIEncoded(reposInfo.reposRootUrl);
                        info.reposUuid = reposInfo.reposUuid;
                    }
                }
                if (f.contains(InfoField.changedRev)) {
                    if (have_work)
                        info.changedRev = getColumnRevNum(stmt_work, SVNWCDbSchema.WORKING_NODE__Fields.changed_rev);
                    else
                        info.changedRev = getColumnRevNum(stmt_base, SVNWCDbSchema.BASE_NODE__Fields.changed_rev);
                }
                if (f.contains(InfoField.changedDate)) {
                    if (have_work)
                        info.changedDate = new Date(getColumnInt64(stmt_work, SVNWCDbSchema.WORKING_NODE__Fields.changed_date));
                    else
                        info.changedDate = new Date(getColumnInt64(stmt_base, SVNWCDbSchema.BASE_NODE__Fields.changed_date));
                }
                if (f.contains(InfoField.changedAuthor)) {
                    if (have_work)
                        info.changedAuthor = getColumnText(stmt_work, SVNWCDbSchema.WORKING_NODE__Fields.changed_author);
                    else
                        info.changedAuthor = getColumnText(stmt_base, SVNWCDbSchema.BASE_NODE__Fields.changed_author);
                }
                if (f.contains(InfoField.lastModTime)) {
                    if (have_work)
                        info.lastModTime = getColumnInt64(stmt_work, SVNWCDbSchema.WORKING_NODE__Fields.last_mod_time);
                    else
                        info.lastModTime = getColumnInt64(stmt_base, SVNWCDbSchema.BASE_NODE__Fields.last_mod_time);
                }
                if (f.contains(InfoField.depth)) {
                    if (node_kind != SVNWCDbKind.Dir && node_kind != SVNWCDbKind.Subdir) {
                        info.depth = SVNDepth.UNKNOWN;
                    } else {
                        String depth_str;

                        if (have_work)
                            depth_str = getColumnText(stmt_work, SVNWCDbSchema.WORKING_NODE__Fields.depth);
                        else
                            depth_str = getColumnText(stmt_base, SVNWCDbSchema.BASE_NODE__Fields.depth);

                        if (depth_str == null)
                            info.depth = SVNDepth.UNKNOWN;
                        else
                            info.depth = depthFromWord(depth_str);
                    }
                }
                if (f.contains(InfoField.checksum)) {
                    if (node_kind != SVNWCDbKind.File) {
                        info.checksum = null;
                    } else {
                        try {
                            if (have_work)
                                info.checksum = getColumnChecksum(stmt_work, SVNWCDbSchema.WORKING_NODE__Fields.checksum);
                            else
                                info.checksum = getColumnChecksum(stmt_base, SVNWCDbSchema.BASE_NODE__Fields.checksum);
                        } catch (SVNException e) {
                            SVNErrorMessage err = SVNErrorMessage.create(e.getErrorMessage().getErrorCode(), "The node ''{0}'' has a corrupt checksum value.", localAbsPath);
                            SVNErrorManager.error(err, SVNLogType.WC);
                        }
                    }
                }
                if (f.contains(InfoField.translatedSize)) {
                    if (have_work)
                        info.translatedSize = getTranslatedSize(stmt_work, SVNWCDbSchema.WORKING_NODE__Fields.translated_size);
                    else
                        info.translatedSize = getTranslatedSize(stmt_base, SVNWCDbSchema.BASE_NODE__Fields.translated_size);
                }
                if (f.contains(InfoField.target)) {
                    if (node_kind != SVNWCDbKind.Symlink)
                        info.target = null;
                    else if (have_work)
                        info.target = SVNFileUtil.createFilePath(getColumnText(stmt_work, SVNWCDbSchema.WORKING_NODE__Fields.symlink_target));
                    else
                        info.target = SVNFileUtil.createFilePath(getColumnText(stmt_base, SVNWCDbSchema.BASE_NODE__Fields.symlink_target));
                }
                if (f.contains(InfoField.changelist)) {
                    if (have_act)
                        info.changelist = getColumnText(stmt_act, SVNWCDbSchema.ACTUAL_NODE__Fields.changelist);
                    else
                        info.changelist = null;
                }
                if (f.contains(InfoField.originalReposRelpath)) {
                    if (have_work && !isColumnNull(stmt_work, SVNWCDbSchema.WORKING_NODE__Fields.copyfrom_repos_path))
                        info.originalReposRelpath = SVNFileUtil.createFilePath(getColumnText(stmt_work, SVNWCDbSchema.WORKING_NODE__Fields.copyfrom_repos_path));
                    else
                        info.originalReposRelpath = null;
                }
                if (!have_work || isColumnNull(stmt_work, SVNWCDbSchema.WORKING_NODE__Fields.copyfrom_repos_id)) {
                    if (f.contains(InfoField.originalRootUrl))
                        info.originalRootUrl = null;
                    if (f.contains(InfoField.originalUuid))
                        info.originalUuid = null;
                } else if (f.contains(InfoField.originalRootUrl) || f.contains(InfoField.originalUuid)) {
                    /* Fetch repository information via COPYFROM_REPOS_ID. */
                    final ReposInfo reposInfo = fetchReposInfo(pdh.getWCRoot().getSDb(), getColumnInt64(stmt_work, SVNWCDbSchema.WORKING_NODE__Fields.copyfrom_repos_id));
                    info.originalRootUrl = SVNURL.parseURIEncoded(reposInfo.reposRootUrl);
                    info.originalUuid = reposInfo.reposUuid;
                }
                if (f.contains(InfoField.originalRevision)) {
                    if (have_work)
                        info.originalRevision = getColumnRevNum(stmt_work, SVNWCDbSchema.WORKING_NODE__Fields.copyfrom_revnum);
                    else
                        info.originalRevision = INVALID_REVNUM;
                }
                if (f.contains(InfoField.textMod)) {
                    /* ### fix this */
                    info.textMod = false;
                }
                if (f.contains(InfoField.propsMod)) {
                    info.propsMod = have_act && !isColumnNull(stmt_act, SVNWCDbSchema.ACTUAL_NODE__Fields.properties);
                }
                if (f.contains(InfoField.conflicted)) {
                    if (have_act) {
                        info.conflicted = getColumnText(stmt_act, SVNWCDbSchema.ACTUAL_NODE__Fields.conflict_old) != null || /* old */
                        getColumnText(stmt_act, SVNWCDbSchema.ACTUAL_NODE__Fields.conflict_new) != null || /* new */
                        getColumnText(stmt_act, SVNWCDbSchema.ACTUAL_NODE__Fields.conflict_working) != null || /* working */
                        getColumnText(stmt_act, SVNWCDbSchema.ACTUAL_NODE__Fields.prop_reject) != null; /* prop_reject */

                        /*
                         * At the end of this function we check for tree
                         * conflicts
                         */
                    } else
                        info.conflicted = false;
                }
                if (f.contains(InfoField.lock)) {
                    final SVNSqlJetStatement stmt_base_lock = stmt_base.getJoinedStatement(SVNWCDbSchema.LOCK.toString());
                    if (isColumnNull(stmt_base_lock, SVNWCDbSchema.LOCK__Fields.lock_token))
                        info.lock = null;
                    else {
                        info.lock = new SVNWCDbLock();
                        info.lock.token = getColumnText(stmt_base_lock, SVNWCDbSchema.LOCK__Fields.lock_token);
                        if (!isColumnNull(stmt_base_lock, SVNWCDbSchema.LOCK__Fields.lock_owner))
                            info.lock.owner = getColumnText(stmt_base_lock, SVNWCDbSchema.LOCK__Fields.lock_owner);
                        if (!isColumnNull(stmt_base_lock, SVNWCDbSchema.LOCK__Fields.lock_comment))
                            info.lock.comment = getColumnText(stmt_base_lock, SVNWCDbSchema.LOCK__Fields.lock_comment);
                        if (!isColumnNull(stmt_base_lock, SVNWCDbSchema.LOCK__Fields.lock_date))
                            info.lock.date = new Date(getColumnInt64(stmt_base_lock, SVNWCDbSchema.LOCK__Fields.lock_date));
                    }
                }
            } else if (have_act) {
                /*
                 * A row in ACTUAL_NODE should never exist without a
                 * corresponding node in BASE_NODE and/or WORKING_NODE.
                 */
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_CORRUPT, "Corrupt data for ''{0}''", localAbsPath);
                SVNErrorManager.error(err, SVNLogType.WC);
            } else {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_PATH_NOT_FOUND, "The node ''{0}'' was not found.", localAbsPath);
                SVNErrorManager.error(err, SVNLogType.WC);
            }
        } finally {
            try {
                stmt_base.reset();
            } finally {
                try {
                    stmt_work.reset();
                } finally {
                    stmt_act.reset();
                }
            }
        }

        /*
         * ### And finally, check for tree conflicts via parent. This reuses
         * stmt_act and throws an error in Sqlite if we do it directly
         */
        if (f.contains(InfoField.conflicted) && !info.conflicted) {
            final SVNTreeConflictDescription cd = opReadTreeConflict(localAbsPath);
            info.conflicted = (cd != null);
        }

        return info;
    }

    public SVNWCDbKind readKind(File localAbsPath, boolean allowMissing) throws SVNException {
        try {
            final WCDbInfo info = readInfo(localAbsPath, InfoField.kind);
            return info.kind;
        } catch (SVNException e) {
            if (allowMissing && e.getErrorMessage().getErrorCode() == SVNErrorCode.WC_PATH_NOT_FOUND) {
                return SVNWCDbKind.Unknown;
            }
            throw e;
        }
    }

    public InputStream readPristine(File wcRootAbsPath, SVNChecksum sha1Checksum) throws SVNException {
        assert (isAbsolute(wcRootAbsPath));
        assert (sha1Checksum != null);

        /*
         * ### Transitional: accept MD-5 and look up the SHA-1. Return an error
         * if the pristine text is not in the store.
         */
        if (sha1Checksum.getKind() != SVNChecksumKind.SHA1)
            sha1Checksum = getPristineSHA1(wcRootAbsPath, sha1Checksum);
        assert (sha1Checksum.getKind() == SVNChecksumKind.SHA1);

        final DirParsedInfo parsed = parseDir(wcRootAbsPath, Mode.ReadOnly);
        SVNWCDbDir pdh = parsed.wcDbDir;
        verifyDirUsable(pdh);

        /* ### should we look in the PRISTINE table for anything? */

        File pristine_abspath = getPristineFileName(pdh, sha1Checksum, false);
        return SVNFileUtil.openFileForReading(pristine_abspath);

    }

    private File getPristineFileName(SVNWCDbDir pdh, SVNChecksum sha1Checksum, boolean createSubdir) {
        /* ### code is in transition. make sure we have the proper data. */
        assert (pdh.getWCRoot() != null);
        assert (sha1Checksum != null);
        assert (sha1Checksum.getKind() == SVNChecksumKind.SHA1);

        /*
         * ### need to fix this to use a symbol for ".svn". we don't need ### to
         * use join_many since we know "/" is the separator for ### internal
         * canonical paths
         */
        File base_dir_abspath = SVNFileUtil.createFilePath(SVNFileUtil.createFilePath(pdh.getWCRoot().getAbsPath(), SVNFileUtil.getAdminDirectoryName()), PRISTINE_STORAGE_RELPATH);

        String hexdigest = sha1Checksum.getDigest();

        /* We should have a valid checksum and (thus) a valid digest. */
        assert (hexdigest != null);

        /* The file is located at DIR/.svn/pristine/XX/XXYYZZ... */
        return SVNFileUtil.createFilePath(base_dir_abspath, hexdigest);
    }

    public SVNProperties readPristineProperties(File localAbsPath) throws SVNException {
        boolean have_row;

        SVNSqlJetStatement stmt = getStatementForPath(localAbsPath, SVNWCDbStatements.SELECT_WORKING_PROPS);
        try {

            have_row = stmt.next();

            /*
             * If there is a WORKING row, then examine its status:
             *
             * For adds/copies/moves, then pristine properties are in this row.
             *
             * For deletes, the pristines may be located here (as a result of a
             * copy/move-here), or they are located in BASE. ### right now, we
             * don't have a strong definition yet. moving to the ### proposed
             * NODE_DATA system will create more determinism around ### where
             * props are located and their relation to layered operations.
             */
            if (have_row) {
                SVNWCDbStatus presence;

                /*
                 * For "base-deleted", it is obvious the pristine props are
                 * located in the BASE table. Fall through to fetch them.
                 *
                 * ### for regular deletes, the properties should be in the
                 * WORKING ### row. though operation layering and the suggested
                 * NODE_DATA may ### really be needed to ensure the props are
                 * always available, ### and what "pristine" really means.
                 */
                presence = getColumnToken(stmt, 1, presenceMap2);
                if (presence != SVNWCDbStatus.BaseDeleted) {
                    return getColumnProperties(stmt, 0);
                }
            }
        } finally {
            stmt.reset();
        }

        /* No WORKING node, so the props must be in the BASE node. */
        return getBaseProps(localAbsPath);

    }

    public String readProperty(File localAbsPath, String propname) throws SVNException {
        // TODO
        throw new UnsupportedOperationException();
    }

    public SVNProperties readProperties(File localAbsPath) throws SVNException {
        SVNProperties props = null;
        boolean have_row = false;
        SVNSqlJetStatement stmt = getStatementForPath(localAbsPath, SVNWCDbStatements.SELECT_ACTUAL_PROPS);
        try {
            have_row = stmt.next();

            if (have_row && !isColumnNull(stmt, 0)) {
                props = getColumnProperties(stmt, 0);
            } else
                have_row = false;
        } finally {
            stmt.reset();
        }

        if (have_row && props != null)
            return props;

        /* No local changes. Return the pristine props for this node. */
        props = readPristineProperties(localAbsPath);
        if (props == null) {
            /*
             * Pristine properties are not defined for this node. ### we need to
             * determine whether this node is in a state that ### allows for
             * ACTUAL properties (ie. not deleted). for now, ### just say all
             * nodes, no matter the state, have at least an ### empty set of
             * props.
             */
            return new SVNProperties();
        }

        return props;
    }

    private SVNProperties getColumnProperties(SVNSqlJetStatement stmt, int f) throws SVNException {
        final byte[] val = stmt.getColumnBlob(f);
        if (val == null) {
            return null;
        }
        final SVNSkel skel = SVNSkel.parse(val);
        return SVNProperties.wrap(skel.parsePropList());
    }

    private SVNSqlJetStatement getStatementForPath(File localAbsPath, SVNWCDbStatements statementIndex) throws SVNException {
        assert (isAbsolute(localAbsPath));
        final DirParsedInfo parsed = parseDir(localAbsPath, Mode.ReadWrite);
        verifyDirUsable(parsed.wcDbDir);
        final SVNWCDbRoot wcRoot = parsed.wcDbDir.getWCRoot();
        final SVNSqlJetStatement statement = wcRoot.getSDb().getStatement(statementIndex);
        statement.bindf("is", wcRoot.getWcId(), SVNFileUtil.getFilePath(parsed.localRelPath));
        return statement;
    }

    public void removeBase(File localAbsPath) throws SVNException {
        // TODO
        throw new UnsupportedOperationException();
    }

    public void removeLock(File localAbsPath) throws SVNException {
        // TODO
        throw new UnsupportedOperationException();
    }

    public void removePristine(File wcRootAbsPath, SVNChecksum sha1Checksum) throws SVNException {
        // TODO
        throw new UnsupportedOperationException();
    }

    public void removeWCLock(File localAbspath) throws SVNException {
        // TODO
        throw new UnsupportedOperationException();
    }

    public void repairPristine(File wcRootAbsPath, SVNChecksum sha1Checksum) throws SVNException {
        // TODO
        throw new UnsupportedOperationException();
    }

    public WCDbAdditionInfo scanAddition(File localAbsPath, AdditionInfoField... fields) throws SVNException {
        assert (isAbsolute(localAbsPath));

        EnumSet<AdditionInfoField> f = getInfoFields(AdditionInfoField.class, fields);

        File current_abspath = localAbsPath;

        File child_abspath = null;
        File build_relpath = SVNFileUtil.createFilePath("");

        boolean found_info = false;

        /*
         * Initialize all the OUT parameters. Generally, we'll only be filling
         * in a subset of these, so it is easier to init all up front. Note that
         * the STATUS parameter will be initialized once we read the status of
         * the specified node.
         */
        WCDbAdditionInfo additionInfo = new WCDbAdditionInfo();
        additionInfo.originalRevision = INVALID_REVNUM;

        DirParsedInfo parsed = parseDir(localAbsPath, Mode.ReadOnly);
        SVNWCDbDir pdh = parsed.wcDbDir;
        File current_relpath = parsed.localRelPath;
        verifyDirUsable(pdh);

        while (true) {

            boolean have_row;
            SVNWCDbStatus presence;

            /* ### is it faster to fetch fewer columns? */
            SVNSqlJetStatement stmt = pdh.getWCRoot().getSDb().getStatement(SVNWCDbStatements.SELECT_WORKING_NODE);
            try {
                stmt.bindf("is", pdh.getWCRoot().getWcId(), SVNFileUtil.getFilePath(current_relpath));
                have_row = stmt.next();

                if (!have_row) {
                    if (current_abspath == localAbsPath) {
                        /* ### maybe we should return a usage error instead? */
                        SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_PATH_NOT_FOUND, "The node ''{0}'' was not found.", localAbsPath);
                        SVNErrorManager.error(err, SVNLogType.WC);
                    }

                    /*
                     * We just fell off the top of the WORKING tree. If we
                     * haven't found the operation root, then the child node
                     * that we just left was that root.
                     */
                    if (f.contains(AdditionInfoField.opRootAbsPath) && additionInfo.opRootAbsPath == null) {
                        assert (child_abspath != null);
                        additionInfo.opRootAbsPath = child_abspath;
                    }

                    /*
                     * This node was added/copied/moved and has an implicit
                     * location in the repository. We now need to traverse BASE
                     * nodes looking for repository info.
                     */
                    break;
                }

                presence = getColumnToken(stmt, 0, presenceMap2);

                /* Record information from the starting node. */
                if (current_abspath == localAbsPath) {
                    /* The starting node should exist normally. */
                    if (presence != SVNWCDbStatus.Normal) {
                        SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_PATH_UNEXPECTED_STATUS, "Expected node ''{0}'' to be added.", localAbsPath);
                        SVNErrorManager.error(err, SVNLogType.WC);
                    }

                    /*
                     * ### in per-dir operation, it is possible that we just
                     * fetched ### the parent stub. examine the KIND field. ###
                     * ### scan_addition is NOT allowed for an obstructed_add
                     * status ### from read_info. there may be key information
                     * in the ### subdir record (eg. copyfrom_*).
                     */
                    {
                        SVNWCDbKind kind = getColumnToken(stmt, 1, kindMap2);
                        assert (kind != SVNWCDbKind.Subdir);
                    }

                    /*
                     * Provide the default status; we'll override as
                     * appropriate.
                     */
                    if (f.contains(AdditionInfoField.status))
                        additionInfo.status = SVNWCDbStatus.Added;
                }

                /*
                 * We want the operation closest to the start node, and then we
                 * ignore any operations on its ancestors.
                 */
                if (!found_info && presence == SVNWCDbStatus.Normal && !isColumnNull(stmt, 9 /* copyfrom_repos_id */)) {
                    if (f.contains(AdditionInfoField.status)) {
                        if (getColumnBoolean(stmt, 12 /* moved_here */))
                            additionInfo.status = SVNWCDbStatus.MovedHere;
                        else
                            additionInfo.status = SVNWCDbStatus.Copied;
                    }
                    if (f.contains(AdditionInfoField.opRootAbsPath))
                        additionInfo.opRootAbsPath = current_abspath;
                    if (f.contains(AdditionInfoField.originalReposRelPath))
                        additionInfo.originalReposRelPath = isColumnNull(stmt, 10) ? null : SVNFileUtil.createFilePath(getColumnText(stmt, 10));
                    if (f.contains(AdditionInfoField.originalRootUrl) || f.contains(AdditionInfoField.originalUuid)) {
                        ReposInfo reposInfo = fetchReposInfo(pdh.getWCRoot().getSDb(), getColumnInt64(stmt, 9));
                        additionInfo.originalRootUrl = reposInfo.reposRootUrl == null ? null : SVNURL.parseURIEncoded(reposInfo.reposRootUrl);
                        additionInfo.originalUuid = reposInfo.reposUuid;
                    }
                    if (f.contains(AdditionInfoField.originalRevision))
                        additionInfo.originalRevision = getColumnRevNum(stmt, 11);

                    /*
                     * We may have to keep tracking upwards for REPOS_* values.
                     * If they're not needed, then just return.
                     */
                    if ((!f.contains(AdditionInfoField.reposRelPath)) && (!f.contains(AdditionInfoField.reposRootUrl)) && (!f.contains(AdditionInfoField.reposUuid)))
                        return additionInfo;

                    /*
                     * We've found the info we needed. Scan for the top of the
                     * WORKING tree, and then the REPOS_* information.
                     */
                    found_info = true;
                }
            } finally {
                stmt.reset();
            }

            /*
             * If the caller wants to know the starting node's REPOS_RELPATH,
             * then keep track of what we're stripping off the ABSPATH as we
             * traverse up the tree.
             */
            if (f.contains(AdditionInfoField.reposRelPath)) {
                // TODO very weird code, sergey
                build_relpath = SVNFileUtil.createFilePath(SVNFileUtil.getFileName(current_abspath), build_relpath.getPath());
            }

            /*
             * Move to the parent node. Remember the abspath to this node, since
             * it could be the root of an add/delete.
             */
            child_abspath = current_abspath;
            if (current_abspath.equals(pdh.getLocalAbsPath())) {
                /* The current node is a directory, so move to the parent dir. */
                pdh = navigateToParent(pdh, Mode.ReadOnly, true);
            }
            current_abspath = pdh.getLocalAbsPath();
            current_relpath = pdh.computeRelPath();
        }

        /*
         * If we're here, then we have an added/copied/moved (start) node, and
         * CURRENT_ABSPATH now points to a BASE node. Figure out the repository
         * information for the current node, and use that to compute the start
         * node's repository information.
         */
        if (f.contains(AdditionInfoField.reposRelPath) || f.contains(AdditionInfoField.reposRootUrl) || f.contains(AdditionInfoField.reposUuid)) {
            /*
             * ### unwrap this. we can optimize away the
             * svn_wc__db_pdh_parse_local_abspath().
             */
            WCDbRepositoryInfo baseReposInfo = scanBaseRepository(current_abspath, RepositoryInfoField.values());
            File base_relpath = baseReposInfo.relPath;
            additionInfo.reposRootUrl = baseReposInfo.rootUrl;
            additionInfo.reposUuid = baseReposInfo.uuid;

            if (f.contains(AdditionInfoField.reposRelPath))
                additionInfo.reposRelPath = SVNFileUtil.createFilePath(base_relpath, build_relpath.getPath());
        }

        return additionInfo;
    }

    public WCDbRepositoryInfo scanBaseRepository(File localAbsPath, RepositoryInfoField... fields) throws SVNException {
        assert (isAbsolute(localAbsPath));
        final EnumSet<RepositoryInfoField> f = getInfoFields(RepositoryInfoField.class, fields);
        final DirParsedInfo parsed = parseDir(localAbsPath, Mode.ReadOnly);
        final SVNWCDbDir pdh = parsed.wcDbDir;
        final File localRelPath = parsed.localRelPath;
        verifyDirUsable(pdh);
        final WCDbRepositoryInfo reposInfo = new WCDbRepositoryInfo();
        final long reposId = scanUpwardsForRepos(reposInfo, pdh.getWCRoot(), localAbsPath, localRelPath);
        if (f.contains(RepositoryInfoField.rootUrl) || f.contains(RepositoryInfoField.uuid)) {
            fetchReposInfo(reposInfo, pdh.getWCRoot().getSDb(), reposId);
        }
        return reposInfo;
    }

    /**
     * Scan from LOCAL_RELPATH upwards through parent nodes until we find a
     * parent that has values in the 'repos_id' and 'repos_relpath' columns.
     * Return that information in REPOS_ID and REPOS_RELPATH (either may be
     * NULL). Use LOCAL_ABSPATH for diagnostics
     */
    private static long scanUpwardsForRepos(WCDbRepositoryInfo reposInfo, SVNWCDbRoot wcroot, File localAbsPath, File localRelPath) throws SVNException {
        assert (reposInfo != null);
        assert (wcroot != null);
        assert (wcroot.getSDb() != null && wcroot.getWcId() != UNKNOWN_WC_ID);

        File relpath_suffix = SVNFileUtil.createFilePath("");
        String current_basename = SVNFileUtil.getFileName(localRelPath);
        File current_relpath = localRelPath;

        /* ### is it faster to fetch fewer columns? */
        SVNSqlJetStatement stmt = wcroot.getSDb().getStatement(SVNWCDbStatements.SELECT_BASE_NODE);
        while (true) {
            try {
                /* Get the current node's repository information. */
                stmt.bindf("is", wcroot.getWcId(), SVNFileUtil.getFilePath(current_relpath));
                boolean have_row = stmt.next();
                if (!have_row) {
                    /*
                     * If we moved upwards at least once, or we're looking at
                     * the root directory of this WCROOT, then something is
                     * wrong.
                     */
                    if ((relpath_suffix != null && !"".equals(relpath_suffix.getPath())) || localRelPath == null) {
                        SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_CORRUPT, "Parent(s) of ''{0}'' should have been present.", localAbsPath);
                        SVNErrorManager.error(err, SVNLogType.WC);
                    } else {
                        SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_PATH_NOT_FOUND, "The node ''{0}'' was not found.", localAbsPath);
                        SVNErrorManager.error(err, SVNLogType.WC);
                    }
                }

                /* Did we find some non-NULL repository columns? */
                if (!isColumnNull(stmt, 0)) {
                    /* If one is non-NULL, then so should the other. */
                    assert (!isColumnNull(stmt, 1));
                    /*
                     * Given the node's relpath, append all the segments that we
                     * stripped as we scanned upwards.
                     */
                    reposInfo.relPath = SVNFileUtil.createFilePath(getColumnText(stmt, 1), relpath_suffix.getPath());
                    long repos_id = getColumnInt64(stmt, 0);
                    return repos_id;
                }

            } finally {
                stmt.reset();
            }

            if (current_relpath == null) {
                /*
                 * We scanned all the way up, and did not find the information.
                 * Something is corrupt in the database.
                 */
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_CORRUPT, "Parent(s) of ''{0}'' should have repository information.", localAbsPath);
                SVNErrorManager.error(err, SVNLogType.WC);
            }

            /*
             * Strip a path segment off the end, and append it to the suffix
             * that we'll use when we finally find a base relpath.
             */
            current_basename = SVNFileUtil.getFileName(current_relpath);
            current_relpath = SVNFileUtil.getFileDir(current_relpath);
            relpath_suffix = SVNFileUtil.createFilePath(relpath_suffix, current_basename);

            /* Loop to try the parent. */

            /*
             * ### strictly speaking, moving to the parent could send us to a
             * ### different SDB, and (thus) we would need to fetch STMT again.
             * ### but we happen to know the parent is *always* in the same db,
             * ### and will have the repos info.
             */
        }
    }

    private static void fetchReposInfo(WCDbRepositoryInfo reposInfo, SVNSqlJetDb sdb, long reposId) throws SVNException {
        final SVNSqlJetStatement stmt = sdb.getStatement(SVNWCDbStatements.SELECT_REPOSITORY_BY_ID);
        try {
            stmt.bindf("i", reposId);
            boolean have_row = stmt.next();
            if (!have_row) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_CORRUPT, "No REPOSITORY table entry for id ''{0}''", reposId);
                SVNErrorManager.error(err, SVNLogType.WC);
            }
            reposInfo.rootUrl = !isColumnNull(stmt, 0) ? SVNURL.parseURIEncoded(getColumnText(stmt, 0)) : null;
            reposInfo.uuid = getColumnText(stmt, 1);
        } finally {
            stmt.reset();
        }
    }

    public WCDbDeletionInfo scanDeletion(File localAbsPath, DeletionInfoField... fields) throws SVNException {
        assert (isAbsolute(localAbsPath));

        final EnumSet<DeletionInfoField> f = getInfoFields(DeletionInfoField.class, fields);

        /* Initialize all the OUT parameters. */
        final WCDbDeletionInfo deletionInfo = new WCDbDeletionInfo();

        File current_abspath = localAbsPath;
        File child_abspath = null;
        boolean child_has_base = false;
        boolean found_moved_to = false;

        /*
         * Initialize to something that won't denote an important parent/child
         * transition.
         */

        SVNWCDbStatus child_presence = SVNWCDbStatus.BaseDeleted;

        final DirParsedInfo parsed = parseDir(localAbsPath, Mode.ReadOnly);
        SVNWCDbDir pdh = parsed.wcDbDir;
        File current_relpath = parsed.localRelPath;
        verifyDirUsable(pdh);

        while (true) {
            SVNSqlJetStatement stmt = pdh.getWCRoot().getSDb().getStatement(SVNWCDbStatements.SELECT_DELETION_INFO);

            try {
                stmt.bindf("is", pdh.getWCRoot().getWcId(), SVNFileUtil.getFilePath(current_relpath));

                boolean have_row = stmt.next();

                if (!have_row) {
                    /* There better be a row for the starting node! */
                    if (current_abspath == localAbsPath) {
                        stmt.reset();
                        SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_PATH_NOT_FOUND, "The node ''{0}'' was not found.", localAbsPath);
                        SVNErrorManager.error(err, SVNLogType.WC);
                    }

                    /* There are no values, so go ahead and reset the stmt now. */
                    stmt.reset();

                    /*
                     * No row means no WORKING node at this path, which means we
                     * just fell off the top of the WORKING tree.
                     *
                     * If the child was not-present this implies the root of the
                     * (added) WORKING subtree was deleted. This can occur
                     * during post-commit processing when the copied parent that
                     * was in the WORKING tree has been moved to the BASE tree.
                     */
                    if (f.contains(DeletionInfoField.workDelAbsPath) && child_presence == SVNWCDbStatus.NotPresent && deletionInfo.workDelAbsPath == null) {
                        deletionInfo.workDelAbsPath = child_abspath;
                    }

                    /*
                     * If the child did not have a BASE node associated with it,
                     * then we're looking at a deletion that occurred within an
                     * added tree. There is no root of a deleted/replaced BASE
                     * tree.
                     *
                     * If the child was base-deleted, then the whole tree is a
                     * simple (explicit) deletion of the BASE tree.
                     *
                     * If the child was normal, then it is the root of a
                     * replacement, which means an (implicit) deletion of the
                     * BASE tree.
                     *
                     * In both cases, set the root of the operation (if we have
                     * not already set it as part of a moved-away).
                     */
                    if (f.contains(DeletionInfoField.baseDelAbsPath) && child_has_base && deletionInfo.baseDelAbsPath == null) {
                        deletionInfo.baseDelAbsPath = child_abspath;
                    }

                    /*
                     * We found whatever roots we needed. This BASE node and its
                     * ancestors are unchanged, so we're done.
                     */
                    break;
                }

                /*
                 * We need the presence of the WORKING node. Note that legal
                 * values are: normal, not-present, base-deleted.
                 */
                SVNWCDbStatus work_presence = getColumnToken(stmt, SVNWCDbSchema.WORKING_NODE__Fields.presence, presenceMap2);

                /* The starting node should be deleted. */
                if (current_abspath == localAbsPath && work_presence != SVNWCDbStatus.NotPresent && work_presence != SVNWCDbStatus.BaseDeleted) {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_PATH_UNEXPECTED_STATUS, "Expected node ''{0}'' to be deleted.", localAbsPath);
                    SVNErrorManager.error(err, SVNLogType.WC);
                }
                assert (work_presence == SVNWCDbStatus.Normal || work_presence == SVNWCDbStatus.NotPresent || work_presence == SVNWCDbStatus.BaseDeleted);

                SVNSqlJetStatement baseStmt = stmt.getJoinedStatement(SVNWCDbSchema.BASE_NODE);
                try {
                    boolean have_base = baseStmt != null && !isColumnNull(baseStmt, SVNWCDbSchema.BASE_NODE__Fields.presence);

                    if (have_base) {
                        SVNWCDbStatus base_presence = getColumnToken(baseStmt, SVNWCDbSchema.BASE_NODE__Fields.presence, presenceMap2);

                        /* Only "normal" and "not-present" are allowed. */
                        assert (base_presence == SVNWCDbStatus.Normal || base_presence == SVNWCDbStatus.NotPresent

                        /*
                         * ### there are cases where the BASE node is ### marked
                         * as incomplete. we should treat this ### as a "normal"
                         * node for the purposes of ### this function. we really
                         * should not allow ### it, but this situation occurs
                         * within the ### following tests: ### switch_tests 31
                         * ### update_tests 46 ### update_tests 53
                         */
                        || base_presence == SVNWCDbStatus.Incomplete);

                        /* ### see above comment */
                        if (base_presence == SVNWCDbStatus.Incomplete)
                            base_presence = SVNWCDbStatus.Normal;

                        /*
                         * If a BASE node is marked as not-present, then we'll
                         * ignore it within this function. That status is simply
                         * a bookkeeping gimmick, not a real node that may have
                         * been deleted.
                         */

                        /*
                         * If we're looking at a present BASE node, *and* there
                         * is a WORKING node (present or deleted), then a
                         * replacement has occurred here or in an ancestor.
                         */
                        if (f.contains(DeletionInfoField.baseReplaced) && base_presence == SVNWCDbStatus.Normal && work_presence != SVNWCDbStatus.BaseDeleted) {
                            deletionInfo.baseReplaced = true;
                        }
                    }

                    /* Only grab the nearest ancestor. */
                    if (!found_moved_to && (f.contains(DeletionInfoField.movedToAbsPath) || f.contains(DeletionInfoField.baseDelAbsPath))
                            && !isColumnNull(stmt, SVNWCDbSchema.WORKING_NODE__Fields.moved_to)) {
                        /* There better be a BASE_NODE (that was moved-away). */
                        assert (have_base);

                        found_moved_to = true;

                        /* This makes things easy. It's the BASE_DEL_ABSPATH! */
                        if (f.contains(DeletionInfoField.baseDelAbsPath))
                            deletionInfo.baseDelAbsPath = current_abspath;

                        if (f.contains(DeletionInfoField.movedToAbsPath))
                            deletionInfo.movedToAbsPath = SVNFileUtil.createFilePath(pdh.getWCRoot().getAbsPath(), getColumnText(stmt, SVNWCDbSchema.WORKING_NODE__Fields.moved_to));
                    }

                    if (f.contains(DeletionInfoField.workDelAbsPath) && work_presence == SVNWCDbStatus.Normal && child_presence == SVNWCDbStatus.NotPresent) {
                        /*
                         * Parent is normal, but child was deleted. Therefore,
                         * the child is the root of a WORKING subtree deletion.
                         */
                        deletionInfo.workDelAbsPath = child_abspath;
                    }

                    /*
                     * Move to the parent node. Remember the information about
                     * this node for our parent to use.
                     */
                    child_abspath = current_abspath;
                    child_presence = work_presence;
                    child_has_base = have_base;
                    if (current_abspath.equals(pdh.getLocalAbsPath())) {
                        /*
                         * The current node is a directory, so move to the
                         * parent dir.
                         */
                        pdh = navigateToParent(pdh, Mode.ReadOnly, true);
                    }
                    current_abspath = pdh.getLocalAbsPath();
                    current_relpath = pdh.computeRelPath();
                } finally {
                    baseStmt.reset();
                }
            } finally {
                stmt.reset();
            }

        }

        return deletionInfo;

    }

    private SVNWCDbDir navigateToParent(SVNWCDbDir childPdh, Mode sMode, boolean verifyParentStub) throws SVNException {

        SVNWCDbDir parentPdh;

        if ((parentPdh = childPdh.getParent()) != null && parentPdh.getWCRoot() != null)
            return parentPdh;

        /* Make sure we don't see the root as its own parent */
        assert (SVNFileUtil.getFileDir(childPdh.getLocalAbsPath()) != null);

        File parentAbsPath = SVNFileUtil.getFileDir(childPdh.getLocalAbsPath());
        DirParsedInfo parsed = parseDir(parentAbsPath, sMode);
        parentPdh = parsed.wcDbDir;
        verifyDirUsable(parentPdh);

        /* Check that the parent has an entry for the child */
        SVNSqlJetStatement stmtBase = parentPdh.getWCRoot().getSDb().getStatement(SVNWCDbStatements.SELECT_SUBDIR_BASE);
        SVNSqlJetStatement stmtWork = parentPdh.getWCRoot().getSDb().getStatement(SVNWCDbStatements.SELECT_SUBDIR_WORKING);
        try {
            stmtBase.bindf("is", parentPdh.getWCRoot().getWcId(), SVNFileUtil.getFileName(childPdh.getLocalAbsPath()));
            stmtWork.bindf("is", parentPdh.getWCRoot().getWcId(), SVNFileUtil.getFileName(childPdh.getLocalAbsPath()));
            boolean got_row = stmtBase.next() || stmtWork.next();

            if (!got_row && verifyParentStub) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_NOT_WORKING_COPY, "''{0}'' does not have a parent.", childPdh.getLocalAbsPath());
                SVNErrorManager.error(err, SVNLogType.WC);
            }

            if (got_row) {
                childPdh.setParent(parentPdh);
            }

        } finally {
            stmtBase.reset();
            stmtWork.reset();
        }

        return parentPdh;

    }

    public void setBaseDavCache(File localAbsPath, SVNProperties props) throws SVNException {
        // TODO
        throw new UnsupportedOperationException();
    }

    public void setBasePropsTemp(File localAbsPath, SVNProperties props) throws SVNException {
        // TODO
        throw new UnsupportedOperationException();
    }

    public void setWCLock(File localAbspath, int levelsToLock) throws SVNException {
        // TODO
        throw new UnsupportedOperationException();
    }

    public void setWorkingPropsTemp(File localAbsPath, SVNProperties props) throws SVNException {
        // TODO
        throw new UnsupportedOperationException();
    }

    public File toRelPath(File localAbsPath) throws SVNException {
        // TODO
        throw new UnsupportedOperationException();
    }

    public String getFileExternalTemp(File path) throws SVNException {
        final SVNSqlJetStatement stmt = getStatementForPath(path, SVNWCDbStatements.SELECT_FILE_EXTERNAL);
        try {
            boolean have_row = stmt.next();
            /*
             * ### file externals are pretty bogus right now. they have just a
             * ### WORKING_NODE for a while, eventually settling into just a
             * BASE_NODE. ### until we get all that fixed, let's just not worry
             * about raising ### an error, and just say it isn't a file
             * external.
             */
            if (!have_row)
                return null;
            /* see below: *serialized_file_external = ... */
            return getColumnText(stmt, 0);
        } finally {
            stmt.reset();
        }
    }

    public boolean determineKeepLocalTemp(File localAbsPath) throws SVNException {
        // TODO
        throw new UnsupportedOperationException();
    }

    public WCDbDirDeletedInfo isDirDeletedTemp(File entryAbspath) throws SVNException {
        // TODO
        throw new UnsupportedOperationException();
    }

    public void cleanupPristine(File wcRootAbsPath) throws SVNException {
        // TODO
        throw new UnsupportedOperationException();
    }

    private long fetchWCId(SVNSqlJetDb sDb) throws SVNException {
        /*
         * ### cheat. we know there is just one WORKING_COPY row, and it has a
         * ### NULL value for local_abspath.
         */
        final SVNSqlJetStatement stmt = sDb.getStatement(SVNWCDbStatements.SELECT_WCROOT_NULL);
        try {
            final boolean have_row = stmt.next();
            if (!have_row) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_CORRUPT, "Missing a row in WCROOT.");
                SVNErrorManager.error(err, SVNLogType.WC);
            }
            // assert (!stmt.isColumnNull("id"));
            return getColumnInt64(stmt, SVNWCDbSchema.WCROOT__Fields.id);
        } finally {
            stmt.reset();
        }
    }

    private static class ReposInfo {

        public String reposRootUrl;
        public String reposUuid;
    }

    private ReposInfo fetchReposInfo(SVNSqlJetDb sDb, long repos_id) throws SVNException {

        ReposInfo info = new ReposInfo();

        SVNSqlJetStatement stmt;
        boolean have_row;

        stmt = sDb.getStatement(SVNWCDbStatements.SELECT_REPOSITORY_BY_ID);
        try {
            stmt.bindf("i", repos_id);
            have_row = stmt.next();
            if (!have_row) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_CORRUPT, "No REPOSITORY table entry for id ''{0}''", repos_id);
                SVNErrorManager.error(err, SVNLogType.WC);
                return info;
            }

            info.reposRootUrl = getColumnText(stmt, SVNWCDbSchema.REPOSITORY__Fields.root);
            info.reposUuid = getColumnText(stmt, SVNWCDbSchema.REPOSITORY__Fields.root);

        } finally {
            stmt.reset();
        }
        return info;
    }

    private static SVNSqlJetDb openDb(File dirAbsPath, String sdbFileName, Mode sMode) throws SVNException, SqlJetException {
        return SVNSqlJetDb.open(admChild(dirAbsPath, sdbFileName), sMode);
    }

    private static File admChild(File dirAbsPath, String sdbFileName) {
        return SVNFileUtil.createFilePath(SVNFileUtil.createFilePath(dirAbsPath, SVNFileUtil.getAdminDirectoryName()), sdbFileName);
    }

    private static boolean isErrorNOENT(final SVNErrorCode errorCode) {
        return errorCode == SVNErrorCode.ENTRY_NOT_FOUND || errorCode == SVNErrorCode.FS_NOT_FOUND || errorCode == SVNErrorCode.FS_NOT_OPEN || errorCode == SVNErrorCode.FS_NOT_FILE;
    }

    private static void verifyDirUsable(SVNWCDbDir pdh) {
        assert (SVNWCDbDir.isUsable(pdh));
    }

    private static String getColumnText(SVNSqlJetStatement stmt, Enum f) throws SVNException {
        return stmt.getColumnString(f.toString());
    }

    private static boolean isColumnNull(SVNSqlJetStatement stmt, Enum f) throws SVNException {
        return stmt.isColumnNull(f.toString());
    }

    private static long getColumnInt64(SVNSqlJetStatement stmt, Enum f) throws SVNException {
        return stmt.getColumnLong(f.toString());
    }

    private byte[] getColumnBlob(SVNSqlJetStatement stmt, Enum f) throws SVNException {
        return stmt.getColumnBlob(f.toString());
    }

    private static String getColumnText(SVNSqlJetStatement stmt, int f) throws SVNException {
        return stmt.getColumnString(f);
    }

    private static boolean isColumnNull(SVNSqlJetStatement stmt, int f) throws SVNException {
        return stmt.isColumnNull(f);
    }

    private static long getColumnInt64(SVNSqlJetStatement stmt, int f) throws SVNException {
        return stmt.getColumnLong(f);
    }

    private byte[] getColumnBlob(SVNSqlJetStatement stmt, int f) throws SVNException {
        return stmt.getColumnBlob(f);
    }

    private boolean getColumnBoolean(SVNSqlJetStatement stmt, int i) throws SVNException {
        return stmt.getColumnBoolean(i);
    }

    private boolean getColumnBoolean(SVNSqlJetStatement stmt, Enum f) throws SVNException {
        return stmt.getColumnBoolean(f);
    }

    private static SVNChecksum getColumnChecksum(SVNSqlJetStatement stmt, Enum f) throws SVNException {
        final String digest = getColumnText(stmt, f);
        if (digest != null) {
            return SVNChecksum.deserializeChecksum(digest);
        }
        return null;
    }

    private static SVNChecksum getColumnChecksum(SVNSqlJetStatement stmt, int f) throws SVNException {
        final String digest = getColumnText(stmt, f);
        if (digest != null) {
            return SVNChecksum.deserializeChecksum(digest);
        }
        return null;
    }

    private static long getColumnRevNum(SVNSqlJetStatement stmt, int i) throws SVNException {
        if (isColumnNull(stmt, i))
            return ISVNWCDb.INVALID_REVNUM;
        return (int) getColumnInt64(stmt, i);
    }

    private static long getColumnRevNum(SVNSqlJetStatement stmt, Enum f) throws SVNException {
        if (isColumnNull(stmt, f))
            return ISVNWCDb.INVALID_REVNUM;
        return (int) getColumnInt64(stmt, f);
    }

    private static long getTranslatedSize(SVNSqlJetStatement stmt, Enum f) throws SVNException {
        if (isColumnNull(stmt, f))
            return INVALID_FILESIZE;
        return getColumnInt64(stmt, f);
    }

    private static <T extends Enum<T>> T getColumnToken(SVNSqlJetStatement stmt, Enum f, Map<String, T> tokenMap) throws SVNException {
        return tokenMap.get(getColumnText(stmt, f));
    }

    private static <T extends Enum<T>> T getColumnToken(SVNSqlJetStatement stmt, int f, Map<String, T> tokenMap) throws SVNException {
        return tokenMap.get(getColumnText(stmt, f));
    }

}
