package org.tmatesoft.svn.core.internal.wc17.db.statement;

import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.db.SVNSqlJetDb;
import org.tmatesoft.svn.core.internal.db.SVNSqlJetSelectStatement;
import org.tmatesoft.svn.core.internal.wc17.db.statement.SVNWCDbSchema.NODES__Fields;

/**
 * SELECT local_relpath, repos_path FROM nodes
 * WHERE wc_id = ?1
 * AND local_relpath = ?2
 * AND op_depth = 0
 * AND (inherited_props not null)
 *
 */
public class SVNWCDbSelectIPropsNode extends SVNSqlJetSelectStatement {

    private SVNDepth depth;

    public SVNWCDbSelectIPropsNode(SVNSqlJetDb sDb) throws SVNException {
        super(sDb, SVNWCDbSchema.NODES);
        setDepth(SVNDepth.EMPTY);
    }
    
    public void setDepth(SVNDepth depth) {
        if (depth == SVNDepth.UNKNOWN) {
            depth = SVNDepth.INFINITY;
        }
        this.depth = depth;
    }

    @Override
    protected String getIndexName() {
        if (depth == SVNDepth.IMMEDIATES || depth == SVNDepth.FILES) {
            return SVNWCDbSchema.NODES__Indices.I_NODES_PARENT.toString();
        }
        return super.getIndexName();
    }

    @Override
    protected Object[] getWhere() throws SVNException {
        if (depth == SVNDepth.EMPTY || depth == SVNDepth.IMMEDIATES || depth == SVNDepth.FILES) {
            return new Object[] {getBind(1), getBind(2)};
        }
        return new Object[] {getBind(1)};
    }

    @Override
    protected boolean isFilterPassed() throws SVNException {
        if (getColumnLong(NODES__Fields.op_depth) != 0) {
            return false;
        }
        if (depth == SVNDepth.INFINITY) {
            final String selectPath = (String) getBind(2);
            final String rowPath = getColumnString(NODES__Fields.local_relpath);
            if (!rowPath.startsWith(selectPath + "/")) {
                return false;
            } 
        } 
        return getColumnBlob(SVNWCDbSchema.NODES__Fields.inherited_props) != null;
    }
}
