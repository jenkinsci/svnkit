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
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.Map;

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
    private Map<SVNWCDbStatements, SVNSqlJetStatement> statements;

    private SVNSqlJetDb(SqlJetDb db) {
        this.db = db;
        statements = new HashMap<SVNWCDbStatements, SVNSqlJetStatement>();
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

    public SVNSqlJetStatement getStatement(SVNWCDbStatements statementIndex) throws SVNException {
        assert (statementIndex != null);
        SVNSqlJetStatement stmt = statements.get(statementIndex);
        if (stmt == null) {
            stmt = prepareStatement(statementIndex);
            statements.put(statementIndex, stmt);
        }
        if (stmt != null && stmt.isNeedsReset()) {
            stmt.reset();
        }
        return stmt;
    }

    private SVNSqlJetStatement prepareStatement(SVNWCDbStatements statementIndex) {
        final Class<? extends SVNSqlJetStatement> statementClass = statementIndex.getStatementClass();
        if (statementClass == null) {
            return null;
        }
        try {
            final Constructor<? extends SVNSqlJetStatement> constructor = statementClass.getConstructor(SVNSqlJetDb.class);
            final SVNSqlJetStatement stmt = constructor.newInstance(this);
            return stmt;
        } catch (Exception e) {
            // TODO
            e.printStackTrace();
            return null;
        }
    }

    public void execStatement(SVNWCDbStatements statementIndex) {
    }

    public long fetchWCId() throws SVNException {
        /*
         * ### cheat. we know there is just one WORKING_COPY row, and it has a
         * ### NULL value for local_abspath.
         */
        final SVNSqlJetStatement stmt = getStatement(SVNWCDbStatements.SELECT_WCROOT_NULL);
        try {
            final boolean have_row = stmt.next();
            if (!have_row) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_CORRUPT, "Missing a row in WCROOT.");
                SVNErrorManager.error(err, SVNLogType.WC);
            }
            // assert (!stmt.isColumnNull("id"));
            return stmt.getColumnLong("id");
        } finally {
            stmt.reset();
        }
    }

    public int upgrade(File absPath, int format) {
        return 0;
    }

    public void verifyNoWork() {
    }

    public static void createSqlJetError(SqlJetException e) throws SVNException {
        SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.SQLITE_ERROR, e);
        SVNErrorManager.error(err, SVNLogType.WC);
    }

}
