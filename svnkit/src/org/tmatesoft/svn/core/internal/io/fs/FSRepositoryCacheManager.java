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

import org.tmatesoft.sqljet.core.SqlJetErrorCode;
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
public class FSRepositoryCacheManager {
    
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
    private FSFS myFSFS;

    public static FSRepositoryCacheManager openRepositoryCache(FSFS fsfs) throws SVNException {
        final FSRepositoryCacheManager cacheObj = new FSRepositoryCacheManager();
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
        return cacheObj;
    }
    
    public boolean insert(final FSRepresentation representation, boolean rejectDup) throws SVNException {
        if (representation.getSHA1HexDigest() == null) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.BAD_CHECKSUM_KIND, 
                    "Only SHA1 checksums can be used as keys in the rep_cache table.\n");
            SVNErrorManager.error(err, SVNLogType.FSFS);
        }
        
        FSRepresentation oldRep = getRepresentationByHash(representation.getSHA1HexDigest());
        if (oldRep != null) {
            if (rejectDup && (oldRep.getRevision() != representation.getRevision() || oldRep.getOffset() != representation.getOffset() ||
                    oldRep.getSize() != representation.getSize() || oldRep.getExpandedSize() != representation.getExpandedSize())) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_CORRUPT, "Representation key for checksum ''{0}'' exists in " + 
                        "filesystem ''{1}'' with a different value ({2},{3},{4},{5}) than what we were about to store ({6},{7},{8},{9})", 
                        new Object[] { representation.getSHA1HexDigest(), myFSFS.getRepositoryRoot(), String.valueOf(oldRep.getRevision()), 
                        String.valueOf(oldRep.getOffset()), String.valueOf(oldRep.getSize()), String.valueOf(oldRep.getExpandedSize()), 
                        String.valueOf(representation.getRevision()), String.valueOf(representation.getOffset()), 
                        String.valueOf(representation.getSize()), String.valueOf(representation.getExpandedSize()) });
                SVNErrorManager.error(err, SVNLogType.FSFS);
            }
            
            return false;
        }
        
/*        Boolean result = (Boolean) myRepCacheDB.runWithLock(new ISqlJetRunnableWithLock() {

            public Object runWithLock(SqlJetDb db) throws SqlJetException {
                final ISqlJetCursor lookup = myTable.lookup(myTable.getPrimaryKeyIndex(), repCache.getHash());
                if (!lookup.eof())
                    return false;
                db.beginTransaction();
                try {
                    table.insert(repCache.getHash(), repCache.getRevision(),
                            repCache.getOffset(), repCache.getSize(), repCache.getExpandedSize());
                    db.commit();
                } catch (SqlJetException e) {
                    db.rollback();
                }
                return true;
            }
        });*/
        return false;
    }

    public void close() throws SVNException {
        try {
            myRepCacheDB.runWithLock(new ISqlJetRunnableWithLock() {
                public Object runWithLock(SqlJetDb db) throws SqlJetException {
                    myCursor.close();
                    myRepCacheDB.close();
                    return null;
                }
            });
        } catch (SqlJetException e) {
            SVNErrorManager.error(convertError(e), SVNLogType.FSFS);
        }
    }
    
    public FSRepresentation getRepresentationByHash(String hash) throws SVNException {
        FSRepositoryCache cache = getByHash(hash);
        if (cache != null) {
            FSRepresentation representation = new FSRepresentation();
            representation.setExpandedSize(cache.getExpandedSize());
            representation.setOffset(cache.getOffset());
            representation.setRevision(cache.getRevision());
            representation.setSize(cache.getSize());
            return representation;
        }
        return null;
    }

    private FSRepositoryCache getByHash(final String hash) throws SVNException {
        try {
            return (FSRepositoryCache) myRepCacheDB.runWithLock(new ISqlJetRunnableWithLock() {

                public Object runWithLock(SqlJetDb db) throws SqlJetException {
                    final ISqlJetCursor lookup = myTable.lookup(myTable.getPrimaryKeyIndex(), new Object[] { hash });
                    if (!lookup.eof()) {
                        return new FSRepositoryCache(lookup);
                    }

                    return null;
                }
            });
        } catch (SqlJetException e) {
            SVNErrorManager.error(convertError(e), SVNLogType.FSFS);
        }
        return null;
    }

    private static SVNErrorMessage convertError(SqlJetException e) {
        SVNErrorMessage err = SVNErrorMessage.create(convertErrorCode(e), e.getMessage());
        return err;
    }
    
    private static SVNErrorCode convertErrorCode(SqlJetException e) {
        SqlJetErrorCode sqlCode = e.getErrorCode();
        if (sqlCode == SqlJetErrorCode.READONLY) {
            return SVNErrorCode.SQLITE_READONLY;
        } 
        return SVNErrorCode.SQLITE_ERROR;
    }
    
}
