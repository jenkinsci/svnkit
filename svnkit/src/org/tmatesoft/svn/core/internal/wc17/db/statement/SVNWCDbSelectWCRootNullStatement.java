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
 * "select id from wcroot where local_abspath is null; "
 * 
 * @author TMate Software Ltd.
 */
public class SVNWCDbSelectWCRootNullStatement extends SVNSqlJetStatement {

    private ISqlJetTable table;

    public SVNWCDbSelectWCRootNullStatement(SVNSqlJetDb sDb) throws SVNException {
        super(sDb);
        try {
            table = sDb.getDb().getTable(SVNWCDbSchema.WCROOT.name());
        } catch (SqlJetException e) {
            SVNSqlJetDb.createSqlJetError(e);
        }
    }

    protected ISqlJetCursor openCursor() throws SVNException {
        try {
            return table.lookup(SVNWCDbSchema.WCROOT__Indices.I_LOCAL_ABSPATH.name(), null);
        } catch (SqlJetException e) {
            SVNSqlJetDb.createSqlJetError(e);
            return null;
        }
    }

}
