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
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.util.SVNLogType;


/**
 *
 * @author  TMate Software Ltd.
 */
public class SVNSqlJetUpdateStatement extends SVNSqlJetSelectStatement {

    public SVNSqlJetUpdateStatement(SVNSqlJetDb sDb, Enum fromTable) throws SVNException {
        super(sDb, fromTable);
        transactionMode = SqlJetTransactionMode.WRITE;
    }

    public SVNSqlJetUpdateStatement(SVNSqlJetDb sDb, Enum fromTable, Enum indexName) throws SVNException {
        super(sDb, fromTable, indexName);
        transactionMode = SqlJetTransactionMode.WRITE;
    }

    public SVNSqlJetUpdateStatement(SVNSqlJetDb sDb, String fromTable) throws SVNException {
        super(sDb,fromTable);
        transactionMode = SqlJetTransactionMode.WRITE;
    }

    public void update(final Map<String, Object> values) throws SVNException {
        if(getCursor()==null){
            throw new UnsupportedOperationException();
        }
        try {
            getCursor().updateByFieldNames(values);
        } catch (SqlJetException e) {
            SVNSqlJetDb.createSqlJetError(e);
        }
    }

    public long exec() throws SVNException {
        long n=0;
        while (next()) {
            Map<String, Object> values = getUpdateValues();
            update(values);
            n++;
        }
        return n;
    }

    public Map<String, Object> getUpdateValues() throws SVNException {
        throw new UnsupportedOperationException();
    }

}
