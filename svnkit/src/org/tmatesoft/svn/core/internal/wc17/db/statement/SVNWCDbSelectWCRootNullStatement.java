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
import org.tmatesoft.sqljet.core.internal.table.SqlJetCursor;
import org.tmatesoft.sqljet.core.internal.table.SqlJetTable;
import org.tmatesoft.sqljet.core.table.ISqlJetCursor;
import org.tmatesoft.sqljet.core.table.ISqlJetTable;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.wc17.db.SVNSqlJetDb;
import org.tmatesoft.svn.core.internal.wc17.db.SVNSqlJetStatement;

/**
 * "select id from wcroot where local_abspath is null; "
 * 
 * @author TMate Software Ltd.
 */
public class SVNWCDbSelectWCRootNullStatement extends SVNSqlJetStatement {

    private static final String WCROOT = "WCROOT";
    private static final String I_LOCAL_ABSPATH = "I_LOCAL_ABSPATH";

    private ISqlJetTable wcRootTable;

    public SVNWCDbSelectWCRootNullStatement(SVNSqlJetDb sDb) {
        super(sDb);
    }

    protected ISqlJetCursor openCursor() throws SVNException {
        try {
            if (wcRootTable == null) {
                wcRootTable = sDb.getDb().getTable(WCROOT);
            }
            return wcRootTable.lookup(I_LOCAL_ABSPATH, null);
        } catch (SqlJetException e) {
            SVNSqlJetDb.createSqlJetError(e);
            return null;
        }

    }

}
