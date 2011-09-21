package org.tmatesoft.svn.core.internal.wc17.db;

import static org.tmatesoft.svn.core.internal.wc17.db.SvnWcDbStatementUtil.getColumnBoolean;
import static org.tmatesoft.svn.core.internal.wc17.db.SvnWcDbStatementUtil.getColumnInt64;
import static org.tmatesoft.svn.core.internal.wc17.db.SvnWcDbStatementUtil.getColumnKind;
import static org.tmatesoft.svn.core.internal.wc17.db.SvnWcDbStatementUtil.getColumnPath;
import static org.tmatesoft.svn.core.internal.wc17.db.SvnWcDbStatementUtil.getColumnText;
import static org.tmatesoft.svn.core.internal.wc17.db.SvnWcDbStatementUtil.isColumnNull;
import static org.tmatesoft.svn.core.internal.wc17.db.SvnWcDbStatementUtil.reset;

import java.io.File;
import java.util.Comparator;
import java.util.Map;
import java.util.TreeMap;

import org.tmatesoft.sqljet.core.SqlJetException;
import org.tmatesoft.sqljet.core.SqlJetTransactionMode;
import org.tmatesoft.sqljet.core.table.ISqlJetCursor;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.internal.db.SVNSqlJetDb;
import org.tmatesoft.svn.core.internal.db.SVNSqlJetDeleteStatement;
import org.tmatesoft.svn.core.internal.db.SVNSqlJetSelectStatement;
import org.tmatesoft.svn.core.internal.db.SVNSqlJetStatement;
import org.tmatesoft.svn.core.internal.db.SVNSqlJetTableStatement;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.wc.SVNEventFactory;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.internal.wc17.SVNWCContext;
import org.tmatesoft.svn.core.internal.wc17.SVNWCUtils;
import org.tmatesoft.svn.core.internal.wc17.db.ISVNWCDb.SVNWCDbKind;
import org.tmatesoft.svn.core.internal.wc17.db.SVNWCDb.DirParsedInfo;
import org.tmatesoft.svn.core.internal.wc17.db.statement.SVNWCDbCreateSchema;
import org.tmatesoft.svn.core.internal.wc17.db.statement.SVNWCDbSchema;
import org.tmatesoft.svn.core.internal.wc17.db.statement.SVNWCDbSchema.NODES__Fields;
import org.tmatesoft.svn.core.internal.wc17.db.statement.SVNWCDbSchema.REVERT_LIST__Fields;
import org.tmatesoft.svn.core.internal.wc17.db.statement.SVNWCDbStatements;
import org.tmatesoft.svn.core.wc.ISVNEventHandler;
import org.tmatesoft.svn.core.wc.SVNEventAction;
import org.tmatesoft.svn.util.SVNLogType;

public class SvnWcDbRevert extends SvnWcDbShared {
    
