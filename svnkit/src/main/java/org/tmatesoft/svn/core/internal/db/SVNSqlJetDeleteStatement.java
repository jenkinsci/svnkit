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

import org.tmatesoft.sqljet.core.SqlJetException;
import org.tmatesoft.sqljet.core.SqlJetTransactionMode;
import org.tmatesoft.svn.core.SVNException;

/**
 * @version 1.4
 * @author TMate Software Ltd.
 */
public class SVNSqlJetDeleteStatement extends SVNSqlJetSelectStatement {

    public SVNSqlJetDeleteStatement(SVNSqlJetDb sDb, Enum<?> fromTable) throws SVNException {
        super(sDb, fromTable);
        transactionMode = SqlJetTransactionMode.WRITE;
    }

    public long exec() throws SVNException {
        long n = 0;
        while (!eof()) {
            try {
                aboutToDeleteRow();
                getCursor().delete();
            } catch (SqlJetException e) {
                SVNSqlJetDb.createSqlJetError(e);
                return n;
            }
            n++;
        }
        try {
            updatePristine();
        } catch (SqlJetException e) {
            SVNSqlJetDb.createSqlJetError(e);
        }
        return n;
    }

}
