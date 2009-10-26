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

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;

import org.tmatesoft.sqljet.core.SqlJetErrorCode;
import org.tmatesoft.sqljet.core.SqlJetException;
import org.tmatesoft.sqljet.core.internal.SqlJetTransactionMode;
import org.tmatesoft.sqljet.core.table.ISqlJetRunnableWithLock;
import org.tmatesoft.sqljet.core.table.ISqlJetTransaction;
import org.tmatesoft.sqljet.core.table.SqlJetDb;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.io.fs.repcache.FSRepresentationCacheManager;
import org.tmatesoft.svn.core.internal.wc.SVNAdminUtil;
import org.tmatesoft.svn.core.internal.wc.SVNClassLoader;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.util.SVNDebugLog;
import org.tmatesoft.svn.util.SVNLogType;


/**
 * @version 1.3
 * @author  TMate Software Ltd.
 */
public class SVNWorkingCopyDB17 implements ISVNWorkingCopyDB {
    private static final int SVN_WC_VERSION = 15;
    
    private static final String[] WC_METADATA_SQL_12 = { 
        "CREATE TABLE REPOSITORY ( id INTEGER PRIMARY KEY AUTOINCREMENT, " + 
                                  "root TEXT UNIQUE NOT NULL, " + 
                                  "uuid TEXT NOT NULL );", 
        "CREATE INDEX I_UUID ON REPOSITORY (uuid);", 
                                  
        "CREATE INDEX I_ROOT ON REPOSITORY (root);", 
        
        "CREATE TABLE WCROOT ( id INTEGER PRIMARY KEY AUTOINCREMENT, " + 
                              "local_abspath TEXT UNIQUE );",
        "CREATE UNIQUE INDEX I_LOCAL_ABSPATH ON WCROOT (local_abspath);",

        "CREATE TABLE BASE_NODE ( wc_id INTEGER NOT NULL, " +
                                 "local_relpath TEXT NOT NULL, " + 
                                 "repos_id INTEGER, " + 
                                 "repos_relpath TEXT, " + 
                                 "parent_relpath TEXT, " + 
                                 "presence TEXT NOT NULL, " +
                                 "kind TEXT NOT NULL, " + 
                                 "revnum INTEGER, " + 
                                 "checksum TEXT, " + 
                                 "translated_size INTEGER, " + 
                                 "changed_rev INTEGER, " + 
                                 "changed_date  INTEGER, " + 
                                 "changed_author TEXT, " + 
                                 "depth TEXT, " + 
                                 "symlink_target TEXT, " + 
                                 "last_mod_time INTEGER, " + 
                                 "last_mod_time INTEGER, " + 
                                 "dav_cache BLOB, " + 
                                 "incomplete_children INTEGER, " + 
                                 "file_external TEXT, " + 
                                 "PRIMARY KEY (wc_id, local_relpath) );",

        "CREATE INDEX I_PARENT ON BASE_NODE (wc_id, parent_relpath);", 
        
        "CREATE TABLE PRISTINE ( checksum TEXT NOT NULL PRIMARY KEY, " + 
                                "compression INTEGER, " + 
                                "size INTEGER, " + 
                                "refcount INTEGER NOT NULL ); ",

        "CREATE TABLE WORKING_NODE ( wc_id INTEGER NOT NULL, " +
                                    "local_relpath TEXT NOT NULL, " +
                                    "parent_relpath TEXT, " +
                                    "presence TEXT NOT NULL, " +
                                    "kind TEXT NOT NULL, " + 
                                    "checksum TEXT, " +
                                    "translated_size INTEGER, " +
                                    "changed_rev INTEGER, " + 
                                    "changed_date INTEGER, " + 
                                    "changed_author TEXT, " +
                                    "depth TEXT, " + 
                                    "symlink_target TEXT, " +
                                    "copyfrom_repos_id INTEGER, " +
                                    "copyfrom_repos_path TEXT, " +
                                    "copyfrom_revnum INTEGER, " + 
                                    "moved_here INTEGER, " +
                                    "moved_to TEXT, " + 
                                    "last_mod_time  INTEGER, " + 
                                    "properties BLOB, " + 
                                    "keep_local INTEGER, " + 
                                    "PRIMARY KEY (wc_id, local_relpath) );",

        "CREATE INDEX I_WORKING_PARENT ON WORKING_NODE (wc_id, parent_relpath);",
        
        "CREATE TABLE ACTUAL_NODE ( wc_id INTEGER NOT NULL, " +
                                   "local_relpath  TEXT NOT NULL, " +
                                   "parent_relpath  TEXT, " + 
                                   "properties BLOB, " + 
                                   "conflict_old TEXT, " + 
                                   "conflict_new TEXT, " +
                                   "conflict_working TEXT, " + 
                                   "prop_reject TEXT, " + 
                                   "changelist TEXT, " + 
                                   "text_mod TEXT, " + 
                                   "tree_conflict_data TEXT, " + 
                                   "PRIMARY KEY (wc_id, local_relpath) );",

        "CREATE INDEX I_ACTUAL_PARENT ON ACTUAL_NODE (wc_id, parent_relpath);",
        
        "CREATE INDEX I_ACTUAL_CHANGELIST ON ACTUAL_NODE (changelist);",
        
        "CREATE TABLE LOCK ( repos_id INTEGER NOT NULL, " +
                            "repos_relpath TEXT NOT NULL, " + 
                            "lock_token TEXT NOT NULL, " +
                            "lock_owner TEXT, " +
                            "lock_comment TEXT, " + 
                            "lock_date INTEGER, " +
                            "PRIMARY KEY (repos_id, repos_relpath) );" 
    };
    
