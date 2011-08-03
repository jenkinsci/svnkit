package org.tmatesoft.svn.core.internal.wc17.db;

import static org.tmatesoft.svn.core.internal.wc17.db.SvnWcDbStatementUtil.getColumnBlob;
import static org.tmatesoft.svn.core.internal.wc17.db.SvnWcDbStatementUtil.getColumnBoolean;
import static org.tmatesoft.svn.core.internal.wc17.db.SvnWcDbStatementUtil.getColumnChecksum;
import static org.tmatesoft.svn.core.internal.wc17.db.SvnWcDbStatementUtil.getColumnDate;
import static org.tmatesoft.svn.core.internal.wc17.db.SvnWcDbStatementUtil.getColumnDepth;
import static org.tmatesoft.svn.core.internal.wc17.db.SvnWcDbStatementUtil.getColumnInt64;
import static org.tmatesoft.svn.core.internal.wc17.db.SvnWcDbStatementUtil.getColumnKind;
import static org.tmatesoft.svn.core.internal.wc17.db.SvnWcDbStatementUtil.getColumnPath;
import static org.tmatesoft.svn.core.internal.wc17.db.SvnWcDbStatementUtil.getColumnPresence;
import static org.tmatesoft.svn.core.internal.wc17.db.SvnWcDbStatementUtil.getColumnRevNum;
import static org.tmatesoft.svn.core.internal.wc17.db.SvnWcDbStatementUtil.getColumnText;
import static org.tmatesoft.svn.core.internal.wc17.db.SvnWcDbStatementUtil.getLockFromColumns;
import static org.tmatesoft.svn.core.internal.wc17.db.SvnWcDbStatementUtil.hasColumnProperties;
import static org.tmatesoft.svn.core.internal.wc17.db.SvnWcDbStatementUtil.isColumnNull;
import static org.tmatesoft.svn.core.internal.wc17.db.SvnWcDbStatementUtil.parseDepth;
import static org.tmatesoft.svn.core.internal.wc17.db.SvnWcDbStatementUtil.reset;

import java.io.File;
import java.util.Collection;

import org.tmatesoft.sqljet.core.SqlJetException;
import org.tmatesoft.sqljet.core.SqlJetTransactionMode;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.db.SVNSqlJetDb;
import org.tmatesoft.svn.core.internal.db.SVNSqlJetStatement;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.internal.wc17.SVNWCUtils;
import org.tmatesoft.svn.core.internal.wc17.db.ISVNWCDb.SVNWCDbKind;
import org.tmatesoft.svn.core.internal.wc17.db.ISVNWCDb.SVNWCDbLock;
import org.tmatesoft.svn.core.internal.wc17.db.ISVNWCDb.SVNWCDbStatus;
import org.tmatesoft.svn.core.internal.wc17.db.StructureFields.AdditionInfo;
import org.tmatesoft.svn.core.internal.wc17.db.StructureFields.DeletionInfo;
import org.tmatesoft.svn.core.internal.wc17.db.StructureFields.NodeInfo;
import org.tmatesoft.svn.core.internal.wc17.db.StructureFields.RepositoryInfo;
import org.tmatesoft.svn.core.internal.wc17.db.statement.SVNWCDbCollectTargets;
import org.tmatesoft.svn.core.internal.wc17.db.statement.SVNWCDbCreateSchema;
import org.tmatesoft.svn.core.internal.wc17.db.statement.SVNWCDbInsertTarget;
import org.tmatesoft.svn.core.internal.wc17.db.statement.SVNWCDbSchema;
import org.tmatesoft.svn.core.internal.wc17.db.statement.SVNWCDbSchema.LOCK__Fields;
import org.tmatesoft.svn.core.internal.wc17.db.statement.SVNWCDbSchema.NODES__Fields;
import org.tmatesoft.svn.core.internal.wc17.db.statement.SVNWCDbSelectDeletionInfo;
import org.tmatesoft.svn.core.internal.wc17.db.statement.SVNWCDbStatements;
import org.tmatesoft.svn.util.SVNLogType;

public class SvnWcDbShared {
    
