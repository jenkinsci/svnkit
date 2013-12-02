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

import java.util.HashMap;
import java.util.Map;

import org.tmatesoft.sqljet.core.SqlJetException;
import org.tmatesoft.sqljet.core.schema.SqlJetConflictAction;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.db.SVNSqlJetDb;
import org.tmatesoft.svn.core.internal.db.SVNSqlJetInsertStatement;
import org.tmatesoft.svn.core.internal.db.SVNSqlJetSelectStatement;
import org.tmatesoft.svn.core.internal.wc17.db.ISVNWCDb;
import org.tmatesoft.svn.core.internal.wc17.db.SvnWcDbStatementUtil;

/**
 * INSERT OR REPLACE INTO nodes (
 *    wc_id, local_relpath, op_depth,
 *    parent_relpath, presence, kind)
 * VALUES(?1, ?2, ?3, ?4, 'base-deleted', ?5)
 *
 * @version 1.8
 * @author TMate Software Ltd.
 */
public class SVNWCDbInstallWorkingNodeForDelete extends SVNSqlJetInsertStatement {

    private SVNSqlJetSelectStatement select;

    public SVNWCDbInstallWorkingNodeForDelete(SVNSqlJetDb sDb) throws SVNException {
        super(sDb, SVNWCDbSchema.NODES);
        select = new SVNSqlJetSelectStatement(sDb, SVNWCDbSchema.NODES);
    }

    public long exec() throws SVNException {
        select.bindf("isi", getBind(1), getBind(2), 0);
        try {
            int n = 0;
            while (select.next()) {
                try {
                    table.insertByFieldNamesOr(SqlJetConflictAction.REPLACE, getInsertValues());
                    n++;
                } catch (SqlJetException e) {
                    SVNSqlJetDb.createSqlJetError(e);
                    return -1;
                }
            }
            return n;
        } finally {
            select.reset();
        }
    }

    protected Map<String, Object> getInsertValues() throws SVNException {
        Map<String, Object> insertValues = new HashMap<String, Object>();
        insertValues.put(SVNWCDbSchema.NODES__Fields.wc_id.toString(), getBind(1));
        insertValues.put(SVNWCDbSchema.NODES__Fields.local_relpath.toString(), getBind(2));
        insertValues.put(SVNWCDbSchema.NODES__Fields.op_depth.toString(), getBind(3));
        insertValues.put(SVNWCDbSchema.NODES__Fields.parent_relpath.toString(), getBind(4));
        insertValues.put(SVNWCDbSchema.NODES__Fields.presence.toString(), SvnWcDbStatementUtil.getPresenceText(ISVNWCDb.SVNWCDbStatus.BaseDeleted));
        insertValues.put(SVNWCDbSchema.NODES__Fields.kind.toString(), getBind(5));
        return insertValues;
    }

}