    private static final String[] WC_METADATA_SQL_13 = {
        "CREATE TABLE WORK_QUEUE ( id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                                   "work BLOB NOT NULL );",
        
        "UPDATE BASE_NODE SET incomplete_children=null, dav_cache=null;"
    };
    
    private static final String[] WC_METADATA_SQL_14 = {
        "CREATE TABLE WC_LOCK ( wc_id INTEGER NOT NULL, " +
                               "local_dir_relpath TEXT NOT NULL, " +
                               "PRIMARY KEY (wc_id, local_dir_relpath) );",
        
        "ALTER TABLE ACTUAL_NODE ADD COLUMN conflict_data  BLOB;",
        
        "ALTER TABLE ACTUAL_NODE ADD COLUMN older_checksum TEXT;",
        
        "ALTER TABLE ACTUAL_NODE ADD COLUMN left_checksum TEXT;",
        
        "ALTER TABLE ACTUAL_NODE ADD COLUMN right_checksum TEXT;"
    };    
    
    private static final String[] WC_METADATA_SQL_15 = {
        "UPDATE base_node SET presence = 'excluded', " +
                             "checksum = NULL, " + 
                             "translated_size = NULL, " + 
                             "changed_rev = NULL, " +
                             "changed_date = NULL, " + 
                             "changed_author = NULL, " + 
                             "depth = NULL, " +
                             "symlink_target = NULL, " + 
                             "last_mod_time = NULL, " + 
                             "properties = NULL, " +
                             "incomplete_children = NULL, " + 
                             "file_external = NULL WHERE depth = 'exclude';", 
        "UPDATE working_node SET presence = 'excluded', " +
                                "checksum = NULL, " + 
                                "translated_size = NULL, " + 
                                "changed_rev = NULL, " + 
                                "changed_date = NULL, " + 
                                "changed_author = NULL, " + 
                                "depth = NULL, " +
                                "symlink_target = NULL, " + 
                                "copyfrom_repos_id = NULL, " + 
                                "copyfrom_repos_path = NULL, " +
                                "copyfrom_revnum = NULL, " +
                                "moved_here = NULL, " + 
                                "moved_to = NULL, " + 
                                "last_mod_time = NULL, " + 
                                "properties = NULL, " + 
                                "keep_local = NULL WHERE depth = 'exclude';"
    };
    
    
    public void createDB(File dirPath, SVNURL reposRootURL, String reposUUID) throws SVNException {
        SqlJetDb db = openDB(dirPath, SqlJetTransactionMode.WRITE);
        
    }
    
    public void createReposId(SqlJetDb db) throws SVNException {
        try {
            db.beginTransaction(SqlJetTransactionMode.READ_ONLY);
            try {
//            printRecords(table.order(table.getPrimaryKeyIndexName()));
            } finally {
                db.commit();
            }    
        } catch (SqlJetException e) {
            SVNSqlJetUtil.convertException(e);
        }
    }
    
    public SqlJetDb openDB(File dirPath, SqlJetTransactionMode mode) throws SVNException {
        File sdbFile = SVNAdminUtil.getSDBFile(dirPath); 

        ISqlJetTransaction openTxn = new ISqlJetTransaction() {
          
            public Object run(SqlJetDb db) throws SqlJetException {
                int version = db.getOptions().getUserVersion();
                if (version < SVN_WC_VERSION) {
                    db.getOptions().setAutovacuum(true);
                    db.runWriteTransaction(new ISqlJetTransaction() {
                        public Object run(SqlJetDb db) throws SqlJetException {
                            db.getOptions().setUserVersion(SVN_WC_VERSION);
                            InputStream commandsStream = null;
                            try {
                                commandsStream = SVNWorkingCopyDB17.class.getClassLoader().getResourceAsStream("wc-metadata.sql");
                                if (commandsStream != null) {
                                    BufferedReader reader = new BufferedReader(new InputStreamReader(commandsStream));
                                    String line = null;
                                    while ((line = reader.readLine()) != null) {
                                        line = line.trim();
                                        if (line.length() == 0) {
                                            continue;
                                        }
                                        
                                        if (line.startsWith("CREATE TABLE")) {
                                            db.createTable(line);
                                        } else if (line.startsWith("CREATE INDEX")) {
                                            db.createIndex(line);
                                        }
                                    }
                                }
                            } catch (IOException e) {
                                throw new SqlJetException(SqlJetErrorCode.IOERR, e);
                            } finally {
                                SVNFileUtil.closeFile(commandsStream);
                            }
                            return null;
                        }
                    });
                } else if (version > SVN_WC_VERSION) {
                    throw new SqlJetException("Schema format " + version + " not recognized");   
                }
                return null;
            }
        };

        return SVNSqlJetUtil.openDB(sdbFile, openTxn, mode, SVN_WC_VERSION);
    }

    
}
