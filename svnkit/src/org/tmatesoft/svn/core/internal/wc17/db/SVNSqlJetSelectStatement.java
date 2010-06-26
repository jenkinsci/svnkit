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
package org.tmatesoft.svn.core.internal.wc17.db;

import java.util.Arrays;
import java.util.Collections;

import org.tmatesoft.sqljet.core.SqlJetException;
import org.tmatesoft.sqljet.core.table.ISqlJetCursor;
import org.tmatesoft.sqljet.core.table.ISqlJetTable;
import org.tmatesoft.svn.core.SVNException;

/**
 * @version 1.3
 * @author TMate Software Ltd.
 */
public class SVNSqlJetSelectStatement extends SVNSqlJetStatement {

    private ISqlJetTable table;

    public SVNSqlJetSelectStatement(SVNSqlJetDb sDb, Enum fromTable) throws SVNException {
        this(sDb, fromTable.toString());
    }

    public SVNSqlJetSelectStatement(SVNSqlJetDb sDb, String fromTable) throws SVNException {
        super(sDb);
        try {
            table = sDb.getDb().getTable(fromTable);
        } catch (SqlJetException e) {
            SVNSqlJetDb.createSqlJetError(e);
        }
    }

    protected ISqlJetCursor openCursor() throws SVNException {
        try {
            return table.lookup(getIndexName(), getWhere());
        } catch (SqlJetException e) {
            SVNSqlJetDb.createSqlJetError(e);
            return null;
        }
    }

    protected String getIndexName() {
        return null;
    }

    protected Object[] getWhere() {
        if (binds.size() == 0) {
            return null;
        }
        return binds.toArray();
    }

}
