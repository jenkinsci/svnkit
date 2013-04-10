package org.tmatesoft.svn.core.internal.wc17.db.statement;

import org.tmatesoft.sqljet.core.SqlJetException;
import org.tmatesoft.sqljet.core.table.ISqlJetCursor;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.db.SVNSqlJetDb;
import org.tmatesoft.svn.core.internal.db.SVNSqlJetSelectStatement;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.wc17.db.statement.SVNWCDbSchema.NODES__Fields;
import org.tmatesoft.svn.util.SVNLogType;

public class SVNWCDbSelectWCRootNodes extends SVNSqlJetSelectStatement {

    public SVNWCDbSelectWCRootNodes(SVNSqlJetDb sDb) throws SVNException {
        super(sDb, SVNWCDbSchema.NODES);
    }

    @Override
    protected boolean isFilterPassed() throws SVNException {
        final String localRelpath = getColumnString(NODES__Fields.local_relpath);
        final String reposRelpath = getColumnString(NODES__Fields.repos_path);
        if ("".equals(localRelpath) && !"".equals(reposRelpath)) {
            return true;
        } else if (!"".equals(localRelpath)) {
            if (getColumnLong(NODES__Fields.op_depth) != 0) {
                return false;
            }
            final long wcId = getColumnLong(NODES__Fields.wc_id);
            final String localParentRelpath = getColumnString(NODES__Fields.parent_relpath);
            final String childName = localRelpath.substring(localParentRelpath.length() + 1);
            final String expectedChildReposPath = SVNPathUtil.append(getNodeReposRelpath(wcId, localParentRelpath), childName);
            if (!expectedChildReposPath.equals(reposRelpath)) {
                return true;
            }
        }
        return false;
    }

    private String getNodeReposRelpath(long wcId, String path) throws SVNException {
        ISqlJetCursor cursor = null;
        try {
            cursor = getTable().lookup(null, wcId, path);
            if (!cursor.eof()) {
                return cursor.getString(NODES__Fields.repos_path.toString());
            }
        } catch (SqlJetException e) {
            SVNSqlJetDb.createSqlJetError(e);
        } finally {
            if (cursor != null) {
                try {
                    cursor.close();
                } catch (SqlJetException e) {
                }
            }
        }
        SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_PATH_NOT_FOUND, "Node ''{0}'' not found.", path);
        SVNErrorManager.error(err, SVNLogType.WC);
        return null;
    }
    
    

}
