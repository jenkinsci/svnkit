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
package org.tmatesoft.svn.core.internal.db;

import java.util.Map;

import org.tmatesoft.sqljet.core.SqlJetException;
import org.tmatesoft.sqljet.core.SqlJetTransactionMode;
import org.tmatesoft.svn.core.SVNException;

/**
 * @version 1.4
 * @author TMate Software Ltd.
 */
public abstract class SVNSqlJetInsertStatement extends SVNSqlJetTableStatement {

    public SVNSqlJetInsertStatement(SVNSqlJetDb sDb, Enum tableName) throws SVNException {
        super(sDb, tableName);
        transactionMode = SqlJetTransactionMode.WRITE;
    }

    public long exec() throws SVNException {
        try {
            return table.insertByFieldNames(getInsertValues());
        } catch (SqlJetException e) {
            SVNSqlJetDb.createSqlJetError(e);
            return -1;
        }
    }

    protected abstract Map<String, Object> getInsertValues() throws SVNException;

}