    public static void revert(SVNWCDbRoot root, File localRelPath) throws SVNException {
        SVNSqlJetDb sdb = root.getSDb();
        
        SvnRevertNodesTrigger nodesTableTrigger = new SvnRevertNodesTrigger(sdb);
        SvnRevertActualNodesTrigger actualNodesTableTrigger = new SvnRevertActualNodesTrigger(sdb);
        
        SVNSqlJetStatement stmt = sdb.getStatement(SVNWCDbStatements.SELECT_NODE_INFO);
        stmt.bindf("is", root.getWcId(), localRelPath);
        if (!stmt.next()) {
            reset(stmt);
            
            stmt = sdb.getStatement(SVNWCDbStatements.DELETE_ACTUAL_NODE);
            ((SVNSqlJetTableStatement) stmt).addTrigger(actualNodesTableTrigger);
            
            stmt.bindf("is", root.getWcId(), localRelPath);
            long affectedRows = stmt.done();
            if (affectedRows > 0) {
                stmt = sdb.getStatement(SVNWCDbStatements.SELECT_ACTUAL_CHILDREN_INFO);
                stmt.bindf("is", root.getWcId(), localRelPath);
                if (stmt.next()) {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_INVALID_OPERATION_DEPTH, "Can''t revert ''{0}'' without reverting children", root.getAbsPath(localRelPath));
                    SVNErrorManager.error(err, SVNLogType.WC);
                }
                return;
            }
            nodeNotFound(root, localRelPath);
        }
        long opDepth = getColumnInt64(stmt, NODES__Fields.op_depth);
        reset(stmt);
        if (opDepth > 0 && opDepth == SVNWCUtils.relpathDepth(localRelPath)) {
            
            stmt = sdb.getStatement(SVNWCDbStatements.SELECT_OP_DEPTH_CHILDREN);
            stmt.bindf("isi", root.getWcId(), localRelPath, opDepth);
            boolean haveRow = stmt.next();
            reset(stmt);
            if (haveRow) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_INVALID_OPERATION_DEPTH, "Can''t revert ''{0}'' without reverting children", root.getAbsPath(localRelPath));
                SVNErrorManager.error(err, SVNLogType.WC);
            }
            stmt = sdb.getStatement(SVNWCDbStatements.SELECT_ACTUAL_CHILDREN_INFO);
            stmt.bindf("is", root.getWcId(), localRelPath);
            haveRow = stmt.next();
            reset(stmt);
            if (haveRow) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_INVALID_OPERATION_DEPTH, "Can''t revert ''{0}'' without reverting children", root.getAbsPath(localRelPath));
                SVNErrorManager.error(err, SVNLogType.WC);
            }

            stmt = sdb.getStatement(SVNWCDbStatements.UPDATE_OP_DEPTH_INCREASE_RECURSIVE);
            ((SVNSqlJetTableStatement) stmt).addTrigger(nodesTableTrigger);

            stmt.bindf("isi", root.getWcId(), localRelPath, opDepth);
            stmt.done();
            
            stmt = sdb.getStatement(SVNWCDbStatements.DELETE_WORKING_NODE);
            ((SVNSqlJetTableStatement) stmt).addTrigger(nodesTableTrigger);

            stmt.bindf("is", root.getWcId(), localRelPath);
            stmt.done();
            stmt = sdb.getStatement(SVNWCDbStatements.DELETE_WC_LOCK_ORPHAN);
            stmt.bindf("is", root.getWcId(), localRelPath);
            stmt.done();
        }
        stmt = sdb.getStatement(SVNWCDbStatements.DELETE_ACTUAL_NODE_LEAVING_CHANGELIST);
        ((SVNSqlJetTableStatement) stmt).addTrigger(actualNodesTableTrigger);
        stmt.bindf("is", root.getWcId(), localRelPath);
        
        long affectedRows = stmt.done();
        if (affectedRows == 0) {
            stmt = sdb.getStatement(SVNWCDbStatements.CLEAR_ACTUAL_NODE_LEAVING_CHANGELIST);
            ((SVNSqlJetTableStatement) stmt).addTrigger(actualNodesTableTrigger);
            
            stmt.bindf("is", root.getWcId(), localRelPath);
            stmt.done();
        }
    }

    public static void revertRecursive(SVNWCDbRoot root, File localRelPath) throws SVNException {
        SVNSqlJetDb sdb = root.getSDb();
        SvnRevertNodesTrigger nodesTableTrigger = new SvnRevertNodesTrigger(sdb);
        SvnRevertActualNodesTrigger actualNodesTableTrigger = new SvnRevertActualNodesTrigger(sdb);
        
        SVNSqlJetStatement stmt = sdb.getStatement(SVNWCDbStatements.SELECT_NODE_INFO);
        stmt.bindf("is", root.getWcId(), localRelPath);
        if (!stmt.next()) {
            reset(stmt);
            stmt = sdb.getStatement(SVNWCDbStatements.DELETE_ACTUAL_NODE_RECURSIVE);
            ((SVNSqlJetTableStatement) stmt).addTrigger(actualNodesTableTrigger);

            stmt.bindf("is", root.getWcId(), localRelPath);
            long affectedRows = stmt.done();
            if (affectedRows > 0) {
                return;
            }
            nodeNotFound(root, localRelPath);
        }
        long opDepth = getColumnInt64(stmt, NODES__Fields.op_depth);
        reset(stmt);
        if (opDepth > 0 && opDepth != SVNWCUtils.relpathDepth(localRelPath)) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_INVALID_OPERATION_DEPTH, "Can''t revert ''{0}'' without reverting parent", root.getAbsPath(localRelPath));
            SVNErrorManager.error(err, SVNLogType.WC);
        }
        if (opDepth == 0) {
            opDepth = 1;
        }
        
        stmt = sdb.getStatement(SVNWCDbStatements.DELETE_NODES_RECURSIVE);
        ((SVNSqlJetTableStatement) stmt).addTrigger(nodesTableTrigger);
        stmt.bindf("isi", root.getWcId(), localRelPath, opDepth);
        stmt.done();
        
        stmt = sdb.getStatement(SVNWCDbStatements.DELETE_ACTUAL_NODE_LEAVING_CHANGELIST_RECURSIVE);
        ((SVNSqlJetTableStatement) stmt).addTrigger(actualNodesTableTrigger);
        stmt.bindf("is", root.getWcId(), localRelPath);
        stmt.done();
        
        stmt = sdb.getStatement(SVNWCDbStatements.CLEAR_ACTUAL_NODE_LEAVING_CHANGELIST_RECURSIVE);
        ((SVNSqlJetTableStatement) stmt).addTrigger(actualNodesTableTrigger);
        stmt.bindf("is", root.getWcId(), localRelPath);
        stmt.done();
        
        stmt = sdb.getStatement(SVNWCDbStatements.DELETE_WC_LOCK_ORPHAN_RECURSIVE);
        stmt.bindf("is", root.getWcId(), localRelPath);
        stmt.done();
    }
    
    public enum RevertInfo {
        reverted, 
        conflictOld, 
        conflictNew,
        conflictWorking,
        propReject,
        copiedHere,
        kind
    }
    
    public static Map<File, SVNWCDbKind> readRevertCopiedChildren(SVNWCContext context, File localAbsPath) throws SVNException {
        Map<File, SVNWCDbKind> result = new TreeMap<File, ISVNWCDb.SVNWCDbKind>(new Comparator<File>() {
            @SuppressWarnings("unchecked")
            public int compare(File o1, File o2) {
                String path1 = o1.getAbsolutePath();
                String path2 = o2.getAbsolutePath();
                
                return -SVNPathUtil.PATH_COMPARATOR.compare(path1, path2);
            }
        });
        
        
        SVNWCDb db = (SVNWCDb) context.getDb();
        DirParsedInfo dirInfo = db.obtainWcRoot(localAbsPath);
        File localRelpath = dirInfo.localRelPath;
        SVNWCDbRoot root = dirInfo.wcDbDir.getWCRoot();

        root.getSDb().getTemporaryDb().beginTransaction(SqlJetTransactionMode.READ_ONLY);
        try {
            SVNSqlJetStatement stmt = root.getSDb().getTemporaryDb().getStatement(SVNWCDbStatements.SELECT_REVERT_LIST_COPIED_CHILDREN);
            stmt.bindf("si", localRelpath, SVNWCUtils.relpathDepth(localRelpath));
            while(stmt.next()) {
                String relpath = getColumnText(stmt, REVERT_LIST__Fields.local_relpath);
                File childFile = SVNFileUtil.createFilePath(root.getAbsPath(), relpath);
                result.put(childFile, getColumnKind(stmt, REVERT_LIST__Fields.kind));
            }
            reset(stmt);
        } finally {
            root.getSDb().getTemporaryDb().commit();
        }
        
        return result;
    }
    
    public static Structure<RevertInfo> readRevertInfo(SVNWCContext context, File localAbsPath) throws SVNException {
        SVNWCDb db = (SVNWCDb) context.getDb();
        DirParsedInfo dirInfo = db.obtainWcRoot(localAbsPath);
        File localRelpath = dirInfo.localRelPath;
        SVNWCDbRoot root = dirInfo.wcDbDir.getWCRoot();

        root.getSDb().getTemporaryDb().beginTransaction(SqlJetTransactionMode.WRITE);
        Structure<RevertInfo> result = Structure.obtain(RevertInfo.class);
        result.set(RevertInfo.kind, SVNWCDbKind.Unknown);
        
        try {            
            SVNSqlJetStatement stmt = new SVNSqlJetSelectStatement(root.getSDb().getTemporaryDb(), SVNWCDbSchema.REVERT_LIST) {
                @Override
                protected ISqlJetCursor openCursor() throws SVNException {
                    try {
                        return super.openCursor().reverse();
                    } catch (SqlJetException e) {
                        SVNSqlJetDb.createSqlJetError(e);
                    }
                    return null;
                }
            };
            stmt.bindf("s", localRelpath);
            boolean haveRow = stmt.next();
            if (haveRow) {
                boolean isActual = getColumnBoolean(stmt, REVERT_LIST__Fields.actual);
                boolean anotherRow = false;
                if (isActual) {
                    result.set(RevertInfo.reverted, !isColumnNull(stmt, REVERT_LIST__Fields.notify));
                    if (!isColumnNull(stmt, REVERT_LIST__Fields.conflict_old)) {
                        String path = getColumnText(stmt, REVERT_LIST__Fields.conflict_old);
                        result.set(RevertInfo.conflictOld, SVNFileUtil.createFilePath(root.getAbsPath(), path));
                    }
                    if (!isColumnNull(stmt, REVERT_LIST__Fields.conflict_new)) {
                        String path = getColumnText(stmt, REVERT_LIST__Fields.conflict_new);
                        result.set(RevertInfo.conflictNew, SVNFileUtil.createFilePath(root.getAbsPath(), path));
                    }
                    if (!isColumnNull(stmt, REVERT_LIST__Fields.conflict_working)) {
                        String path = getColumnText(stmt, REVERT_LIST__Fields.conflict_working);
                        result.set(RevertInfo.conflictWorking, SVNFileUtil.createFilePath(root.getAbsPath(), path));
                    }
                    if (!isColumnNull(stmt, REVERT_LIST__Fields.prop_reject)) {
                        String path = getColumnText(stmt, REVERT_LIST__Fields.prop_reject);
                        result.set(RevertInfo.propReject, SVNFileUtil.createFilePath(root.getAbsPath(), path));
                    }
                    anotherRow = stmt.next();
                }
                if (!isActual || anotherRow) {
                    result.set(RevertInfo.reverted, true);
                    if (!isColumnNull(stmt, REVERT_LIST__Fields.op_depth)) {
                        long opDepth = getColumnInt64(stmt, REVERT_LIST__Fields.op_depth);
                        result.set(RevertInfo.copiedHere, opDepth == SVNWCUtils.relpathDepth(localRelpath));
                    }
                    result.set(RevertInfo.kind, getColumnKind(stmt, REVERT_LIST__Fields.kind));
                }
            }
            reset(stmt);
            if (haveRow) {
                stmt = new SVNSqlJetDeleteStatement(root.getSDb().getTemporaryDb(), SVNWCDbSchema.REVERT_LIST);
                stmt.bindf("s", localRelpath);
                stmt.done();
            }
            root.getSDb().getTemporaryDb().commit();
        } catch (SVNException e) {
            root.getSDb().getTemporaryDb().rollback();
            throw e;
        }
        return result;
    }

    public static void dropRevertList(SVNWCContext context, File localAbsPath) throws SVNException {
        SVNWCDb db = (SVNWCDb) context.getDb();
        DirParsedInfo dirInfo = db.obtainWcRoot(localAbsPath);
        SVNWCDbRoot root = dirInfo.wcDbDir.getWCRoot();
        
        SVNSqlJetStatement stmt = new SVNWCDbCreateSchema(root.getSDb(), SVNWCDbCreateSchema.DROP_REVERT_LIST, -1);
        stmt.done();
    }

    public static void notifyRevert(SVNWCContext context, File localAbsPath, ISVNEventHandler eventHandler) throws SVNException {
        SVNWCDb db = (SVNWCDb) context.getDb();
        DirParsedInfo dirInfo = db.obtainWcRoot(localAbsPath);
        File localRelpath = dirInfo.localRelPath;
        SVNWCDbRoot root = dirInfo.wcDbDir.getWCRoot();
        
        SVNSqlJetStatement stmt = new SVNSqlJetSelectStatement(root.getSDb().getTemporaryDb(), SVNWCDbSchema.REVERT_LIST) {

            @Override
            protected boolean isFilterPassed() throws SVNException {
                String rowPath = getColumnString(REVERT_LIST__Fields.local_relpath);
                String selectPath = (String) getBind(1);
                if (selectPath.equals(rowPath) || "".equals(selectPath) || rowPath.startsWith(selectPath + "/")) {
                    return !isColumnNull(REVERT_LIST__Fields.notify) || getColumnLong(REVERT_LIST__Fields.actual) == 0;
                }
                return false;
            }
            @Override
            protected Object[] getWhere() throws SVNException {
                return new Object[] {};
            }
        };
        stmt.bindf("s", localRelpath);
        
        File previousPath = null;
        while(stmt.next()) {
            File notifyRelPath = getColumnPath(stmt, REVERT_LIST__Fields.local_relpath);
            if (previousPath != null && notifyRelPath.equals(previousPath)) {
                continue;
            }
            previousPath = notifyRelPath;
            File notifyAbsPath = SVNFileUtil.createFilePath(root.getAbsPath(), notifyRelPath);
            eventHandler.handleEvent(SVNEventFactory.createSVNEvent(notifyAbsPath, SVNNodeKind.NONE, null, -1, SVNEventAction.REVERT, 
                    SVNEventAction.REVERT, null, null, -1, -1), -1);
        }
        reset(stmt);

        stmt = new SVNSqlJetDeleteStatement(root.getSDb().getTemporaryDb(), SVNWCDbSchema.REVERT_LIST) {
            @Override
            protected Object[] getWhere() throws SVNException {
                return new Object[0];
            }

            @Override
            protected boolean isFilterPassed() throws SVNException {
                String selectPath = (String) getBind(1);
                if ("".equals(selectPath)) {
                    return true;
                }
                String rowPath = getColumnString(REVERT_LIST__Fields.local_relpath);
                return rowPath.equals(selectPath) || rowPath.startsWith(selectPath + "/");
            }
        };
        stmt.bindf("s", localRelpath);
        stmt.done();
    }
}
