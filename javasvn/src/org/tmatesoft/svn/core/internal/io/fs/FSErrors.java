/*
 * ====================================================================
 * Copyright (c) 2004-2006 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://tmate.org/svn/license.html.
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.core.internal.io.fs;

import java.io.File;

import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;

/**
 * @version 1.0
 * @author  TMate Software Ltd.
 */
public class FSErrors {
    
    public static SVNErrorMessage errorDanglingId(FSID id, File reposRootDir) {
        File fsDir = FSRepositoryUtil.getRepositoryDBDir(reposRootDir);
        SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_ID_NOT_FOUND, "Reference to non-existent node ''{0}'' in filesystem ''{1}''", new Object[]{id, fsDir});
        return err;
    }
    
    public static SVNErrorMessage errorTxnNotMutable(String txnId, File reposRootDir) {
        File fsDir = FSRepositoryUtil.getRepositoryDBDir(reposRootDir);
        SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_TRANSACTION_NOT_MUTABLE, "Cannot modify transaction named ''{0}'' in filesystem ''{1}''", new Object[]{txnId, fsDir});
        return err;
    }

    public static SVNErrorMessage errorNotMutable(long revision, String path, File reposRootDir) {
        File fsDir = FSRepositoryUtil.getRepositoryDBDir(reposRootDir);
        SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_NOT_MUTABLE, "File is not mutable: filesystem ''{0}'', revision {1,number,integer}, path ''{2}''", new Object[]{fsDir, new Long(revision), path});
        return err;
    }
    
    public static SVNErrorMessage errorNotFound(FSOldRoot root, String path) {
        SVNErrorMessage err;
        if(root.isTxnRoot()){
            err = SVNErrorMessage.create(SVNErrorCode.FS_NOT_FOUND, "File not found: transaction ''{0}'', path ''{1}''", new Object[]{root.getTxnId(), path});
        }else{
            err = SVNErrorMessage.create(SVNErrorCode.FS_NOT_FOUND, "File not found: revision {0,number,integer}, path ''{1}''", new Object[]{new Long(root.getRevision()), path});
        }
        return err;
    }
    
    public static SVNErrorMessage errorNotDirectory(String path, File reposRootDir) {
        File fsDir = FSRepositoryUtil.getRepositoryDBDir(reposRootDir);
        SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_NOT_DIRECTORY, "''{0}'' is not a directory in filesystem ''{1}''", new Object[]{path, fsDir});
        return err;
    }
    
    public static SVNErrorMessage errorCorruptLockFile(String path, File reposRootDir) {
        File fsDir = FSRepositoryUtil.getRepositoryDBDir(reposRootDir);
        SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_CORRUPT, "Corrupt lockfile for path ''{0}'' in filesystem ''{1}''", new Object[]{path, fsDir});
        return err;
    }
    
    public static SVNErrorMessage errorOutOfDate(String path, String txnId) {
        SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_TXN_OUT_OF_DATE, "Out of date: ''{0}'' in transaction ''{1}''", new Object[]{path, txnId});
        return err;
    }
    
    public static SVNErrorMessage errorAlreadyExists(FSOldRoot root, String path, File reposRootDir) {
        File fsDir = FSRepositoryUtil.getRepositoryDBDir(reposRootDir);
        SVNErrorMessage err = null;
        if(root.isTxnRoot()){
            err = SVNErrorMessage.create(SVNErrorCode.FS_ALREADY_EXISTS, "File already exists: filesystem ''{0}'', transaction ''{1}'', path ''{2}''", new Object[]{fsDir, root.getTxnId(), path});
        }else{
            err = SVNErrorMessage.create(SVNErrorCode.FS_ALREADY_EXISTS, "File already exists: filesystem ''{0}'', revision {1}, path ''{2}''", new Object[]{fsDir, new Long(root.getRevision()), path});
        }
        return err;
    }
    
    public static SVNErrorMessage errorNotTxn() {
        SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_NOT_TXN_ROOT, "Root object must be a transaction root");
        return err;
    }
    
    public static SVNErrorMessage errorConflict(String path){
        SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_CONFLICT, "Conflict at ''{0}''", path);
        return err;
    }
    
    public static SVNErrorMessage errorNoSuchLock(String path, File reposRootDir){
        File fsDir = FSRepositoryUtil.getRepositoryDBDir(reposRootDir);
        SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_NO_SUCH_LOCK, "No lock on path ''{0}'' in filesystem ''{1}''", new Object[]{path, fsDir});
        return err;
    }
    
    public static SVNErrorMessage errorLockExpired(String lockToken, File reposRootDir){
        File fsDir = FSRepositoryUtil.getRepositoryDBDir(reposRootDir);
        SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_LOCK_EXPIRED, "Lock has expired:  lock-token ''{0}'' in filesystem ''{1}''", new Object[]{lockToken, fsDir});
        return err;
    }
    
    public static SVNErrorMessage errorNoUser(File reposRootDir){
        File fsDir = FSRepositoryUtil.getRepositoryDBDir(reposRootDir);
        SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_NO_USER, "No username is currently associated with filesystem ''{0}''", fsDir);
        return err;
    }

    public static SVNErrorMessage errorLockOwnerMismatch(String username, String lockOwner, File reposRootDir){
        File fsDir = FSRepositoryUtil.getRepositoryDBDir(reposRootDir);
        SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_LOCK_OWNER_MISMATCH, "User ''{0}'' is trying to use a lock owned by ''{1}'' in filesystem ''{2}''", new Object[]{username, lockOwner, fsDir});
        return err;
    }

    public static SVNErrorMessage errorNotFile(String path, File reposRootDir){
        File fsDir = FSRepositoryUtil.getRepositoryDBDir(reposRootDir);
        SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_NOT_FILE, "''{0}'' is not a file in filesystem ''{1}''", new Object[]{path, fsDir});
        return err;
    }

    public static SVNErrorMessage errorPathAlreadyLocked(String path, String owner, File reposRootDir){
        File fsDir = FSRepositoryUtil.getRepositoryDBDir(reposRootDir);
        SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_PATH_ALREADY_LOCKED, "Path ''{0}'' is already locked by user ''{1}'' in filesystem ''{2}''", new Object[]{path, owner, fsDir});
        return err;
    }

    /*
     * Return TRUE if err is an error specifically related to locking a
     * path in the repository, FALSE otherwise. 
     *
     * FS_OUT_OF_DATE is in here because it's a non-fatal error
     * that can be thrown when attempting to lock an item.
     */
    public static boolean isLockError(SVNErrorMessage err){
        if(err == null){
            return false;
        }
        SVNErrorCode errCode = err.getErrorCode();
        return errCode == SVNErrorCode.FS_PATH_ALREADY_LOCKED || errCode == SVNErrorCode.FS_OUT_OF_DATE;
    }

    /*
     * Return TRUE if err is an error specifically related to unlocking
     * a path in the repository, FALSE otherwise.
     */
    public static boolean isUnlockError(SVNErrorMessage err){
        if(err == null){
            return false;
        }
        SVNErrorCode errCode = err.getErrorCode();
        return errCode == SVNErrorCode.FS_PATH_NOT_LOCKED || errCode == SVNErrorCode.FS_BAD_LOCK_TOKEN || 
               errCode == SVNErrorCode.FS_LOCK_OWNER_MISMATCH || errCode == SVNErrorCode.FS_NO_SUCH_LOCK || 
               errCode == SVNErrorCode.RA_NOT_LOCKED || errCode == SVNErrorCode.FS_LOCK_EXPIRED;  
    }
    
}
