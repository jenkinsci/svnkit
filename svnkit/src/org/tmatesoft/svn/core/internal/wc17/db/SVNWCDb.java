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
import java.util.Collections;
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
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.internal.util.SVNSkel;
import org.tmatesoft.svn.core.internal.wc.SVNChecksum;
import org.tmatesoft.svn.core.internal.wc.SVNConfigFile;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.wc.SVNFileType;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.internal.wc.SVNTreeConflictUtil;
import org.tmatesoft.svn.core.internal.wc17.db.ISVNWCDb.WCDbKind;
import org.tmatesoft.svn.core.internal.wc17.db.ISVNWCDb.WCDbAdditionInfo.AdditionInfoField;
import org.tmatesoft.svn.core.internal.wc17.db.ISVNWCDb.WCDbBaseInfo.BaseInfoField;
import org.tmatesoft.svn.core.internal.wc17.db.ISVNWCDb.WCDbDeletionInfo.DeletionInfoField;
import org.tmatesoft.svn.core.internal.wc17.db.ISVNWCDb.WCDbInfo.InfoField;
import org.tmatesoft.svn.core.internal.wc17.db.ISVNWCDb.WCDbRepositoryInfo.RepositoryInfoField;
import org.tmatesoft.svn.core.internal.wc17.db.SVNSqlJetDb.Mode;
import org.tmatesoft.svn.core.internal.wc17.db.SVNWCDbSchema.ACTUAL_NODE__Fields;
import org.tmatesoft.svn.core.internal.wc17.db.statement.SVNWCDbStatements;
import org.tmatesoft.svn.core.wc.ISVNOptions;
import org.tmatesoft.svn.core.wc.SVNConflictDescription;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNTreeConflictDescription;
import org.tmatesoft.svn.util.SVNLogType;

/**
 * @version 1.3
 * @author TMate Software Ltd.
 */
public class SVNWCDb implements ISVNWCDb {

    private static final int FORMAT_FROM_SDB = -1;
    private static final long UNKNOWN_WC_ID = -1;

    private static final EnumMap<WCDbKind, String> kindMap = new EnumMap<WCDbKind, String>(WCDbKind.class);
    private static final HashMap<String, WCDbKind> kindMap2 = new HashMap<String, WCDbKind>();
    static {
        kindMap.put(WCDbKind.File, "file");
        kindMap.put(WCDbKind.Dir, "dir");
        kindMap.put(WCDbKind.Symlink, "symlink");
        kindMap.put(WCDbKind.Subdir, "subdir");
        kindMap.put(WCDbKind.Unknown, "unknown");

        kindMap2.put("file", WCDbKind.File);
        kindMap2.put("dir", WCDbKind.Dir);
        kindMap2.put("symlink", WCDbKind.Symlink);
        kindMap2.put("subdir", WCDbKind.Subdir);
        kindMap2.put("unknown", WCDbKind.Unknown);
    };

