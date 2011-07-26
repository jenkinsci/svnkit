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

import static org.tmatesoft.svn.core.internal.wc17.db.SvnWcDbStatementUtil.getColumnBlob;
import static org.tmatesoft.svn.core.internal.wc17.db.SvnWcDbStatementUtil.getColumnBoolean;
import static org.tmatesoft.svn.core.internal.wc17.db.SvnWcDbStatementUtil.getColumnChecksum;
import static org.tmatesoft.svn.core.internal.wc17.db.SvnWcDbStatementUtil.getColumnDepth;
import static org.tmatesoft.svn.core.internal.wc17.db.SvnWcDbStatementUtil.getColumnInt64;
import static org.tmatesoft.svn.core.internal.wc17.db.SvnWcDbStatementUtil.getColumnKind;
import static org.tmatesoft.svn.core.internal.wc17.db.SvnWcDbStatementUtil.getColumnPresence;
import static org.tmatesoft.svn.core.internal.wc17.db.SvnWcDbStatementUtil.getColumnProperties;
import static org.tmatesoft.svn.core.internal.wc17.db.SvnWcDbStatementUtil.getColumnRevNum;
import static org.tmatesoft.svn.core.internal.wc17.db.SvnWcDbStatementUtil.getColumnText;
import static org.tmatesoft.svn.core.internal.wc17.db.SvnWcDbStatementUtil.getKindText;
import static org.tmatesoft.svn.core.internal.wc17.db.SvnWcDbStatementUtil.getPresenceText;
import static org.tmatesoft.svn.core.internal.wc17.db.SvnWcDbStatementUtil.getTranslatedSize;
import static org.tmatesoft.svn.core.internal.wc17.db.SvnWcDbStatementUtil.hasColumnProperties;
import static org.tmatesoft.svn.core.internal.wc17.db.SvnWcDbStatementUtil.isColumnNull;
import static org.tmatesoft.svn.core.internal.wc17.db.SvnWcDbStatementUtil.parseDepth;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeSet;
import java.util.logging.Level;

import org.tmatesoft.sqljet.core.SqlJetException;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNProperties;
import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.db.SVNSqlJetDb;
import org.tmatesoft.svn.core.internal.db.SVNSqlJetDb.Mode;
import org.tmatesoft.svn.core.internal.db.SVNSqlJetStatement;
import org.tmatesoft.svn.core.internal.db.SVNSqlJetTransaction;
import org.tmatesoft.svn.core.internal.util.SVNDate;
import org.tmatesoft.svn.core.internal.util.SVNSkel;
import org.tmatesoft.svn.core.internal.wc.SVNChecksum;
import org.tmatesoft.svn.core.internal.wc.SVNChecksumKind;
import org.tmatesoft.svn.core.internal.wc.SVNConfigFile;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.wc.SVNFileType;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.internal.wc.SVNTreeConflictUtil;
import org.tmatesoft.svn.core.internal.wc17.SVNExternalsStore;
import org.tmatesoft.svn.core.internal.wc17.SVNWCUtils;
import org.tmatesoft.svn.core.internal.wc17.db.ISVNWCDb.WCDbAdditionInfo.AdditionInfoField;
import org.tmatesoft.svn.core.internal.wc17.db.ISVNWCDb.WCDbBaseInfo.BaseInfoField;
import org.tmatesoft.svn.core.internal.wc17.db.ISVNWCDb.WCDbDeletionInfo.DeletionInfoField;
import org.tmatesoft.svn.core.internal.wc17.db.ISVNWCDb.WCDbInfo.InfoField;
import org.tmatesoft.svn.core.internal.wc17.db.ISVNWCDb.WCDbRepositoryInfo.RepositoryInfoField;
import org.tmatesoft.svn.core.internal.wc17.db.SVNWCDbRoot.WCLock;
import org.tmatesoft.svn.core.internal.wc17.db.StructureFields.NodeInfo;
import org.tmatesoft.svn.core.internal.wc17.db.StructureFields.PristineInfo;
import org.tmatesoft.svn.core.internal.wc17.db.StructureFields.RepositoryInfo;
import org.tmatesoft.svn.core.internal.wc17.db.statement.SVNWCDbSchema;
import org.tmatesoft.svn.core.internal.wc17.db.statement.SVNWCDbSchema.ACTUAL_NODE__Fields;
import org.tmatesoft.svn.core.internal.wc17.db.statement.SVNWCDbSchema.NODES__Fields;
import org.tmatesoft.svn.core.internal.wc17.db.statement.SVNWCDbSelectDeletionInfo;
import org.tmatesoft.svn.core.internal.wc17.db.statement.SVNWCDbStatements;
import org.tmatesoft.svn.core.wc.ISVNOptions;
import org.tmatesoft.svn.core.wc.SVNConflictDescription;
import org.tmatesoft.svn.core.wc.SVNMergeFileSet;
import org.tmatesoft.svn.core.wc.SVNPropertyConflictDescription;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNTextConflictDescription;
import org.tmatesoft.svn.core.wc.SVNTreeConflictDescription;
import org.tmatesoft.svn.core.wc2.SvnChecksum;
import org.tmatesoft.svn.util.SVNLogType;

/**
 *
 * @author TMate Software Ltd.
 */
public class SVNWCDb implements ISVNWCDb {

    public static final int FORMAT_FROM_SDB = -1;
    public static final long UNKNOWN_WC_ID = -1;
    private static final long INVALID_REPOS_ID = -1;    
    private static final String PRISTINE_STORAGE_EXT = ".svn-base";

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

    private ISVNOptions config;
    private boolean autoUpgrade;
    private boolean enforceEmptyWQ;
    private HashMap<File, SVNWCDbDir> dirData;

    public void open(final SVNWCDbOpenMode mode, final ISVNOptions config, final boolean autoUpgrade, final boolean enforceEmptyWQ) throws SVNException {
        this.config = config;
        this.autoUpgrade = autoUpgrade;
        this.enforceEmptyWQ = enforceEmptyWQ;
        this.dirData = new HashMap<File, SVNWCDbDir>();
    }

    public void open(SVNWCDbOpenMode mode, SVNConfigFile config, boolean autoUpgrade, boolean enforceEmptyWQ) throws SVNException {
        throw new UnsupportedOperationException();
    }

