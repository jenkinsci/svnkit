package org.tmatesoft.svn.core.internal.wc17.db;

import static org.tmatesoft.svn.core.internal.wc17.db.SvnWcDbStatementUtil.reset;

import java.io.File;
import java.util.Collection;

import org.tmatesoft.sqljet.core.SqlJetTransactionMode;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.db.SVNSqlJetDb;
import org.tmatesoft.svn.core.internal.db.SVNSqlJetStatement;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.wc17.db.statement.SVNWCDbCollectTargets;
import org.tmatesoft.svn.core.internal.wc17.db.statement.SVNWCDbCreateSchema;
import org.tmatesoft.svn.core.internal.wc17.db.statement.SVNWCDbInsertTarget;
import org.tmatesoft.svn.util.SVNLogType;

public class SvnWcDbShared {
    
    public static void begingReadTransaction(SVNWCDbRoot root) throws SVNException {
        root.getSDb().beginTransaction(SqlJetTransactionMode.READ_ONLY);
    }
    
    public static void commit(SVNWCDbRoot root) throws SVNException {
        root.getSDb().commit();
    }
    
    protected static void nodeNotFound(File absolutePath) throws SVNException {
        SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_PATH_NOT_FOUND, "The node ''{0}'' was not found.", absolutePath);
        SVNErrorManager.error(err, SVNLogType.WC);
    }
    
    protected static void collectTargets(SVNWCDbRoot root, File relpath, SVNDepth depth, Collection<String> changelists) throws SVNException {
        SVNSqlJetDb tmpDb = root.getSDb().getTemporaryDb();
        SVNSqlJetStatement stmt = null;
        try {
            stmt = new SVNWCDbCreateSchema(tmpDb, SVNWCDbCreateSchema.TARGETS_LIST, -1);
            stmt.done();
            stmt  = new SVNWCDbInsertTarget(tmpDb, new SVNWCDbCollectTargets(root.getSDb(), root.getWcId(), relpath, depth, changelists));
            stmt.done();
            if (depth == SVNDepth.FILES || depth == SVNDepth.IMMEDIATES) {
                stmt  = new SVNWCDbInsertTarget(tmpDb, new SVNWCDbCollectTargets(root.getSDb(), root.getWcId(), relpath, SVNDepth.EMPTY, changelists));
                stmt.done();
            }
        } finally {
            reset(stmt);
        }
    }
}