    /*
     * Note: we only decode presence values from the database. These are a
     * subset of all the status values.
     */
    private static final EnumMap<WCDbStatus, String> presenceMap = new EnumMap<WCDbStatus, String>(WCDbStatus.class);
    private static final HashMap<String, WCDbStatus> presenceMap2 = new HashMap<String, WCDbStatus>();
    static {
        presenceMap.put(WCDbStatus.Normal, "normal");
        presenceMap.put(WCDbStatus.Absent, "absent");
        presenceMap.put(WCDbStatus.Excluded, "excluded");
        presenceMap.put(WCDbStatus.NotPresent, "not-present");
        presenceMap.put(WCDbStatus.Incomplete, "incomplete");
        presenceMap.put(WCDbStatus.BaseDeleted, "base-deleted");

        presenceMap2.put("normal", WCDbStatus.Normal);
        presenceMap2.put("absent", WCDbStatus.Absent);
        presenceMap2.put("excluded", WCDbStatus.Excluded);
        presenceMap2.put("not-present", WCDbStatus.NotPresent);
        presenceMap2.put("incomplete", WCDbStatus.Incomplete);
        presenceMap2.put("base-deleted", WCDbStatus.BaseDeleted);

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

    private static <E extends Enum<E>> EnumSet<E> getInfoFields(Class<E> clazz, E... fields) {
        final EnumSet<E> set = EnumSet.noneOf(clazz);
        for (E f : fields) {
            set.add(f);
        }
        return set;
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
            try {
                wcRoot.close();
            } catch (SqlJetException e) {
                // TODO
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

    public void addLock(File localAbsPath, WCDbLock lock) throws SVNException {
        // TODO
        throw new UnsupportedOperationException();
    }

    public void addWorkQueue(File wcRootAbsPath, SVNSkel workItem) throws SVNException {
        // TODO
        throw new UnsupportedOperationException();
    }

    public boolean checkPristine(File wcRootAbsPath, SVNChecksum sha1Checksum, WCDbCheckMode mode) throws SVNException {
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
        // TODO
        throw new UnsupportedOperationException();
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

        final DirParsedInfo dir = parseDirLocalAbsPath(localAbsPath, Mode.ReadOnly);
        final SVNWCDbDir pdh = dir.wcDbDir;
        final File localRelPath = dir.localRelPath;

        verifyDirUsable(pdh);

        SVNSqlJetStatement stmt = pdh.getWCRoot().getSDb().getStatement(f.contains(BaseInfoField.lock) ? SVNWCDbStatements.SELECT_BASE_NODE_WITH_LOCK : SVNWCDbStatements.SELECT_BASE_NODE);
        try {
            stmt.bindf("is", pdh.getWCRoot().getWcId(), localRelPath.toString());
            have_row = stmt.next();

            if (have_row) {
                WCDbKind node_kind = getColumnToken(stmt, SVNWCDbSchema.BASE_NODE__Fields.kind, kindMap2);

                if (f.contains(BaseInfoField.kind)) {
                    if (node_kind == WCDbKind.Subdir)
                        info.kind = WCDbKind.Dir;
                    else
                        info.kind = node_kind;
                }
                if (f.contains(BaseInfoField.status)) {
                    info.status = getColumnToken(stmt, SVNWCDbSchema.BASE_NODE__Fields.presence, presenceMap2);

                    if (node_kind == WCDbKind.Subdir && info.status == WCDbStatus.Normal) {
                        /*
                         * We're looking at the subdir record in the *parent*
                         * directory, which implies per-dir .svn subdirs. We
                         * should be looking at the subdir itself; therefore, it
                         * is missing or obstructed in some way. Inform the
                         * caller.
                         */
                        info.status = WCDbStatus.Obstructed;
                    }
                }
                if (f.contains(BaseInfoField.revision)) {
                    info.revision = getColumnRevNum(stmt, SVNWCDbSchema.BASE_NODE__Fields.revnum);
                }
                if (f.contains(BaseInfoField.reposRelPath)) {
                    info.reposRelPath = new File(getColumnText(stmt, SVNWCDbSchema.BASE_NODE__Fields.repos_relpath));
                }
                if (f.contains(BaseInfoField.lock)) {
                    if (isColumnNull(stmt, SVNWCDbSchema.LOCK__Fields.lock_token)) {
                        info.lock = null;
                    } else {
                        info.lock = new WCDbLock();
                        info.lock.token = getColumnText(stmt, SVNWCDbSchema.LOCK__Fields.lock_token);
                        if (!isColumnNull(stmt, SVNWCDbSchema.LOCK__Fields.lock_owner))
                            info.lock.owner = getColumnText(stmt, SVNWCDbSchema.LOCK__Fields.lock_owner);
                        if (!isColumnNull(stmt, SVNWCDbSchema.LOCK__Fields.lock_comment))
                            info.lock.comment = getColumnText(stmt, SVNWCDbSchema.LOCK__Fields.lock_comment);
                        if (!isColumnNull(stmt, SVNWCDbSchema.LOCK__Fields.lock_date))
                            info.lock.date = new Date(getColumnInt64(stmt, SVNWCDbSchema.LOCK__Fields.lock_date));
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
                    if (node_kind != WCDbKind.Dir) {
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
                    if (node_kind != WCDbKind.File) {
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
                    if (node_kind != WCDbKind.Symlink)
                        info.target = null;
                    else
                        info.target = new File(getColumnText(stmt, SVNWCDbSchema.BASE_NODE__Fields.symlink_target));
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
        // TODO
        throw new UnsupportedOperationException();
    }

    public File getPristinePath(File wcRootAbsPath, SVNChecksum sha1Checksum) throws SVNException {
        // TODO
        throw new UnsupportedOperationException();
    }

    public SVNChecksum getPristineSHA1(File wcRootAbsPath, SVNChecksum md5Checksum) throws SVNException {
        // TODO
        throw new UnsupportedOperationException();
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

    public void globalUpdate(File localAbsPath, WCDbKind newKind, File newReposRelpath, long newRevision, SVNProperties newProps, long newChangedRev, Date newChangedDate, String newChangedAuthor,
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
        final DirParsedInfo parsedInfo = parseDirLocalAbsPath(localAbsPath, Mode.ReadWrite);
        SVNWCDbDir pdh = parsedInfo.wcDbDir;
        File localRelPath = parsedInfo.localRelPath;

        verifyDirUsable(pdh);

        /* First check the working node. */
        SVNSqlJetStatement stmt = pdh.getWCRoot().getSDb().getStatement(SVNWCDbStatements.SELECT_WORKING_NODE);
        try {
            stmt.bindf("is", pdh.getWCRoot().getWcId(), localRelPath.toString());
            boolean have_row = stmt.next();
            if (have_row) {
                /*
                 * Note: this can ONLY be an add/copy-here/move-here. It is not
                 * possible to delete a "hidden" node.
                 */
                WCDbStatus work_status = presenceMap2.get(getColumnText(stmt, SVNWCDbSchema.WORKING_NODE__Fields.presence));
                return (work_status == WCDbStatus.Excluded);
            }
        } finally {
            stmt.reset();
        }

        /* Now check the BASE node's status. */
        final WCDbBaseInfo baseInfo = getBaseInfo(localAbsPath, BaseInfoField.status);
        WCDbStatus base_status = baseInfo.status;
        return (base_status == WCDbStatus.Absent || base_status == WCDbStatus.NotPresent || base_status == WCDbStatus.Excluded);
    }

    public static class DirParsedInfo {

        public SVNWCDbDir wcDbDir;
        public File localRelPath;
    }

    public DirParsedInfo parseDirLocalAbsPath(File localAbsPath, Mode sMode) throws SVNException {

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
            buildRelPath = SVNFileUtil.getBasePath(localAbsPath);
            localAbsPath = SVNFileUtil.getParentFile(localAbsPath);

            /*
             * ### if *pdh != NULL (from further above), then there is (quite
             * ### probably) a bogus value in the DIR_DATA hash table. maybe ###
             * clear it out? but what if there is an access baton?
             */

            /* Is this directory in our hash? */
            info.wcDbDir = info.wcDbDir = dirData.get(localAbsPath);
            if (info.wcDbDir != null && info.wcDbDir.getWCRoot() != null) {
                /* Stashed directory's local_relpath + basename. */
                info.localRelPath = new File(info.wcDbDir.computeRelPath(), buildRelPath);
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
            localAbsPath = SVNFileUtil.getParentFile(localAbsPath);
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
        }

        {
            /*
             * The subdirectory's relpath is easily computed relative to the
             * wcroot that we just found.
             */
            File dirRelPath = info.wcDbDir.computeRelPath();

            /* And the result local_relpath may include a filename. */
            info.localRelPath = new File(dirRelPath, buildRelPath);
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
            File parent_dir = SVNFileUtil.getParentFile(localAbsPath);
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
                String lookfor_relpath = SVNFileUtil.getBasePath(localAbsPath);

                /* Was there supposed to be a file sitting here? */
                info.wcDbDir.setObstructedFile(parent_pdh.getWCRoot().determineObstructedFile(lookfor_relpath));

                /*
                 * If we determined that a file was supposed to be at the
                 * LOCAL_ABSPATH requested, then return the PDH and
                 * LOCAL_RELPATH which describes that file.
                 */
                if (info.wcDbDir.isObstructedFile()) {
                    info.wcDbDir = parent_pdh;
                    info.localRelPath = new File(lookfor_relpath);
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
            File parent_dir = SVNFileUtil.getParentFile(child_pdh.getLocalAbsPath());
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
        return 0;
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
        final File parentFile = SVNFileUtil.getParentFile(localAbspath);
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

        final DirParsedInfo parsed = parseDirLocalAbsPath(localAbsPath, Mode.ReadWrite);
        final SVNWCDbDir pdh = parsed.wcDbDir;
        final File local_relpath = parsed.localRelPath;
        verifyDirUsable(pdh);

        String tree_conflict_data;

        /* Get the conflict information for the parent of LOCAL_ABSPATH. */

        SVNSqlJetStatement stmt = pdh.getWCRoot().getSDb().getStatement(SVNWCDbStatements.SELECT_ACTUAL_NODE);
        try {
            stmt.bindf("is", pdh.getWCRoot().getWcId(), local_relpath);
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
        File parentAbsPath = SVNFileUtil.getParentFile(localAbsPath);
        try {
            Map<String, SVNTreeConflictDescription> tree_conflicts = opReadAllTreeConflicts(parentAbsPath);
            if (tree_conflicts != null)
                return tree_conflicts.get(SVNFileUtil.getBasePath(localAbsPath));
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

    public void open(WCDbOpenMode mode, SVNConfigFile config, boolean autoUpgrade, boolean enforceEmptyWQ) throws SVNException {
        // TODO
        throw new UnsupportedOperationException();
    }

    public List<String> readChildren(File localAbsPath) throws SVNException {
        return gatherChildren(localAbsPath, false);
    }

    private List<String> gatherChildren(File localAbsPath, boolean baseOnly) throws SVNException {
        assert (isAbsolute(localAbsPath));

        final DirParsedInfo parsed = parseDirLocalAbsPath(localAbsPath, Mode.ReadOnly);
        SVNWCDbDir pdh = parsed.wcDbDir;
        File localRelPath = parsed.localRelPath;

        verifyDirUsable(pdh);

        final long wcId = pdh.getWCRoot().getWcId();
        final SVNSqlJetDb sDb = pdh.getWCRoot().getSDb();

        final List<String> names = new ArrayList<String>();

        final SVNSqlJetStatement base_stmt = sDb.getStatement(SVNWCDbStatements.SELECT_BASE_NODE_CHILDREN);
        base_stmt.bindf("is", wcId, localRelPath);
        addChildren(names, base_stmt);

        if (!baseOnly) {
            final SVNSqlJetStatement work_stmt = sDb.getStatement(SVNWCDbStatements.SELECT_WORKING_NODE_CHILDREN);
            work_stmt.bindf("is", wcId, localRelPath);
            addChildren(names, work_stmt);
        }

        return names;
    }

    private void addChildren(List<String> children, SVNSqlJetStatement stmt) throws SVNException {
        try {
            while (stmt.next()) {
                String child_relpath = getColumnText(stmt, SVNWCDbSchema.BASE_NODE__Fields.local_relpath);
                String name = SVNFileUtil.getBasePath(new File(child_relpath));
                children.add(name);
            }
        } finally {
            stmt.reset();
        }
    }

    public List<String> readConflictVictims(File localAbsPath) throws SVNException {
        assert (isAbsolute(localAbsPath));

        /* The parent should be a working copy directory. */
        final DirParsedInfo parsed = parseDirLocalAbsPath(localAbsPath, Mode.ReadOnly);
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
            stmt.bindf("is", pdh.getWCRoot().getWcId(), localRelPath);
            while (stmt.next()) {
                String child_relpath = getColumnText(stmt, SVNWCDbSchema.ACTUAL_NODE__Fields.local_relpath);
                String child_name = SVNFileUtil.getBasePath(new File(child_relpath));
                found.add(child_name);
            }
        } finally {
            stmt.reset();
        }

        /* And add tree conflicts */
        stmt = sDb.getStatement(SVNWCDbStatements.SELECT_ACTUAL_TREE_CONFLICT);
        try {
            stmt.bindf("is", pdh.getWCRoot().getWcId(), localRelPath);
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
                String child_name = SVNFileUtil.getBasePath(new File(conflict));
                /* Using a hash avoids duplicates */
                found.add(child_name);
            }
        }

        victims.addAll(found);
        return victims;
    }

    public List<SVNTreeConflictDescription> readConflicts(File localAbspath) throws SVNException {
        // TODO
        throw new UnsupportedOperationException();
    }

    public WCDbInfo readInfo(File localAbsPath, InfoField... fields) throws SVNException {
        assert (isAbsolute(localAbsPath));

        final DirParsedInfo parseDir = parseDirLocalAbsPath(localAbsPath, Mode.ReadOnly);
        SVNWCDbDir pdh = parseDir.wcDbDir;
        File localRelPath = parseDir.localRelPath;

        verifyDirUsable(pdh);

        WCDbInfo info = new WCDbInfo();

        final EnumSet<InfoField> f = getInfoFields(InfoField.class, fields);

        SVNSqlJetStatement stmt_base = pdh.getWCRoot().getSDb().getStatement(f.contains(InfoField.lock) ? SVNWCDbStatements.SELECT_BASE_NODE_WITH_LOCK : SVNWCDbStatements.SELECT_BASE_NODE);
        SVNSqlJetStatement stmt_work = pdh.getWCRoot().getSDb().getStatement(SVNWCDbStatements.SELECT_WORKING_NODE);
        SVNSqlJetStatement stmt_act = pdh.getWCRoot().getSDb().getStatement(SVNWCDbStatements.SELECT_ACTUAL_NODE);

        try {

            stmt_base.bindf("is", pdh.getWCRoot().getWcId(), localRelPath);
            boolean have_base = stmt_base.next();

            stmt_work.bindf("is", pdh.getWCRoot().getWcId(), localRelPath);
            boolean have_work = stmt_work.next();

            stmt_act.bindf("is", pdh.getWCRoot().getWcId(), localRelPath);
            boolean have_act = stmt_act.next();

            if (have_base || have_work) {
                WCDbKind node_kind;

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
                        assert ((info.status != WCDbStatus.Absent && info.status != WCDbStatus.Excluded
                        /* && info.status != WCDbStatus.Incomplete */) || !have_work);

                        if (node_kind == WCDbKind.Subdir && info.status == WCDbStatus.Normal) {
                            /*
                             * We should have read a row from the subdir wc.db.
                             * It must be obstructed in some way.
                             * 
                             * It is also possible that a WORKING node will
                             * override this value with a proper status.
                             */
                            info.status = WCDbStatus.Obstructed;
                        }
                    }

                    if (have_work) {
                        WCDbStatus work_status;

                        work_status = getColumnToken(stmt_work, SVNWCDbSchema.WORKING_NODE__Fields.presence, presenceMap2);
                        assert (work_status == WCDbStatus.Normal || work_status == WCDbStatus.Excluded || work_status == WCDbStatus.NotPresent || work_status == WCDbStatus.BaseDeleted || work_status == WCDbStatus.Incomplete);

                        if (work_status == WCDbStatus.Incomplete) {
                            info.status = WCDbStatus.Incomplete;
                        } else if (work_status == WCDbStatus.Excluded) {
                            info.status = WCDbStatus.Excluded;
                        } else if (work_status == WCDbStatus.NotPresent || work_status == WCDbStatus.BaseDeleted) {
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
                            if (node_kind == WCDbKind.Subdir)
                                info.status = WCDbStatus.ObstructedDelete;
                            else
                                info.status = WCDbStatus.Deleted;
                        } else /* normal */
                        {
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
                            if (node_kind == WCDbKind.Subdir)
                                info.status = WCDbStatus.ObstructedAdd;
                            else
                                info.status = WCDbStatus.Added;
                        }
                    }
                }
                if (f.contains(InfoField.kind)) {
                    if (node_kind == WCDbKind.Subdir)
                        info.kind = WCDbKind.Dir;
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
                        info.reposRelPath = new File(getColumnText(stmt_base, SVNWCDbSchema.BASE_NODE__Fields.repos_relpath));
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
                    if (node_kind != WCDbKind.Dir && node_kind != WCDbKind.Subdir) {
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
                    if (node_kind != WCDbKind.File) {
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
                    if (node_kind != WCDbKind.Symlink)
                        info.target = null;
                    else if (have_work)
                        info.target = new File(getColumnText(stmt_work, SVNWCDbSchema.WORKING_NODE__Fields.symlink_target));
                    else
                        info.target = new File(getColumnText(stmt_base, SVNWCDbSchema.BASE_NODE__Fields.symlink_target));
                }
                if (f.contains(InfoField.changelist)) {
                    if (have_act)
                        info.changelist = getColumnText(stmt_act, SVNWCDbSchema.ACTUAL_NODE__Fields.changelist);
                    else
                        info.changelist = null;
                }
                if (f.contains(InfoField.originalReposRelpath)) {
                    if (have_work)
                        info.originalReposRelpath = new File(getColumnText(stmt_work, SVNWCDbSchema.WORKING_NODE__Fields.copyfrom_repos_path));
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
                if (f.contains(InfoField.baseShadowed)) {
                    info.baseShadowed = have_base && have_work;
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
                        info.lock = new WCDbLock();
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

    public WCDbKind readKind(File localAbsPath, boolean allowMissing) throws SVNException {
        try {
            final WCDbInfo info = readInfo(localAbsPath, InfoField.kind);
            return info.kind;
        } catch (SVNException e) {
            if (allowMissing && e.getErrorMessage().getErrorCode() == SVNErrorCode.WC_PATH_NOT_FOUND) {
                return WCDbKind.Unknown;
            }
            throw e;
        }
    }

    public InputStream readPristine(File wcRootAbsPath, SVNChecksum sha1Checksum) throws SVNException {
        // TODO
        throw new UnsupportedOperationException();
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
                WCDbStatus presence;

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
                if (presence != WCDbStatus.BaseDeleted) {
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
        final DirParsedInfo parsed = parseDirLocalAbsPath(localAbsPath, Mode.ReadWrite);
        verifyDirUsable(parsed.wcDbDir);
        final SVNWCDbRoot wcRoot = parsed.wcDbDir.getWCRoot();
        final SVNSqlJetStatement statement = wcRoot.getSDb().getStatement(statementIndex);
        statement.bindf("is", wcRoot.getWcId(), parsed.localRelPath);
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
        // TODO
        throw new UnsupportedOperationException();
    }

    public WCDbRepositoryInfo scanBaseRepository(File localAbsPath, RepositoryInfoField... fields) throws SVNException {
        // TODO
        throw new UnsupportedOperationException();
    }

    public WCDbDeletionInfo scanDeletion(File localAbsPath, DeletionInfoField... fields) throws SVNException {
        // TODO
        throw new UnsupportedOperationException();
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

    public WCDbDirDeletedInfo isDirDeletedTem(File entryAbspath) throws SVNException {
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
        return new File(dirAbsPath, SVNPathUtil.append(SVNFileUtil.getAdminDirectoryName(), sdbFileName));
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

    private static SVNChecksum getColumnChecksum(SVNSqlJetStatement stmt, Enum f) throws SVNException {
        final String digest = getColumnText(stmt, f);
        if (digest != null) {
            return SVNChecksum.deserializeChecksum(digest);
        }
        return null;
    }

    private static long getColumnRevNum(SVNSqlJetStatement stmt, Enum f) throws SVNException {
        if (isColumnNull(stmt, f))
            return ISVNWCDb.INVALID_REVNUM;
        return getColumnInt64(stmt, f);
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
