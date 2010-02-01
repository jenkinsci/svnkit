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
import org.tmatesoft.sqljet.core.internal.ISqlJetMemoryPointer;
import org.tmatesoft.sqljet.core.internal.SqlJetUtility;
import org.tmatesoft.sqljet.core.table.ISqlJetRunnableWithLock;
import org.tmatesoft.sqljet.core.table.ISqlJetTransaction;
import org.tmatesoft.sqljet.core.table.SqlJetDb;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNProperties;
import org.tmatesoft.svn.core.internal.util.SVNSkel;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.util.SVNLogType;


/**
 * @version 1.3
 * @author  TMate Software Ltd.
 */
public class SVNSqlJetUtil {

    public static SVNProperties getPropertiesFromBLOB(Object obj) throws SVNException {
        if (obj != null && obj instanceof ISqlJetMemoryPointer) {//check if it's a BLOB object and convert it to byte[] if it is
            byte[] bytes = SqlJetUtility.readByteBuffer((ISqlJetMemoryPointer) obj);
            SVNSkel skel = SVNSkel.createAtom(bytes);
            Map propsMap = skel != null ? skel.parsePropList() : null;
            if (propsMap == null) {
                return null;
            }
            return SVNProperties.wrap(propsMap);
        }
        return null;
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

    public static final SVNDbTableField[] OUR_BASE_NODE_FIELDS = { 
        SVNDbTableField.repos_id, 
        SVNDbTableField.repos_relpath, 
        SVNDbTableField.presence, 
        SVNDbTableField.kind, 
        SVNDbTableField.checksum, 
        SVNDbTableField.translated_size, 
        SVNDbTableField.changed_rev, 
        SVNDbTableField.changed_date, 
        SVNDbTableField.changed_author, 
        SVNDbTableField.depth, 
        SVNDbTableField.symlink_target, 
        SVNDbTableField.last_mod_time, 
        SVNDbTableField.properties 
    };
    
    public static final SVNDbTableField[] OUR_PRESENCE_FIELD = {
        SVNDbTableField.presence
    };
    
    protected static final SVNDbTableField[] OUR_PARENT_STUB_INFO_FIELDS = { 
        SVNDbTableField.presence, 
        SVNDbTableField.revnum 
    };
    
    public static final SVNDbTableField[] OUR_LOCK_FIELDS = { 
        SVNDbTableField.lock_token, 
        SVNDbTableField.lock_owner, 
        SVNDbTableField.lock_comment, 
        SVNDbTableField.lock_date 
    };
    
    public static final SVNDbTableField[] OUR_ACTUAL_NODE_FIELDS = { 
        SVNDbTableField.prop_reject, 
        SVNDbTableField.changelist, 
        SVNDbTableField.conflict_old, 
        SVNDbTableField.conflict_new, 
        SVNDbTableField.conflict_working, 
        SVNDbTableField.tree_conflict_data, 
        SVNDbTableField.properties 
    };
    
    public static final SVNDbTableField[] OUR_ACTUAL_TREE_CONFLICT_FIELDS = {
        SVNDbTableField.tree_conflict_data
    };
    
    public static final SVNDbTableField[] OUR_CONFLICT_DETAILS_FIELDS = {
        SVNDbTableField.prop_reject,
        SVNDbTableField.conflict_new,
        SVNDbTableField.conflict_old, 
        SVNDbTableField.conflict_working
    };
    
    public static final SVNDbTableField[] OUR_WORKING_NODE_FIELDS = { 
        SVNDbTableField.presence, 
        SVNDbTableField.kind, 
        SVNDbTableField.checksum, 
        SVNDbTableField.translated_size, 
        SVNDbTableField.changed_rev, 
        SVNDbTableField.changed_date, 
        SVNDbTableField.changed_author, 
        SVNDbTableField.depth, 
        SVNDbTableField.symlink_target, 
        SVNDbTableField.copyfrom_repos_id, 
        SVNDbTableField.copyfrom_repos_path, 
        SVNDbTableField.copyfrom_revnum, 
        SVNDbTableField.moved_here, 
        SVNDbTableField.moved_to, 
        SVNDbTableField.last_mod_time, 
        SVNDbTableField.properties
    };
    
    public static final SVNDbTableField[] OUR_KEEP_LOCAL_FIELD = {
        SVNDbTableField.keep_local
    };
    
    public static final SVNDbTableField[] OUR_SELECT_REPOSITORY_BY_ID_FIELDS = {
        SVNDbTableField.root,
        SVNDbTableField.uuid
    };
    
    public static final SVNDbTableField[] OUR_KIND_FIELD = {
        SVNDbTableField.kind
    };
    
    public static final SVNDbTableField[] OUR_ID_FIELD = {
        SVNDbTableField.id
    };
    
    public static final SVNDbTableField[] OUR_DELETION_INFO_FIELDS = {
        SVNDbTableField.presence,
        SVNDbTableField.moved_to
    };
    
    public static final SVNDbTableField[] OUR_FILE_EXTERNAL_FIELD = {
        SVNDbTableField.file_external
    };
    
    public static final SVNDbTableField[] OUR_DAV_CACHE_FIELD = {
        SVNDbTableField.dav_cache
    };

}
