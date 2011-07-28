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
import org.tmatesoft.sqljet.core.table.ISqlJetTransaction;
import org.tmatesoft.sqljet.core.table.SqlJetDb;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.db.SVNSqlJetDb;
import org.tmatesoft.svn.core.internal.db.SVNSqlJetStatement;
import org.tmatesoft.svn.core.internal.wc17.db.ISVNWCDb;

/**
 * @version 1.4
 * @author TMate Software Ltd.
 */
public class SVNWCDbCreateSchema extends SVNSqlJetStatement {

    public static final Statement[] MAIN_DB_STATEMENTS = new Statement[] {
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
            new Statement(Type.INDEX, "CREATE INDEX I_NODES_PARENT ON NODES (wc_id, parent_relpath, op_depth); "),

            new Statement(Type.TABLE, "CREATE TABLE EXTERNALS ( " +
            "  wc_id  INTEGER NOT NULL REFERENCES WCROOT (id), " +
            "  local_relpath  TEXT NOT NULL, " +
            "  parent_relpath  TEXT NOT NULL, " +
            "  repos_id  INTEGER NOT NULL REFERENCES REPOSITORY (id), " +
            "  presence  TEXT NOT NULL, " +
            "  kind  TEXT NOT NULL, " +
            "  def_local_relpath         TEXT NOT NULL, " +
            "  def_repos_relpath         TEXT NOT NULL, " +
            "  def_operational_revision  TEXT, " +
            "  def_revision              TEXT, " +
            "  PRIMARY KEY (wc_id, local_relpath) " +
            "); "),
            new Statement(Type.INDEX, "CREATE INDEX I_EXTERNALS_PARENT ON EXTERNALS (wc_id, parent_relpath); " ),
            new Statement(Type.INDEX, "CREATE UNIQUE INDEX I_EXTERNALS_DEFINED ON EXTERNALS " +
            		" (wc_id, def_local_relpath, local_relpath); " ),

    		new Statement(Type.VIEW, "CREATE VIEW NODES_BASE AS SELECT * FROM nodes WHERE op_depth = 0;"),
            new Statement(Type.VIEW, "CREATE VIEW NODES_CURRENT AS SELECT * FROM nodes AS n WHERE op_depth = (SELECT MAX(op_depth) FROM nodes AS n2 WHERE n2.wc_id = n.wc_id AND n2.local_relpath = n.local_relpath);"),
            
            new Statement(Type.TRIGGER, "CREATE TRIGGER nodes_insert_trigger AFTER INSERT ON nodes WHEN NEW.checksum IS NOT NULL BEGIN UPDATE pristine SET refcount = refcount + 1 WHERE checksum = NEW.checksum; END;"),
            new Statement(Type.TRIGGER, "CREATE TRIGGER nodes_delete_trigger AFTER DELETE ON nodes WHEN OLD.checksum IS NOT NULL BEGIN UPDATE pristine SET refcount = refcount - 1 WHERE checksum = OLD.checksum; END;"),
            new Statement(Type.TRIGGER, "CREATE TRIGGER nodes_update_checksum_trigger AFTER UPDATE OF checksum ON nodes WHEN NEW.checksum IS NOT OLD.checksum BEGIN UPDATE pristine SET refcount = refcount + 1 WHERE checksum = NEW.checksum; UPDATE pristine SET refcount = refcount - 1 WHERE checksum = OLD.checksum; END;"),
    };
    
    public static final Statement[] TARGETS_LIST = new Statement[] {
        new Statement(Type.TABLE, "CREATE TABLE TARGETS_LIST (wc_id  INTEGER NOT NULL, local_relpath TEXT NOT NULL, parent_relpath TEXT, kind TEXT NOT NULL, PRIMARY KEY (wc_id, local_relpath) );"),
        new Statement(Type.INDEX, "CREATE INDEX targets_list_kind ON targets_list (kind);"),
    };
    
    public static final Statement[] DROP_TARGETS_LIST = new Statement[] {
        new Statement(Type.INDEX, "targets_list_kind", true),
        new Statement(Type.TABLE, "TARGETS_LIST", true),
    };
    
    public static final Statement[] NODE_PROPS_CACHE = new Statement[] {
        new Statement(Type.TABLE, "CREATE TABLE NODE_PROPS_CACHE (local_Relpath TEXT NOT NULL, kind TEXT NOT NULL, properties BLOB, PRIMARY KEY (local_Relpath) );"),
    };
    
    public static final Statement[] DROP_NODE_PROPS_CACHE = new Statement[] {
        new Statement(Type.TABLE, "NODE_PROPS_CACHE", true),
    };


    private enum Type {
        TABLE, INDEX, VIEW, TRIGGER;
    }

    public static class Statement {

        private Type type;
        private String sql;
        private boolean isDrop;
        
        public Statement(Type type, String sql) {
            this(type, sql, false);
        }

        public Statement(Type type, String sql, boolean isDrop) {
            this.type = type;
            this.sql = sql;
            this.isDrop = isDrop;
        }
        
        public boolean isDrop() {
            return isDrop;
        }

        public Type getType() {
            return type;
        }

        public String getSql() {
            return sql;
        }
    }

    private Statement[] statements;
    private int userVersion;
    
    public SVNWCDbCreateSchema(SVNSqlJetDb sDb) {
        this(sDb, MAIN_DB_STATEMENTS, ISVNWCDb.WC_FORMAT_17);
    }

    public SVNWCDbCreateSchema(SVNSqlJetDb sDb, Statement[] statements, int userVersion) {        
        super(sDb);
        this.statements = statements;
        this.userVersion = userVersion;
    }

    public long exec() throws SVNException {
        try {
            sDb.getDb().runWriteTransaction(new ISqlJetTransaction() {

                public Object run(SqlJetDb db) throws SqlJetException {
                    for (Statement stmt : statements) {
                        switch (stmt.getType()) {
                            case TABLE:                                
                                if (stmt.isDrop()) {
                                    if (db.getSchema().getTableNames().contains(stmt.getSql())) {
                                        db.dropTable(stmt.getSql());
                                    }
                                } else {
                                    db.createTable(stmt.getSql()); 
                                }
                                break;
                            case INDEX:
                                if (stmt.isDrop()) {
                                    if (db.getSchema().getIndexNames().contains(stmt.getSql())) {
                                        db.dropIndex(stmt.getSql());
                                    }
                                } else {
                                    db.createIndex(stmt.getSql()); 
                                }
                                break;
                            case VIEW:
                                if (stmt.isDrop()) {
                                    if (db.getSchema().getViewNames().contains(stmt.getSql())) {
                                        db.dropView(stmt.getSql());
                                    }
                                } else {
                                    db.createView(stmt.getSql()); 
                                }
                                break;
                            case TRIGGER:
                                if (stmt.isDrop()) {
                                    if (db.getSchema().getTriggerNames().contains(stmt.getSql())) {
                                        db.dropTrigger(stmt.getSql());
                                    }
                                } else {
                                    db.createTrigger(stmt.getSql()); 
                                }
                                break;
                            default:
                                break;
                        }
                    }
                    if (userVersion >= 0) {
                        db.getOptions().setUserVersion(userVersion);
                    }
                    return null;
                }
            });
        } catch (SqlJetException e) {
            SVNSqlJetDb.createSqlJetError(e);
        }
        return 0;
    }

}
