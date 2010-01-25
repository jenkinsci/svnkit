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
package org.tmatesoft.svn.core.internal.wc.db;

import org.tmatesoft.sqljet.core.SqlJetException;
import org.tmatesoft.sqljet.core.SqlJetTransactionMode;
import org.tmatesoft.sqljet.core.table.ISqlJetTable;
import org.tmatesoft.sqljet.core.table.SqlJetDb;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.util.SVNLogType;


/**
 * @version 1.3
 * @author  TMate Software Ltd.
 */
public abstract class SVNDbCommand {
    
    private ISqlJetTable myTable;
    
    public Object runDbCommand(SqlJetDb sdb, SVNDbTables tableName, SqlJetTransactionMode txnMode, boolean rollBack) throws SVNException {
        try {
            sdb.beginTransaction(txnMode);
            try {
                myTable = tableName != null ? sdb.getTable(tableName.toString()) : null;
                return execCommand();
            } finally {
                sdb.commit();
            }    
        } catch (SqlJetException e) {
            convertException(sdb, rollBack, e);
        }
        return null;
    }

    protected ISqlJetTable getTable() {
        return myTable;
    }

    private void convertException(SqlJetDb sdb, boolean rollback, SqlJetException e) throws SVNException {
        if (rollback) {
            SqlJetException rollBackException = null;
            try {
                sdb.rollback();
            } catch (SqlJetException e1) {
                rollBackException = e1;
            }
                
            if (rollBackException != null) {
                SVNErrorMessage err = SVNSqlJetUtil.convertError(rollBackException);
                SVNErrorMessage originalErr = SVNSqlJetUtil.convertError(e);
                err.setChildErrorMessage(originalErr);
                SVNErrorManager.error(err, SVNLogType.WC);
            }
        }
        
        SVNSqlJetUtil.convertException(e);
    }

    protected abstract Object execCommand() throws SqlJetException, SVNException;
    
}
