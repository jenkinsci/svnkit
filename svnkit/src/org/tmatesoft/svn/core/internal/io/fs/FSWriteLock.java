/*
 * ====================================================================
 * Copyright (c) 2004-2007 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.core.internal.io.fs;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileLock;
import java.util.HashMap;
import java.util.Map;

import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.wc.SVNFileType;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;

/**
 * @version 1.1.1
 * @author  TMate Software Ltd.
 */
public class FSWriteLock {

    private static final Map ourThreadLocksCache = new HashMap();

    private RandomAccessFile myLockRAFile;
    private FileLock myLock;
    private int myReferencesCount = 0;
    private File myLockFile;
    private String myToken;

    private FSWriteLock(String token, File lockFile) {
        myToken = token;
        myLockFile = lockFile;
    }

    public static synchronized FSWriteLock getWriteLockForDB(FSFS owner) throws SVNException {
        String uuid = owner.getUUID();
        FSWriteLock lock = (FSWriteLock) ourThreadLocksCache.get(uuid);
        if (lock == null) {
            lock = new FSWriteLock(uuid, owner.getWriteLockFile());
            ourThreadLocksCache.put(uuid, lock);
        }
        lock.myReferencesCount++;
        return lock;
    }

    public static synchronized FSWriteLock getWriteLockForCurrentTxn(String token, FSFS owner) throws SVNException {
        String uuid = owner.getUUID() + (token != null ? token : "");
        FSWriteLock lock = (FSWriteLock) ourThreadLocksCache.get(uuid);
        if (lock == null) {
            lock = new FSWriteLock(uuid, owner.getTransactionCurrentLockFile());
            ourThreadLocksCache.put(uuid, lock);
        }
        lock.myReferencesCount++;
        return lock;
    }

    public static synchronized FSWriteLock getWriteLockForTxn(String txnID, FSFS owner) throws SVNException {
        String uuid = owner.getUUID() + (txnID != null ? txnID : "");
        FSWriteLock lock = (FSWriteLock) ourThreadLocksCache.get(uuid);
        if (lock == null) {
            lock = new FSWriteLock(uuid, owner.getTransactionProtoRevLockFile(txnID));
            ourThreadLocksCache.put(uuid, lock);
        }
        lock.myReferencesCount++;
        return lock;
    }

    public synchronized void lock() throws SVNException {
        if (myLock != null) {
            return;
        }
        try {
            SVNFileType type = SVNFileType.getType(myLockFile);
            if (type == SVNFileType.UNKNOWN || type == SVNFileType.NONE) {
                SVNFileUtil.createEmptyFile(myLockFile);
            }
            myLockRAFile = new RandomAccessFile(myLockFile, "rw");
            myLock = myLockRAFile.getChannel().lock();
        } catch (IOException ioe) {
            unlock();
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, 
                    "Can''t get exclusive lock on file ''{0}'': {1}", new Object[] {
                    myLockFile, ioe.getLocalizedMessage() });
            SVNErrorManager.error(err, ioe);
        }
    }

    public static synchronized void release(FSWriteLock lock) {
        if (lock == null) {
            return;
        }
        if ((--lock.myReferencesCount) == 0) {
            ourThreadLocksCache.remove(lock.myToken);
        }
    }

    public synchronized void unlock() {
        if (myLock != null) {
            try {
                myLock.release();
            } catch (IOException ioex) {
                //
            }
            myLock = null;
        }
        SVNFileUtil.closeFile(myLockRAFile);
        myLockFile = null;
    }

}
