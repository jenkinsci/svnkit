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

import org.tmatesoft.sqljet.core.SqlJetException;
import org.tmatesoft.sqljet.core.table.ISqlJetCursor;
import org.tmatesoft.sqljet.core.table.ISqlJetTable;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.wc17.db.SVNSqlJetDb;
import org.tmatesoft.svn.core.internal.wc17.db.SVNSqlJetStatement;
import org.tmatesoft.svn.core.internal.wc17.db.SVNWCDbSchema;

/**
 * "select repos_id, repos_relpath, presence, kind, revnum, checksum, " \
 * "  translated_size, changed_rev, changed_date, changed_author, depth, " \
 * "  symlink_target, last_mod_time, properties from base_node " \
 * "where wc_id = ?1 and local_relpath = ?2; "
 * 
 * @author TMate Software Ltd.
 */
public class SVNWCDbSelectBaseNodeStatement extends SVNSqlJetStatement {

    private ISqlJetTable table;

    public SVNWCDbSelectBaseNodeStatement(SVNSqlJetDb sDb) throws SVNException {
        super(sDb);
        try {
            table = sDb.getDb().getTable(SVNWCDbSchema.BASE_NODE.toString());
        } catch (SqlJetException e) {
            SVNSqlJetDb.createSqlJetError(e);
        }
    }

    protected ISqlJetCursor openCursor() throws SVNException {
        try {
            return table.lookup(null, binds.get(0), binds.get(1));
        } catch (SqlJetException e) {
            SVNSqlJetDb.createSqlJetError(e);
            return null;
        }
    }

}
