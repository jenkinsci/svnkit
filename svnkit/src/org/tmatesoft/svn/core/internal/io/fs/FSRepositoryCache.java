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
package org.tmatesoft.svn.core.internal.io.fs;

import org.tmatesoft.sqljet.core.SqlJetException;
import org.tmatesoft.sqljet.core.internal.SqlJetUtility;
import org.tmatesoft.sqljet.core.internal.table.SqlJetTable;
import org.tmatesoft.sqljet.core.schema.ISqlJetSchema;
import org.tmatesoft.sqljet.core.schema.ISqlJetTableDef;
import org.tmatesoft.sqljet.core.table.ISqlJetCursor;
import org.tmatesoft.sqljet.core.table.ISqlJetRunnableWithLock;
import org.tmatesoft.sqljet.core.table.SqlJetDb;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.util.SVNLogType;


/**
 * @version 1.3
 * @author  TMate Software Ltd.
 */
public class FSRepositoryCache {
    
    public static final String REP_CACHE_TABLE = "rep_cache";

    private static final String REP_CACHE_DB_SQL =  "pragma auto_vacuum = 1; " +
                                                    "create table rep_cache (hash text not null primary key, " +
                                                    "                        revision integer not null, " + 
                                                    "                        offset integer not null, " + 
                                                    "                        size integer not null, " +
                                                    "                        expanded_size integer not null); ";

    private SqlJetDb myRepCacheDB;
    private SqlJetTable myTable;
    private ISqlJetCursor myCursor;

    public static FSRepositoryCache openRepositoryCache(FSFS fsfs) throws SVNException {
        final FSRepositoryCache cacheObj = new FSRepositoryCache();
        SqlJetDb repCacheDB;
        try {
            repCacheDB = SqlJetDb.open(fsfs.getRepositoryCacheFile(), true);
            repCacheDB.runWithLock(new ISqlJetRunnableWithLock() {
                public Object runWithLock(SqlJetDb db) throws SqlJetException {
                    cacheObj.myTable = db.getTable(REP_CACHE_TABLE);
                    cacheObj.myCursor = cacheObj.myTable.open();
                    return null;
                }
            });

        } catch (SqlJetException e) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.SQLITE_ERROR, e);
            SVNErrorManager.error(err, SVNLogType.FSFS);
        }
        return null;
    }
}
