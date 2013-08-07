package org.tmatesoft.svn.core.internal.wc17.db;

import static org.tmatesoft.svn.core.internal.wc17.db.SvnWcDbStatementUtil.getColumnInt64;
import static org.tmatesoft.svn.core.internal.wc17.db.SvnWcDbStatementUtil.reset;

import java.io.File;
import java.util.Comparator;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;

import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.internal.db.SVNSqlJetDb;
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
import org.tmatesoft.svn.core.internal.wc17.db.SvnWcDbRevertList.RevertListRow;
import org.tmatesoft.svn.core.internal.wc17.db.statement.SVNWCDbCreateSchema;
import org.tmatesoft.svn.core.internal.wc17.db.statement.SVNWCDbSchema.NODES__Fields;
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
        long opDepth;
        try {
            stmt.bindf("is", root.getWcId(), localRelPath);
            if (!stmt.next()) {
                reset(stmt);

                stmt = sdb.getStatement(SVNWCDbStatements.DELETE_ACTUAL_NODE);
                long affectedRows;
                try {
                    ((SVNSqlJetTableStatement) stmt).addTrigger(actualNodesTableTrigger);

                    stmt.bindf("is", root.getWcId(), localRelPath);
                    affectedRows = stmt.done();
                } finally {
                    stmt.reset();
                }
                if (affectedRows > 0) {
                    stmt = sdb.getStatement(SVNWCDbStatements.SELECT_ACTUAL_CHILDREN_INFO);
                    try {
                        stmt.bindf("is", root.getWcId(), localRelPath);
                        if (stmt.next()) {
                            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_INVALID_OPERATION_DEPTH, "Can''t revert ''{0}'' without reverting children", root.getAbsPath(localRelPath));
                            SVNErrorManager.error(err, SVNLogType.WC);
                        }
                    } finally {
                        reset(stmt);
                    }
                    return;
                }
                nodeNotFound(root, localRelPath);
            }
            opDepth = getColumnInt64(stmt, NODES__Fields.op_depth);
        } finally {
            reset(stmt);
        }
        if (opDepth > 0 && opDepth == SVNWCUtils.relpathDepth(localRelPath)) {

            boolean haveRow;
            stmt = sdb.getStatement(SVNWCDbStatements.SELECT_GE_OP_DEPTH_CHILDREN);
            try {
                stmt.bindf("isi", root.getWcId(), localRelPath, opDepth);
                haveRow = stmt.next();
            } finally {
                reset(stmt);
            }
            if (haveRow) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_INVALID_OPERATION_DEPTH, "Can''t revert ''{0}'' without reverting children", root.getAbsPath(localRelPath));
                SVNErrorManager.error(err, SVNLogType.WC);
            }
            stmt = sdb.getStatement(SVNWCDbStatements.SELECT_ACTUAL_CHILDREN_INFO);
            try {
                stmt.bindf("is", root.getWcId(), localRelPath);
                haveRow = stmt.next();
            } finally {
                reset(stmt);
            }
            if (haveRow) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_INVALID_OPERATION_DEPTH, "Can''t revert ''{0}'' without reverting children", root.getAbsPath(localRelPath));
                SVNErrorManager.error(err, SVNLogType.WC);
            }

            stmt = sdb.getStatement(SVNWCDbStatements.UPDATE_OP_DEPTH_INCREASE_RECURSIVE);
            try {
                ((SVNSqlJetTableStatement) stmt).addTrigger(nodesTableTrigger);

                stmt.bindf("isi", root.getWcId(), localRelPath, opDepth);
                stmt.done();
            } finally {
                stmt.reset();
            }

            stmt = sdb.getStatement(SVNWCDbStatements.DELETE_WORKING_NODE);
            try {
                ((SVNSqlJetTableStatement) stmt).addTrigger(nodesTableTrigger);

                stmt.bindf("is", root.getWcId(), localRelPath);
                stmt.done();
            } finally {
                stmt.reset();
            }
            stmt = sdb.getStatement(SVNWCDbStatements.DELETE_WC_LOCK_ORPHAN);
            try {
                stmt.bindf("is", root.getWcId(), localRelPath);
                stmt.done();
            } finally {
                stmt.reset();
            }
        }
        long affectedRows;
        stmt = sdb.getStatement(SVNWCDbStatements.DELETE_ACTUAL_NODE_LEAVING_CHANGELIST);
        try {
            ((SVNSqlJetTableStatement) stmt).addTrigger(actualNodesTableTrigger);
            stmt.bindf("is", root.getWcId(), localRelPath);

            affectedRows = stmt.done();
        } finally {
            stmt.reset();
        }
        if (affectedRows == 0) {
            stmt = sdb.getStatement(SVNWCDbStatements.CLEAR_ACTUAL_NODE_LEAVING_CHANGELIST);
            try {
                ((SVNSqlJetTableStatement) stmt).addTrigger(actualNodesTableTrigger);

                stmt.bindf("is", root.getWcId(), localRelPath);
                stmt.done();
            } finally {
                stmt.reset();
            }
        }
    }

    public static void revertRecursive(SVNWCDbRoot root, File localRelPath) throws SVNException {
        SVNSqlJetDb sdb = root.getSDb();
        SvnRevertNodesTrigger nodesTableTrigger = new SvnRevertNodesTrigger(sdb);
        SvnRevertActualNodesTrigger actualNodesTableTrigger = new SvnRevertActualNodesTrigger(sdb);

        SVNSqlJetStatement stmt = sdb.getStatement(SVNWCDbStatements.SELECT_NODE_INFO);
        long opDepth;
        try {
            stmt.bindf("is", root.getWcId(), localRelPath);
            if (!stmt.next()) {
                reset(stmt);
                long affectedRows;
                stmt = sdb.getStatement(SVNWCDbStatements.DELETE_ACTUAL_NODE_RECURSIVE);
                try {
                    ((SVNSqlJetTableStatement) stmt).addTrigger(actualNodesTableTrigger);

                    stmt.bindf("is", root.getWcId(), localRelPath);
                    affectedRows = stmt.done();
                } finally {
                    stmt.reset();
                }
                if (affectedRows > 0) {
                    return;
                }
                nodeNotFound(root, localRelPath);
            }
            opDepth = getColumnInt64(stmt, NODES__Fields.op_depth);
        } finally {
            reset(stmt);
        }
        if (opDepth > 0 && opDepth != SVNWCUtils.relpathDepth(localRelPath)) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_INVALID_OPERATION_DEPTH, "Can''t revert ''{0}'' without reverting parent", root.getAbsPath(localRelPath));
            SVNErrorManager.error(err, SVNLogType.WC);
        }
        if (opDepth == 0) {
            opDepth = 1;
        }

        stmt = sdb.getStatement(SVNWCDbStatements.DELETE_NODES_RECURSIVE);
        try {
            ((SVNSqlJetTableStatement) stmt).addTrigger(nodesTableTrigger);
            stmt.bindf("isi", root.getWcId(), localRelPath, opDepth);
            stmt.done();
        } finally {
            stmt.reset();
        }

        stmt = sdb.getStatement(SVNWCDbStatements.DELETE_ACTUAL_NODE_LEAVING_CHANGELIST_RECURSIVE);
        try {
            ((SVNSqlJetTableStatement) stmt).addTrigger(actualNodesTableTrigger);
            stmt.bindf("is", root.getWcId(), localRelPath);
            stmt.done();
        } finally {
            stmt.reset();
        }

        stmt = sdb.getStatement(SVNWCDbStatements.CLEAR_ACTUAL_NODE_LEAVING_CHANGELIST_RECURSIVE);
        try {
            ((SVNSqlJetTableStatement) stmt).addTrigger(actualNodesTableTrigger);
            stmt.bindf("is", root.getWcId(), localRelPath);
            stmt.done();
        } finally {
            stmt.reset();
        }

        stmt = sdb.getStatement(SVNWCDbStatements.DELETE_WC_LOCK_ORPHAN_RECURSIVE);
        try {
            stmt.bindf("is", root.getWcId(), localRelPath);
            stmt.done();
        } finally {
            stmt.reset();
        }
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

        final SvnWcDbRevertList revertList = root.getSDb().getRevertList();
        final long selectDepth = SVNWCUtils.relpathDepth(localRelpath);
        final String selectPath = SVNFileUtil.getFilePath(localRelpath);
        for(Iterator<RevertListRow> rows = revertList.rows(); rows.hasNext();) {
            final RevertListRow row = rows.next();
            if (row.reposId == 0) {
                continue;
            }
            if ("".equals(row.localRelpath)) {
                continue;
            }
            if ("".equals(selectPath) || row.localRelpath.startsWith(selectPath + "/")) {
                if (row.opDepth >= selectDepth) {
                    File childFile = SVNFileUtil.createFilePath(root.getAbsPath(), row.localRelpath);
                    result.put(childFile, SvnWcDbStatementUtil.getKindForString(row.kind));
                }
            }
        }
        return result;
    }
    
    public static Structure<RevertInfo> readRevertInfo(SVNWCContext context, File localAbsPath) throws SVNException {
        SVNWCDb db = (SVNWCDb) context.getDb();
        DirParsedInfo dirInfo = db.obtainWcRoot(localAbsPath);
        File localRelpath = dirInfo.localRelPath;
        SVNWCDbRoot root = dirInfo.wcDbDir.getWCRoot();

        Structure<RevertInfo> result = Structure.obtain(RevertInfo.class);
        result.set(RevertInfo.kind, SVNWCDbKind.Unknown);
        result.set(RevertInfo.reverted, false);
        result.set(RevertInfo.copiedHere, false);
        
        RevertListRow row = root.getSDb().getRevertList().getActualRow(SVNFileUtil.getFilePath(localRelpath));
        row = row == null ? root.getSDb().getRevertList().getRow(SVNFileUtil.getFilePath(localRelpath)) : row;
        if (row != null) {
            if (row.actual != 0) {
                result.set(RevertInfo.reverted, row.notify != 0);
                if (row.conflictOld != null) {
                    result.set(RevertInfo.conflictOld, SVNFileUtil.createFilePath(root.getAbsPath(), row.conflictOld));
                }
                if (row.conflictNew != null) {
                    result.set(RevertInfo.conflictNew, SVNFileUtil.createFilePath(root.getAbsPath(), row.conflictNew));
                }
                if (row.conflictWorking != null) {
                    result.set(RevertInfo.conflictWorking, SVNFileUtil.createFilePath(root.getAbsPath(), row.conflictWorking));
                }
                if (row.propReject != null) {
                    result.set(RevertInfo.propReject, SVNFileUtil.createFilePath(root.getAbsPath(), row.propReject));
                }
                row = root.getSDb().getRevertList().getRow(SVNFileUtil.getFilePath(localRelpath));
            }
            if (row != null) {
                result.set(RevertInfo.reverted, true);
                if (row.reposId != 0) {
                    result.set(RevertInfo.copiedHere, row.opDepth == SVNWCUtils.relpathDepth(localRelpath));
                }
                result.set(RevertInfo.kind, SvnWcDbStatementUtil.getKindForString(row.kind));
                
            }
            root.getSDb().getRevertList().deleteRow((SVNFileUtil.getFilePath(localRelpath)));
        }
        return result;
    }

    public static void dropRevertList(SVNWCContext context, File localAbsPath) throws SVNException {
        SVNWCDb db = (SVNWCDb) context.getDb();
        DirParsedInfo dirInfo = db.obtainWcRoot(localAbsPath);
        SVNWCDbRoot root = dirInfo.wcDbDir.getWCRoot();
        
        SVNSqlJetStatement stmt = new SVNWCDbCreateSchema(root.getSDb(), SVNWCDbCreateSchema.DROP_REVERT_LIST, -1);
        try {
            stmt.done();
        } finally {
            stmt.reset();
        }
    }

    public static void notifyRevert(SVNWCContext context, File localAbsPath, ISVNEventHandler eventHandler) throws SVNException {
        SVNWCDb db = (SVNWCDb) context.getDb();
        DirParsedInfo dirInfo = db.obtainWcRoot(localAbsPath);
        String localRelpath = SVNFileUtil.getFilePath(dirInfo.localRelPath);
        SVNWCDbRoot root = dirInfo.wcDbDir.getWCRoot();
        
        final SvnWcDbRevertList revertList = root.getSDb().getRevertList();
        File previousPath = null;
        if (eventHandler != null) {
            for(Iterator<RevertListRow> rows = revertList.rows(); rows.hasNext();) {
                final RevertListRow row = rows.next();
                final String rowPath = row.localRelpath;
                if (localRelpath.equals(rowPath) || "".equals(localRelpath) || rowPath.startsWith(localRelpath + "/")) {
                    if (!(row.notify != 0 || row.actual == 0)) {
                        continue;
                    }
                } else {
                    continue;
                }
                final File notifyRelPath = SVNFileUtil.createFilePath(rowPath);
                if (previousPath != null && notifyRelPath.equals(previousPath)) {
                    continue;
                }
                previousPath = notifyRelPath;
                final File notifyAbsPath = SVNFileUtil.createFilePath(root.getAbsPath(), notifyRelPath);
                eventHandler.handleEvent(SVNEventFactory.createSVNEvent(notifyAbsPath, SVNNodeKind.NONE, null, -1, SVNEventAction.REVERT, 
                        SVNEventAction.REVERT, null, null, -1, -1), -1);
                
            }
        }
        for(Iterator<RevertListRow> rows = revertList.rows(); rows.hasNext();) {
            final RevertListRow row = rows.next();
            final String rowPath = row.localRelpath;
            if ("".equals(localRelpath)) {
                revertList.deleteRow(rowPath);
            } else if (rowPath.equals(localRelpath) || rowPath.startsWith(localRelpath + "/")){
                revertList.deleteRow(rowPath);
            }
            
        }
    }
}