    public static void begingReadTransaction(SVNWCDbRoot root) throws SVNException {
        root.getSDb().beginTransaction(SqlJetTransactionMode.READ_ONLY);
    }
    
    public static void begingWriteTransaction(SVNWCDbRoot root) throws SVNException {
        root.getSDb().beginTransaction(SqlJetTransactionMode.WRITE);
    }
    
    public static void commitTransaction(SVNWCDbRoot root) throws SVNException {
        root.getSDb().commit();
    }
    
    public static void rollbackTransaction(SVNWCDbRoot root) throws SVNException {
        root.getSDb().rollback();
    }
    
    protected static void nodeNotFound(File absolutePath) throws SVNException {
        SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_PATH_NOT_FOUND, "The node ''{0}'' was not found.", absolutePath);
        SVNErrorManager.error(err, SVNLogType.WC);
    }

    protected static void nodeNotFound(SVNWCDbRoot root, File relPath) throws SVNException {
        nodeNotFound(root.getAbsPath(relPath));
    }
    
    protected static void sqliteError(SqlJetException e) throws SVNException {
        SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.SQLITE_ERROR, e);
        SVNErrorManager.error(err, SVNLogType.WC);
    }
    
    protected static void collectTargets(SVNWCDbRoot root, File relpath, SVNDepth depth, Collection<String> changelists) throws SVNException {
        SVNSqlJetDb tmpDb = root.getSDb().getTemporaryDb();
        SVNSqlJetStatement stmt = null;
        try {
            stmt = new SVNWCDbCreateSchema(tmpDb, SVNWCDbCreateSchema.TARGETS_LIST, -1);
            stmt.done();
            stmt = new SVNWCDbInsertTarget(tmpDb, new SVNWCDbCollectTargets(root.getSDb(), root.getWcId(), relpath, depth, changelists));
            stmt.done();
            if (depth == SVNDepth.FILES || depth == SVNDepth.IMMEDIATES) {
                stmt  = new SVNWCDbInsertTarget(tmpDb, new SVNWCDbCollectTargets(root.getSDb(), root.getWcId(), relpath, SVNDepth.EMPTY, changelists));
                stmt.done();
            }
        } finally {
            reset(stmt);
        }
    }
    
    protected static Structure<AdditionInfo> scanAddition(SVNWCDbRoot root, File localRelpath, AdditionInfo... fields) throws SVNException {
        Structure<AdditionInfo> info = Structure.obtain(AdditionInfo.class, fields);
        info.set(AdditionInfo.originalRevision, SVNWCDb.INVALID_REVNUM);
        info.set(AdditionInfo.originalReposId, SVNWCDb.INVALID_REPOS_ID);
        
        begingReadTransaction(root);
        File buildRelpath = SVNFileUtil.createFilePath("");
        File currentRelpath = localRelpath;
        
        SVNSqlJetStatement stmt = null;
        try {
            SVNWCDbStatus presence;
            File reposPrefixPath = SVNFileUtil.createFilePath("");
            int i;

            stmt = root.getSDb().getStatement(SVNWCDbStatements.SELECT_WORKING_NODE);

            stmt.bindf("is", root.getWcId(), localRelpath);
            if (!stmt.next()) {
                reset(stmt);
                nodeNotFound(root, localRelpath);
            }

            presence = getColumnPresence(stmt);

            if (presence != SVNWCDbStatus.Normal) {
                reset(stmt);
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_PATH_UNEXPECTED_STATUS, "Expected node ''{0}'' to be added.", root.getAbsPath(localRelpath));
                SVNErrorManager.error(err, SVNLogType.WC);
            }
            info.set(AdditionInfo.originalRevision, getColumnRevNum(stmt, NODES__Fields.revision));
            info.set(AdditionInfo.status, SVNWCDbStatus.Added);
            long opDepth = getColumnInt64(stmt, SVNWCDbSchema.NODES__Fields.op_depth);
            currentRelpath = localRelpath;
            for (i = SVNWCUtils.relpathDepth(localRelpath); i > opDepth; --i) {
                reposPrefixPath = SVNFileUtil.createFilePath(SVNFileUtil.createFilePath(SVNFileUtil.getFileName(currentRelpath)), reposPrefixPath);
                currentRelpath = SVNFileUtil.getFileDir(currentRelpath);
            }
            info.set(AdditionInfo.opRootRelPath, currentRelpath);
            info.set(AdditionInfo.opRootAbsPath, root.getAbsPath(currentRelpath));

            if (info.hasField(AdditionInfo.originalReposRelPath) 
                    || info.hasField(AdditionInfo.originalRootUrl) 
                    || info.hasField(AdditionInfo.originalUuid)
                    || (info.hasField(AdditionInfo.originalRevision) && info.lng(AdditionInfo.originalRevision) == SVNWCDb.INVALID_REVNUM) 
                    || info.hasField(AdditionInfo.status)) {
                    if (!localRelpath.equals(currentRelpath)) {
                        reset(stmt);
                        stmt.bindf("is", root.getWcId(), currentRelpath);
                        if (!stmt.next()) {
                            reset(stmt);
                            nodeNotFound(root, currentRelpath);
                        }

                        if (info.hasField(AdditionInfo.originalRevision) 
                                && info.lng(AdditionInfo.originalRevision) == SVNWCDb.INVALID_REVNUM)
                            info.set(AdditionInfo.originalRevision, getColumnRevNum(stmt, NODES__Fields.revision));
                    }
                    info.set(AdditionInfo.originalReposRelPath, getColumnPath(stmt, NODES__Fields.repos_path));

                    if (!isColumnNull(stmt, NODES__Fields.repos_id) 
                            && (info.hasField(AdditionInfo.status) || info.hasField(AdditionInfo.originalReposId))) {
                        info.set(AdditionInfo.originalReposId, getColumnInt64(stmt, NODES__Fields.repos_id));
                        if (getColumnBoolean(stmt, NODES__Fields.moved_here)) {
                            info.set(AdditionInfo.status, SVNWCDbStatus.MovedHere);
                        } else {
                            info.set(AdditionInfo.status, SVNWCDbStatus.Copied);
                        }
                    }
                }

            while (true) {
                reset(stmt);
                reposPrefixPath = SVNFileUtil.createFilePath(SVNFileUtil.createFilePath(SVNFileUtil.getFileName(currentRelpath)), reposPrefixPath);
                currentRelpath = SVNFileUtil.getFileDir(currentRelpath);
                stmt.bindf("is", root.getWcId(), currentRelpath);
                if (!stmt.next()) {
                    break;
                }
                opDepth = getColumnInt64(stmt, NODES__Fields.op_depth);
                for (i = SVNWCUtils.relpathDepth(currentRelpath); i > opDepth; i--) {
                    reposPrefixPath = SVNFileUtil.createFilePath(SVNFileUtil.createFilePath(SVNFileUtil.getFileName(currentRelpath)), reposPrefixPath);
                    currentRelpath = SVNFileUtil.getFileDir(currentRelpath);
                }
            }
            reset(stmt);
            buildRelpath = reposPrefixPath;
            
            if (info.hasField(AdditionInfo.reposRelPath) || info.hasField(AdditionInfo.reposId)) {
                Structure<NodeInfo> baseInfo = getBaseInfo(root, currentRelpath, NodeInfo.reposRelPath, NodeInfo.reposId);
                info.set(AdditionInfo.reposRelPath, SVNFileUtil.createFilePath(baseInfo.<File>get(NodeInfo.reposRelPath), buildRelpath));
                info.set(AdditionInfo.reposId, baseInfo.lng(NodeInfo.reposId));
                baseInfo.release();
            }
        } finally {
            reset(stmt);
            commitTransaction(root);
        }
        
        return info;
    }
    
    protected static Structure<DeletionInfo> scanDeletion(SVNWCDbRoot root, File localRelpath) throws SVNException {
        Structure<DeletionInfo> info = Structure.obtain(DeletionInfo.class);
        
        SVNWCDbStatus childPresence = SVNWCDbStatus.BaseDeleted;
        boolean childHasBase = false;
        boolean foundMovedTo = false;
        long opDepth = 0, localOpDepth = 0;
        File currentRelPath = localRelpath;
        File childRelpath = null;
        
        begingReadTransaction(root);
        SVNSqlJetStatement stmt = null;
        try {while (true) {
                stmt = root.getSDb().getStatement(SVNWCDbStatements.SELECT_DELETION_INFO);
                stmt.bindf("is", root.getWcId(), SVNFileUtil.getFilePath(currentRelPath));
                try {
                    if (!stmt.next()) {
                        if (currentRelPath == localRelpath) {
                            nodeNotFound(root, localRelpath);
                        }
                        if (childHasBase) {
                            info.set(DeletionInfo.baseDelRelPath, childRelpath);
                        }
                        break;
                    }
                } finally {
                    reset(stmt);
                }
                
                SVNWCDbStatus workPresence = getColumnPresence(stmt);
                if (currentRelPath.equals(localRelpath) && workPresence != SVNWCDbStatus.NotPresent && workPresence != SVNWCDbStatus.BaseDeleted) {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_PATH_UNEXPECTED_STATUS, "Expected node ''{0}'' to be deleted.", root.getAbsPath(localRelpath));
                    SVNErrorManager.error(err, SVNLogType.WC);
                }
                assert (workPresence == SVNWCDbStatus.Normal || workPresence == SVNWCDbStatus.NotPresent || workPresence == SVNWCDbStatus.BaseDeleted);
                
                SVNSqlJetStatement baseStmt = stmt.getJoinedStatement(SVNWCDbSelectDeletionInfo.NODES_BASE);
                boolean haveBase = false;
                try {
                    haveBase = baseStmt != null && baseStmt.next() && !isColumnNull(baseStmt, SVNWCDbSchema.NODES__Fields.presence);
                    if (haveBase) {
                        SVNWCDbStatus basePresence = getColumnPresence(baseStmt);
                        if (basePresence == SVNWCDbStatus.Incomplete) {
                            basePresence = SVNWCDbStatus.Normal;
                        }
                    }
                    
                    
                    if (!foundMovedTo && !isColumnNull(stmt, SVNWCDbSchema.NODES__Fields.moved_to)) {
                        foundMovedTo = true;
                        info.set(DeletionInfo.baseDelRelPath, currentRelPath);
                        info.set(DeletionInfo.movedToRelPath, getColumnPath(stmt, SVNWCDbSchema.NODES__Fields.moved_to));
                    }
                    
                    opDepth = getColumnInt64(stmt, NODES__Fields.op_depth);
                    if (currentRelPath.equals(localRelpath)) {
                        localOpDepth = opDepth;
                    }
                    if (!info.hasValue(DeletionInfo.workDelRelPath) && 
                            ((opDepth < localOpDepth && opDepth > 0) || childPresence == SVNWCDbStatus.NotPresent)) {
                        info.set(DeletionInfo.workDelRelPath, childRelpath);
                    }
                } finally {
                    reset(stmt);
                    reset(baseStmt);
                }
                childRelpath = currentRelPath;
                childPresence = workPresence;
                childHasBase = haveBase;
                
                currentRelPath = SVNFileUtil.getFileDir(currentRelPath);                
            }
        } finally {
            commitTransaction(root);
            reset(stmt);
        }
        return info;
    }
    
    protected static Structure<NodeInfo> getBaseInfo(SVNWCDbRoot wcroot, File localRelPath, NodeInfo...fields) throws SVNException {
        Structure<NodeInfo> info = Structure.obtain(NodeInfo.class, fields);
        SVNSqlJetStatement stmt = wcroot.getSDb().getStatement(info.hasField(NodeInfo.lock) ? SVNWCDbStatements.SELECT_BASE_NODE_WITH_LOCK : SVNWCDbStatements.SELECT_BASE_NODE);
        try {
            stmt.bindf("is", wcroot.getWcId(), SVNFileUtil.getFilePath(localRelPath));
            if (stmt.next()) {
                SVNWCDbKind node_kind = getColumnKind(stmt, NODES__Fields.kind);

                if (info.hasField(NodeInfo.kind)) {
                    info.set(NodeInfo.kind, node_kind);
                }
                if (info.hasField(NodeInfo.status)) {
                    info.set(NodeInfo.status, getColumnPresence(stmt));
                }
                if (info.hasField(NodeInfo.reposId)) {
                    info.set(NodeInfo.reposId, getColumnInt64(stmt, NODES__Fields.repos_id));
                }
                if (info.hasField(NodeInfo.revision)) {
                    info.set(NodeInfo.revision, getColumnRevNum(stmt, SVNWCDbSchema.NODES__Fields.revision));
                }
                if (info.hasField(NodeInfo.reposRelPath)) {
                    info.set(NodeInfo.reposRelPath, getColumnPath(stmt, SVNWCDbSchema.NODES__Fields.repos_path));
                }
                if (info.hasField(NodeInfo.lock)) {
                    final SVNSqlJetStatement lockStmt = stmt.getJoinedStatement(SVNWCDbSchema.LOCK);
                    SVNWCDbLock lock = getLockFromColumns(lockStmt, LOCK__Fields.lock_owner, LOCK__Fields.lock_owner, LOCK__Fields.lock_comment, LOCK__Fields.lock_date);
                    info.set(NodeInfo.lock, lock);
                }
                
                if (info.hasField(NodeInfo.reposRootUrl) || info.hasField(NodeInfo.reposUuid)) {
                    if (isColumnNull(stmt, SVNWCDbSchema.NODES__Fields.repos_id)) {
                        if (info.hasField(NodeInfo.reposRootUrl)) {
                            info.set(NodeInfo.reposRootUrl,null);
                        }
                        if (info.hasField(NodeInfo.reposUuid)) {
                            info.set(NodeInfo.reposUuid,null);
                        }
                    } else {
                        Structure<RepositoryInfo> repositoryInfo = wcroot.getDb().fetchRepositoryInfo(wcroot.getSDb(), getColumnInt64(stmt, SVNWCDbSchema.NODES__Fields.repos_id));
                        repositoryInfo.
                            from(RepositoryInfo.reposRootUrl, RepositoryInfo.reposUuid).
                            into(info, NodeInfo.reposRootUrl, NodeInfo.reposUuid);
                        repositoryInfo.release();
                    }
                }
                if (info.hasField(NodeInfo.changedRev)) {
                    info.set(NodeInfo.changedRev, getColumnRevNum(stmt, SVNWCDbSchema.NODES__Fields.changed_revision));
                }
                if (info.hasField(NodeInfo.changedDate)) {
                    info.set(NodeInfo.changedDate, getColumnDate(stmt, SVNWCDbSchema.NODES__Fields.changed_date));
                }
                if (info.hasField(NodeInfo.changedAuthor)) {
                    /* Result may be NULL. */
                    info.set(NodeInfo.changedAuthor,getColumnText(stmt, SVNWCDbSchema.NODES__Fields.changed_author));
                }
                if (info.hasField(NodeInfo.depth)) {
                    if (node_kind != SVNWCDbKind.Dir) {
                        info.set(NodeInfo.depth, SVNDepth.UNKNOWN);
                    } else {
                        String depth_str = getColumnText(stmt, SVNWCDbSchema.NODES__Fields.depth);

                        if (depth_str == null) {
                            info.set(NodeInfo.depth, SVNDepth.UNKNOWN);
                        } else {
                            info.set(NodeInfo.depth, parseDepth(depth_str));
                        }
                    }
                }
                if (info.hasField(NodeInfo.checksum)) {
                    if (node_kind != SVNWCDbKind.File) {
                        info.set(NodeInfo.checksum,null);
                    } else {
                        try {
                            info.set(NodeInfo.checksum, getColumnChecksum(stmt, SVNWCDbSchema.NODES__Fields.checksum));
                        } catch (SVNException e) {
                            SVNErrorMessage err = SVNErrorMessage.create(e.getErrorMessage().getErrorCode(), "The node ''{0}'' has a corrupt checksum value.", wcroot.getAbsPath(localRelPath));
                            SVNErrorManager.error(err, SVNLogType.WC);
                        }
                    }
                }
                if (info.hasField(NodeInfo.target)) {
                    if (node_kind != SVNWCDbKind.Symlink)
                        info.set(NodeInfo.target,null);
                    else
                        info.set(NodeInfo.target, getColumnPath(stmt, SVNWCDbSchema.NODES__Fields.symlink_target));
                }
                if (info.hasField(NodeInfo.updateRoot)) {
                    info.set(NodeInfo.updateRoot, getColumnBoolean(stmt, SVNWCDbSchema.NODES__Fields.file_external));
                }
                if (info.hasField(NodeInfo.hadProps)) {
                    info.set(NodeInfo.hadProps, hasColumnProperties(stmt, NODES__Fields.properties));
                }
            } else {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_PATH_NOT_FOUND, "The node ''{0}'' was not found.", wcroot.getAbsPath(localRelPath));
                SVNErrorManager.error(err, SVNLogType.WC);
            }

        } finally {
            stmt.reset();
        }

        return info;

    }

    protected static Structure<NodeInfo> readInfo(SVNWCDbRoot wcRoot, File localRelPath, NodeInfo... fields) throws SVNException {
        Structure<NodeInfo> info = Structure.obtain(NodeInfo.class, fields);
    
        SVNSqlJetStatement stmtInfo = null;
        SVNSqlJetStatement stmtActual = null;
        
        try {
            stmtInfo = wcRoot.getSDb().getStatement(info.hasField(NodeInfo.lock) ? SVNWCDbStatements.SELECT_NODE_INFO_WITH_LOCK : SVNWCDbStatements.SELECT_NODE_INFO);
            stmtInfo.bindf("is", wcRoot.getWcId(), localRelPath);
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
                        info.set(NodeInfo.status, SVNWCDb.getWorkingStatus(info.<SVNWCDbStatus>get(NodeInfo.status)));
                    }
                }
                if (info.hasField(NodeInfo.kind)) {
                    info.set(NodeInfo.kind, nodeKind);
                }
                info.set(NodeInfo.reposId, opDepth != 0 ? SVNWCDb.INVALID_REPOS_ID : getColumnInt64(stmtInfo, SVNWCDbSchema.NODES__Fields.repos_id));
                if (info.hasField(NodeInfo.revision)) {
                    info.set(NodeInfo.revision, opDepth != 0 ? SVNWCDb.INVALID_REVNUM : getColumnRevNum(stmtInfo, SVNWCDbSchema.NODES__Fields.revision));
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
                info.set(NodeInfo.originalReposId, opDepth == 0 ? SVNWCDb.INVALID_REPOS_ID : getColumnInt64(stmtInfo, SVNWCDbSchema.NODES__Fields.repos_id));
                if (info.hasField(NodeInfo.originalRevision)) {
                    info.set(NodeInfo.originalRevision, opDepth == 0 ? SVNWCDb.INVALID_REVNUM : getColumnRevNum(stmtInfo, SVNWCDbSchema.NODES__Fields.revision));
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
                    SVNWCDbLock lock = getLockFromColumns(stmtBaseLock, LOCK__Fields.lock_owner, LOCK__Fields.lock_owner, LOCK__Fields.lock_comment, LOCK__Fields.lock_date);
                    info.set(NodeInfo.lock, lock);
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
                    info.set(NodeInfo.revision, SVNWCDb.INVALID_REVNUM);
                }
                info.set(NodeInfo.reposId, SVNWCDb.INVALID_REPOS_ID);
                if (info.hasField(NodeInfo.changedRev)) {
                    info.set(NodeInfo.changedRev, SVNWCDb.INVALID_REVNUM);
                }
                if (info.hasField(NodeInfo.depth)) {
                    info.set(NodeInfo.depth, SVNDepth.UNKNOWN);
                }
                if (info.hasField(NodeInfo.originalRevision)) {
                    info.set(NodeInfo.originalRevision, SVNWCDb.INVALID_REVNUM);
                }
                if (info.hasField(NodeInfo.originalReposId)) {
                    info.set(NodeInfo.originalReposId, SVNWCDb.INVALID_REPOS_ID);
                }
                if (info.hasField(NodeInfo.changelist)) {
                    info.set(NodeInfo.changelist, stmtActual.getColumnString(SVNWCDbSchema.ACTUAL_NODE__Fields.changelist));
                }
                if (info.hasField(NodeInfo.originalRevision))
                    info.set(NodeInfo.originalRevision, SVNWCDb.INVALID_REVNUM);
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
}
