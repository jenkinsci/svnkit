package org.tmatesoft.svn.core.internal.wc17.db.statement;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.db.SVNSqlJetDb;
import org.tmatesoft.svn.core.internal.db.SVNSqlJetSelectFieldsStatement;

/**
 * SELECT local_relpath, moved_to, op_depth FROM nodes
 * WHERE wc_id = ?1
 *  AND (local_relpath = ?2 OR IS_STRICT_DESCENDANT_OF(local_relpath, ?2))
 *  AND moved_to IS NOT NULL
 *  AND op_depth >= (SELECT MAX(op_depth) FROM nodes o
 *                    WHERE o.wc_id = ?1
 *                        AND o.local_relpath = ?2)
 *
 * @version 1.8
 */
public class SVNWCDbSelectMovedForDelete extends SVNSqlJetSelectFieldsStatement<SVNWCDbSchema.NODES__Fields> {

    private long maxOpDepth;

    public SVNWCDbSelectMovedForDelete(SVNSqlJetDb sDb) throws SVNException {
        super(sDb, SVNWCDbSchema.NODES);
        maxOpDepth = -1;
    }

    @Override
    protected void defineFields() {
        fields.add(SVNWCDbSchema.NODES__Fields.local_relpath);
        fields.add(SVNWCDbSchema.NODES__Fields.moved_to);
        fields.add(SVNWCDbSchema.NODES__Fields.op_depth);
    }

    @Override
    public void reset() throws SVNException {
        maxOpDepth = -1;
        super.reset();
    }

    @Override
    protected String getPathScope() {
        return (String) getBind(2);
    }

    @Override
    protected boolean isStrictiDescendant() {
        return false;
    }

    @Override
    protected boolean isFilterPassed() throws SVNException {
        return !isColumnNull(SVNWCDbSchema.NODES__Fields.moved_to) && getColumnLong(SVNWCDbSchema.NODES__Fields.op_depth) >= getMaxOpDepth();
    }

    private long getMaxOpDepth() throws SVNException {
        if (maxOpDepth == -1) {
            SVNWCDbNodesMaxOpDepth maxOpDepth = new SVNWCDbNodesMaxOpDepth(sDb, 0);
            try {
                this.maxOpDepth = maxOpDepth.getMaxOpDepth((Long)getBind(1), (String) getBind(2));
            } finally {
                maxOpDepth.reset();
            }
        }
        return maxOpDepth;
    }

    @Override
    protected Object[] getWhere() throws SVNException {
        return new Object[]{getBind(1)};
    }
}
