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

import java.io.File;

import org.tmatesoft.sqljet.core.SqlJetException;
import org.tmatesoft.sqljet.core.table.SqlJetDb;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.util.SVNLogType;

/**
 * @author TMate Software Ltd.
 */
public class SVNSqlJetDb {

    public static enum Mode {
        /** open the database read-only */
        ReadOnly,
        /** open the database read-write */
        ReadWrite,
        /** open/create the database read-write */
        RWCreate
    };

    private SqlJetDb db;

    private SVNSqlJetDb(SqlJetDb db) {
        this.db = db;
    }

    public SqlJetDb getDb() {
        return db;
    }

    public void close() throws SqlJetException {
        if (db != null) {
            db.close();
        }
    }

    public static SVNSqlJetDb open(File sdbAbsPath, Mode mode) throws SVNException, SqlJetException {
        if (mode != Mode.RWCreate) {
            if (!sdbAbsPath.exists()) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_NOT_FOUND, "File not found ''{0}''", sdbAbsPath);
                SVNErrorManager.error(err, SVNLogType.WC);
            }
        }
        final SqlJetDb db = SqlJetDb.open(sdbAbsPath, mode != Mode.ReadOnly);
        SVNSqlJetDb sDb = new SVNSqlJetDb(db);
        return sDb;
    }

    public SVNSqlJetStatement getStatement(SVNWCDbStatements statementIndex) {
        return null;
    }

    public void execStatement(SVNWCDbStatements statementIndex) {
    }

    public long fetchWCId() throws SVNException {
        return 0;
    }

    public int readSchemaVersion() {
        return 0;
    }

    public int upgrade(File absPath, int format) {
        return 0;
    }

    public void verifyNoWork() {
    }

}
