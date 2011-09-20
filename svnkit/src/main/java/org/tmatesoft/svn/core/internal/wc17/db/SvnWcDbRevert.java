package org.tmatesoft.svn.core.internal.wc17.db;

import java.io.File;

import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.db.SVNSqlJetDb;
import org.tmatesoft.svn.core.internal.db.SVNSqlJetStatement;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.wc17.SVNWCUtils;
import org.tmatesoft.svn.core.internal.wc17.db.statement.SVNWCDbStatements;
import org.tmatesoft.svn.core.internal.wc17.db.statement.SVNWCDbSchema.NODES__Fields;
import org.tmatesoft.svn.util.SVNLogType;

import static org.tmatesoft.svn.core.internal.wc17.db.SvnWcDbStatementUtil.*;

public class SvnWcDbRevert extends SvnWcDbShared {
    
    public static void revert(SVNWCDbRoot root, File localRelPath) throws SVNException {
        SVNSqlJetDb sdb = root.getSDb();
        
        SVNSqlJetStatement stmt = sdb.getStatement(SVNWCDbStatements.SELECT_NODE_INFO);
        stmt.bindf("is", root.getWcId(), localRelPath);
        if (!stmt.next()) {
            reset(stmt);
            stmt = sdb.getStatement(SVNWCDbStatements.DELETE_ACTUAL_NODE);
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
            stmt.bindf("isi", root.getWcId(), localRelPath, opDepth);
            stmt.done();
            stmt = sdb.getStatement(SVNWCDbStatements.DELETE_WORKING_NODE);
            stmt.bindf("is", root.getWcId(), localRelPath);
            stmt.done();
            stmt = sdb.getStatement(SVNWCDbStatements.DELETE_WC_LOCK_ORPHAN);
            stmt.bindf("is", root.getWcId(), localRelPath);
            stmt.done();
        }
        stmt = sdb.getStatement(SVNWCDbStatements.DELETE_ACTUAL_NODE_LEAVING_CHANGELIST);
        stmt.bindf("is", root.getWcId(), localRelPath);
        long affectedRows = stmt.done();
        if (affectedRows == 0) {
            stmt = sdb.getStatement(SVNWCDbStatements.CLEAR_ACTUAL_NODE_LEAVING_CHANGELIST);
            stmt.bindf("is", root.getWcId(), localRelPath);
            stmt.done();
        }
    }

    public static void revertRecursive(SVNWCDbRoot root, File localRelPath) throws SVNException {
        SVNSqlJetDb sdb = root.getSDb();
        
        SVNSqlJetStatement stmt = sdb.getStatement(SVNWCDbStatements.SELECT_NODE_INFO);
        stmt.bindf("is", root.getWcId(), localRelPath);
        if (!stmt.next()) {
            reset(stmt);
            stmt = sdb.getStatement(SVNWCDbStatements.DELETE_ACTUAL_NODE_RECURSIVE);
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
        stmt.bindf("isi", root.getWcId(), localRelPath, opDepth);
        stmt.done();
        stmt = sdb.getStatement(SVNWCDbStatements.DELETE_ACTUAL_NODE_LEAVING_CHANGELIST_RECURSIVE);
        stmt.bindf("is", root.getWcId(), localRelPath);
        stmt.done();
        stmt = sdb.getStatement(SVNWCDbStatements.CLEAR_ACTUAL_NODE_LEAVING_CHANGELIST_RECURSIVE);
        stmt.bindf("is", root.getWcId(), localRelPath);
        stmt.done();
        stmt = sdb.getStatement(SVNWCDbStatements.DELETE_WC_LOCK_ORPHAN_RECURSIVE);
        stmt.bindf("is", root.getWcId(), localRelPath);
        stmt.done();
    }
}