    public void close() throws SVNException {
        final Set<SVNWCDbRoot> roots = new HashSet<SVNWCDbRoot>();
        /* Collect all the unique WCROOT structures, and empty out DIR_DATA. */
        if (dirData != null) {
            for (Map.Entry<File, SVNWCDbDir> entry : dirData.entrySet()) {
                final SVNWCDbDir pdh = entry.getValue();
                if (pdh.getWCRoot() != null && pdh.getWCRoot().getSDb() != null) {
                    roots.add(pdh.getWCRoot());
                }
            }
            dirData.clear();
        }
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

    public void init(File localAbsPath, File reposRelPath, SVNURL reposRootUrl, String reposUuid, long initialRev, SVNDepth depth) throws SVNException {

        assert (SVNFileUtil.isAbsolute(localAbsPath));
        assert (reposRelPath != null);
        assert (depth == SVNDepth.EMPTY || depth == SVNDepth.FILES || depth == SVNDepth.IMMEDIATES || depth == SVNDepth.INFINITY);

        /* ### REPOS_ROOT_URL and REPOS_UUID may be NULL. ... more doc: tbd */

        /* Create the SDB and insert the basic rows. */
        CreateDbInfo createDb = createDb(localAbsPath, reposRootUrl, reposUuid, SDB_FILE);

        /* Begin construction of the PDH. */
        SVNWCDbDir pdh = new SVNWCDbDir(localAbsPath);

        /* Create the WCROOT for this directory. */
        pdh.setWCRoot(new SVNWCDbRoot(this, localAbsPath, createDb.sDb, createDb.wcId, FORMAT_FROM_SDB, false, false));

        /* The PDH is complete. Stash it into DB. */
        dirData.put(localAbsPath, pdh);

        InsertBase ibb = new InsertBase();

        if (initialRev > 0) {
            ibb.status = SVNWCDbStatus.Incomplete;
        } else {
            ibb.status = SVNWCDbStatus.Normal;
        }
        
        ibb.kind = SVNWCDbKind.Dir;
        ibb.reposId = createDb.reposId;
        ibb.reposRelpath = reposRelPath;
        ibb.revision = initialRev;

        /* ### what about the children? */
        ibb.children = null;
        ibb.depth = depth;

        ibb.wcId = createDb.wcId;
        ibb.localRelpath = SVNFileUtil.createFilePath("");
        /* ### no children, conflicts, or work items to install in a txn... */

        createDb.sDb.runTransaction(ibb);
    }

    private static class CreateDbInfo {

        public SVNSqlJetDb sDb;
        public long reposId;
        public long wcId;
    }

    private CreateDbInfo createDb(File dirAbsPath, SVNURL reposRootUrl, String reposUuid, String sdbFileName) throws SVNException {

        CreateDbInfo info = new CreateDbInfo();

        info.sDb = openDb(dirAbsPath, sdbFileName, SVNSqlJetDb.Mode.RWCreate);

        /* Create the database's schema. */
        info.sDb.execStatement(SVNWCDbStatements.CREATE_SCHEMA);

        /* Insert the repository. */
        info.reposId = createReposId(info.sDb, reposRootUrl, reposUuid);

        /* Insert the wcroot. */
        /* ### Right now, this just assumes wc metadata is being stored locally. */
        final SVNSqlJetStatement statement = info.sDb.getStatement(SVNWCDbStatements.INSERT_WCROOT);

        info.wcId = statement.done();

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
        return insertStmt.done();
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
        stmt.done();
    }
    
    public Map<File, File> getExternalsDefinedBelow(File localAbsPath) throws SVNException {
        Map<File, File> externals = new HashMap<File, File>();
        
        DirParsedInfo parseDir = parseDir(localAbsPath, Mode.ReadWrite);
        SVNWCDbDir pdh = parseDir.wcDbDir;
        File localRelpath = parseDir.localRelPath;
        verifyDirUsable(pdh);
        
        SVNSqlJetStatement stmt = pdh.getWCRoot().getSDb().getStatement(SVNWCDbStatements.SELECT_EXTERNALS_DEFINED);
        try {
            stmt.bindf("is", pdh.getWCRoot().getWcId(), localRelpath);
            while(stmt.next()) {
                localRelpath = SVNFileUtil.createFilePath(getColumnText(stmt, SVNWCDbSchema.EXTERNALS__Fields.local_relpath));
                File defLocalRelpath = SVNFileUtil.createFilePath(getColumnText(stmt, SVNWCDbSchema.EXTERNALS__Fields.def_local_relpath));
                externals.put(SVNFileUtil.createFilePath(pdh.getWCRoot().getAbsPath(), localRelpath), 
                        SVNFileUtil.createFilePath(pdh.getWCRoot().getAbsPath(), defLocalRelpath));
            }
        } finally {
            stmt.reset();
        }
        return externals;
    }
    
    public void gatherExternalDefinitions(File localAbsPath, SVNExternalsStore externals) throws SVNException {
        DirParsedInfo parseDir = parseDir(localAbsPath, Mode.ReadWrite);
        SVNWCDbDir pdh = parseDir.wcDbDir;
        File localRelpath = parseDir.localRelPath;
        verifyDirUsable(pdh);
        
        SVNSqlJetStatement stmt = pdh.getWCRoot().getSDb().getStatement(SVNWCDbStatements.SELECT_EXTERNAL_PROPERTIES);
        try {
            stmt.bindf("is", pdh.getWCRoot().getWcId(), localRelpath);
            while(stmt.next()) {
                SVNProperties properties = getColumnProperties(stmt, NODES__Fields.properties);
                if (properties == null) {
                    continue;
                }
                String externalProperty = properties.getStringValue(SVNProperty.EXTERNALS);
                if (externalProperty == null) {
                    continue;
                }
                
                File nodeRelPath = SVNFileUtil.createFilePath(getColumnText(stmt, NODES__Fields.local_relpath));
                File nodeAbsPath = SVNFileUtil.createFilePath(pdh.getWCRoot().getAbsPath(), nodeRelPath);
                externals.addExternal(nodeAbsPath, null, externalProperty);
                String depthWord = getColumnText(stmt, NODES__Fields.depth);
                externals.addDepth(nodeAbsPath, parseDepth(depthWord));
            }
        } finally {
            stmt.reset();
        }
    }

    public void addBaseExcludedNode(File localAbsPath, File reposRelPath, SVNURL reposRootUrl, String reposUuid, long revision, SVNWCDbKind kind, SVNWCDbStatus status, SVNSkel conflict,
            SVNSkel workItems) throws SVNException {
        assert (status == SVNWCDbStatus.ServerExcluded || status == SVNWCDbStatus.Excluded);
        addExcludedOrNotPresentNode(localAbsPath, reposRelPath, reposRootUrl, reposUuid, revision, kind, status, conflict, workItems);
    }

    public void addBaseDirectory(File localAbsPath, File reposRelPath, SVNURL reposRootUrl, String reposUuid, long revision, SVNProperties props, long changedRev, SVNDate changedDate, String changedAuthor,
            List<File> children, SVNDepth depth, SVNProperties davCache, SVNSkel conflict, boolean updateActualProps, SVNProperties actualProps, SVNSkel workItems) throws SVNException {
        assert (SVNFileUtil.isAbsolute(localAbsPath));
        assert (reposRelPath != null);
        assert (reposUuid != null);
        assert (SVNRevision.isValidRevisionNumber(revision));
        assert (props != null);
        assert (SVNRevision.isValidRevisionNumber(changedRev));

        DirParsedInfo parseDir = parseDir(localAbsPath, Mode.ReadWrite);
        SVNWCDbDir pdh = parseDir.wcDbDir;
        File localRelpath = parseDir.localRelPath;
        verifyDirUsable(pdh);

        InsertBase ibb = new InsertBase();
        ibb.reposRootURL = reposRootUrl;
        ibb.reposUUID = reposUuid;
        
        ibb.status = SVNWCDbStatus.Normal;
        ibb.kind = SVNWCDbKind.Dir;
        ibb.reposRelpath = reposRelPath;
        ibb.revision = revision;
        
        ibb.props = props;
        ibb.changedRev = changedRev;
        ibb.changedDate = changedDate;
        ibb.changedAuthor = changedAuthor;
        
        ibb.children = children;
        ibb.depth = depth;
        
        ibb.davCache = davCache;
        ibb.conflict = conflict;
        ibb.workItems = workItems;
        
        if (updateActualProps) {
            ibb.updateActualProps = true;
            ibb.actualProps = actualProps;
        }
        
        ibb.localRelpath = localRelpath;
        ibb.wcId = pdh.getWCRoot().getWcId();
            
        pdh.getWCRoot().getSDb().runTransaction(ibb);
        pdh.flushEntries(localAbsPath);
    }
    
    private class Delete implements SVNSqlJetTransaction {
        
        public SVNWCDbRoot root;
        public File localRelPath;
        public long deleteDepth;
        
        public void transaction(SVNSqlJetDb db) throws SqlJetException, SVNException {
            WCDbInfo info = readInfo(root, localRelPath, InfoField.status, InfoField.opRoot);
            SVNWCDbStatus status = info.status;
            if (status == SVNWCDbStatus.Deleted || status == SVNWCDbStatus.NotPresent) {
                return;
            }
            boolean addWork = false;
            boolean refetchDepth = false;
            long selectDepth;
            
            if (info.opRoot) {
                WCDbInfo infoBelow = readInfoBelowWorking(root, localRelPath, -1);
                if ((infoBelow.haveBase || infoBelow.haveWork) 
                        && infoBelow.status == SVNWCDbStatus.NotPresent 
                        && infoBelow.status == SVNWCDbStatus.Deleted) {
                    addWork = true;
                    refetchDepth = true;
                }
                selectDepth = SVNWCUtils.relpathDepth(localRelPath);
            } else {
                addWork = true;
                selectDepth = readOpDepth(root, localRelPath);
            } 
            SVNSqlJetStatement deleteStmt = root.getSDb().getStatement(SVNWCDbStatements.DELETE_NODES_RECURSIVE);
            deleteStmt.bindf("isi", root.getWcId(), localRelPath, deleteDepth);
            deleteStmt.done();
            
            if (refetchDepth) {
                selectDepth = readOpDepth(root, localRelPath);
            }
            deleteStmt = root.getSDb().getStatement(SVNWCDbStatements.DELETE_ACTUAL_NODE_LEAVING_CHANGELIST_RECURSIVE);
            deleteStmt.bindf("is", root.getWcId(), localRelPath);
            deleteStmt.done();

            deleteStmt = root.getSDb().getStatement(SVNWCDbStatements.CLEAR_ACTUAL_NODE_LEAVING_CHANGELIST_RECURSIVE);
            deleteStmt.bindf("is", root.getWcId(), localRelPath);
            deleteStmt.done();
            
            deleteStmt = root.getSDb().getStatement(SVNWCDbStatements.DELETE_WC_LOCK_ORPHAN_RECURSIVE);
            deleteStmt.bindf("is", root.getWcId(), localRelPath);
            deleteStmt.done();
            
            if (addWork) {
                SVNSqlJetStatement insertStmt = root.getSDb().getStatement(SVNWCDbStatements.INSERT_DELETE_FROM_NODE_RECURSIVE);
                insertStmt.bindf("isii", root.getWcId(), localRelPath, selectDepth, deleteDepth);
                insertStmt.done();                
            }
        }
        
    }

    private class InsertBase implements SVNSqlJetTransaction {

        public SVNWCDbStatus status;
        public SVNWCDbKind kind;
        public long reposId = INVALID_REPOS_ID;
        public File reposRelpath;
        public long revision = INVALID_REVNUM;
        
        public SVNURL reposRootURL;
        public String reposUUID;
        
        public SVNProperties props;
        public long changedRev = INVALID_REVNUM;
        public SVNDate changedDate;
        public String changedAuthor;
        public SVNProperties davCache;

        public List<File> children;
        public SVNDepth depth = SVNDepth.INFINITY;

        public SVNChecksum checksum;
        
        public File target;
        
        public boolean fileExternal;

        public SVNSkel conflict;

        public boolean updateActualProps;
        public SVNProperties actualProps;
        
       public boolean insertBaseDeleted;
       public boolean keepRecordedInfo;
       
        public SVNSkel workItems;

        public long wcId;
        public File localRelpath;
        
        public void transaction(SVNSqlJetDb db) throws SqlJetException, SVNException {
            assert (conflict == null);
            
            long recordedSize = INVALID_FILESIZE;
            long recordedModTime = 0;
            File parentRelpath = SVNFileUtil.getFileDir(localRelpath);
            
            if (reposId == INVALID_REPOS_ID) {
                reposId = createReposId(db, reposRootURL, reposUUID);                
            }
            
            if (keepRecordedInfo) {
                SVNSqlJetStatement stmt = db.getStatement(SVNWCDbStatements.SELECT_BASE_NODE);
                try {
                    stmt.bindf("is", wcId, localRelpath);
                    boolean haveRow = stmt.next();
                    if (haveRow) {
                        recordedSize = stmt.getColumnLong(SVNWCDbSchema.NODES__Fields.translated_size);
                        recordedModTime = stmt.getColumnLong(SVNWCDbSchema.NODES__Fields.last_mod_time);
                    }
                } finally {
                    stmt.reset();
                }
            }
            
            
            SVNSqlJetStatement stmt = db.getStatement(SVNWCDbStatements.INSERT_NODE);
            stmt.bindf("isisisrtstrisnnnnns", 
                    wcId, 
                    localRelpath, 
                    0, 
                    parentRelpath, 
                    reposId, 
                    reposRelpath, 
                    revision, 
                    getPresenceText(status), 
                    (kind == SVNWCDbKind.Dir) ? SVNDepth.asString(depth) : null, 
                    getKindText(kind), 
                    changedRev, 
                    changedDate, 
                    changedAuthor, 
                    (kind == SVNWCDbKind.Symlink) ? target : null);

            if (kind == SVNWCDbKind.File) {
                stmt.bindChecksum(14, checksum);
                if (recordedSize != INVALID_FILESIZE) {
                    stmt.bindLong(16, recordedSize);
                    stmt.bindLong(17, recordedModTime);
                } else {
                    stmt.bindNull(16);
                    stmt.bindNull(17);
                }
            } else {
                stmt.bindNull(14);
                stmt.bindNull(16);
                stmt.bindNull(17);
            }

            stmt.bindProperties(15, props);
            if (davCache != null) {
                stmt.bindProperties(18, davCache);
            } else {
                stmt.bindNull(18);
            }
            if (fileExternal) {
                stmt.bindLong(20, 1);
            } else {
                stmt.bindNull(20);
            }
            stmt.done();
            
            if (updateActualProps) {
                SVNProperties baseProps = props;
                SVNProperties newActualProps = actualProps;
                if (baseProps != null && newActualProps != null && baseProps.size() == newActualProps.size()) {
                    SVNProperties diff = newActualProps.compareTo(baseProps);
                    if (diff.size() == 0) {
                        newActualProps = null;
                    }
                }
                setActualProperties(db, wcId, localRelpath, newActualProps);
            }

            if (kind == SVNWCDbKind.Dir && children != null) {
                insertIncompleteChildren(db, wcId, localRelpath, revision, children, 0);
            }

            if (parentRelpath != null) {
                if (localRelpath != null && (status == SVNWCDbStatus.Normal || status == SVNWCDbStatus.Incomplete) && !fileExternal) {
                    extendParentDelete(db, wcId, localRelpath);
                } else if (status == SVNWCDbStatus.NotPresent || status == SVNWCDbStatus.ServerExcluded || status == SVNWCDbStatus.Excluded) {
                    retractParentDelete(db, wcId, localRelpath);
                }
            }
            
            if (insertBaseDeleted) {
                stmt = db.getStatement(SVNWCDbStatements.INSERT_DELETE_FROM_BASE);
                stmt.bindf("isi", wcId, localRelpath, SVNWCUtils.relpathDepth(localRelpath));
                stmt.done();                
            }
            
            addWorkItems(db, workItems);
        }

    }

    public void insertIncompleteChildren(SVNSqlJetDb db, long wcId, File localRelpath, long revision, List<File> children, int opDepth) throws SVNException {
        SVNSqlJetStatement stmt = db.getStatement(SVNWCDbStatements.INSERT_NODE);
        for (File name : children) {
            stmt.bindf("isisnnrsns", wcId, SVNFileUtil.createFilePath(localRelpath, name), opDepth, localRelpath, revision, "incomplete", "unknown");
            stmt.done();
        }
        return;
    }
    
    public void retractParentDelete(SVNSqlJetDb db, long wcId, File localRelPath) throws SVNException {
        SVNSqlJetStatement stmt = db.getStatement(SVNWCDbStatements.DELETE_LOWEST_WORKING_NODE);
        stmt.bindf("is", wcId, localRelPath);
        stmt.done();
    }

    public void extendParentDelete(SVNSqlJetDb db, long wcId, File localRelpath) throws SVNException {
        assert (localRelpath != null);
        boolean haveRow;
        SVNSqlJetStatement stmt;
        long parentOpDepth = 0;
        File parentRelpath = SVNFileUtil.getFileDir(localRelpath);
        assert (parentRelpath != null);
        stmt = db.getStatement(SVNWCDbStatements.SELECT_LOWEST_WORKING_NODE);
        try {
            stmt.bindf("is", wcId, parentRelpath);
            haveRow = stmt.next();
            if (haveRow) {
                parentOpDepth = stmt.getColumnLong(SVNWCDbSchema.NODES__Fields.op_depth);
            }
        } finally {
            stmt.reset();
        }
        if (haveRow) {
            long opDepth = 0;
            try {
                stmt.bindf("is", wcId, localRelpath);
                haveRow = stmt.next();
                if (haveRow) {
                    opDepth = stmt.getColumnLong(SVNWCDbSchema.NODES__Fields.op_depth);
                }
            } finally {
                stmt.reset();
            }
            if (!haveRow || parentOpDepth < opDepth) {
                stmt = db.getStatement(SVNWCDbStatements.INSTALL_WORKING_NODE_FOR_DELETE);
                stmt.bindf("isit", wcId, localRelpath, parentOpDepth, getPresenceText(SVNWCDbStatus.BaseDeleted));
                stmt.done();
            }
        }
    }

    public void addBaseFile(File localAbspath, File reposRelpath, SVNURL reposRootUrl, String reposUuid, long revision, SVNProperties props, long changedRev, SVNDate changedDate,
            String changedAuthor, SVNChecksum checksum, SVNProperties davCache, SVNSkel conflict, boolean updateActualProps, SVNProperties actualProps,
            boolean keepRecordedInfo, boolean insertBaseDeleted, SVNSkel workItems) throws SVNException {

        assert (SVNFileUtil.isAbsolute(localAbspath));
        assert (reposRelpath != null);
        assert (reposUuid != null);
        assert (SVNRevision.isValidRevisionNumber(revision));
        assert (props != null);
        assert (SVNRevision.isValidRevisionNumber(changedRev));
        assert (checksum != null);

        DirParsedInfo parseDir = parseDir(localAbspath, Mode.ReadWrite);
        SVNWCDbDir pdh = parseDir.wcDbDir;
        File localRelpath = parseDir.localRelPath;
        verifyDirUsable(pdh);


        InsertBase ibb = new InsertBase();
        ibb.reposRootURL = reposRootUrl;
        ibb.reposUUID = reposUuid;

        ibb.status = SVNWCDbStatus.Normal;
        ibb.kind = SVNWCDbKind.File;
        ibb.reposRelpath = reposRelpath;
        ibb.revision = revision;

        ibb.props = props;
        ibb.changedRev = changedRev;
        ibb.changedDate = changedDate;
        ibb.changedAuthor = changedAuthor;

        ibb.checksum = checksum;

        ibb.davCache = davCache;
        ibb.conflict = conflict;
        ibb.workItems = workItems;
        
        if (updateActualProps) {
            ibb.updateActualProps = true;
            ibb.actualProps = actualProps;
        }
        ibb.keepRecordedInfo = keepRecordedInfo;
        ibb.insertBaseDeleted = insertBaseDeleted;

        ibb.localRelpath = localRelpath;
        ibb.wcId = pdh.getWCRoot().getWcId();
        
        pdh.getWCRoot().getSDb().runTransaction(ibb);
        pdh.flushEntries(localAbspath);
    }

    public void addBaseSymlink(File localAbsPath, File reposRelPath, SVNURL reposRootUrl, String reposUuid, long revision, SVNProperties props, long changedRev, SVNDate changedDate,
            String changedAuthor, File target, SVNProperties davCache, SVNSkel conflict, boolean updateActualProps, SVNProperties acutalProps, SVNSkel workItems) throws SVNException {

        assert (SVNFileUtil.isAbsolute(localAbsPath));
        assert (reposRelPath != null);
        assert (reposUuid != null);
        assert (SVNRevision.isValidRevisionNumber(revision));
        assert (props != null);
        assert (SVNRevision.isValidRevisionNumber(changedRev));
        assert (target != null);

        DirParsedInfo parseDir = parseDir(localAbsPath, Mode.ReadWrite);
        SVNWCDbDir pdh = parseDir.wcDbDir;
        File localRelpath = parseDir.localRelPath;
        verifyDirUsable(pdh);

        InsertBase ibb = new InsertBase();
        ibb.reposUUID = reposUuid;
        ibb.reposRootURL = reposRootUrl;

        ibb.status = SVNWCDbStatus.Normal;
        ibb.kind = SVNWCDbKind.Symlink;
        ibb.reposRelpath = reposRelPath;
        ibb.revision = revision;

        ibb.props = props;
        ibb.changedRev = changedRev;
        ibb.changedDate = changedDate;
        ibb.changedAuthor = changedAuthor;

        ibb.target = target;

        ibb.davCache = davCache;
        ibb.conflict = conflict;
        ibb.workItems = workItems;
        
        if (updateActualProps) {
            ibb.updateActualProps = true;
            ibb.actualProps = acutalProps;
        }

        ibb.wcId = pdh.getWCRoot().getWcId();
        ibb.localRelpath = localRelpath;
        pdh.getWCRoot().getSDb().runTransaction(ibb);
        pdh.flushEntries(localAbsPath);
    }

    public void addLock(File localAbsPath, SVNWCDbLock lock) throws SVNException {
        // TODO
        throw new UnsupportedOperationException();
    }

    public void addWorkQueue(File wcRootAbsPath, SVNSkel workItem) throws SVNException {
        assert (SVNFileUtil.isAbsolute(wcRootAbsPath));
        if (workItem == null) {
            return;
        }
        DirParsedInfo parseDir = parseDir(wcRootAbsPath, Mode.ReadWrite);
        SVNWCDbDir pdh = parseDir.wcDbDir;
        verifyDirUsable(pdh);
        addWorkItems(pdh.getWCRoot().getSDb(), workItem);
    }

    public boolean checkPristine(File wcRootAbsPath, SVNChecksum sha1Checksum) throws SVNException {
        assert (SVNFileUtil.isAbsolute(wcRootAbsPath));
        assert (sha1Checksum != null);
        if (sha1Checksum.getKind() != SVNChecksumKind.SHA1) {
            sha1Checksum = getPristineSHA1(wcRootAbsPath, sha1Checksum);
        }
        assert (sha1Checksum.getKind() == SVNChecksumKind.SHA1);
        DirParsedInfo parseDir = parseDir(wcRootAbsPath, Mode.ReadWrite);
        SVNWCDbDir pdh = parseDir.wcDbDir;
        verifyDirUsable(pdh);
        boolean haveRow = false;
        SVNSqlJetStatement stmt = pdh.getWCRoot().getSDb().getStatement(SVNWCDbStatements.SELECT_PRISTINE_MD5_CHECKSUM);
        try {
            haveRow = stmt.next();
        } finally {
            stmt.reset();
        }
        File pristineAbspath = getPristineFileName(pdh, sha1Checksum, false);
        SVNNodeKind kindOnDisk = SVNFileType.getNodeKind(SVNFileType.getType(pristineAbspath));
        if (kindOnDisk != (haveRow ? SVNNodeKind.FILE : SVNNodeKind.NONE)) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_DB_ERROR, "The pristine text with checksum ''{0}'' was found in the DB or on disk but not both", sha1Checksum);
            SVNErrorManager.error(err, SVNLogType.WC);
        }
        return haveRow;
    }

    public void completedWorkQueue(File wcRootAbsPath, long id) throws SVNException {
        assert (SVNFileUtil.isAbsolute(wcRootAbsPath));
        assert (id != 0);
        DirParsedInfo parseDir = parseDir(wcRootAbsPath, Mode.ReadWrite);
        SVNWCDbDir pdh = parseDir.wcDbDir;
        verifyDirUsable(pdh);
        SVNSqlJetStatement stmt = pdh.getWCRoot().getSDb().getStatement(SVNWCDbStatements.DELETE_WORK_ITEM);
        stmt.bindLong(1, id);
        stmt.done();
    }

    public long ensureRepository(File localAbsPath, SVNURL reposRootUrl, String reposUuid) throws SVNException {
        // TODO
        throw new UnsupportedOperationException();
    }

    public WCDbWorkQueueInfo fetchWorkQueue(File wcRootAbsPath) throws SVNException {
        assert (SVNFileUtil.isAbsolute(wcRootAbsPath));
        WCDbWorkQueueInfo info = new WCDbWorkQueueInfo();
        DirParsedInfo parseDir = parseDir(wcRootAbsPath, Mode.ReadOnly);
        SVNWCDbDir pdh = parseDir.wcDbDir;
        verifyDirUsable(pdh);
        SVNSqlJetStatement stmt = pdh.getWCRoot().getSDb().getStatement(SVNWCDbStatements.SELECT_WORK_ITEM);
        try {
            boolean haveRow = stmt.next();
            if (!haveRow) {
                info.id = 0;
                info.workItem = null;
                return info;
            }
            info.id = stmt.getColumnLong(SVNWCDbSchema.WORK_QUEUE__Fields.id);
            info.workItem = SVNSkel.parse(stmt.getColumnBlob(SVNWCDbSchema.WORK_QUEUE__Fields.work));
            return info;
        } finally {
            stmt.reset();
        }
    }

    public File fromRelPath(File wcRootAbsPath, File localRelPath) throws SVNException {
        // TODO
        throw new UnsupportedOperationException();
    }

    public Set<String> getBaseChildren(File localAbsPath) throws SVNException {
        return gatherChildren(localAbsPath, true);
    }

    public SVNProperties getBaseDavCache(File localAbsPath) throws SVNException {
        SVNSqlJetStatement stmt = getStatementForPath(localAbsPath, SVNWCDbStatements.SELECT_BASE_DAV_CACHE);
        try {
            boolean haveRow = stmt.next();
            if (!haveRow) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_PATH_NOT_FOUND, "The node ''{0}'' was not found.", localAbsPath);
                SVNErrorManager.error(err, SVNLogType.WC);
            }
            return stmt.getColumnProperties(SVNWCDbSchema.NODES__Fields.dav_cache);
        } finally {
            stmt.reset();
        }
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
                SVNWCDbKind node_kind = getColumnKind(stmt, NODES__Fields.kind);

                if (f.contains(BaseInfoField.kind)) {
                    info.kind = node_kind;
                }
                if (f.contains(BaseInfoField.status)) {
                    info.status = getColumnPresence(stmt);
                }
                if (f.contains(BaseInfoField.revision)) {
                    info.revision = getColumnRevNum(stmt, SVNWCDbSchema.NODES__Fields.revision);
                }
                if (f.contains(BaseInfoField.reposRelPath)) {
                    info.reposRelPath = SVNFileUtil.createFilePath(getColumnText(stmt, SVNWCDbSchema.NODES__Fields.repos_path));
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
                            info.lock.date = SVNWCUtils.readDate(getColumnInt64(lockStmt, SVNWCDbSchema.LOCK__Fields.lock_date));
                    }
                }
                if (f.contains(BaseInfoField.reposId)) {
                    info.reposId = getColumnInt64(stmt, NODES__Fields.repos_id);
                }
                
                if (f.contains(BaseInfoField.reposRootUrl) || f.contains(BaseInfoField.reposUuid)) {
                    /* Fetch repository information via REPOS_ID. */
                    if (isColumnNull(stmt, SVNWCDbSchema.NODES__Fields.repos_id)) {
                        if (f.contains(BaseInfoField.reposRootUrl))
                            info.reposRootUrl = null;
                        if (f.contains(BaseInfoField.reposUuid))
                            info.reposUuid = null;
                    } else {
                        final ReposInfo reposInfo = fetchReposInfo(pdh.getWCRoot().getSDb(), getColumnInt64(stmt, SVNWCDbSchema.NODES__Fields.repos_id));
                        info.reposRootUrl = SVNURL.parseURIEncoded(reposInfo.reposRootUrl);
                    }
                }
                if (f.contains(BaseInfoField.changedRev)) {
                    info.changedRev = getColumnRevNum(stmt, SVNWCDbSchema.NODES__Fields.changed_revision);
                }
                if (f.contains(BaseInfoField.changedDate)) {
                    info.changedDate = SVNWCUtils.readDate(getColumnInt64(stmt, SVNWCDbSchema.NODES__Fields.changed_date));
                }
                if (f.contains(BaseInfoField.changedAuthor)) {
                    /* Result may be NULL. */
                    info.changedAuthor = getColumnText(stmt, SVNWCDbSchema.NODES__Fields.changed_author);
                }
                if (f.contains(BaseInfoField.lastModTime)) {
                    info.lastModTime = SVNWCUtils.readDate(getColumnInt64(stmt, SVNWCDbSchema.NODES__Fields.last_mod_time));
                }
                if (f.contains(BaseInfoField.depth)) {
                    if (node_kind != SVNWCDbKind.Dir) {
                        info.depth = SVNDepth.UNKNOWN;
                    } else {
                        String depth_str = getColumnText(stmt, SVNWCDbSchema.NODES__Fields.depth);

                        if (depth_str == null)
                            info.depth = SVNDepth.UNKNOWN;
                        else
                            info.depth = parseDepth(depth_str);
                    }
                }
                if (f.contains(BaseInfoField.checksum)) {
                    if (node_kind != SVNWCDbKind.File) {
                        info.checksum = null;
                    } else {
                        try {
                            info.checksum = getColumnChecksum(stmt, SVNWCDbSchema.NODES__Fields.checksum);
                        } catch (SVNException e) {
                            SVNErrorMessage err = SVNErrorMessage.create(e.getErrorMessage().getErrorCode(), "The node ''{0}'' has a corrupt checksum value.", localAbsPath);
                            SVNErrorManager.error(err, SVNLogType.WC);
                        }
                    }
                }
                if (f.contains(BaseInfoField.translatedSize)) {
                    info.translatedSize = getTranslatedSize(stmt, SVNWCDbSchema.NODES__Fields.translated_size);
                }
                if (f.contains(BaseInfoField.target)) {
                    if (node_kind != SVNWCDbKind.Symlink)
                        info.target = null;
                    else
                        info.target = SVNFileUtil.createFilePath(getColumnText(stmt, SVNWCDbSchema.NODES__Fields.symlink_target));
                }
                if (f.contains(BaseInfoField.updateRoot)) {
                    info.updateRoot = getColumnBoolean(stmt, SVNWCDbSchema.NODES__Fields.file_external);
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

            SVNProperties props = getColumnProperties(stmt, NODES__Fields.properties);
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
        assert (isAbsolute(localDirAbsPath));
        SVNWCDbDir pdh = getOrCreateDir(localDirAbsPath, false);
        if (pdh == null || pdh.getWCRoot() == null) {
            try {
                final DirParsedInfo parsed = parseDir(localDirAbsPath, Mode.ReadOnly);
                pdh = parsed.wcDbDir;
            } catch (SVNException e) {
                if (e.getErrorMessage().getErrorCode() != SVNErrorCode.WC_NOT_WORKING_COPY) {
                    throw e;
                }
                if (pdh != null) {
                    pdh.setWCRoot(null);
                }
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_MISSING, "Path ''{0}'' is not a working copy", localDirAbsPath);
                SVNErrorManager.error(err, SVNLogType.WC);
            }
            assert (pdh.getWCRoot() != null);
        }
        assert (pdh.getWCRoot().getFormat() >= 1);
        return pdh.getWCRoot().getFormat();
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
        assert (SVNFileUtil.isAbsolute(wcRootAbsPath));
        assert (sha1Checksum != null);
        if (sha1Checksum.getKind() != SVNChecksumKind.SHA1) {
            sha1Checksum = getPristineSHA1(wcRootAbsPath, sha1Checksum);
        }
        assert (sha1Checksum.getKind() == SVNChecksumKind.SHA1);
        final DirParsedInfo parsed = parseDir(wcRootAbsPath, Mode.ReadOnly);
        final SVNWCDbDir pdh = parsed.wcDbDir;
        verifyDirUsable(pdh);
        boolean present = checkPristine(wcRootAbsPath, sha1Checksum);
        if (!present) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_DB_ERROR, "Pristine text not found");
            SVNErrorManager.error(err, SVNLogType.WC);
        }
        return getPristineFileName(pdh, sha1Checksum, false);
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
        assert (SVNFileUtil.isAbsolute(wcRootAbsPath));
        final DirParsedInfo parsed = parseDir(wcRootAbsPath, Mode.ReadOnly);
        SVNWCDbDir pdh = parsed.wcDbDir;
        verifyDirUsable(pdh);
        return SVNFileUtil.createFilePath(SVNFileUtil.createFilePath(pdh.getWCRoot().getAbsPath(), SVNFileUtil.getAdminDirectoryName()), PRISTINE_TEMPDIR_RELPATH);
    }

    public void globalRecordFileinfo(File localAbspath, long translatedSize, SVNDate lastModTime) throws SVNException {
        assert (SVNFileUtil.isAbsolute(localAbspath));
        final DirParsedInfo parsed = parseDir(localAbspath, Mode.ReadWrite);
        SVNWCDbDir pdh = parsed.wcDbDir;
        verifyDirUsable(pdh);
        RecordFileinfo rb = new RecordFileinfo();
        rb.wcRoot = pdh.getWCRoot();
        rb.localRelpath = parsed.localRelPath;
        rb.translatedSize = translatedSize;
        rb.lastModTime = lastModTime;
        pdh.getWCRoot().getSDb().runTransaction(rb);
        pdh.flushEntries(localAbspath);
    }

    private class RecordFileinfo implements SVNSqlJetTransaction {

        public SVNDate lastModTime;
        public long translatedSize;
        public File localRelpath;
        public SVNWCDbRoot wcRoot;

        public void transaction(SVNSqlJetDb db) throws SqlJetException, SVNException {
            SVNSqlJetStatement stmt = db.getStatement(SVNWCDbStatements.UPDATE_NODE_FILEINFO);
            stmt.bindf("isii", wcRoot.getWcId(), localRelpath, translatedSize, lastModTime);
            long affectedRows = stmt.done();
            assert (affectedRows == 1);
        }
    }

    public static class TreesExistInfo {

        public boolean baseExists;
        public boolean workingExists;
    }

    public TreesExistInfo whichTreesExist(SVNSqlJetDb db, long wcId, File localRelpath) throws SVNException {
        TreesExistInfo info = new TreesExistInfo();
        info.baseExists = false;
        info.workingExists = false;
        SVNSqlJetStatement stmt = db.getStatement(SVNWCDbStatements.DETERMINE_TREE_FOR_RECORDING);
        try {
            stmt.bindf("is", wcId, localRelpath);
            boolean haveRow = stmt.next();
            if (haveRow) {
                long value = stmt.getColumnLong(0);

                if (value != 0) {
                    info.workingExists = true;
                } else {
                    info.baseExists = true;
                }
                haveRow = stmt.next();
                if (haveRow) {
                    info.workingExists = true;
                    info.baseExists = true;
                }
            }
            return info;
        } finally {
            stmt.reset();
        }
    }

    public void globalRelocate(File localDirAbspath, SVNURL reposRootUrl, boolean singleDb) throws SVNException {
        // TODO
        throw new UnsupportedOperationException();
    }

    public void globalUpdate(File localAbsPath, SVNWCDbKind newKind, File newReposRelpath, long newRevision, SVNProperties newProps, long newChangedRev, SVNDate newChangedDate,
            String newChangedAuthor, List<File> newChildren, SVNChecksum newChecksum, File newTarget, SVNProperties newDavCache, SVNSkel conflict, SVNSkel workItems) throws SVNException {
        // TODO
        throw new UnsupportedOperationException();
    }

    public void installPristine(File tempfileAbspath, SVNChecksum sha1Checksum, SVNChecksum md5Checksum) throws SVNException {

        assert (SVNFileUtil.isAbsolute(tempfileAbspath));
        File wriAbspath = SVNFileUtil.getFileDir(tempfileAbspath);
        final DirParsedInfo parsed = parseDir(wriAbspath, Mode.ReadWrite);
        SVNWCDbDir pdh = parsed.wcDbDir;
        verifyDirUsable(pdh);
        File pristineAbspath = getPristineFileName(pdh, sha1Checksum, true);
        SVNNodeKind kind = SVNFileType.getNodeKind(SVNFileType.getType(pristineAbspath));
        if (kind == SVNNodeKind.FILE) {
            SVNFileUtil.deleteFile(tempfileAbspath);
            return;
        }
        SVNFileUtil.rename(tempfileAbspath, pristineAbspath);
        long size = pristineAbspath.length();
        SVNSqlJetStatement stmt = pdh.getWCRoot().getSDb().getStatement(SVNWCDbStatements.INSERT_PRISTINE);
        stmt.bindChecksum(1, sha1Checksum);
        stmt.bindChecksum(2, md5Checksum);
        stmt.bindLong(3, size);
        stmt.done();
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
                SVNWCDbStatus work_status = getColumnPresence(stmt);
                return (work_status == SVNWCDbStatus.Excluded);
            }
        } finally {
            stmt.reset();
        }

        /* Now check the BASE node's status. */
        final WCDbBaseInfo baseInfo = getBaseInfo(localAbsPath, BaseInfoField.status);
        SVNWCDbStatus base_status = baseInfo.status;
        return (base_status == SVNWCDbStatus.ServerExcluded || base_status == SVNWCDbStatus.NotPresent || base_status == SVNWCDbStatus.Excluded);
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
         * but that assumes we can track the fact that it was ### originally
         * write to it. The solution is to reopen the db in readwrite ### mode,
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
                if (e.getErrorMessage().getErrorCode() != SVNErrorCode.SQLITE_ERROR && !isErrorNOENT(e.getErrorMessage().getErrorCode()))
                    throw e;
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
            info.wcDbDir.setWCRoot(new SVNWCDbRoot(this, localAbsPath, sDb, wcId, FORMAT_FROM_SDB, autoUpgrade, enforceEmptyWQ));

        } else {
            /* We found a wc-1 working copy directory. */
            info.wcDbDir.setWCRoot(new SVNWCDbRoot(this, localAbsPath, null, UNKNOWN_WC_ID, wc_format, autoUpgrade, enforceEmptyWQ));

            /*
             * Don't test for a directory obstructing a versioned file. The wc-1
             * code can manage that itself.
             */
            obstruction_possible = false;

            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_UNSUPPORTED_FORMAT);
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
                    parent_pdh.setWCRoot(new SVNWCDbRoot(this, parent_pdh.getLocalAbsPath(), sDb, 1, FORMAT_FROM_SDB, autoUpgrade, enforceEmptyWQ));

                    dirData.put(parent_pdh.getLocalAbsPath(), parent_pdh);

                    info.wcDbDir.setParent(parent_pdh);
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
            int formatVersion = 0;
            File adminDir = new File(localAbsPath, SVNFileUtil.getAdminDirectoryName());
            File entriesFile = new File(adminDir, "entries");
            if (entriesFile.exists()) {
                formatVersion = readFormatVersion(entriesFile);
            } else {
                File formatFile = new File(adminDir, "format");
                if (formatFile.exists()) {
                    formatVersion = readFormatVersion(formatFile);
                }
            }
            return formatVersion;
        } catch (SVNException e) {
            return 0;
        }
    }

    private int readFormatVersion(File path) throws SVNException {
        int formatVersion = -1;
        BufferedReader reader = null;
        String line = null;
        try {
            reader = new BufferedReader(new InputStreamReader(SVNFileUtil.openFileForReading(path, Level.FINEST, SVNLogType.WC), "UTF-8"));
            line = reader.readLine();
        } catch (IOException e) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, "Cannot read entries file ''{0}'': {1}", new Object[] {
                    path, e.getLocalizedMessage()
            });
            SVNErrorManager.error(err, e, SVNLogType.WC);
        } catch (SVNException svne) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_NOT_DIRECTORY, "''{0}'' is not a working copy", path);
            err.setChildErrorMessage(svne.getErrorMessage());
            SVNErrorManager.error(err, svne, Level.FINEST, SVNLogType.WC);
        } finally {
            SVNFileUtil.closeFile(reader);
        }
        if (line == null || line.length() == 0) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.STREAM_UNEXPECTED_EOF, "Reading ''{0}''", path);
            SVNErrorMessage err1 = SVNErrorMessage.create(SVNErrorCode.WC_NOT_DIRECTORY, "''{0}'' is not a working copy", path);
            err1.setChildErrorMessage(err);
            SVNErrorManager.error(err1, Level.FINEST, SVNLogType.WC);
        }
        try {
            formatVersion = Integer.parseInt(line.trim());
        } catch (NumberFormatException e) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.BAD_VERSION_FILE_FORMAT, "First line of ''{0}'' contains non-digit", path);
            SVNErrorMessage err1 = SVNErrorMessage.create(SVNErrorCode.WC_NOT_DIRECTORY, "''{0}'' is not a working copy", path);
            err1.setChildErrorMessage(err);
            SVNErrorManager.error(err1, Level.FINEST, SVNLogType.WC);
        }
        return formatVersion;
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

    public void opCopyDir(File localAbsPath, SVNProperties props, long changedRev, SVNDate changedDate, String changedAuthor, File originalReposRelPath, SVNURL originalRootUrl, String originalUuid,
            long originalRevision, List<File> children, SVNDepth depth, SVNSkel conflict, SVNSkel workItems) throws SVNException {
        // TODO
        throw new UnsupportedOperationException();
    }

    public void opCopyFile(File localAbsPath, SVNProperties props, long changedRev, SVNDate changedDate, String changedAuthor, File originalReposRelPath, SVNURL originalRootUrl, String originalUuid,
            long originalRevision, SVNChecksum checksum, SVNSkel conflict, SVNSkel workItems) throws SVNException {
        // TODO
        throw new UnsupportedOperationException();
    }

    public void opCopySymlink(File localAbsPath, SVNProperties props, long changedRev, SVNDate changedDate, String changedAuthor, File originalReposRelPath, SVNURL originalRootUrl,
            String originalUuid, long originalRevision, File target, SVNSkel conflict, SVNSkel workItems) throws SVNException {
        // TODO
        throw new UnsupportedOperationException();
    }

    public void opDelete(File localAbsPath) throws SVNException {
        final DirParsedInfo parsed = parseDir(localAbsPath, Mode.ReadWrite);
        final SVNWCDbDir pdh = parsed.wcDbDir;
        final File localRelpath = parsed.localRelPath;
        verifyDirUsable(pdh);
        
        Delete deleteTxn = new Delete();
        deleteTxn.root = pdh.getWCRoot();
        deleteTxn.localRelPath = localRelpath;
        deleteTxn.deleteDepth = SVNWCUtils.relpathDepth(localRelpath); 
        
        pdh.flushEntries(localAbsPath);
        pdh.getWCRoot().getSDb().runTransaction(deleteTxn);
    }

    public void opMarkConflict(File localAbsPath) throws SVNException {
        // TODO
        throw new UnsupportedOperationException();
    }

    public void opMarkResolved(File localAbspath, boolean resolvedText, boolean resolvedProps, boolean resolvedTree) throws SVNException {
        assert (isAbsolute(localAbspath));
        assert (!resolvedTree);

        final DirParsedInfo parsed = parseDir(localAbspath, Mode.ReadWrite);
        final SVNWCDbDir pdh = parsed.wcDbDir;
        final File localRelpath = parsed.localRelPath;
        verifyDirUsable(pdh);

        if (resolvedText) {
            SVNSqlJetStatement stmt = pdh.getWCRoot().getSDb().getStatement(SVNWCDbStatements.CLEAR_TEXT_CONFLICT);
            stmt.bindf("is", pdh.getWCRoot().getWcId(), localRelpath);
            stmt.done();
        }
        if (resolvedProps) {
            SVNSqlJetStatement stmt = pdh.getWCRoot().getSDb().getStatement(SVNWCDbStatements.CLEAR_PROPS_CONFLICT);
            stmt.bindf("is", pdh.getWCRoot().getWcId(), localRelpath);
            stmt.done();
        }

        pdh.flushEntries(localAbspath);

        return;
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
        final File localRelpath = parsed.localRelPath;
        verifyDirUsable(pdh);
        return readAllTreeConflicts(pdh, localRelpath);
    }

    private Map<String, SVNTreeConflictDescription> readAllTreeConflicts(SVNWCDbDir pdh, File localRelpath) throws SVNException {
        Map<String, SVNTreeConflictDescription> treeConflicts = new HashMap<String, SVNTreeConflictDescription>();
        SVNSqlJetStatement stmt = pdh.getWCRoot().getSDb().getStatement(SVNWCDbStatements.SELECT_ACTUAL_CHILDREN_TREE_CONFLICT);
        try {
            stmt.bindf("is", pdh.getWCRoot().getWcId(), localRelpath);
            boolean haveRow = stmt.next();
            while (haveRow) {
                File childRelpath = SVNFileUtil.createFilePath(stmt.getColumnString(SVNWCDbSchema.ACTUAL_NODE__Fields.local_relpath));
                String childBaseName = SVNFileUtil.getFileName(childRelpath);
                byte[] conflictData = stmt.getColumnBlob(SVNWCDbSchema.ACTUAL_NODE__Fields.tree_conflict_data);
                SVNSkel skel = SVNSkel.parse(conflictData);
                SVNTreeConflictDescription treeConflict = SVNTreeConflictUtil.readSingleTreeConflict(skel, pdh.getWCRoot().getAbsPath());
                treeConflicts.put(childBaseName, treeConflict);
                haveRow = stmt.next();
            }
        } finally {
            stmt.reset();
        }
        return treeConflicts;
    }

    public SVNTreeConflictDescription opReadTreeConflict(File localAbsPath) throws SVNException {
        assert (isAbsolute(localAbsPath));
        final DirParsedInfo parsed = parseDir(localAbsPath, Mode.ReadWrite);
        final SVNWCDbDir pdh = parsed.wcDbDir;
        final File localRelpath = parsed.localRelPath;
        verifyDirUsable(pdh);
        return readTreeConflict(pdh, localRelpath);
    }

    private SVNTreeConflictDescription readTreeConflict(SVNWCDbDir pdh, File localRelpath) throws SVNException {
        SVNSqlJetStatement stmt = pdh.getWCRoot().getSDb().getStatement(SVNWCDbStatements.SELECT_ACTUAL_TREE_CONFLICT);
        try {
            stmt.bindf("is", pdh.getWCRoot().getWcId(), localRelpath);
            boolean haveRow = stmt.next();
            if (!haveRow) {
                return null;
            }
            byte[] conflictData = stmt.getColumnBlob(SVNWCDbSchema.ACTUAL_NODE__Fields.tree_conflict_data);
            SVNSkel skel = SVNSkel.parse(conflictData);
            return SVNTreeConflictUtil.readSingleTreeConflict(skel, pdh.getWCRoot().getAbsPath());
        } finally {
            stmt.reset();
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
        assert (SVNFileUtil.isAbsolute(localAbsPath));
        final DirParsedInfo parsed = parseDir(localAbsPath, Mode.ReadWrite);
        SVNWCDbDir pdh = parsed.wcDbDir;
        verifyDirUsable(pdh);
        SetProperties spb = new SetProperties();
        spb.props = props;
        spb.pdh = pdh;
        spb.conflict = conflict;
        spb.workItems = workItems;
        spb.localRelpath = parsed.localRelPath;
        pdh.getWCRoot().getSDb().runTransaction(spb);
    }

    private class SetProperties implements SVNSqlJetTransaction {

        SVNProperties props;
        SVNWCDbDir pdh;
        File localRelpath;
        SVNSkel conflict;
        SVNSkel workItems;

        public void transaction(SVNSqlJetDb db) throws SqlJetException, SVNException {
            assert (conflict == null);
            addWorkItems(db, workItems);
            SVNProperties pristineProps = readPristineProperties(pdh, localRelpath);
            if (props != null && pristineProps != null) {
                SVNProperties propDiffs = SVNWCUtils.propDiffs(props, pristineProps);
                if (propDiffs.isEmpty()) {
                    props = null;
                }
            }
            setActualProperties(db, pdh.getWCRoot().getWcId(), localRelpath, props);
        }

    };

    public void setActualProperties(SVNSqlJetDb db, long wcId, File localRelpath, SVNProperties props) throws SVNException {
        SVNSqlJetStatement stmt = db.getStatement(SVNWCDbStatements.UPDATE_ACTUAL_PROPS);
        stmt.bindf("is", wcId, localRelpath);
        stmt.bindProperties(3, props);
        long affectedRows = stmt.done();
        if (affectedRows == 1 || props == null) {
            return;
        }
        stmt = db.getStatement(SVNWCDbStatements.INSERT_ACTUAL_PROPS);
        stmt.bindf("is", wcId, localRelpath);
        if (localRelpath != null && !"".equals(SVNFileUtil.getFilePath(localRelpath))) {
            stmt.bindString(3, SVNFileUtil.getFilePath(SVNFileUtil.getFileDir(localRelpath)));
        } else {
            stmt.bindNull(3);
        }
        stmt.bindProperties(4, props);
        stmt.done();
    }

    public SVNProperties readPristineProperties(SVNWCDbDir pdh, File localRelpath) throws SVNException {
        SVNSqlJetStatement stmt = pdh.getWCRoot().getSDb().getStatement(SVNWCDbStatements.SELECT_NODE_PROPS);
        try {
            stmt.bindf("is", pdh.getWCRoot().getWcId(), localRelpath);
            boolean haveRow = stmt.next();
            if (!haveRow) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_PATH_NOT_FOUND, "The node ''{0}'' was not found.", SVNFileUtil.createFilePath(pdh.getWCRoot().getAbsPath(), localRelpath));
                SVNErrorManager.error(err, SVNLogType.WC);
            }
            SVNWCDbStatus presence = getColumnPresence(stmt);
            if (presence == SVNWCDbStatus.BaseDeleted) {
                haveRow = stmt.next();
                assert (haveRow);
                presence = getColumnPresence(stmt);
            }
            if (presence == SVNWCDbStatus.Normal || presence == SVNWCDbStatus.Incomplete) {
                SVNProperties props = stmt.getColumnProperties(SVNWCDbSchema.NODES__Fields.properties);
                if (props == null) {
                    props = new SVNProperties();
                }
                return props;
            }
            return null;
        } finally {
            stmt.reset();
        }
    }

    public void opSetTreeConflict(File localAbspath, SVNTreeConflictDescription treeConflict) throws SVNException {
        assert (isAbsolute(localAbspath));
        SetTreeConflict stb = new SetTreeConflict();
        stb.treeConflict = treeConflict;
        DirParsedInfo parseDir = parseDir(localAbspath, Mode.ReadWrite);
        stb.localRelpath = parseDir.localRelPath;
        SVNWCDbDir pdh = parseDir.wcDbDir;
        verifyDirUsable(pdh);
        stb.wcId = pdh.getWCRoot().getWcId();
        stb.parentRelpath = SVNFileUtil.getFileDir(stb.localRelpath);
        pdh.getWCRoot().getSDb().runTransaction(stb);
        pdh.flushEntries(localAbspath);
    }

    private class SetTreeConflict implements SVNSqlJetTransaction {

        public File localRelpath;
        public long wcId;
        public File parentRelpath;
        public SVNTreeConflictDescription treeConflict;

        public void transaction(SVNSqlJetDb db) throws SqlJetException, SVNException {
            boolean haveRow;
            SVNSqlJetStatement stmt = db.getStatement(SVNWCDbStatements.SELECT_ACTUAL_NODE);
            try {
                stmt.bindf("is", wcId, localRelpath);
                haveRow = stmt.next();
            } finally {
                stmt.reset();
            }
            String treeConflictData;
            if (treeConflict != null) {
                treeConflictData = SVNTreeConflictUtil.getSingleTreeConflictData(treeConflict);
            } else {
                treeConflictData = null;
            }
            if (haveRow) {
                stmt = db.getStatement(SVNWCDbStatements.UPDATE_ACTUAL_TREE_CONFLICTS);
            } else {
                stmt = db.getStatement(SVNWCDbStatements.INSERT_ACTUAL_TREE_CONFLICTS);
            }
            stmt.bindf("iss", wcId, localRelpath, treeConflictData);
            if (!haveRow) {
                stmt.bindString(4, SVNFileUtil.getFilePath(parentRelpath));
            } else {
                stmt.bindNull(4);
            }
            stmt.done();
            if (treeConflictData == null) {
                stmt = db.getStatement(SVNWCDbStatements.DELETE_ACTUAL_EMPTY);
                stmt.bindf("is", wcId, localRelpath);
                stmt.done();
            }
        }
    }

    public Set<String> readChildren(File localAbsPath) throws SVNException {
        return gatherChildren(localAbsPath, false);
    }
    
    public void readChildren(File localAbsPath, Map<String, SVNWCDbInfo> children, Set<String> conflicts) throws SVNException {
        final DirParsedInfo parsed = parseDir(localAbsPath, Mode.ReadOnly);
        SVNWCDbDir pdh = parsed.wcDbDir;
        File localRelPath = parsed.localRelPath;
        
        verifyDirUsable(pdh);
        
        GatherChildren gather = new GatherChildren();
        gather.dirRelPath = localRelPath;
        gather.wcRoot = pdh.getWCRoot();
        
        gather.nodes = children;
        gather.conflicts = conflicts;
        
        pdh.getWCRoot().getSDb().runTransaction(gather);
        
    }
    
    private class GatherChildren implements SVNSqlJetTransaction {
        
        Map<String, SVNWCDbInfo> nodes;
        Set<String> conflicts;
        
        File dirRelPath;
        SVNWCDbRoot wcRoot;
        
        public void transaction(SVNSqlJetDb db) throws SqlJetException, SVNException {
            WCDbRepositoryInfo reposInfo = new WCDbRepositoryInfo();
            
            SVNSqlJetStatement stmt = db.getStatement(SVNWCDbStatements.SELECT_NODE_CHILDREN_INFO);
            stmt.bindf("is", wcRoot.getWcId(), dirRelPath);
            boolean haveRow = stmt.next();
            
            while(haveRow) {
                File childRelPath = SVNFileUtil.createFilePath(getColumnText(stmt, SVNWCDbSchema.NODES__Fields.local_relpath));
                String name = SVNFileUtil.getFileName(childRelPath);
                GatheredChildItem childItem = (GatheredChildItem) nodes.get(name);
                boolean newChild = false; 
                if (childItem == null) {
                    newChild = true;
                    childItem = new GatheredChildItem();
                }
                long opDepth = getColumnInt64(stmt, SVNWCDbSchema.NODES__Fields.op_depth);
                
                if (newChild || opDepth > childItem.opDepth) {
                    childItem.opDepth = opDepth;
                    childItem.kind = getColumnKind(stmt, SVNWCDbSchema.NODES__Fields.kind);
                    childItem.status = getColumnPresence(stmt);
                    if (opDepth != 0) {
                        childItem.status = getWorkingStatus(childItem.status);
                    }
                    if (opDepth != 0) {
                        childItem.revnum = INVALID_REVNUM;
                        childItem.reposRelpath = null;
                    } else {
                        childItem.revnum = getColumnRevNum(stmt, SVNWCDbSchema.NODES__Fields.revision);
                        childItem.reposRelpath = SVNFileUtil.createFilePath(getColumnText(stmt, SVNWCDbSchema.NODES__Fields.repos_path));
                    }
                    if (opDepth != 0 || isColumnNull(stmt, SVNWCDbSchema.NODES__Fields.repos_id)) {
                        childItem.reposRootUrl = null;
                        childItem.reposUuid = null;
                    } else {
                        long reposId = getColumnInt64(stmt, SVNWCDbSchema.NODES__Fields.repos_id);
                        if (reposInfo.rootUrl == null) {
                            fetchReposInfo(reposInfo, db, reposId);
                        }
                        childItem.reposRootUrl = reposInfo.rootUrl;
                        childItem.reposUuid = reposInfo.uuid;
                    }
                    childItem.changedRev = getColumnRevNum(stmt, SVNWCDbSchema.NODES__Fields.changed_revision);
                    childItem.changedDate = SVNWCUtils.readDate(getColumnInt64(stmt, SVNWCDbSchema.NODES__Fields.changed_date));
                    childItem.changedAuthor = getColumnText(stmt, SVNWCDbSchema.NODES__Fields.changed_author);
                    if (childItem.kind != SVNWCDbKind.Dir) {
                        childItem.depth = SVNDepth.UNKNOWN;
                    } else {
                        childItem.depth = getColumnDepth(stmt, SVNWCDbSchema.NODES__Fields.depth);
                        if (newChild) {
                            childItem.locked = isWCLocked(SVNFileUtil.createFilePath(wcRoot.getAbsPath(), childRelPath)); 
                        }
                    }
                    childItem.recordedModTime = getColumnInt64(stmt, SVNWCDbSchema.NODES__Fields.last_mod_time);
                    childItem.recordedSize = getColumnInt64(stmt, SVNWCDbSchema.NODES__Fields.translated_size);
                    childItem.hasChecksum = !isColumnNull(stmt, SVNWCDbSchema.NODES__Fields.checksum);
                    childItem.hadProps = !isColumnNull(stmt, SVNWCDbSchema.NODES__Fields.properties) && getColumnBlob(stmt, SVNWCDbSchema.NODES__Fields.properties).length > 2;
                    
                    if (childItem.hadProps) {
                        SVNProperties properties = getColumnProperties(stmt, SVNWCDbSchema.NODES__Fields.properties);
                        childItem.special = properties.getSVNPropertyValue(SVNProperty.SPECIAL) != null;
                    }
                    
                    if (opDepth == 0) {
                        childItem.opRoot = false;
                    } else {
                        childItem.opRoot = opDepth == SVNWCUtils.relpathDepth(childRelPath);
                    }
                    nodes.put(name, childItem);
                }
                if (opDepth == 0) {
                    childItem.haveBase = true;
                    SVNSqlJetStatement lockStmt = stmt.getJoinedStatement(SVNWCDbSchema.LOCK);
                    if (lockStmt != null && !lockStmt.eof()) { 
                        childItem.lock = new SVNWCDbLock();
                        childItem.lock.token = getColumnText(lockStmt, SVNWCDbSchema.LOCK__Fields.lock_token);
                        if (!isColumnNull(lockStmt, SVNWCDbSchema.LOCK__Fields.lock_owner))
                            childItem.lock.owner = getColumnText(lockStmt, SVNWCDbSchema.LOCK__Fields.lock_owner);
                        if (!isColumnNull(lockStmt, SVNWCDbSchema.LOCK__Fields.lock_comment))
                            childItem.lock.comment = getColumnText(lockStmt, SVNWCDbSchema.LOCK__Fields.lock_comment);
                        if (!isColumnNull(lockStmt, SVNWCDbSchema.LOCK__Fields.lock_date))
                            childItem.lock.date = SVNWCUtils.readDate(getColumnInt64(lockStmt, SVNWCDbSchema.LOCK__Fields.lock_date));
                    }
                } else {
                    childItem.layersCount++;
                    childItem.haveMoreWork = childItem.layersCount > 1;
                }
                haveRow = stmt.next();
            }
            stmt.reset();
            stmt = db.getStatement(SVNWCDbStatements.SELECT_ACTUAL_CHILDREN_INFO);
            stmt.bindf("is", wcRoot.getWcId(), dirRelPath);
            while(stmt.next()) {
                File childRelPath = SVNFileUtil.createFilePath(getColumnText(stmt, SVNWCDbSchema.ACTUAL_NODE__Fields.local_relpath));
                String name = SVNFileUtil.getFileName(childRelPath);
                
                GatheredChildItem childItem = (GatheredChildItem) nodes.get(name);
                if (childItem == null) {
                    childItem = new GatheredChildItem();
                    childItem.status = SVNWCDbStatus.NotPresent;
                }
                childItem.changelist = getColumnText(stmt, SVNWCDbSchema.ACTUAL_NODE__Fields.changelist);
                childItem.propsMod = !isColumnNull(stmt, SVNWCDbSchema.ACTUAL_NODE__Fields.properties);
                if (childItem.propsMod) {
                    SVNProperties properties = getColumnProperties(stmt, SVNWCDbSchema.ACTUAL_NODE__Fields.properties);
                    childItem.special = properties.getSVNPropertyValue(SVNProperty.SPECIAL) != null;
                }
                childItem.conflicted = !isColumnNull(stmt, SVNWCDbSchema.ACTUAL_NODE__Fields.conflict_old) ||
                                        !isColumnNull(stmt, SVNWCDbSchema.ACTUAL_NODE__Fields.conflict_new) ||
                                        !isColumnNull(stmt, SVNWCDbSchema.ACTUAL_NODE__Fields.conflict_working) ||
                                        !isColumnNull(stmt, SVNWCDbSchema.ACTUAL_NODE__Fields.prop_reject) ||
                                        !isColumnNull(stmt, SVNWCDbSchema.ACTUAL_NODE__Fields.tree_conflict_data);
                if (childItem.conflicted) {
                    conflicts.add(name);
                }
            }
            stmt.reset();
        }
        
    }
    
    private static class GatheredChildItem extends SVNWCDbInfo {
        public int layersCount;
        public long opDepth;
    }
    
    


    private Set<String> gatherChildren(File localAbsPath, boolean baseOnly) throws SVNException {
        final DirParsedInfo wcInfo = obtainWcRoot(localAbsPath);
        final File localRelPath = wcInfo.localRelPath;

        final long wcId = wcInfo.wcDbDir.getWCRoot().getWcId();
        final SVNSqlJetDb sDb = wcInfo.wcDbDir.getWCRoot().getSDb();

        final Set<String> names = new TreeSet<String>();

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

    private void addChildren(Set<String> children, SVNSqlJetStatement stmt) throws SVNException {
        try {
            while (stmt.next()) {
                String child_relpath = getColumnText(stmt, SVNWCDbSchema.NODES__Fields.local_relpath);
                String name = SVNFileUtil.getFileName(SVNFileUtil.createFilePath(child_relpath));
                children.add(name);
            }
        } finally {
            stmt.reset();
        }
    }

    public List<String> readConflictVictims(File localAbsPath) throws SVNException {
        final DirParsedInfo wcInfo = obtainWcRoot(localAbsPath);
        final File localRelPath = wcInfo.localRelPath;
        final long wcId = wcInfo.wcDbDir.getWCRoot().getWcId();
        final SVNSqlJetDb sDb = wcInfo.wcDbDir.getWCRoot().getSDb();

        SVNSqlJetStatement stmt;

        List<String> victims = new ArrayList<String>();

        /*
         * ### This will be much easier once we have all conflicts in one field
         * of actual
         */

        Set<String> found = new HashSet<String>();

        /* First look for text and property conflicts in ACTUAL */
        stmt = sDb.getStatement(SVNWCDbStatements.SELECT_ACTUAL_CONFLICT_VICTIMS);
        try {
            stmt.bindf("is", wcId, SVNFileUtil.getFilePath(localRelPath));
            while (stmt.next()) {
                String child_relpath = getColumnText(stmt, SVNWCDbSchema.ACTUAL_NODE__Fields.local_relpath);
                String child_name = SVNFileUtil.getFileName(SVNFileUtil.createFilePath(child_relpath));
                found.add(child_name);
            }
        } finally {
            stmt.reset();
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
                    final File reposFile = SVNFileUtil.createFilePath(pdh.getWCRoot().getAbsPath(), prop_reject);
                    final SVNMergeFileSet mergeFiles = new SVNMergeFileSet(null, null, null, localAbsPath, null, reposFile, null, null, null);
                    final SVNPropertyConflictDescription desc = new SVNPropertyConflictDescription(mergeFiles, SVNNodeKind.UNKNOWN, "", null, null);
                    conflicts.add(desc);
                }

                final String conflict_old = getColumnText(stmt, 1);
                final String conflict_new = getColumnText(stmt, 2);
                final String conflict_working = getColumnText(stmt, 3);

                if (conflict_old != null || conflict_new != null || conflict_working != null) {
                    File baseFile = conflict_old != null ? SVNFileUtil.createFilePath(pdh.getWCRoot().getAbsPath(), conflict_old) : null;
                    File theirFile = conflict_new != null ? SVNFileUtil.createFilePath(pdh.getWCRoot().getAbsPath(), conflict_new) : null;
                    File myFile = conflict_working != null ? SVNFileUtil.createFilePath(pdh.getWCRoot().getAbsPath(), conflict_working) : null;
                    File mergedFile = new File(SVNFileUtil.getFileName(localAbsPath));
                    SVNMergeFileSet mergeFiles = new SVNMergeFileSet(null, null, baseFile, myFile, null, theirFile, mergedFile, null, null);
                    SVNTextConflictDescription desc = new SVNTextConflictDescription(mergeFiles, SVNNodeKind.UNKNOWN, null, null);
                    conflicts.add(desc);
                }

                byte[] conflict_data = getColumnBlob(stmt, 4);
                if (conflict_data != null) {
                    SVNSkel skel = SVNSkel.parse(conflict_data);
                    SVNTreeConflictDescription desc = SVNTreeConflictUtil.readSingleTreeConflict(skel, SVNFileUtil.getFileDir(localAbsPath));
                    conflicts.add(desc);
                }

            }
        } finally {
            stmt.reset();
        }

        return conflicts;
    }

    public WCDbInfo readInfo(File localAbsPath, InfoField... fields) throws SVNException {
        final DirParsedInfo wcInfo = obtainWcRoot(localAbsPath);
        final File localRelPath = wcInfo.localRelPath;
        final SVNSqlJetDb sDb = wcInfo.wcDbDir.getWCRoot().getSDb();
        
        WCDbInfo info = readInfo(wcInfo.wcDbDir.getWCRoot(), localRelPath, fields);
        
        final EnumSet<InfoField> f = getInfoFields(InfoField.class, fields);
        if (f.contains(InfoField.reposRootUrl) || f.contains(InfoField.reposUuid)) {
            ReposInfo reposInfo = fetchReposInfo(sDb, info.reposId);
            if (reposInfo.reposRootUrl != null) {
                info.reposRootUrl = f.contains(InfoField.reposRootUrl) ? SVNURL.parseURIEncoded(reposInfo.reposRootUrl) : null;
            }
            info.reposUuid = f.contains(InfoField.reposUuid) ? reposInfo.reposUuid : null;
        }
        if (f.contains(InfoField.originalRootUrl) || f.contains(InfoField.originalUuid)) {
            ReposInfo reposInfo = fetchReposInfo(sDb, info.originalReposId);
            if (reposInfo.reposRootUrl != null) {
                info.originalRootUrl = f.contains(InfoField.originalRootUrl) ? SVNURL.parseURIEncoded(reposInfo.reposRootUrl) : null;
            }
            info.originalUuid = f.contains(InfoField.originalUuid) ? reposInfo.reposUuid : null;
        }
        return info;
    }

    public Structure<NodeInfo> readInfo(File localAbsPath, NodeInfo... fields) throws SVNException {
        final DirParsedInfo wcInfo = obtainWcRoot(localAbsPath);
        final File localRelPath = wcInfo.localRelPath;
        final SVNSqlJetDb sDb = wcInfo.wcDbDir.getWCRoot().getSDb();
        
        Structure<NodeInfo> info = readInfo(wcInfo.wcDbDir.getWCRoot(), localRelPath, fields);
        
        
        if (info.hasField(NodeInfo.reposRootUrl) || info.hasField(NodeInfo.reposUuid)) {
            Structure<RepositoryInfo> reposInfo = fetchRepositoryInfo(sDb, info.lng(NodeInfo.reposId));
            reposInfo.from(RepositoryInfo.reposRootUrl, RepositoryInfo.reposUuid).into(info, NodeInfo.reposRootUrl, NodeInfo.reposUuid);
            reposInfo.release();
        }
        if (info.hasField(NodeInfo.originalRootUrl) || info.hasField(NodeInfo.originalUuid)) {
            Structure<RepositoryInfo> reposInfo = fetchRepositoryInfo(sDb, info.lng(NodeInfo.originalReposId));
            reposInfo.from(RepositoryInfo.reposRootUrl, RepositoryInfo.reposUuid).into(info, NodeInfo.originalRootUrl, NodeInfo.originalUuid);
            reposInfo.release();
        }
        return info;
    }
    
    public long readOpDepth(SVNWCDbRoot root, File localRelPath) throws SVNException {
        SVNSqlJetStatement stmt = null;
        try { 
            stmt = root.getSDb().getStatement(SVNWCDbStatements.SELECT_NODE_INFO);
            stmt.bindf("is", root.getWcId(), localRelPath);
            if (stmt.next()) {
                return getColumnInt64(stmt, SVNWCDbSchema.NODES__Fields.op_depth);
            }
        } finally {
            try {
                if (stmt != null) {
                    stmt.reset();
                } 
            } catch (SVNException e) {} 
        }
        return 0;
    }
    
    public WCDbInfo readInfoBelowWorking(File localAbsPath) throws SVNException {
        final DirParsedInfo wcInfo = obtainWcRoot(localAbsPath);
        final File localRelPath = wcInfo.localRelPath;
        return readInfoBelowWorking(wcInfo.wcDbDir.getWCRoot(), localRelPath, -1);
    }
    
    public WCDbInfo readInfoBelowWorking(SVNWCDbRoot wcRoot, File localRelPath, int belowOpDepth) throws SVNException {

        WCDbInfo info = new WCDbInfo();
        SVNSqlJetStatement stmt = null;
        boolean haveRow;
        try { 
            stmt = wcRoot.getSDb().getStatement(SVNWCDbStatements.SELECT_NODE_INFO);
            stmt.bindf("is", wcRoot.getWcId(), localRelPath);
            haveRow = stmt.next();
            
            if (belowOpDepth >= 0) {
                while(haveRow && getColumnInt64(stmt, SVNWCDbSchema.NODES__Fields.op_depth) > belowOpDepth) {
                    haveRow = stmt.next();
                }
            }
            if (haveRow) {
                haveRow = stmt.next();
                if (haveRow) {
                    info.status = getColumnPresence(stmt);
                }
                while (haveRow) {
                    if (getColumnInt64(stmt, SVNWCDbSchema.NODES__Fields.op_depth) > 0) {
                        info.haveWork = true; 
                    } else {
                        info.haveBase = true;
                    }
                    haveRow = stmt.next();
                }
            }
        } finally {
            try {
                if (stmt != null) {
                    stmt.reset();
                } 
            } catch (SVNException e) {} 
        }
        if (info.haveWork) {
            info.status = getWorkingStatus(info.status); 
        }
        return info;
    }
    
    private WCDbInfo readInfo(SVNWCDbRoot wcRoot, File localRelPath, InfoField... fields) throws SVNException {

        WCDbInfo info = new WCDbInfo();

        final EnumSet<InfoField> f = getInfoFields(InfoField.class, fields);
        SVNSqlJetStatement stmtInfo = null;
        SVNSqlJetStatement stmtActual = null;
        
        try {
            stmtInfo = wcRoot.getSDb().getStatement(f.contains(InfoField.lock) ? SVNWCDbStatements.SELECT_NODE_INFO_WITH_LOCK : SVNWCDbStatements.SELECT_NODE_INFO);
            stmtInfo.bindf("is", wcRoot.getWcId(), localRelPath);
            boolean haveInfo = stmtInfo.next();
            boolean haveActual = false;
            
            if (f.contains(InfoField.changelist) || f.contains(InfoField.conflicted) || f.contains(InfoField.propsMod)) {
                stmtActual = wcRoot.getSDb().getStatement(SVNWCDbStatements.SELECT_ACTUAL_NODE);
                stmtActual.bindf("is", wcRoot.getWcId(), localRelPath);
                haveActual = stmtActual.next();
            }
            
            if (haveInfo) {
                long opDepth = getColumnInt64(stmtInfo, SVNWCDbSchema.NODES__Fields.op_depth);
                SVNWCDbKind nodeKind = getColumnKind(stmtInfo, NODES__Fields.kind);
                if (f.contains(InfoField.status)) {
                    info.status = getColumnPresence(stmtInfo);
                    if (opDepth != 0) {
                        info.status = getWorkingStatus(info.status);
                    }
                }
                if (f.contains(InfoField.kind)) {
                    info.kind = nodeKind;
                }
                info.reposId = opDepth != 0 ? INVALID_REPOS_ID : getColumnInt64(stmtInfo, SVNWCDbSchema.NODES__Fields.repos_id);
                if (f.contains(InfoField.revision)) {
                    info.revision = opDepth != 0 ? INVALID_REVNUM : getColumnRevNum(stmtInfo, SVNWCDbSchema.NODES__Fields.revision);
                }
                if (f.contains(InfoField.reposRelPath)) {
                    info.reposRelPath = opDepth != 0 ? null : SVNFileUtil.createFilePath(getColumnText(stmtInfo, SVNWCDbSchema.NODES__Fields.repos_path));
                }
                if (f.contains(InfoField.changedDate)) {
                    info.changedDate = SVNWCUtils.readDate(getColumnInt64(stmtInfo, SVNWCDbSchema.NODES__Fields.changed_date));
                }
                if (f.contains(InfoField.changedRev)) {
                    info.changedRev = getColumnRevNum(stmtInfo, SVNWCDbSchema.NODES__Fields.changed_revision);
                }
                if (f.contains(InfoField.changedAuthor)) {
                    info.changedAuthor = getColumnText(stmtInfo, SVNWCDbSchema.NODES__Fields.changed_author);                
                }
                if (f.contains(InfoField.lastModTime)) {
                    info.lastModTime = getColumnInt64(stmtInfo, SVNWCDbSchema.NODES__Fields.last_mod_time);
                }
                if (f.contains(InfoField.depth)) {
                    if (nodeKind != SVNWCDbKind.Dir) {
                        info.depth = SVNDepth.UNKNOWN;
                    } else {
                        info.depth = getColumnDepth(stmtInfo, SVNWCDbSchema.NODES__Fields.depth);
                    }
                }
                if (f.contains(InfoField.checksum)) {
                    if (nodeKind != SVNWCDbKind.File) {
                        info.checksum = null;
                    } else {
                        try {
                            info.checksum = getColumnChecksum(stmtInfo, SVNWCDbSchema.NODES__Fields.checksum);
                        } catch (SVNException e) {
                            SVNErrorMessage err = SVNErrorMessage.create(e.getErrorMessage().getErrorCode(), "The node ''{0}'' has a corrupt checksum value.", wcRoot.getAbsPath(localRelPath));
                            SVNErrorManager.error(err, SVNLogType.WC);
                        }
                    }                
                }
                if (f.contains(InfoField.translatedSize)) {
                    info.translatedSize = getColumnInt64(stmtInfo, SVNWCDbSchema.NODES__Fields.translated_size);
                }
                if (f.contains(InfoField.target)) {
                    info.target = SVNFileUtil.createFilePath(getColumnText(stmtInfo, SVNWCDbSchema.NODES__Fields.symlink_target));
                }
                if (f.contains(InfoField.changelist) && haveActual) {
                    info.changelist = getColumnText(stmtActual, SVNWCDbSchema.ACTUAL_NODE__Fields.changelist);
                }
                info.originalReposId = opDepth == 0 ? INVALID_REPOS_ID : getColumnInt64(stmtInfo, SVNWCDbSchema.NODES__Fields.repos_id);
                if (f.contains(InfoField.originalRevision)) {
                    info.originalRevision = opDepth == 0 ? INVALID_REVNUM : getColumnRevNum(stmtInfo, SVNWCDbSchema.NODES__Fields.revision);
                }
                if (f.contains(InfoField.originalReposRelpath)) {
                    info.originalReposRelpath = opDepth == 0 ? null : SVNFileUtil.createFilePath(getColumnText(stmtInfo, SVNWCDbSchema.NODES__Fields.repos_path));
                }
                if (f.contains(InfoField.propsMod) && haveActual) {
                    info.propsMod = !isColumnNull(stmtActual, SVNWCDbSchema.ACTUAL_NODE__Fields.properties);
                }
                if (f.contains(InfoField.hadProps)) {
                    byte[] props = getColumnBlob(stmtInfo, SVNWCDbSchema.NODES__Fields.properties);
                    info.hadProps = props != null && props.length > 2; 
                }
                if (f.contains(InfoField.conflicted) && haveActual) {
                    info.conflicted = !isColumnNull(stmtActual, SVNWCDbSchema.ACTUAL_NODE__Fields.conflict_old) || /* old */
                        !isColumnNull(stmtActual, SVNWCDbSchema.ACTUAL_NODE__Fields.conflict_new) || /* new */
                        !isColumnNull(stmtActual, SVNWCDbSchema.ACTUAL_NODE__Fields.conflict_working) || /* working */
                        !isColumnNull(stmtActual, SVNWCDbSchema.ACTUAL_NODE__Fields.prop_reject) || /* prop_reject */
                        !isColumnNull(stmtActual, SVNWCDbSchema.ACTUAL_NODE__Fields.tree_conflict_data) /* tree_conflict_data */;
                }
                if (f.contains(InfoField.lock) && opDepth == 0) {
                    final SVNSqlJetStatement stmtBaseLock = stmtInfo.getJoinedStatement(SVNWCDbSchema.LOCK.toString());
                    if (!isColumnNull(stmtBaseLock, SVNWCDbSchema.LOCK__Fields.lock_token)) {
                        info.lock = new SVNWCDbLock();
                        info.lock.token = getColumnText(stmtBaseLock, SVNWCDbSchema.LOCK__Fields.lock_token);
                        if (!isColumnNull(stmtBaseLock, SVNWCDbSchema.LOCK__Fields.lock_owner)) {
                            info.lock.owner = getColumnText(stmtBaseLock, SVNWCDbSchema.LOCK__Fields.lock_owner);
                        }
                        if (!isColumnNull(stmtBaseLock, SVNWCDbSchema.LOCK__Fields.lock_comment)) {
                            info.lock.comment = getColumnText(stmtBaseLock, SVNWCDbSchema.LOCK__Fields.lock_comment);
                        }
                        if (!isColumnNull(stmtBaseLock, SVNWCDbSchema.LOCK__Fields.lock_date)) {
                            info.lock.date = SVNWCUtils.readDate(getColumnInt64(stmtBaseLock, SVNWCDbSchema.LOCK__Fields.lock_date));
                        }
                    }
                }
                if (f.contains(InfoField.haveWork)) {
                    info.haveWork = opDepth != 0;
                }
                if (f.contains(InfoField.opRoot)) {
                    info.opRoot = opDepth > 0 && opDepth == SVNWCUtils.relpathDepth(localRelPath);
                }
                if (f.contains(InfoField.haveBase) || f.contains(InfoField.haveWork)) {
                    while(opDepth != 0) {
                        haveInfo = stmtInfo.next();
                        if (!haveInfo) {
                            break;
                        }
                        opDepth = getColumnInt64(stmtInfo, SVNWCDbSchema.NODES__Fields.op_depth);
                        if (f.contains(InfoField.haveMoreWork)) {
                            if (opDepth > 0) {
                                info.haveMoreWork = true;
                            }
                            if (!f.contains(InfoField.haveBase)) {
                                break;
                            }
                        }
                    }
                    if (f.contains(InfoField.haveBase)) {
                        info.haveBase = opDepth == 0;
                    }
                }
            } else if (haveActual) {
                if (isColumnNull(stmtActual, SVNWCDbSchema.ACTUAL_NODE__Fields.tree_conflict_data)) {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_CORRUPT, "Corrupt data for ''{0}''", wcRoot.getAbsPath(localRelPath));
                    SVNErrorManager.error(err, SVNLogType.WC);
                }    
                assert (f.contains(InfoField.conflicted));    
                if (f.contains(InfoField.status)) {
                    info.status = SVNWCDbStatus.Normal;
                }
                if (f.contains(InfoField.kind)) {
                    info.kind = SVNWCDbKind.Unknown;
                }
                if (f.contains(InfoField.revision)) {
                    info.revision = INVALID_REVNUM;
                }
                info.reposId = INVALID_REPOS_ID;
                if (f.contains(InfoField.changedRev)) {
                    info.changedRev = INVALID_REVNUM;
                }
                if (f.contains(InfoField.depth)) {
                    info.depth = SVNDepth.UNKNOWN;
                }
                if (f.contains(InfoField.originalRevision)) {
                    info.originalRevision = INVALID_REVNUM;
                }
                if (f.contains(InfoField.originalReposId)) {
                    info.originalReposId = INVALID_REPOS_ID;
                }
                if (f.contains(InfoField.changelist)) {
                    info.changelist = stmtActual.getColumnString(SVNWCDbSchema.ACTUAL_NODE__Fields.changelist);
                }
                if (f.contains(InfoField.originalRevision))
                    info.originalRevision = INVALID_REVNUM;
            } else {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_PATH_NOT_FOUND, "The node ''{0}'' was not found.", wcRoot.getAbsPath(localRelPath));
                SVNErrorManager.error(err, SVNLogType.WC);
            }
        } finally {            
            try {
                if (stmtInfo != null) {
                    stmtInfo.reset();
                }
            } catch (SVNException e) {} 
            try {
                if (stmtActual != null) {
                    stmtActual.reset();
                }
            } catch (SVNException e) {} 
        }

        return info;
    }
    
    private Structure<NodeInfo> readInfo(SVNWCDbRoot wcRoot, File localRelPath, NodeInfo... fields) throws SVNException {
        Structure<NodeInfo> info = Structure.obtain(NodeInfo.class, fields);

        SVNSqlJetStatement stmtInfo = null;
        SVNSqlJetStatement stmtActual = null;
        
        try {
            stmtInfo = wcRoot.getSDb().getStatement(info.hasField(NodeInfo.lock) ? SVNWCDbStatements.SELECT_NODE_INFO_WITH_LOCK : SVNWCDbStatements.SELECT_NODE_INFO);
            stmtInfo.bindf("is", wcRoot.getWcId()  , localRelPath);
            boolean haveInfo = stmtInfo.next();
            boolean haveActual = false;
            
            if (info.hasField(NodeInfo.changelist) || info.hasField(NodeInfo.conflicted) || info.hasField(NodeInfo.propsMod)) {
                stmtActual = wcRoot.getSDb().getStatement(SVNWCDbStatements.SELECT_ACTUAL_NODE);
                stmtActual.bindf("is", wcRoot.getWcId(), localRelPath);
                haveActual = stmtActual.next();
            }
            
            if (haveInfo) {
                long opDepth = getColumnInt64(stmtInfo, SVNWCDbSchema.NODES__Fields.op_depth);
                SVNWCDbKind nodeKind = getColumnKind(stmtInfo, NODES__Fields.kind);
                
                if (info.hasField(NodeInfo.status)) {
                    info.set(NodeInfo.status, getColumnPresence(stmtInfo));
                    if (opDepth != 0) {
                        info.set(NodeInfo.status, getWorkingStatus(info.<SVNWCDbStatus>get(NodeInfo.status)));
                    }
                }
                if (info.hasField(NodeInfo.kind)) {
                    info.set(NodeInfo.kind, nodeKind);
                }
                info.set(NodeInfo.reposId, opDepth != 0 ? INVALID_REPOS_ID : getColumnInt64(stmtInfo, SVNWCDbSchema.NODES__Fields.repos_id));
                if (info.hasField(NodeInfo.revision)) {
                    info.set(NodeInfo.revision, opDepth != 0 ? INVALID_REVNUM : getColumnRevNum(stmtInfo, SVNWCDbSchema.NODES__Fields.revision));
                }
                if (info.hasField(NodeInfo.reposRelPath)) {
                    info.set(NodeInfo.reposRelPath, opDepth != 0 ? null : SVNFileUtil.createFilePath(getColumnText(stmtInfo, SVNWCDbSchema.NODES__Fields.repos_path)));
                }
                if (info.hasField(NodeInfo.changedDate)) {
                    info.set(NodeInfo.changedDate, SVNWCUtils.readDate(getColumnInt64(stmtInfo, SVNWCDbSchema.NODES__Fields.changed_date)));
                }
                if (info.hasField(NodeInfo.changedRev)) {
                    info.set(NodeInfo.changedRev, getColumnRevNum(stmtInfo, SVNWCDbSchema.NODES__Fields.changed_revision));
                }
                if (info.hasField(NodeInfo.changedAuthor)) {
                    info.set(NodeInfo.changedAuthor, getColumnText(stmtInfo, SVNWCDbSchema.NODES__Fields.changed_author));                
                }
                if (info.hasField(NodeInfo.recordedTime)) {
                    info.set(NodeInfo.recordedTime, getColumnInt64(stmtInfo, SVNWCDbSchema.NODES__Fields.last_mod_time));
                }
                if (info.hasField(NodeInfo.depth)) {
                    if (nodeKind != SVNWCDbKind.Dir) {
                        info.set(NodeInfo.depth, SVNDepth.UNKNOWN);
                    } else {
                        info.set(NodeInfo.depth, getColumnDepth(stmtInfo, SVNWCDbSchema.NODES__Fields.depth));
                    }
                }
                if (info.hasField(NodeInfo.checksum)) {
                    if (nodeKind != SVNWCDbKind.File) {
                        info.set(NodeInfo.checksum, null);
                    } else {
                        try {
                            info.set(NodeInfo.checksum, getColumnChecksum(stmtInfo, SVNWCDbSchema.NODES__Fields.checksum));
                        } catch (SVNException e) {
                            SVNErrorMessage err = SVNErrorMessage.create(e.getErrorMessage().getErrorCode(), "The node ''{0}'' has a corrupt checksum value.", wcRoot.getAbsPath(localRelPath));
                            SVNErrorManager.error(err, SVNLogType.WC);
                        }
                    }                
                }
                if (info.hasField(NodeInfo.recordedSize)) {
                    info.set(NodeInfo.recordedSize, getColumnInt64(stmtInfo, SVNWCDbSchema.NODES__Fields.translated_size));
                }
                if (info.hasField(NodeInfo.target)) {
                    info.set(NodeInfo.target, SVNFileUtil.createFilePath(getColumnText(stmtInfo, SVNWCDbSchema.NODES__Fields.symlink_target)));
                }
                if (info.hasField(NodeInfo.changelist) && haveActual) {
                    info.set(NodeInfo.changelist, getColumnText(stmtActual, SVNWCDbSchema.ACTUAL_NODE__Fields.changelist));
                }
                info.set(NodeInfo.originalReposId, opDepth == 0 ? INVALID_REPOS_ID : getColumnInt64(stmtInfo, SVNWCDbSchema.NODES__Fields.repos_id));
                if (info.hasField(NodeInfo.originalRevision)) {
                    info.set(NodeInfo.originalRevision, opDepth == 0 ? INVALID_REVNUM : getColumnRevNum(stmtInfo, SVNWCDbSchema.NODES__Fields.revision));
                }
                if (info.hasField(NodeInfo.originalReposRelpath)) {
                    info.set(NodeInfo.originalReposRelpath, opDepth == 0 ? null : SVNFileUtil.createFilePath(getColumnText(stmtInfo, SVNWCDbSchema.NODES__Fields.repos_path)));
                }
                if (info.hasField(NodeInfo.propsMod) && haveActual) {
                    info.set(NodeInfo.propsMod, !isColumnNull(stmtActual, SVNWCDbSchema.ACTUAL_NODE__Fields.properties));
                }
                if (info.hasField(NodeInfo.hadProps)) {
                    byte[] props = getColumnBlob(stmtInfo, SVNWCDbSchema.NODES__Fields.properties);
                    info.set(NodeInfo.hadProps, props != null && props.length > 2); 
                }
                if (info.hasField(NodeInfo.conflicted) && haveActual) {
                    info.set(NodeInfo.conflicted, !isColumnNull(stmtActual, SVNWCDbSchema.ACTUAL_NODE__Fields.conflict_old) || /* old */
                        !isColumnNull(stmtActual, SVNWCDbSchema.ACTUAL_NODE__Fields.conflict_new) || /* new */
                        !isColumnNull(stmtActual, SVNWCDbSchema.ACTUAL_NODE__Fields.conflict_working) || /* working */
                        !isColumnNull(stmtActual, SVNWCDbSchema.ACTUAL_NODE__Fields.prop_reject) || /* prop_reject */
                        !isColumnNull(stmtActual, SVNWCDbSchema.ACTUAL_NODE__Fields.tree_conflict_data)); /* tree_conflict_data */
                }
                if (info.hasField(NodeInfo.lock) && opDepth == 0) {
                    final SVNSqlJetStatement stmtBaseLock = stmtInfo.getJoinedStatement(SVNWCDbSchema.LOCK.toString());
                    if (!isColumnNull(stmtBaseLock, SVNWCDbSchema.LOCK__Fields.lock_token)) {
                        info.set(NodeInfo.lock, new SVNWCDbLock());
                        info.<SVNWCDbLock>get(NodeInfo.lock).token = getColumnText(stmtBaseLock, SVNWCDbSchema.LOCK__Fields.lock_token);
                        if (!isColumnNull(stmtBaseLock, SVNWCDbSchema.LOCK__Fields.lock_owner)) {
                            info.<SVNWCDbLock>get(NodeInfo.lock).owner = getColumnText(stmtBaseLock, SVNWCDbSchema.LOCK__Fields.lock_owner);
                        }
                        if (!isColumnNull(stmtBaseLock, SVNWCDbSchema.LOCK__Fields.lock_comment)) {
                            info.<SVNWCDbLock>get(NodeInfo.lock).comment = getColumnText(stmtBaseLock, SVNWCDbSchema.LOCK__Fields.lock_comment);
                        }
                        if (!isColumnNull(stmtBaseLock, SVNWCDbSchema.LOCK__Fields.lock_date)) {
                            info.<SVNWCDbLock>get(NodeInfo.lock).date = SVNWCUtils.readDate(getColumnInt64(stmtBaseLock, SVNWCDbSchema.LOCK__Fields.lock_date));
                        }
                    }
                }
                if (info.hasField(NodeInfo.haveWork)) {
                    info.set(NodeInfo.haveWork, opDepth != 0);
                }
                if (info.hasField(NodeInfo.opRoot)) {
                    info.set(NodeInfo.opRoot, opDepth > 0 && opDepth == SVNWCUtils.relpathDepth(localRelPath));
                }
                if (info.hasField(NodeInfo.haveBase) || info.hasField(NodeInfo.haveWork)) {
                    while(opDepth != 0) {
                        haveInfo = stmtInfo.next();
                        if (!haveInfo) {
                            break;
                        }
                        opDepth = getColumnInt64(stmtInfo, SVNWCDbSchema.NODES__Fields.op_depth);
                        if (info.hasField(NodeInfo.haveMoreWork)) {
                            if (opDepth > 0) {
                                info.set(NodeInfo.haveMoreWork, true);
                            }
                            if (!info.hasField(NodeInfo.haveBase)) {
                                break;
                            }
                        }
                    }
                    if (info.hasField(NodeInfo.haveBase)) {
                        info.set(NodeInfo.haveBase, opDepth == 0);
                    }
                }
            } else if (haveActual) {
                if (isColumnNull(stmtActual, SVNWCDbSchema.ACTUAL_NODE__Fields.tree_conflict_data)) {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_CORRUPT, "Corrupt data for ''{0}''", wcRoot.getAbsPath(localRelPath));
                    SVNErrorManager.error(err, SVNLogType.WC);
                }    
                assert (info.hasField(NodeInfo.conflicted));    
                if (info.hasField(NodeInfo.status)) {
                    info.set(NodeInfo.status, SVNWCDbStatus.Normal);
                }
                if (info.hasField(NodeInfo.kind)) {
                    info.set(NodeInfo.kind, SVNWCDbKind.Unknown);
                }
                if (info.hasField(NodeInfo.revision)) {
                    info.set(NodeInfo.revision, INVALID_REVNUM);
                }
                info.set(NodeInfo.reposId, INVALID_REPOS_ID);
                if (info.hasField(NodeInfo.changedRev)) {
                    info.set(NodeInfo.changedRev, INVALID_REVNUM);
                }
                if (info.hasField(NodeInfo.depth)) {
                    info.set(NodeInfo.depth, SVNDepth.UNKNOWN);
                }
                if (info.hasField(NodeInfo.originalRevision)) {
                    info.set(NodeInfo.originalRevision, INVALID_REVNUM);
                }
                if (info.hasField(NodeInfo.originalReposId)) {
                    info.set(NodeInfo.originalReposId, INVALID_REPOS_ID);
                }
                if (info.hasField(NodeInfo.changelist)) {
                    info.set(NodeInfo.changelist, stmtActual.getColumnString(SVNWCDbSchema.ACTUAL_NODE__Fields.changelist));
                }
                if (info.hasField(NodeInfo.originalRevision))
                    info.set(NodeInfo.originalRevision, INVALID_REVNUM);
            } else {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_PATH_NOT_FOUND, "The node ''{0}'' was not found.", wcRoot.getAbsPath(localRelPath));
                SVNErrorManager.error(err, SVNLogType.WC);
            }
        } finally {            
            try {
                if (stmtInfo != null) {
                    stmtInfo.reset();
                }
            } catch (SVNException e) {} 
            try {
                if (stmtActual != null) {
                    stmtActual.reset();
                }
            } catch (SVNException e) {} 
        }

        return info;
    }

    public static SVNWCDbStatus getWorkingStatus(SVNWCDbStatus status) {
        if (status == SVNWCDbStatus.Incomplete || status == SVNWCDbStatus.Excluded) {
            return status;
        } else if (status == SVNWCDbStatus.NotPresent || status == SVNWCDbStatus.BaseDeleted) {
            return SVNWCDbStatus.Deleted;
        } 
        return SVNWCDbStatus.Added;
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
        if (sha1Checksum.getKind() != SVNChecksumKind.SHA1) {
            sha1Checksum = getPristineSHA1(wcRootAbsPath, sha1Checksum);
        }
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

        /* Get the first two characters of the digest, for the subdir. */
        String subdir = hexdigest.substring(0, 2);

        if (createSubdir) {
            File subdirAbspath = SVNFileUtil.createFilePath(base_dir_abspath, subdir);
            subdirAbspath.mkdirs();
            /*
             * Whatever error may have occurred... ignore it. Typically, this
             * will be "directory already exists", but if it is something
             * different*, then presumably another error will follow when we try
             * to access the file within this (missing?) pristine subdir.
             */
        }

        /* The file is located at DIR/.svn/pristine/XX/XXYYZZ... */
        return SVNFileUtil.createFilePath(SVNFileUtil.createFilePath(base_dir_abspath, subdir), hexdigest + PRISTINE_STORAGE_EXT);
    }

    public SVNProperties readPristineProperties(File localAbsPath) throws SVNException {
        assert (isAbsolute(localAbsPath));
        final DirParsedInfo parseDir = parseDir(localAbsPath, Mode.ReadWrite);
        SVNWCDbDir pdh = parseDir.wcDbDir;
        File localRelPath = parseDir.localRelPath;
        verifyDirUsable(pdh);
        return readPristineProperties(pdh, localRelPath);
    }

    public String readProperty(File localAbsPath, String propname) throws SVNException {
        // TODO
        throw new UnsupportedOperationException();
    }

    public SVNProperties readProperties(File localAbsPath) throws SVNException {
        assert (isAbsolute(localAbsPath));
        final DirParsedInfo parseDir = parseDir(localAbsPath, Mode.ReadWrite);
        SVNWCDbDir pdh = parseDir.wcDbDir;
        File localRelPath = parseDir.localRelPath;
        verifyDirUsable(pdh);

        SVNProperties props = null;
        boolean have_row = false;
        SVNSqlJetStatement stmt = pdh.getWCRoot().getSDb().getStatement(SVNWCDbStatements.SELECT_ACTUAL_PROPS);
        stmt.bindf("is", pdh.getWCRoot().getWcId(), localRelPath);
        try {
            have_row = stmt.next();

            if (have_row && !isColumnNull(stmt, ACTUAL_NODE__Fields.properties)) {
                props = getColumnProperties(stmt, ACTUAL_NODE__Fields.properties);
            } else
                have_row = false;
        } finally {
            stmt.reset();
        }

        if (have_row) {
            return props;
        }

        /* No local changes. Return the pristine props for this node. */
        props = readPristineProperties(pdh, localRelPath);
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
        assert (isAbsolute(localAbsPath));
        DirParsedInfo parseDir = parseDir(localAbsPath, Mode.ReadWrite);
        SVNWCDbDir pdh = parseDir.wcDbDir;
        File localRelpath = parseDir.localRelPath;
        verifyDirUsable(pdh);
        BaseRemove brb = new BaseRemove();
        brb.localRelpath = localRelpath;
        brb.wcId = pdh.getWCRoot().getWcId();
        pdh.getWCRoot().getSDb().runTransaction(brb);
        pdh.flushEntries(localAbsPath);
    }

    private class BaseRemove implements SVNSqlJetTransaction {

        public long wcId;
        public File localRelpath;

        public void transaction(SVNSqlJetDb db) throws SqlJetException, SVNException {
            boolean haveRow = false;
            SVNSqlJetStatement stmt = db.getStatement(SVNWCDbStatements.DELETE_BASE_NODE);
            stmt.bindf("is", wcId, localRelpath);
            stmt.done();
            retractParentDelete(db);
            stmt = db.getStatement(SVNWCDbStatements.SELECT_WORKING_NODE);
            try {
                stmt.bindf("is", wcId, localRelpath);
                haveRow = stmt.next();
            } finally {
                stmt.reset();
            }
            if (!haveRow) {
                stmt = db.getStatement(SVNWCDbStatements.DELETE_ACTUAL_NODE_WITHOUT_CONFLICT);
                stmt.bindf("is", wcId, localRelpath);
                stmt.done();
            }
        }

        private void retractParentDelete(SVNSqlJetDb db) throws SVNException {
            SVNSqlJetStatement stmt = db.getStatement(SVNWCDbStatements.DELETE_LOWEST_WORKING_NODE);
            stmt.bindf("is", wcId, localRelpath);
            stmt.done();
        }
    }

    public void removeLock(File localAbsPath) throws SVNException {
        assert (isAbsolute(localAbsPath));
        DirParsedInfo parseDir = parseDir(localAbsPath, Mode.ReadWrite);
        SVNWCDbDir pdh = parseDir.wcDbDir;
        File localRelpath = parseDir.localRelPath;
        verifyDirUsable(pdh);
        WCDbRepositoryInfo reposInfo = new WCDbRepositoryInfo();
        long reposId = scanUpwardsForRepos(reposInfo, pdh.getWCRoot(), localRelpath);
        SVNSqlJetStatement stmt = pdh.getWCRoot().getSDb().getStatement(SVNWCDbStatements.DELETE_LOCK);
        stmt.bindf("is", reposId, reposInfo.relPath);
        stmt.done();
        pdh.flushEntries(localAbsPath);
    }

    public void removePristine(File wcRootAbsPath, SVNChecksum sha1Checksum) throws SVNException {
        assert (isAbsolute(wcRootAbsPath));
        assert (sha1Checksum != null);
        if (sha1Checksum.getKind() != SVNChecksumKind.SHA1) {
            sha1Checksum = getPristineSHA1(wcRootAbsPath, sha1Checksum);
        }
        assert (sha1Checksum.getKind() == SVNChecksumKind.SHA1);
        DirParsedInfo parseDir = parseDir(wcRootAbsPath, Mode.ReadWrite);
        SVNWCDbDir pdh = parseDir.wcDbDir;
        verifyDirUsable(pdh);
        {
            SVNSqlJetStatement stmt = pdh.getWCRoot().getSDb().getStatement(SVNWCDbStatements.LOOK_FOR_WORK);
            boolean haveRow;
            try {
                haveRow = stmt.next();
            } finally {
                stmt.reset();
            }
            if (haveRow) {
                return;
            }
        }
        boolean isReferenced;
        {
            SVNChecksum md5Checksum = getPristineMD5(wcRootAbsPath, sha1Checksum);
            SVNSqlJetStatement stmt = pdh.getWCRoot().getSDb().getStatement(SVNWCDbStatements.SELECT_ANY_PRISTINE_REFERENCE);
            try {
                stmt.bindChecksum(1, sha1Checksum);
                stmt.bindChecksum(2, md5Checksum);
                isReferenced = stmt.next();
            } finally {
                stmt.reset();
            }
        }
        if (!isReferenced) {
            pristineRemove(pdh, sha1Checksum);
        }
    }

    private void pristineRemove(SVNWCDbDir pdh, SVNChecksum sha1Checksum) throws SVNException {
        SVNSqlJetStatement stmt = pdh.getWCRoot().getSDb().getStatement(SVNWCDbStatements.DELETE_PRISTINE);
        stmt.bindChecksum(1, sha1Checksum);
        stmt.done();
        File pristineAbspath = getPristineFileName(pdh, sha1Checksum, true);
        SVNFileUtil.deleteFile(pristineAbspath);
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
        File buildRelpath = SVNFileUtil.createFilePath("");

        /*
         * Initialize all the OUT parameters. Generally, we'll only be filling
         * in a subset of these, so it is easier to init all up front. Note that
         * the STATUS parameter will be initialized once we read the status of
         * the specified node.
         */
        WCDbAdditionInfo additionInfo = new WCDbAdditionInfo();
        additionInfo.originalRevision = INVALID_REVNUM;
        long originalReposId = INVALID_REPOS_ID;

        DirParsedInfo parsed = parseDir(localAbsPath, Mode.ReadOnly);
        SVNWCDbDir pdh = parsed.wcDbDir;
        File localRelpath = parsed.localRelPath;
        verifyDirUsable(pdh);

        File currentRelpath = localRelpath;

        boolean haveRow;
        SVNWCDbStatus presence;
        File reposPrefixPath = SVNFileUtil.createFilePath("");
        int i;

        SVNSqlJetStatement stmt = pdh.getWCRoot().getSDb().getStatement(SVNWCDbStatements.SELECT_WORKING_NODE);

        try {
            stmt.bindf("is", pdh.getWCRoot().getWcId(), localRelpath);
            haveRow = stmt.next();

            if (!haveRow) {
                stmt.reset();
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_PATH_NOT_FOUND, "The node ''{0}'' was not found.", localAbsPath);
                SVNErrorManager.error(err, SVNLogType.WC);
            }

            presence = getColumnPresence(stmt);

            if (presence != SVNWCDbStatus.Normal) {
                stmt.reset();
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_PATH_UNEXPECTED_STATUS, "Expected node ''{0}'' to be added.", localAbsPath);
                SVNErrorManager.error(err, SVNLogType.WC);
            }

            if (f.contains(AdditionInfoField.originalRevision)) {
                additionInfo.originalRevision = getColumnRevNum(stmt, 12);
            }

            if (f.contains(AdditionInfoField.status)) {
                additionInfo.status = SVNWCDbStatus.Added;
            }

            long opDepth = stmt.getColumnLong(SVNWCDbSchema.NODES__Fields.op_depth);

            for (i = SVNWCUtils.relpathDepth(localRelpath); i > opDepth; --i) {
                reposPrefixPath = SVNFileUtil.createFilePath(SVNFileUtil.createFilePath(SVNFileUtil.getFileName(currentRelpath)), reposPrefixPath);
                currentRelpath = SVNFileUtil.getFileDir(currentRelpath);
            }

            if (f.contains(AdditionInfoField.opRootAbsPath)) {
                additionInfo.opRootAbsPath = SVNFileUtil.createFilePath(pdh.getWCRoot().getAbsPath(), currentRelpath);
            }

            if (f.contains(AdditionInfoField.originalReposRelPath) || f.contains(AdditionInfoField.originalRootUrl) || f.contains(AdditionInfoField.originalUuid)
                    || (f.contains(AdditionInfoField.originalRevision) && additionInfo.originalRevision == INVALID_REVNUM) || f.contains(AdditionInfoField.status)) {
                if (!localRelpath.equals(currentRelpath)) {
                    stmt.reset();
                    stmt.bindf("is", pdh.getWCRoot().getWcId(), currentRelpath);
                    haveRow = stmt.next();
                    if (!haveRow) {
                        stmt.reset();
                        SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_PATH_NOT_FOUND, "The node ''{0}'' was not found.",
                                SVNFileUtil.createFilePath(pdh.getWCRoot().getAbsPath(), currentRelpath));
                        SVNErrorManager.error(err, SVNLogType.WC);
                    }

                    if (f.contains(AdditionInfoField.originalRevision) && additionInfo.originalRevision == INVALID_REVNUM)
                        additionInfo.originalRevision = getColumnRevNum(stmt, 12);
                }

                /*
                 * current_relpath / current_abspath as well as the record in
                 * stmt contain the data of the op_root
                 */
                if (f.contains(AdditionInfoField.originalReposRelPath)) {
                    additionInfo.originalReposRelPath = SVNFileUtil.createFilePath(stmt.getColumnString(11));
                }

                if (!stmt.isColumnNull(10) && (f.contains(AdditionInfoField.status) || f.contains(AdditionInfoField.originalRootUrl) || f.contains(AdditionInfoField.originalUuid))) {
                    if (f.contains(AdditionInfoField.originalRootUrl) || f.contains(AdditionInfoField.originalUuid)) {
                        originalReposId = stmt.getColumnLong(10);
                        ReposInfo reposInfo = fetchReposInfo(pdh.getWCRoot().getSDb(), originalReposId);
                        additionInfo.originalRootUrl = SVNURL.parseURIDecoded(reposInfo.reposRootUrl);
                        additionInfo.originalUuid = reposInfo.reposUuid;
                    }
                    if (f.contains(AdditionInfoField.status)) {
                        /* moved_here */
                        if (stmt.getColumnBoolean(13))
                            additionInfo.status = SVNWCDbStatus.MovedHere;
                        else
                            additionInfo.status = SVNWCDbStatus.Copied;
                    }
                }
            }

            while (true) {
                stmt.reset();
                reposPrefixPath = SVNFileUtil.createFilePath(SVNFileUtil.createFilePath(SVNFileUtil.getFileName(currentRelpath)), reposPrefixPath);
                currentRelpath = SVNFileUtil.getFileDir(currentRelpath);
                stmt.bindf("is", pdh.getWCRoot().getWcId(), currentRelpath);
                haveRow = stmt.next();
                if (!haveRow) {
                    break;
                }
                opDepth = stmt.getColumnLong(0);
                for (i = SVNWCUtils.relpathDepth(currentRelpath); i > opDepth; i--) {
                    reposPrefixPath = SVNFileUtil.createFilePath(SVNFileUtil.createFilePath(SVNFileUtil.getFileName(currentRelpath)), reposPrefixPath);
                    currentRelpath = SVNFileUtil.getFileDir(currentRelpath);
                }
            }

        } finally {
            stmt.reset();
        }

        buildRelpath = reposPrefixPath;

        if (f.contains(AdditionInfoField.reposRelPath) || f.contains(AdditionInfoField.reposRootUrl) || f.contains(AdditionInfoField.reposUuid)) {
            WCDbRepositoryInfo rInfo = new WCDbRepositoryInfo();
            long reposId = scanUpwardsForRepos(rInfo, pdh.getWCRoot(), currentRelpath);
            if (f.contains(AdditionInfoField.reposRelPath)) {
                additionInfo.reposRelPath = SVNFileUtil.createFilePath(rInfo.relPath, buildRelpath);
            }
            if (reposId != INVALID_REPOS_ID && f.contains(AdditionInfoField.reposRootUrl) || f.contains(AdditionInfoField.reposUuid)) {
                ReposInfo reposInfo = fetchReposInfo(pdh.getWCRoot().getSDb(), reposId);
                if (reposInfo.reposRootUrl != null) {
                    additionInfo.reposRootUrl = SVNURL.parseURIDecoded(reposInfo.reposRootUrl);
                }
                additionInfo.reposUuid = reposInfo.reposUuid;
            }
        }

        if (originalReposId != INVALID_REPOS_ID && f.contains(AdditionInfoField.originalRootUrl) || f.contains(AdditionInfoField.originalUuid)) {
            ReposInfo reposInfo = fetchReposInfo(pdh.getWCRoot().getSDb(), originalReposId);
            if (reposInfo.reposRootUrl != null) {
                additionInfo.originalRootUrl = SVNURL.parseURIDecoded(reposInfo.reposRootUrl);
            }
            additionInfo.originalUuid = reposInfo.reposUuid;
        }

        return additionInfo;
    }

    public WCDbRepositoryInfo scanBaseRepository(File localAbsPath, RepositoryInfoField... fields) throws SVNException {
        assert (isAbsolute(localAbsPath));
        final EnumSet<RepositoryInfoField> f = getInfoFields(RepositoryInfoField.class, fields);
        final DirParsedInfo parsed = parseDir(localAbsPath, Mode.ReadOnly);
        final SVNWCDbDir pdh = parsed.wcDbDir;
        verifyDirUsable(pdh);

        WCDbBaseInfo baseInfo = getBaseInfo(localAbsPath, BaseInfoField.reposId, BaseInfoField.reposRelPath);
        final WCDbRepositoryInfo reposInfo = new WCDbRepositoryInfo();
        reposInfo.relPath = baseInfo.reposRelPath;
        if (f.contains(RepositoryInfoField.rootUrl) || f.contains(RepositoryInfoField.uuid)) {
            fetchReposInfo(reposInfo, pdh.getWCRoot().getSDb(), baseInfo.reposId);
        }
        return reposInfo;
    }

    /**
     * Scan from LOCAL_RELPATH upwards through parent nodes until we find a
     * parent that has values in the 'repos_id' and 'repos_relpath' columns.
     * Return that information in REPOS_ID and REPOS_RELPATH (either may be
     * NULL). Use LOCAL_ABSPATH for diagnostics
     */
    private static long scanUpwardsForRepos(WCDbRepositoryInfo reposInfo, SVNWCDbRoot wcroot, File localRelPath) throws SVNException {
        assert (wcroot.getSDb() != null && wcroot.getWcId() != UNKNOWN_WC_ID);
        assert (reposInfo != null);
        SVNSqlJetStatement stmt = wcroot.getSDb().getStatement(SVNWCDbStatements.SELECT_BASE_NODE);
        try {
            stmt.bindf("is", wcroot.getWcId(), localRelPath);
            boolean haveRow = stmt.next();
            if (!haveRow) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_PATH_NOT_FOUND, "The node ''{0}'' was not found.", SVNFileUtil.createFilePath(wcroot.getAbsPath(), localRelPath));
                SVNErrorManager.error(err, SVNLogType.WC);
                return 0;
            }
            assert (!stmt.isColumnNull(SVNWCDbSchema.NODES__Fields.repos_id));
            assert (!stmt.isColumnNull(SVNWCDbSchema.NODES__Fields.repos_path));
            reposInfo.relPath = SVNFileUtil.createFilePath(stmt.getColumnString(SVNWCDbSchema.NODES__Fields.repos_path));
            return stmt.getColumnLong(SVNWCDbSchema.NODES__Fields.repos_id);
        } finally {
            stmt.reset();
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

        long opDepth = 0, localOpDepth = 0;

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
                SVNWCDbStatus work_presence = getColumnPresence(stmt);

                /* The starting node should be deleted. */
                if (current_abspath.equals(localAbsPath) && work_presence != SVNWCDbStatus.NotPresent && work_presence != SVNWCDbStatus.BaseDeleted) {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_PATH_UNEXPECTED_STATUS, "Expected node ''{0}'' to be deleted.", localAbsPath);
                    SVNErrorManager.error(err, SVNLogType.WC);
                }
                assert (work_presence == SVNWCDbStatus.Normal || work_presence == SVNWCDbStatus.NotPresent || work_presence == SVNWCDbStatus.BaseDeleted);

                SVNSqlJetStatement baseStmt = stmt.getJoinedStatement(SVNWCDbSelectDeletionInfo.NODES_BASE);
                try {
                    boolean have_base = baseStmt != null && baseStmt.next() && !isColumnNull(baseStmt, SVNWCDbSchema.NODES__Fields.presence);

                    if (have_base) {
                        SVNWCDbStatus base_presence = getColumnPresence(baseStmt);

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
                    if (!found_moved_to && (f.contains(DeletionInfoField.movedToAbsPath) || f.contains(DeletionInfoField.baseDelAbsPath)) && !isColumnNull(stmt, SVNWCDbSchema.NODES__Fields.moved_to)) {
                        /* There better be a BASE_NODE (that was moved-away). */
                        assert (have_base);

                        found_moved_to = true;

                        /* This makes things easy. It's the BASE_DEL_ABSPATH! */
                        if (f.contains(DeletionInfoField.baseDelAbsPath))
                            deletionInfo.baseDelAbsPath = current_abspath;

                        if (f.contains(DeletionInfoField.movedToAbsPath))
                            deletionInfo.movedToAbsPath = SVNFileUtil.createFilePath(pdh.getWCRoot().getAbsPath(), getColumnText(stmt, SVNWCDbSchema.NODES__Fields.moved_to));
                    }

                    opDepth = stmt.getColumnLong(SVNWCDbSchema.NODES__Fields.op_depth);
                    if (current_abspath.equals(localAbsPath)) {
                        localOpDepth = opDepth;
                    }

                    if (f.contains(DeletionInfoField.workDelAbsPath) && deletionInfo.workDelAbsPath == null && ((opDepth < localOpDepth && opDepth > 0) || child_presence == SVNWCDbStatus.NotPresent)) {
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
                        pdh = navigateToParent(pdh, Mode.ReadOnly);
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

    public SVNWCDbDir navigateToParent(SVNWCDbDir childPdh, Mode sMode) throws SVNException {
        SVNWCDbDir parentPdh = childPdh.getParent();
        if (parentPdh != null && parentPdh.getWCRoot() != null)
            return parentPdh;
        File parentAbsPath = SVNFileUtil.getFileDir(childPdh.getLocalAbsPath());
        /* Make sure we don't see the root as its own parent */
        assert (parentAbsPath != null);
        parentPdh = parseDir(parentAbsPath, sMode).wcDbDir;
        verifyDirUsable(parentPdh);
        childPdh.setParent(parentPdh);
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
        if (repos_id == INVALID_REPOS_ID) {
            return info;
        }

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
            info.reposUuid = getColumnText(stmt, SVNWCDbSchema.REPOSITORY__Fields.uuid);

        } finally {
            stmt.reset();
        }
        return info;
    }
    
    private Structure<RepositoryInfo> fetchRepositoryInfo(SVNSqlJetDb sDb, long repos_id) throws SVNException {
        Structure<RepositoryInfo> info = Structure.obtain(RepositoryInfo.class);
        if (repos_id == INVALID_REPOS_ID) {
            return info;
        }

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

            info.set(RepositoryInfo.reposRootUrl, SVNURL.parseURIEncoded(getColumnText(stmt, SVNWCDbSchema.REPOSITORY__Fields.root)));
            info.set(RepositoryInfo.reposUuid, getColumnText(stmt, SVNWCDbSchema.REPOSITORY__Fields.uuid));
        } finally {
            stmt.reset();
        }
        
        return info;
    }

    private static SVNSqlJetDb openDb(File dirAbsPath, String sdbFileName, Mode sMode) throws SVNException {
        return SVNSqlJetDb.open(SVNWCUtils.admChild(dirAbsPath, sdbFileName), sMode);
    }

    private static boolean isErrorNOENT(final SVNErrorCode errorCode) {
        return errorCode == SVNErrorCode.ENTRY_NOT_FOUND || errorCode == SVNErrorCode.FS_NOT_FOUND || errorCode == SVNErrorCode.FS_NOT_OPEN || errorCode == SVNErrorCode.FS_NOT_FILE;
    }

    private static void verifyDirUsable(SVNWCDbDir pdh) {
        assert (SVNWCDbDir.isUsable(pdh));
    }

    public SVNSqlJetDb borrowDbTemp(File localDirAbsPath, SVNWCDbOpenMode mode) throws SVNException {
        assert (isAbsolute(localDirAbsPath));
        final Mode smode = mode == SVNWCDbOpenMode.ReadOnly ? Mode.ReadOnly : Mode.ReadWrite;
        final DirParsedInfo parsed = parseDir(localDirAbsPath, smode);
        final SVNWCDbDir pdh = parsed.wcDbDir;
        verifyDirUsable(pdh);
        return pdh.getWCRoot().getSDb();
    }

    public boolean isWCRoot(File localAbspath) throws SVNException {
        assert (SVNFileUtil.isAbsolute(localAbspath));
        DirParsedInfo parsed = parseDir(localAbspath, Mode.ReadWrite);
        SVNWCDbDir pdh = parsed.wcDbDir;
        File localRelPath = parsed.localRelPath;
        verifyDirUsable(pdh);
        if (localRelPath != null && !localRelPath.getPath().equals("")) {
            /* Node is a file, or has a parent directory within the same wcroot */
            return false;
        }
        return true;
    }

    public void opStartDirectoryUpdateTemp(File localAbspath, File newReposRelpath, long newRevision) throws SVNException {
        assert (SVNFileUtil.isAbsolute(localAbspath));
        assert (SVNRevision.isValidRevisionNumber(newRevision));
        // SVN_ERR_ASSERT(svn_relpath_is_canonical(new_repos_relpath,
        // scratch_pool));
        DirParsedInfo parsed = parseDir(localAbspath, Mode.ReadWrite);
        SVNWCDbDir pdh = parsed.wcDbDir;
        File localRelPath = parsed.localRelPath;
        verifyDirUsable(pdh);
        final StartDirectoryUpdate du = new StartDirectoryUpdate();
        du.wcId = pdh.getWCRoot().getWcId();
        du.newRevision = newRevision;
        du.newReposRelpath = newReposRelpath;
        du.localRelpath = localRelPath;
        pdh.getWCRoot().getSDb().runTransaction(du);
        pdh.flushEntries(localAbspath);
    }

    private class StartDirectoryUpdate implements SVNSqlJetTransaction {

        public long wcId;
        public File localRelpath;
        public long newRevision;
        public File newReposRelpath;

        public void transaction(SVNSqlJetDb db) throws SqlJetException, SVNException {
            SVNSqlJetStatement stmt = db.getStatement(SVNWCDbStatements.UPDATE_BASE_NODE_PRESENCE_REVNUM_AND_REPOS_PATH);
            stmt.bindf("istis", wcId, localRelpath, getPresenceText(SVNWCDbStatus.Incomplete), newRevision, newReposRelpath);
            stmt.done();
        }

    }

    public void opMakeCopyTemp(File localAbspath, boolean removeBase) throws SVNException {
        assert (SVNFileUtil.isAbsolute(localAbspath));
        DirParsedInfo parsed = parseDir(localAbspath, Mode.ReadWrite);
        SVNWCDbDir pdh = parsed.wcDbDir;
        File localRelPath = parsed.localRelPath;
        verifyDirUsable(pdh);
        boolean haveRow = false;
        SVNSqlJetStatement stmt = pdh.getWCRoot().getSDb().getStatement(SVNWCDbStatements.SELECT_WORKING_NODE);
        try {
            stmt.bindf("is", pdh.getWCRoot().getWcId(), localRelPath);
            haveRow = stmt.next();
        } finally {
            stmt.reset();
        }
        if (haveRow) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_PATH_UNEXPECTED_STATUS, "Modification of ''{0}'' already exists", localAbspath);
            SVNErrorManager.error(err, SVNLogType.WC);
        }
        catchCopyOfAbsent(pdh, localRelPath);
        final MakeCopy mcb = new MakeCopy();
        mcb.pdh = pdh;
        mcb.localRelpath = localRelPath;
        mcb.localAbspath = localAbspath;
        mcb.opDepth = SVNWCUtils.relpathDepth(localRelPath);
        pdh.getWCRoot().getSDb().runTransaction(mcb);
        pdh.flushEntries(localAbspath);
    }

    private void catchCopyOfAbsent(SVNWCDbDir pdh, File localRelPath) {

        // TODO

    }

    private class MakeCopy implements SVNSqlJetTransaction {

        File localAbspath;
        SVNWCDbDir pdh;
        File localRelpath;
        long opDepth;

        public void transaction(SVNSqlJetDb db) throws SqlJetException, SVNException {
            SVNSqlJetStatement stmt;
            boolean haveRow;
            boolean removeWorking = false;
            boolean addWorkingBaseDeleted = false;
            stmt = db.getStatement(SVNWCDbStatements.SELECT_LOWEST_WORKING_NODE);
            stmt.bindf("is", pdh.getWCRoot().getWcId(), localRelpath);
            try {
                haveRow = stmt.next();
                if (haveRow) {
                    SVNWCDbStatus workingStatus = getColumnPresence(stmt);
                    long workingOpDepth = getColumnInt64(stmt, NODES__Fields.op_depth);
                    assert (workingStatus == SVNWCDbStatus.Normal || workingStatus == SVNWCDbStatus.BaseDeleted || workingStatus == SVNWCDbStatus.NotPresent || workingStatus == SVNWCDbStatus.Incomplete);
                    if (workingOpDepth <= opDepth) {
                        addWorkingBaseDeleted = true;
                        if (workingStatus == SVNWCDbStatus.BaseDeleted) {
                            removeWorking = true;
                        }
                    }
                }
            } finally {
                stmt.reset();
            }
            if (removeWorking) {
                stmt = db.getStatement(SVNWCDbStatements.DELETE_LOWEST_WORKING_NODE);
                stmt.bindf("is", pdh.getWCRoot().getWcId(), localRelpath);
                stmt.done();
            }
            if (addWorkingBaseDeleted) {
                stmt = db.getStatement(SVNWCDbStatements.INSERT_DELETE_FROM_BASE);
                stmt.bindf("isi", pdh.getWCRoot().getWcId(), localRelpath, opDepth);
                stmt.done();
            } else {
                stmt = db.getStatement(SVNWCDbStatements.INSERT_WORKING_NODE_FROM_BASE_COPY);
                stmt.bindf("isi", pdh.getWCRoot().getWcId(), localRelpath, opDepth);
                stmt.done();
            }
            
            final List<String> children = gatherRepoChildren(pdh, localRelpath, 0);
            for (String name : children) {
                MakeCopy cbt = new MakeCopy();
                cbt.localAbspath = SVNFileUtil.createFilePath(localAbspath, name);
                DirParsedInfo parseDir = parseDir(cbt.localAbspath, Mode.ReadWrite);
                cbt.pdh = parseDir.wcDbDir;
                cbt.localRelpath = parseDir.localRelPath;
                verifyDirUsable(cbt.pdh);
                cbt.opDepth = opDepth;
                cbt.transaction(db);
            }
            
            pdh.flushEntries(localAbspath);
        }

    };

    private List<String> gatherRepoChildren(SVNWCDbDir pdh, File localRelpath, long opDepth) throws SVNException {
        final List<String> children = new ArrayList<String>();
        final SVNSqlJetStatement stmt = pdh.getWCRoot().getSDb().getStatement(SVNWCDbStatements.SELECT_OP_DEPTH_CHILDREN);
        try {
            stmt.bindf("isi", pdh.getWCRoot().getWcId(), localRelpath, opDepth);
            boolean haveRow = stmt.next();
            while (haveRow) {
                String childRelpath = SVNFileUtil.getFileName(SVNFileUtil.createFilePath(stmt.getColumnString(SVNWCDbSchema.NODES__Fields.local_relpath)));
                children.add(childRelpath);
                haveRow = stmt.next();
            }
        } finally {
            stmt.reset();
        }
        return children;
    }

    public long fetchReposId(SVNSqlJetDb db, SVNURL reposRootUrl, String reposUuid) throws SVNException {
        SVNSqlJetStatement getStmt = db.getStatement(SVNWCDbStatements.SELECT_REPOSITORY);
        try {
            getStmt.bindf("s", reposRootUrl);
            getStmt.nextRow();
            return getStmt.getColumnLong(SVNWCDbSchema.REPOSITORY__Fields.id);
        } finally {
            getStmt.reset();
        }
    }

    public void opSetNewDirToIncompleteTemp(File localAbspath, File reposRelpath, SVNURL reposRootURL, String reposUuid, long revision, SVNDepth depth) throws SVNException {

        assert (SVNFileUtil.isAbsolute(localAbspath));
        assert (SVNRevision.isValidRevisionNumber(revision));
        assert (reposRelpath != null && reposRootURL != null && reposUuid != null);
        DirParsedInfo parsed = parseDir(localAbspath, Mode.ReadWrite);
        SVNWCDbDir pdh = parsed.wcDbDir;
        verifyDirUsable(pdh);
        
        final InsertBase insertBase = new InsertBase();
        insertBase.reposRootURL = reposRootURL;
        insertBase.reposUUID = reposUuid;
        insertBase.status = SVNWCDbStatus.Incomplete;
        insertBase.kind = SVNWCDbKind.Dir;
        insertBase.reposRelpath = reposRelpath;
        insertBase.revision = revision;
        insertBase.depth = depth;
        
        insertBase.localRelpath = parsed.localRelPath;
        insertBase.wcId = pdh.getWCRoot().getWcId();
        
        pdh.getWCRoot().getSDb().runTransaction(insertBase);
        pdh.flushEntries(localAbspath);
    }
    
    public void opBumpRevisionPostUpdate(File localAbsPath, SVNDepth depth, File newReposRelPath, SVNURL newReposRootURL, String newReposUUID,
            long newRevision, Collection<File> excludedPaths) throws SVNException {
        DirParsedInfo parseDir = parseDir(localAbsPath, Mode.ReadWrite);
        SVNWCDbDir pdh = parseDir.wcDbDir;
        verifyDirUsable(pdh);
        File localRelPath = parseDir.localRelPath;
        if (excludedPaths != null && excludedPaths.contains(localRelPath)) {
            return;
        }
        if (depth == SVNDepth.UNKNOWN) {
            depth = SVNDepth.INFINITY;
        }
        BumpRevisionPostUpdate brb = new BumpRevisionPostUpdate();
        
        brb.depth = depth;
        brb.newReposRelPath = newReposRelPath;
        brb.newReposRootURL = newReposRootURL;
        brb.newReposUUID = newReposUUID;
        brb.newRevision = newRevision;
        
        brb.localRelPath = localRelPath;
        brb.wcRoot = pdh.getWCRoot().getAbsPath();
        brb.exludedRelPaths = excludedPaths;
        
        pdh.getWCRoot().getSDb().runTransaction(brb);
        pdh.flushEntries(localAbsPath);
    }
    
    private class BumpRevisionPostUpdate implements SVNSqlJetTransaction {
        
        private SVNDepth depth;
        private File newReposRelPath;
        private SVNURL newReposRootURL;
        private String newReposUUID;
        private long newRevision;
        private Collection<File> exludedRelPaths;
        
        private File localRelPath;
        private File wcRoot;

        public void transaction(SVNSqlJetDb db) throws SqlJetException, SVNException {
            SVNWCDbStatus status = null;
            try {
                File absPath = SVNFileUtil.createFilePath(wcRoot, localRelPath);
                WCDbBaseInfo baseInfo = getBaseInfo(absPath, BaseInfoField.status, BaseInfoField.kind);
                status = baseInfo.status;
            } catch (SVNException e) {
                if (e.getErrorMessage().getErrorCode() == SVNErrorCode.WC_PATH_NOT_FOUND) {
                    return;
                }
                throw e;
            }
            switch (status) {
            case NotPresent:
            case ServerExcluded:
            case Excluded:
                return;
            default:
                break;
            }
            
            long reposId = INVALID_REPOS_ID;
            if (newReposRootURL != null) {
                reposId = createReposId(db, newReposRootURL, newReposUUID);
            }
            bumpNodeRevision(wcRoot, localRelPath, reposId, newReposRelPath, newRevision, depth, exludedRelPaths, true, false);
        }

        private void bumpNodeRevision(File wcRoot, File localRelPath, long reposId, File newReposRelPath, long newRevision,
                SVNDepth depth, Collection<File> exludedRelPaths, boolean isRoot, boolean skipWhenDir) throws SVNException {
            if (exludedRelPaths != null && exludedRelPaths.contains(localRelPath)) {
                return;
            }
            File absPath = SVNFileUtil.createFilePath(wcRoot, localRelPath);
            WCDbBaseInfo baseInfo = getBaseInfo(absPath, BaseInfoField.status, BaseInfoField.kind, BaseInfoField.revision, 
                    BaseInfoField.reposRelPath, BaseInfoField.updateRoot);
            
            if (baseInfo.updateRoot && baseInfo.kind == SVNWCDbKind.File && !isRoot) {
                return;
            }
            if (skipWhenDir && baseInfo.kind == SVNWCDbKind.Dir) {
                return;
            }
            
            if (!isRoot && (baseInfo.status == SVNWCDbStatus.NotPresent || 
                    (baseInfo.status == SVNWCDbStatus.ServerExcluded && baseInfo.revision != newRevision))) {
                removeBase(SVNFileUtil.createFilePath(wcRoot, localRelPath));
                return;
            }
            boolean setReposRelPath = false;
            if (newReposRelPath != null && !baseInfo.reposRelPath.equals(newReposRelPath)) {
                setReposRelPath = true;
            }
            if (setReposRelPath || (newRevision >= 0 && newRevision != baseInfo.revision)) {
                opSetRevAndReposRelpathTemp(absPath, newRevision, setReposRelPath, newReposRelPath, newReposRootURL, newReposUUID);
            }
            
            if (depth.compareTo(SVNDepth.EMPTY) <= 0 ||
                    baseInfo.kind != SVNWCDbKind.Dir ||
                    baseInfo.status == SVNWCDbStatus.ServerExcluded ||
                    baseInfo.status == SVNWCDbStatus.Excluded ||
                    baseInfo.status == SVNWCDbStatus.NotPresent) {
                return;
            }
            SVNDepth depthBelowHere = depth;
            if (depth == SVNDepth.IMMEDIATES || depth == SVNDepth.FILES) {
                depthBelowHere = SVNDepth.EMPTY;
            }
            DirParsedInfo parseDir = parseDir(absPath, Mode.ReadWrite);
            SVNWCDbDir pdh = parseDir.wcDbDir;
            
            List<String> children = gatherRepoChildren(pdh, localRelPath, 0);
            for (String child : children) {
                File childReposRelPath = null;
                File childLocalRelPath = SVNFileUtil.createFilePath(localRelPath, child);                
                if (newReposRelPath != null) {
                    childReposRelPath = SVNFileUtil.createFilePath(newReposRelPath, child);
                }
                bumpNodeRevision(wcRoot, childLocalRelPath, reposId, childReposRelPath, newRevision, depthBelowHere, exludedRelPaths, false, depth.compareTo(SVNDepth.IMMEDIATES) < 0);               
            }        
        }
    }
    

    public void opDeleteTemp(File localAbspath) throws SVNException {
        // TODO
        throw new UnsupportedOperationException();
    }

    public File getWCRootTempDir(File localAbspath) throws SVNException {
        assert (isAbsolute(localAbspath));
        DirParsedInfo parseDir = parseDir(localAbspath, Mode.ReadWrite);
        SVNWCDbDir pdh = parseDir.wcDbDir;
        verifyDirUsable(pdh);
        return SVNFileUtil.createFilePath(SVNFileUtil.createFilePath(pdh.getWCRoot().getAbsPath(), SVNFileUtil.getAdminDirectoryName()), WCROOT_TEMPDIR_RELPATH);
    }

    public void opSetFileExternal(File localAbspath, File reposRelpath, SVNRevision pegRev, SVNRevision rev) throws SVNException {
        assert (isAbsolute(localAbspath));
        DirParsedInfo parseDir = parseDir(localAbspath, Mode.ReadWrite);
        SVNWCDbDir pdh = parseDir.wcDbDir;
        File localRelpath = parseDir.localRelPath;
        verifyDirUsable(pdh);
        boolean gotRow;
        SVNSqlJetStatement stmt = pdh.getWCRoot().getSDb().getStatement(SVNWCDbStatements.SELECT_BASE_NODE);
        try {
            stmt.bindf("is", pdh.getWCRoot().getWcId(), localRelpath);
            gotRow = stmt.next();
        } finally {
            stmt.reset();
        }
        if (!gotRow) {
            if (reposRelpath == null) {
                return;
            }
            WCDbRepositoryInfo baseRep = scanBaseRepository(pdh.getLocalAbsPath(), RepositoryInfoField.rootUrl, RepositoryInfoField.uuid);
            SVNURL reposRootUrl = baseRep.rootUrl;
            String reposUuid = baseRep.uuid;
            long reposId = fetchReposId(pdh.getWCRoot().getSDb(), reposRootUrl, reposUuid);
            stmt = pdh.getWCRoot().getSDb().getStatement(SVNWCDbStatements.INSERT_NODE);
            stmt.bindf("isisisntnt", pdh.getWCRoot().getWcId(), localRelpath, 0, SVNFileUtil.getFileDir(localRelpath), reposId, reposRelpath, getPresenceText(SVNWCDbStatus.NotPresent),
                    getKindText(SVNWCDbKind.File));
            stmt.done();
        }
        stmt = pdh.getWCRoot().getSDb().getStatement(SVNWCDbStatements.UPDATE_FILE_EXTERNAL);
        stmt.bindf("is", pdh.getWCRoot().getWcId(), localRelpath);
        if (reposRelpath != null) {
            String str = SVNWCUtils.serializeFileExternal(SVNFileUtil.getFilePath(reposRelpath), pegRev, rev);
            stmt.bindString(3, str);
        } else {
            stmt.bindNull(3);
        }
        stmt.done();
        pdh.flushEntries(localAbspath);
    }

    public void opRemoveWorkingTemp(File localAbspath) throws SVNException {
        assert (isAbsolute(localAbspath));
        DirParsedInfo parseDir = parseDir(localAbspath, Mode.ReadWrite);
        SVNWCDbDir pdh = parseDir.wcDbDir;
        File localRelpath = parseDir.localRelPath;
        verifyDirUsable(pdh);
        pdh.flushEntries(localAbspath);
        SVNSqlJetStatement stmt = pdh.getWCRoot().getSDb().getStatement(SVNWCDbStatements.DELETE_WORKING_NODE);
        stmt.bindf("is", pdh.getWCRoot().getWcId(), localRelpath);
        stmt.done();
    }

    public void opSetBaseIncompleteTemp(File localDirAbspath, boolean incomplete) throws SVNException {
        SVNWCDbStatus baseStatus = getBaseInfo(localDirAbspath, BaseInfoField.status).status;
        assert (baseStatus == SVNWCDbStatus.Normal || baseStatus == SVNWCDbStatus.Incomplete);
        SVNSqlJetStatement stmt = getStatementForPath(localDirAbspath, SVNWCDbStatements.UPDATE_NODE_BASE_PRESENCE);
        stmt.bindString(3, incomplete ? "incomplete" : "normal");
        long affectedNodeRows = stmt.done();
        long affectedRows = affectedNodeRows;
        if (affectedRows > 0) {
            SVNWCDbDir pdh = getOrCreateDir(localDirAbspath, false);
            if (pdh != null) {
                pdh.flushEntries(localDirAbspath);
            }
        }
    }

    private SVNWCDbDir getOrCreateDir(File localDirAbspath, boolean createAllowed) {
        SVNWCDbDir pdh = dirData.get(localDirAbspath);
        if (pdh == null && createAllowed) {
            pdh = new SVNWCDbDir(localDirAbspath);
            dirData.put(localDirAbspath, pdh);
        }
        return pdh;
    }

    public void opSetDirDepthTemp(File localAbspath, SVNDepth depth) throws SVNException {
        assert (isAbsolute(localAbspath));
        assert (depth.getId() >= SVNDepth.EMPTY.getId() && depth.getId() <= SVNDepth.INFINITY.getId());
        DirParsedInfo parseDir = parseDir(localAbspath, Mode.ReadWrite);
        SVNWCDbDir pdh = parseDir.wcDbDir;
        File localRelpath = parseDir.localRelPath;
        verifyDirUsable(pdh);
        updateDepthValues(localAbspath, pdh, localRelpath, depth);
    }

    private void updateDepthValues(File localAbspath, SVNWCDbDir pdh, File localRelpath, SVNDepth depth) throws SVNException {
        boolean excluded = (depth == SVNDepth.EXCLUDE);
        pdh.flushEntries(localAbspath);
        SVNSqlJetStatement stmt = pdh.getWCRoot().getSDb().getStatement(excluded ? SVNWCDbStatements.UPDATE_NODE_BASE_EXCLUDED : SVNWCDbStatements.UPDATE_NODE_BASE_DEPTH);
        stmt.bindf("is", pdh.getWCRoot().getWcId(), localRelpath);
        if (!excluded) {
            stmt.bindString(3, SVNDepth.asString(depth));
        } else {
            stmt.bindNull(3);
        }
        stmt.done();
        stmt = pdh.getWCRoot().getSDb().getStatement(excluded ? SVNWCDbStatements.UPDATE_NODE_WORKING_EXCLUDED : SVNWCDbStatements.UPDATE_NODE_WORKING_DEPTH);
        stmt.bindf("is", pdh.getWCRoot().getWcId(), localRelpath);
        if (!excluded) {
            stmt.bindString(3, SVNDepth.asString(depth));
        } else {
            stmt.bindNull(3);
        }
        stmt.done();
    }

    public void opRemoveEntryTemp(File localAbspath) throws SVNException {
        assert (isAbsolute(localAbspath));
        DirParsedInfo parseDir = parseDir(localAbspath, Mode.ReadWrite);
        SVNWCDbDir pdh = parseDir.wcDbDir;
        File localRelpath = parseDir.localRelPath;
        verifyDirUsable(pdh);
        pdh.flushEntries(localAbspath);
        SVNSqlJetDb sdb = pdh.getWCRoot().getSDb();
        long wcId = pdh.getWCRoot().getWcId();
        SVNSqlJetStatement stmt = sdb.getStatement(SVNWCDbStatements.DELETE_NODES);
        stmt.bindf("is", wcId, localRelpath);
        stmt.done();
        stmt = sdb.getStatement(SVNWCDbStatements.DELETE_ACTUAL_NODE_WITHOUT_CONFLICT);
        stmt.bindf("is", wcId, localRelpath);
        stmt.done();
    }

    public void opSetRevAndReposRelpathTemp(File localAbspath, long revision, boolean setReposRelpath, File reposRelpath, SVNURL reposRootUrl, String reposUuid) throws SVNException {
        assert (isAbsolute(localAbspath));
        assert (SVNRevision.isValidRevisionNumber(revision) || setReposRelpath);
        DirParsedInfo parseDir = parseDir(localAbspath, Mode.ReadWrite);
        SVNWCDbDir pdh = parseDir.wcDbDir;
        verifyDirUsable(pdh);
        SetRevRelpath baton = new SetRevRelpath();
        baton.pdh = pdh;
        baton.localRelpath = parseDir.localRelPath;
        baton.rev = revision;
        baton.setReposRelpath = setReposRelpath;
        baton.reposRelpath = reposRelpath;
        baton.reposRootUrl = reposRootUrl;
        baton.reposUuid = reposUuid;
        pdh.flushEntries(localAbspath);
        pdh.getWCRoot().getSDb().runTransaction(baton);
    }

    private class SetRevRelpath implements SVNSqlJetTransaction {

        public SVNWCDbDir pdh;
        public File localRelpath;
        public long rev;
        public boolean setReposRelpath;
        public File reposRelpath;
        public SVNURL reposRootUrl;
        public String reposUuid;

        public void transaction(SVNSqlJetDb db) throws SqlJetException, SVNException {
            SVNSqlJetStatement stmt;
            if (SVNRevision.isValidRevisionNumber(rev)) {
                stmt = db.getStatement(SVNWCDbStatements.UPDATE_BASE_REVISION);
                stmt.bindf("isi", pdh.getWCRoot().getWcId(), localRelpath, rev);
                stmt.done();
            }
            if (setReposRelpath) {
                long reposId = createReposId(pdh.getWCRoot().getSDb(), reposRootUrl, reposUuid);
                stmt = db.getStatement(SVNWCDbStatements.UPDATE_BASE_REPOS);
                stmt.bindf("isis", pdh.getWCRoot().getWcId(), localRelpath, reposId, reposRelpath);
                stmt.done();
            }
        }
    };

    public void obtainWCLock(File localAbspath, int levelsToLock, boolean stealLock) throws SVNException {
        assert (isAbsolute(localAbspath));
        assert (levelsToLock >= -1);
        DirParsedInfo parseDir = parseDir(localAbspath, Mode.ReadWrite);
        SVNWCDbDir pdh = parseDir.wcDbDir;
        File localRelpath = parseDir.localRelPath;
        verifyDirUsable(pdh);
        WCLockObtain baton = new WCLockObtain();
        baton.pdh = pdh;
        baton.localRelpath = localRelpath;
        if (!stealLock) {
            SVNWCDbRoot wcroot = pdh.getWCRoot();
            int depth = SVNWCUtils.relpathDepth(localRelpath);
            for (WCLock lock : wcroot.getOwnedLocks()) {
                if (SVNWCUtils.isAncestor(lock.localRelpath, localRelpath) && (lock.levels == -1 || (lock.levels + SVNWCUtils.relpathDepth(lock.localRelpath)) >= depth)) {
                    File lockAbspath = SVNFileUtil.createFilePath(pdh.getWCRoot().getAbsPath(), lock.localRelpath);
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_LOCKED, "''{0}'' is already locked via ''{1}''", new Object[] {
                            localAbspath, lockAbspath
                    });
                    SVNErrorManager.error(err, SVNLogType.WC);
                }
            }
        }
        baton.stealLock = stealLock;
        baton.levelsToLock = levelsToLock;
        pdh.getWCRoot().getSDb().runTransaction(baton);
    }

    private class WCLockObtain implements SVNSqlJetTransaction {

        SVNWCDbDir pdh;
        File localRelpath;
        int levelsToLock;
        boolean stealLock;

        public void transaction(SVNSqlJetDb db) throws SqlJetException, SVNException {
            SVNWCDbRoot wcroot = pdh.getWCRoot();
            if (localRelpath != null) {
                TreesExistInfo whichTreesExist = whichTreesExist(db, pdh.getWCRoot().getWcId(), localRelpath);
                boolean haveBase = whichTreesExist.baseExists;
                boolean haveWorking = whichTreesExist.workingExists;
                if (!haveBase && !haveWorking) {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_PATH_NOT_FOUND, "The node ''{0}'' was not found.",
                            SVNFileUtil.createFilePath(pdh.getWCRoot().getAbsPath(), localRelpath));
                    SVNErrorManager.error(err, SVNLogType.WC);
                }
            }
            int lockDepth = SVNWCUtils.relpathDepth(localRelpath);
            int maxDepth = lockDepth + levelsToLock;
            File lockRelpath;
            SVNSqlJetStatement stmt = wcroot.getSDb().getStatement(SVNWCDbStatements.FIND_WC_LOCK);
            try {
                stmt.bindf("is", wcroot.getWcId(), localRelpath);
                boolean gotRow = stmt.next();
                while (gotRow) {
                    lockRelpath = SVNFileUtil.createFilePath(stmt.getColumnString(SVNWCDbSchema.WC_LOCK__Fields.local_dir_relpath));
                    if (levelsToLock >= 0 && SVNWCUtils.relpathDepth(lockRelpath) > maxDepth) {
                        gotRow = stmt.next();
                        continue;
                    }
                    File lockAbspath = SVNFileUtil.createFilePath(wcroot.getAbsPath(), lockRelpath);
                    boolean ownLock = isWCLockOwns(lockAbspath, true);
                    if (!ownLock && !stealLock) {
                        SVNErrorMessage err1 = SVNErrorMessage.create(SVNErrorCode.WC_LOCKED, "''{0}'' is already locked.", SVNFileUtil.createFilePath(wcroot.getAbsPath(), lockRelpath));
                        SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_LOCKED, "Working copy ''{0}'' locked", SVNFileUtil.createFilePath(wcroot.getAbsPath(), localRelpath));
                        err.setChildErrorMessage(err1);
                        SVNErrorManager.error(err, SVNLogType.WC);
                    } else if (!ownLock) {
                        stealWCLock(wcroot, lockRelpath);
                    }
                    gotRow = stmt.next();
                }
            } finally {
                stmt.reset();
            }
            if (stealLock) {
                stealWCLock(wcroot, localRelpath);
            }
            stmt = wcroot.getSDb().getStatement(SVNWCDbStatements.SELECT_WC_LOCK);
            lockRelpath = localRelpath;
            while (true) {
                stmt.bindf("is", wcroot.getWcId(), lockRelpath);
                boolean gotRow = stmt.next();
                if (gotRow) {
                    long levels = stmt.getColumnLong(SVNWCDbSchema.WC_LOCK__Fields.locked_levels);
                    if (levels >= 0) {
                        levels += SVNWCUtils.relpathDepth(lockRelpath);
                    }
                    stmt.reset();
                    if (levels == -1 || levels >= lockDepth) {
                        SVNErrorMessage err1 = SVNErrorMessage.create(SVNErrorCode.WC_LOCKED, "''{0}'' is already locked.", SVNFileUtil.createFilePath(wcroot.getAbsPath(), lockRelpath));
                        SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_LOCKED, "Working copy ''{0}'' locked", SVNFileUtil.createFilePath(wcroot.getAbsPath(), localRelpath));
                        err.setChildErrorMessage(err1);
                        SVNErrorManager.error(err, SVNLogType.WC);
                    }
                    break;
                }
                stmt.reset();
                if (lockRelpath == null) {
                    break;
                }
                lockRelpath = SVNFileUtil.getFileDir(lockRelpath);
            }
            stmt = wcroot.getSDb().getStatement(SVNWCDbStatements.INSERT_WC_LOCK);
            stmt.bindf("isi", wcroot.getWcId(), localRelpath, levelsToLock);
            try {
                stmt.done();
            } catch (SVNException e) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_LOCKED, "Working copy ''{0}'' locked", SVNFileUtil.createFilePath(wcroot.getAbsPath(), localRelpath));
                SVNErrorManager.error(err, SVNLogType.WC);
            }
            WCLock lock = new WCLock();
            lock.localRelpath = localRelpath;
            lock.levels = levelsToLock;
            wcroot.getOwnedLocks().add(lock);
        }

    };

    private void stealWCLock(SVNWCDbRoot wcroot, File localRelpath) throws SVNException {
        SVNSqlJetStatement stmt = wcroot.getSDb().getStatement(SVNWCDbStatements.DELETE_WC_LOCK);
        stmt.bindf("is", wcroot.getWcId(), localRelpath);
        stmt.done();
    }

    public void releaseWCLock(File localAbspath) throws SVNException {
        DirParsedInfo parseDir = parseDir(localAbspath, Mode.ReadWrite);
        SVNWCDbDir pdh = parseDir.wcDbDir;
        File localRelpath = parseDir.localRelPath;
        verifyDirUsable(pdh);
        WCLock foundLock = null;
        List<WCLock> ownedLocks = pdh.getWCRoot().getOwnedLocks();
        for (WCLock lock : ownedLocks) {
            if (lock.localRelpath.equals(localRelpath)) {
                foundLock = lock;
                break;
            }
        }
        if (foundLock == null) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_NOT_LOCKED, "Working copy not locked at ''{0}''", localAbspath);
            SVNErrorManager.error(err, SVNLogType.WC);
        }
        ownedLocks.remove(foundLock);
        SVNSqlJetStatement stmt = pdh.getWCRoot().getSDb().getStatement(SVNWCDbStatements.DELETE_WC_LOCK);
        stmt.bindf("is", pdh.getWCRoot().getWcId(), localRelpath);
        stmt.done();
    }

    public File getWCRoot(File wcRootAbspath) throws SVNException {
        DirParsedInfo parseDir = parseDir(wcRootAbspath, Mode.ReadOnly);
        SVNWCDbDir pdh = parseDir.wcDbDir;
        if (pdh.getWCRoot() == null) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_NOT_WORKING_COPY, "The node ''{0}'' is not in the working copy", wcRootAbspath);
            SVNErrorManager.error(err, SVNLogType.WC);
        }
        return pdh.getWCRoot().getAbsPath();
    }

    public void forgetDirectoryTemp(File localDirAbspath) throws SVNException {
        Set<SVNWCDbRoot> roots = new HashSet<SVNWCDbRoot>();
        for (Iterator<Entry<File, SVNWCDbDir>> i = dirData.entrySet().iterator(); i.hasNext();) {
            Entry<File, SVNWCDbDir> entry = i.next();
            SVNWCDbDir pdh = entry.getValue();
            if (!SVNWCUtils.isAncestor(localDirAbspath, pdh.getLocalAbsPath())) {
                continue;
            }
            try {
                releaseWCLock(pdh.getLocalAbsPath());
            } catch (SVNException e) {
                if (e.getErrorMessage().getErrorCode() != SVNErrorCode.WC_NOT_WORKING_COPY && e.getErrorMessage().getErrorCode() != SVNErrorCode.WC_NOT_LOCKED) {
                    throw e;
                }
            }
            i.remove();
            if (pdh.getWCRoot() != null && pdh.getWCRoot().getSDb() != null && SVNWCUtils.isAncestor(localDirAbspath, pdh.getWCRoot().getAbsPath())) {
                roots.add(pdh.getWCRoot());
            }
        }
        closeManyWCRoots(roots);
    }

    public boolean isWCLockOwns(File localAbspath, boolean exact) throws SVNException {
        DirParsedInfo parseDir = parseDir(localAbspath, Mode.ReadWrite);
        SVNWCDbDir pdh = parseDir.wcDbDir;
        File localRelpath = parseDir.localRelPath;
        if (pdh.getWCRoot() == null) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_NOT_WORKING_COPY, "The node ''{0}'' was not found.", localAbspath);
            SVNErrorManager.error(err, SVNLogType.WC);
        }
        verifyDirUsable(pdh);
        boolean ownLock = false;
        List<WCLock> ownedLocks = pdh.getWCRoot().getOwnedLocks();
        int lockLevel = SVNWCUtils.relpathDepth(localRelpath);
        if (exact)
            for (WCLock lock : ownedLocks) {
                if (lock.localRelpath.equals(localRelpath)) {
                    ownLock = true;
                    return ownLock;
                }
            }
        else
            for (WCLock lock : ownedLocks) {
                if (SVNWCUtils.isAncestor(lock.localRelpath, localRelpath) && (lock.levels == -1 || ((SVNWCUtils.relpathDepth(lock.localRelpath) + lock.levels) >= lockLevel))) {
                    ownLock = true;
                    return ownLock;
                }
            }
        return ownLock;
    }

    public void opSetTextConflictMarkerFilesTemp(File localAbspath, File oldBasename, File newBasename, File wrkBasename) throws SVNException {
        assert (isAbsolute(localAbspath));
        DirParsedInfo parseDir = parseDir(localAbspath, Mode.ReadWrite);
        SVNWCDbDir pdh = parseDir.wcDbDir;
        File localRelpath = parseDir.localRelPath;
        verifyDirUsable(pdh);
        boolean gotRow = false;
        SVNSqlJetStatement stmt = pdh.getWCRoot().getSDb().getStatement(SVNWCDbStatements.SELECT_ACTUAL_NODE);
        try {
            stmt.bindf("is", pdh.getWCRoot().getWcId(), localRelpath);
            gotRow = stmt.next();
        } finally {
            stmt.reset();
        }
        if (gotRow) {
            stmt = pdh.getWCRoot().getSDb().getStatement(SVNWCDbStatements.UPDATE_ACTUAL_TEXT_CONFLICTS);
        } else if (oldBasename == null && newBasename == null && wrkBasename == null) {
            return;
        } else {
            stmt = pdh.getWCRoot().getSDb().getStatement(SVNWCDbStatements.INSERT_ACTUAL_TEXT_CONFLICTS);
            stmt.bindString(6, SVNFileUtil.getFilePath(SVNFileUtil.getFileDir(localRelpath)));
        }
        stmt.bindf("issss", pdh.getWCRoot().getWcId(), localRelpath, oldBasename, newBasename, wrkBasename);
        stmt.done();
    }

    public void addBaseNotPresentNode(File localAbspath, File reposRelPath, SVNURL reposRootUrl, String reposUuid, long revision, SVNWCDbKind kind, SVNSkel conflict, SVNSkel workItems)
            throws SVNException {
        addExcludedOrNotPresentNode(localAbspath, reposRelPath, reposRootUrl, reposUuid, revision, kind, SVNWCDbStatus.NotPresent, conflict, workItems);
    }

    public void opSetPropertyConflictMarkerFileTemp(File localAbspath, String prejBasename) throws SVNException {
        assert (isAbsolute(localAbspath));
        DirParsedInfo parseDir = parseDir(localAbspath, Mode.ReadWrite);
        SVNWCDbDir pdh = parseDir.wcDbDir;
        File localRelpath = parseDir.localRelPath;
        verifyDirUsable(pdh);
        boolean gotRow = false;
        SVNSqlJetStatement stmt = pdh.getWCRoot().getSDb().getStatement(SVNWCDbStatements.SELECT_ACTUAL_NODE);
        try {
            stmt.bindf("is", pdh.getWCRoot().getWcId(), localRelpath);
            gotRow = stmt.next();
        } finally {
            stmt.reset();
        }
        if (gotRow) {
            stmt = pdh.getWCRoot().getSDb().getStatement(SVNWCDbStatements.UPDATE_ACTUAL_PROPERTY_CONFLICTS);
        } else if (prejBasename == null) {
            return;
        } else {
            stmt = pdh.getWCRoot().getSDb().getStatement(SVNWCDbStatements.INSERT_ACTUAL_PROPERTY_CONFLICTS);
            if (localRelpath != null && !"".equals(SVNFileUtil.getFilePath(localRelpath))) {
                stmt.bindString(4, SVNFileUtil.getFilePath(SVNFileUtil.getFileDir(localRelpath)));
            } else {
                stmt.bindNull(4);
            }
        }
        stmt.bindf("iss", pdh.getWCRoot().getWcId(), localRelpath, prejBasename);
        stmt.done();
    }

    private void addExcludedOrNotPresentNode(File localAbspath, File reposRelpath, SVNURL reposRootUrl, String reposUuid, long revision, SVNWCDbKind kind, SVNWCDbStatus status, SVNSkel conflict,
            SVNSkel workItems) throws SVNException {
        assert (isAbsolute(localAbspath));
        assert (reposRelpath != null);
        // SVN_ERR_ASSERT(svn_uri_is_absolute(repos_root_url));
        assert (reposUuid != null);
        assert (SVNRevision.isValidRevisionNumber(revision));
        assert (status == SVNWCDbStatus.ServerExcluded || status == SVNWCDbStatus.Excluded || status == SVNWCDbStatus.NotPresent);
        
        DirParsedInfo parseDir = parseDir(SVNFileUtil.getParentFile(localAbspath), Mode.ReadWrite);
        SVNWCDbDir pdh = parseDir.wcDbDir;
        File localRelpath = SVNFileUtil.createFilePath(parseDir.localRelPath, SVNFileUtil.getFileName(localAbspath));
        verifyDirUsable(pdh);

        InsertBase ibb = new InsertBase();
        ibb.status = status;
        ibb.kind = kind;
        ibb.reposRelpath = reposRelpath;
        ibb.revision = revision;
        ibb.children = null;
        ibb.depth = SVNDepth.UNKNOWN;
        ibb.checksum = null;
        ibb.target = null;
        ibb.conflict = conflict;
        ibb.workItems = workItems;
        ibb.reposRootURL = reposRootUrl;
        ibb.reposUUID = reposUuid;
        
        ibb.wcId = pdh.getWCRoot().getWcId();
        ibb.localRelpath = localRelpath;
        pdh.getWCRoot().getSDb().runTransaction(ibb);
        pdh.flushEntries(localAbspath);
    }

    public void globalCommit(File localAbspath, long newRevision, long changedRevision, SVNDate changedDate, String changedAuthor, SVNChecksum newChecksum, List<File> newChildren,
            SVNProperties newDavCache, boolean keepChangelist, boolean noUnlock, SVNSkel workItems) throws SVNException {
        assert (isAbsolute(localAbspath));
        assert (SVNRevision.isValidRevisionNumber(newRevision));
        assert (newChecksum == null || newChildren == null);
        DirParsedInfo parseDir = parseDir(localAbspath, Mode.ReadWrite);
        SVNWCDbDir pdh = parseDir.wcDbDir;
        File localRelpath = parseDir.localRelPath;
        verifyDirUsable(pdh);
        Commit cb = new Commit();
        cb.pdh = pdh;
        cb.localRelpath = localRelpath;
        cb.newRevision = newRevision;
        cb.changedRev = changedRevision;
        cb.changedDate = changedDate;
        cb.changedAuthor = changedAuthor;
        cb.newChecksum = newChecksum;
        cb.newChildren = newChildren;
        cb.newDavCache = newDavCache;
        cb.keepChangelist = keepChangelist;
        cb.noUnlock = noUnlock;
        cb.workItems = workItems;
        ReposInfo2 reposInfo = determineReposInfo(pdh, localRelpath);
        cb.reposId = reposInfo.reposId;
        cb.reposRelPath = reposInfo.reposRelPath;
        pdh.getWCRoot().getSDb().runTransaction(cb);
        pdh.flushEntries(localAbspath);
    }

    private static class ReposInfo2 {

        public long reposId;
        public File reposRelPath;
    }

    private ReposInfo2 determineReposInfo(SVNWCDbDir pdh, File localRelpath) throws SVNException {
        ReposInfo2 info = new ReposInfo2();
        SVNSqlJetStatement stmt = pdh.getWCRoot().getSDb().getStatement(SVNWCDbStatements.SELECT_BASE_NODE);
        try {
            stmt.bindf("is", pdh.getWCRoot().getWcId(), localRelpath);
            boolean haveRow = stmt.next();
            if (haveRow) {
                assert (!stmt.isColumnNull(SVNWCDbSchema.NODES__Fields.repos_id));
                assert (!stmt.isColumnNull(SVNWCDbSchema.NODES__Fields.repos_path));
                info.reposId = stmt.getColumnLong(SVNWCDbSchema.NODES__Fields.repos_id);
                info.reposRelPath = SVNFileUtil.createFilePath(stmt.getColumnString(SVNWCDbSchema.NODES__Fields.repos_path));
                return info;
            }
        } finally {
            stmt.reset();
        }
        File localParentRelpath = SVNFileUtil.getFileDir(localRelpath);
        String name = SVNFileUtil.getFileName(localRelpath);
        WCDbRepositoryInfo reposInfo = new WCDbRepositoryInfo();
        info.reposId = scanUpwardsForRepos(reposInfo, pdh.getWCRoot(), localParentRelpath);
        File reposParentRelpath = reposInfo.relPath;
        info.reposRelPath = SVNFileUtil.createFilePath(reposParentRelpath, name);
        return info;
    }

    public class Commit implements SVNSqlJetTransaction {

        public SVNWCDbDir pdh;
        public File localRelpath;
        public long newRevision;
        public long changedRev;
        public SVNDate changedDate;
        public String changedAuthor;
        public SVNChecksum newChecksum;
        public List<File> newChildren;
        public SVNProperties newDavCache;
        public boolean keepChangelist;
        public boolean noUnlock;
        public SVNSkel workItems;
        public long reposId;
        public File reposRelPath;

        public void transaction(SVNSqlJetDb db) throws SqlJetException, SVNException {

            SVNSqlJetStatement stmtBase;
            SVNSqlJetStatement stmtWork;
            SVNSqlJetStatement stmtAct;
            boolean haveBase;
            boolean haveWork;
            boolean haveAct;
            byte[] propBlob = null;
            String changelist = null;
            File parentRelpath;
            SVNWCDbStatus newPresence;
            SVNWCDbKind newKind;
            String newDepthStr = null;
            SVNSqlJetStatement stmt;

            stmtBase = pdh.getWCRoot().getSDb().getStatement(SVNWCDbStatements.SELECT_BASE_NODE);
            stmtWork = pdh.getWCRoot().getSDb().getStatement(SVNWCDbStatements.SELECT_WORKING_NODE);
            stmtAct = pdh.getWCRoot().getSDb().getStatement(SVNWCDbStatements.SELECT_ACTUAL_NODE);

            try {
                stmtBase.bindf("is", pdh.getWCRoot().getWcId(), localRelpath);
                stmtWork.bindf("is", pdh.getWCRoot().getWcId(), localRelpath);
                haveBase = stmtBase.next();
                haveWork = stmtWork.next();
                stmtAct.bindf("is", pdh.getWCRoot().getWcId(), localRelpath);
                haveAct = stmtAct.next();

                if (haveWork) {
                    newKind = getColumnKind(stmtWork, NODES__Fields.kind);
                } else {
                    newKind = getColumnKind(stmtBase, NODES__Fields.kind);
                }

                if (newKind == SVNWCDbKind.Dir) {
                    if (haveWork) {
                        newDepthStr = stmtWork.getColumnString(SVNWCDbSchema.NODES__Fields.depth);
                    } else {
                        newDepthStr = stmtBase.getColumnString(SVNWCDbSchema.NODES__Fields.depth);
                    }
                }

                if (haveBase) {
                    assert (!stmtBase.isColumnNull(SVNWCDbSchema.NODES__Fields.repos_id));
                    assert (!stmtBase.isColumnNull(SVNWCDbSchema.NODES__Fields.repos_path));
                    assert (reposId == stmtBase.getColumnLong(SVNWCDbSchema.NODES__Fields.repos_id));
                    assert (reposRelPath.equals(SVNFileUtil.createFilePath(stmtBase.getColumnString(SVNWCDbSchema.NODES__Fields.repos_path))));
                }

                if (haveAct) {
                    propBlob = stmtAct.getColumnBlob(SVNWCDbSchema.ACTUAL_NODE__Fields.properties);
                }
                if (haveWork && propBlob == null) {
                    propBlob = stmtAct.getColumnBlob(SVNWCDbSchema.NODES__Fields.properties);
                }
                if (haveBase && propBlob == null) {
                    propBlob = stmtBase.getColumnBlob(SVNWCDbSchema.NODES__Fields.properties);
                }
                if (keepChangelist && haveAct) {
                    changelist = stmtAct.getColumnString(SVNWCDbSchema.ACTUAL_NODE__Fields.changelist);
                }

            } finally {
                stmtBase.reset();
                stmtWork.reset();
                stmtAct.reset();
            }

            if (localRelpath == null) {
                parentRelpath = null;
            } else {
                parentRelpath = SVNFileUtil.getFileDir(localRelpath);
            }

            newPresence = SVNWCDbStatus.Normal;

            stmt = pdh.getWCRoot().getSDb().getStatement(SVNWCDbStatements.APPLY_CHANGES_TO_BASE_NODE);
            stmt.bindf("issisrtstrisnbn", pdh.getWCRoot().getWcId(), localRelpath, parentRelpath, reposId, reposRelPath, newRevision, getPresenceText(newPresence), newDepthStr, 
                    getKindText(newKind),
                    changedRev, changedDate, changedAuthor, propBlob);
            stmt.bindChecksum(13, newChecksum);
            stmt.bindProperties(15, newDavCache);
            stmt.done();

            if (haveWork) {
                stmt = pdh.getWCRoot().getSDb().getStatement(SVNWCDbStatements.DELETE_ALL_WORKING_NODES);
                stmt.bindf("is", pdh.getWCRoot().getWcId(), localRelpath);
                stmt.done();
            }

            if (haveAct) {
                if (keepChangelist && changelist != null) {
                    stmt = pdh.getWCRoot().getSDb().getStatement(SVNWCDbStatements.RESET_ACTUAL_WITH_CHANGELIST);
                    stmt.bindf("isss", pdh.getWCRoot().getWcId(), localRelpath, SVNFileUtil.getFileDir(localRelpath), changelist);
                    stmt.done();
                } else {
                    stmt = pdh.getWCRoot().getSDb().getStatement(SVNWCDbStatements.DELETE_ACTUAL_NODE);
                    stmt.bindf("is", pdh.getWCRoot().getWcId(), localRelpath);
                    stmt.done();
                }
            }

            if (newKind == SVNWCDbKind.Dir) {
                /* When committing a directory, we should have its new children. */
                /* ### one day. just not today. */
                /* ### process the children */
            }

            if (!noUnlock) {
                SVNSqlJetStatement lockStmt = pdh.getWCRoot().getSDb().getStatement(SVNWCDbStatements.DELETE_LOCK);
                lockStmt.bindf("is", reposId, reposRelPath);
                lockStmt.done();
            }

            addWorkItems(pdh.getWCRoot().getSDb(), workItems);
        }

    }

    public Structure<PristineInfo> readPristineInfo(File localAbspath) throws SVNException {
        final DirParsedInfo wcInfo = obtainWcRoot(localAbspath);
        SVNWCDbDir dir = wcInfo.wcDbDir;
        SVNSqlJetStatement stmt = dir.getWCRoot().getSDb().getStatement(SVNWCDbStatements.SELECT_NODE_INFO);
        stmt.bindf("is", dir.getWCRoot().getWcId(), dir.computeRelPath());
        if (!stmt.next()) {
            SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.WC_PATH_NOT_FOUND, "The node ''{1}'' was not found.", 
                    dir.getWCRoot().getAbsPath(dir.computeRelPath())), SVNLogType.WC);
        }
        
        long opDepth = getColumnInt64(stmt, NODES__Fields.op_depth);
        SVNWCDbStatus status = getColumnPresence(stmt);
        
        if (opDepth > 0 && status == SVNWCDbStatus.BaseDeleted) {
            assert stmt.next();
            opDepth = getColumnInt64(stmt, NODES__Fields.op_depth);
            status = getColumnPresence(stmt);
        }
        Structure<PristineInfo> result = Structure.obtain(PristineInfo.class);
        SVNWCDbKind kind = getColumnKind(stmt, NODES__Fields.kind);
        result.set(PristineInfo.kind, kind);
        result.set(PristineInfo.changed_date, SVNWCUtils.readDate(getColumnInt64(stmt, NODES__Fields.changed_date)));
        result.set(PristineInfo.changed_author, getColumnText(stmt, NODES__Fields.changed_author));
        result.set(PristineInfo.changed_rev, getColumnInt64(stmt, NODES__Fields.changed_revision));
        
        if (opDepth > 0) {
            result.set(PristineInfo.status, getWorkingStatus(status));
        } else {
            result.set(PristineInfo.status, getWorkingStatus(status));
        }
        if (kind == SVNWCDbKind.File) {
            SvnChecksum checksum = new SvnChecksum();
            checksum.setDigest(getColumnText(stmt, NODES__Fields.checksum));
            checksum.setKind(SvnChecksum.Kind.sha1);
            result.set(PristineInfo.checksum, checksum);
        } else if (kind == SVNWCDbKind.Symlink) {
            // TODO File?
            result.set(PristineInfo.target, getColumnText(stmt, NODES__Fields.symlink_target));
        }
        result.set(PristineInfo.hadProps, hasColumnProperties(stmt, NODES__Fields.properties));
            
        return result;
    }

    public DirParsedInfo obtainWcRoot(File localAbspath) throws SVNException {
        assert (isAbsolute(localAbspath));

        final DirParsedInfo parseDir = parseDir(localAbspath, Mode.ReadOnly);
        SVNWCDbDir pdh = parseDir.wcDbDir;
        verifyDirUsable(pdh);
        
        return parseDir;
    }

}
