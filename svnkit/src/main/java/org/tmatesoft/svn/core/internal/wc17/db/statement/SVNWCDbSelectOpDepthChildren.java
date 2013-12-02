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
package org.tmatesoft.svn.core.internal.wc17.db.statement;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.db.SVNSqlJetDb;
import org.tmatesoft.svn.core.internal.db.SVNSqlJetSelectFieldsStatement;
import org.tmatesoft.svn.core.internal.wc17.db.statement.SVNWCDbSchema.NODES__Fields;

/**
 * SELECT local_relpath FROM nodes WHERE wc_id = ?1 AND parent_relpath = ?2 AND
 * op_depth = ?3;
 *
 * @version 1.4
 * @author TMate Software Ltd.
 */
public class SVNWCDbSelectOpDepthChildren extends SVNSqlJetSelectFieldsStatement<NODES__Fields> {

    public SVNWCDbSelectOpDepthChildren(SVNSqlJetDb sDb) throws SVNException {
        super(sDb, SVNWCDbSchema.NODES, SVNWCDbSchema.NODES__Indices.I_NODES_PARENT);
    }

    protected void defineFields() {
        fields.add(SVNWCDbSchema.NODES__Fields.local_relpath);
    }

    protected boolean isFilterPassed() throws SVNException {
        if (getBind(3) != null) {
            final long opDepth = (Long) getBind(3);
            if (opDepth != getColumnLong(NODES__Fields.op_depth)) {
                return false;
            }
        }
        if ("base-deleted".equals(getColumn(NODES__Fields.presence))) {
            return false;
        }
        if (getColumn(SVNWCDbSchema.NODES__Fields.file_external) != null) {
            return false;
        }
        return true;
    }

    protected Object[] getWhere() throws SVNException {
        return new Object[] {getBind(1), getBind(2)};
    }

}
