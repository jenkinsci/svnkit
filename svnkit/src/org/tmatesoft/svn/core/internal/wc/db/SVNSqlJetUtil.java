/*
 * ====================================================================
 * Copyright (c) 2004-2009 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html.
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.core.internal.wc.db;

import java.io.File;
import java.util.Map;

import org.tmatesoft.sqljet.core.SqlJetErrorCode;
import org.tmatesoft.sqljet.core.SqlJetException;
import org.tmatesoft.sqljet.core.SqlJetTransactionMode;
import org.tmatesoft.sqljet.core.internal.table.SqlJetCursor;
import org.tmatesoft.sqljet.core.table.ISqlJetCursor;
import org.tmatesoft.sqljet.core.table.ISqlJetRunnableWithLock;
import org.tmatesoft.sqljet.core.table.ISqlJetTransaction;
import org.tmatesoft.sqljet.core.table.SqlJetDb;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.util.SVNSkel;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.util.SVNLogType;


/**
 * @version 1.3
 * @author  TMate Software Ltd.
 */
public class SVNSqlJetUtil {

    public static Map getFieldProperties(ISqlJetCursor cursor, String fieldName) throws SqlJetException, SVNException {
        byte[] blobBytes = cursor.getBlobAsArray(fieldName);
        SVNSkel skel = SVNSkel.createAtom(blobBytes);
        
        return skel != null ? skel.parsePropList() : null;
    }
    
    public static SqlJetDb openDB(File dbFile, ISqlJetTransaction sqlTransaction, SqlJetTransactionMode mode, int latestSchema) throws SVNException {
        mode = mode == null ? SqlJetTransactionMode.WRITE : mode;
        SqlJetDb db = null;
        try {
            db = SqlJetDb.open(dbFile, true);
            checkFormat(db, sqlTransaction, latestSchema);
        } catch (SqlJetException e) {
            convertException(e);
        }
        return db;
    }

    
    public static void convertException(SqlJetException e) throws SVNException {
        SVNErrorManager.error(convertError(e), e, SVNLogType.WC);
    }
    
    public static SVNErrorMessage convertError(SqlJetException e) {
        SVNErrorMessage err = SVNErrorMessage.create(convertSQLJetErrorCodeToSVNErrorCode(e), e.getMessage());
        return err;
    }
    
    public static SVNErrorCode convertSQLJetErrorCodeToSVNErrorCode(SqlJetException e) {
        SqlJetErrorCode sqlCode = e.getErrorCode();
        if (sqlCode == SqlJetErrorCode.READONLY) {
            return SVNErrorCode.SQLITE_READONLY;
        } 
        return SVNErrorCode.SQLITE_ERROR;
    }

    private static void checkFormat(final SqlJetDb db, final ISqlJetTransaction sqlTransaction, final int latestSchema) throws SqlJetException {
        db.runWithLock(new ISqlJetRunnableWithLock() {
            public Object runWithLock(SqlJetDb db) throws SqlJetException {
                int version = db.getOptions().getUserVersion();
                if (version < latestSchema) {
                    db.runWriteTransaction(sqlTransaction);
                } else if (version > latestSchema) {
                    throw new SqlJetException("Schema format " + version + " not recognized");   
                }
                return null;
            }
        });
    }

}
