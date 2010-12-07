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
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.db.SVNSqlJetDb;
import org.tmatesoft.svn.core.internal.db.SVNSqlJetStatement;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.wc17.db.ISVNWCDb;
import org.tmatesoft.svn.util.SVNLogType;

/**
 * @version 1.4
 * @author TMate Software Ltd.
 */
public class SVNWCDbCreateSchema extends SVNSqlJetStatement {

    private static final Statement[] statements = new Statement[] {
            new Statement(Type.TABLE, "CREATE TABLE REPOSITORY ( id INTEGER PRIMARY KEY AUTOINCREMENT, root  TEXT UNIQUE NOT NULL, uuid  TEXT NOT NULL ); "),
            new Statement(Type.INDEX, "CREATE INDEX I_UUID ON REPOSITORY (uuid); "),
            new Statement(Type.INDEX, "CREATE INDEX I_ROOT ON REPOSITORY (root); "),
            new Statement(Type.TABLE, "CREATE TABLE WCROOT ( id  INTEGER PRIMARY KEY AUTOINCREMENT, local_abspath  TEXT UNIQUE ); "),
            new Statement(Type.INDEX, "CREATE UNIQUE INDEX I_LOCAL_ABSPATH ON WCROOT (local_abspath); "),
            new Statement(Type.TABLE, "CREATE TABLE PRISTINE ( checksum  TEXT NOT NULL PRIMARY KEY, compression  INTEGER, size  INTEGER NOT NULL, "
                    + "  refcount  INTEGER NOT NULL, md5_checksum  TEXT NOT NULL ); "),
            new Statement(Type.TABLE, "CREATE TABLE ACTUAL_NODE ( wc_id  INTEGER NOT NULL REFERENCES WCROOT (id), local_relpath  TEXT NOT NULL, parent_relpath  TEXT, "
                    + "  properties  BLOB, conflict_old  TEXT, conflict_new  TEXT, conflict_working  TEXT, prop_reject  TEXT, changelist  TEXT, "
                    + "  text_mod  TEXT, tree_conflict_data  TEXT, conflict_data  BLOB, older_checksum  TEXT, left_checksum  TEXT, right_checksum  TEXT, PRIMARY KEY (wc_id, local_relpath) ); "),
            new Statement(Type.INDEX, "CREATE INDEX I_ACTUAL_PARENT ON ACTUAL_NODE (wc_id, parent_relpath); "),
            new Statement(Type.INDEX, "CREATE INDEX I_ACTUAL_CHANGELIST ON ACTUAL_NODE (changelist); "),
            new Statement(Type.TABLE, "CREATE TABLE LOCK ( repos_id  INTEGER NOT NULL REFERENCES REPOSITORY (id), repos_relpath  TEXT NOT NULL, lock_token  TEXT NOT NULL, "
                    + "  lock_owner  TEXT, lock_comment  TEXT, lock_date  INTEGER, PRIMARY KEY (repos_id, repos_relpath) ); "),
            new Statement(Type.TABLE, "CREATE TABLE WORK_QUEUE ( id  INTEGER PRIMARY KEY AUTOINCREMENT, work  BLOB NOT NULL ); "),
            new Statement(Type.TABLE, "CREATE TABLE WC_LOCK ( wc_id  INTEGER NOT NULL  REFERENCES WCROOT (id), local_dir_relpath  TEXT NOT NULL, "
                    + "  locked_levels  INTEGER NOT NULL DEFAULT -1, PRIMARY KEY (wc_id, local_dir_relpath) ); "),
            new Statement(Type.TABLE, "CREATE TABLE NODES ( wc_id  INTEGER NOT NULL REFERENCES WCROOT (id), local_relpath  TEXT NOT NULL, op_depth INTEGER NOT NULL, "
                    + "  parent_relpath  TEXT, repos_id  INTEGER REFERENCES REPOSITORY (id), repos_path  TEXT, revision  INTEGER, presence  TEXT NOT NULL, "
                    + "  moved_here  INTEGER, moved_to  TEXT, kind  TEXT NOT NULL, properties  BLOB, depth  TEXT, checksum  TEXT, symlink_target  TEXT, "
                    + "  changed_revision  INTEGER, changed_date INTEGER, changed_author TEXT, translated_size  INTEGER, last_mod_time  INTEGER, "
                    + "  dav_cache  BLOB, file_external  TEXT, PRIMARY KEY (wc_id, local_relpath, op_depth) ); "),
            new Statement(Type.INDEX, "CREATE INDEX I_NODES_PARENT ON NODES (wc_id, parent_relpath, op_depth); ")
    };

    private enum Type {
        TABLE, INDEX;
    }

    private static class Statement {

        private Type type;
        private String sql;

        public Statement(Type type, String sql) {
            this.type = type;
            this.sql = sql;
        }

        public Type getType() {
            return type;
        }

        public String getSql() {
            return sql;
        }
    }

    public SVNWCDbCreateSchema(SVNSqlJetDb sDb) {
        super(sDb);
    }

    public long exec() throws SVNException {
        try {
            sDb.getDb().getOptions().setUserVersion(ISVNWCDb.WC_FORMAT_17);
            for (Statement stmt : statements) {
                switch (stmt.getType()) {
                    case TABLE:
                        sDb.getDb().createTable(stmt.getSql());
                        break;

                    case INDEX:
                        sDb.getDb().createIndex(stmt.getSql());
                        break;

                    default:
                        SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.ASSERTION_FAIL, "Unknown statement type ''{0}''", stmt.getType().toString());
                        SVNErrorManager.error(err, SVNLogType.WC);
                        break;
                }
            }
        } catch (SqlJetException e) {
            e.printStackTrace();
        }
        return 0;
    }

}
