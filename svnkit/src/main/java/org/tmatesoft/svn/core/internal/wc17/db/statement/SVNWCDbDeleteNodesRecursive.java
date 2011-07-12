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
import org.tmatesoft.svn.core.internal.db.SVNSqlJetDeleteStatement;

/**
 * DELETE FROM nodes
 * WHERE wc_id = ?1
 *  AND (?2 = ''
 *      OR local_relpath = ?2
 *      OR (local_relpath > ?2 || '/' AND local_relpath < ?2 || '0'))
 *  AND op_depth >= ?3
 *
 *
 * @version 1.4
 * @author TMate Software Ltd.
 */
public class SVNWCDbDeleteNodesRecursive extends SVNSqlJetDeleteStatement {

    public SVNWCDbDeleteNodesRecursive(SVNSqlJetDb sDb) throws SVNException {
        super(sDb, SVNWCDbSchema.NODES);
    }

    protected Object[] getWhere() throws SVNException {
        return new Object[] {getBind(1)}; // wc_id only
    }

    protected boolean isFilterPassed() throws SVNException {
        if (isColumnNull(SVNWCDbSchema.NODES__Fields.op_depth)) {
            return false;
        }
        if (isColumnNull(SVNWCDbSchema.NODES__Fields.local_relpath)) {
            return false;
        }
        long selectDepth = (Long) getBind(3);
        String selectPath = getBind(2).toString();
        if (getColumnLong(SVNWCDbSchema.NODES__Fields.op_depth) < selectDepth) {
            return false;
        }
        if ("".equals(selectPath)) {
            return true;
        }
        String rowPath = getColumnString(SVNWCDbSchema.NODES__Fields.local_relpath);
        return selectPath.equals(rowPath) || rowPath.startsWith(selectPath + '/');
    }

}
