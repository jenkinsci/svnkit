/*
 * ====================================================================
 * Copyright (c) 2004 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.core.internal.io.fs;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileLock;
import java.io.File;
import java.util.HashMap;
import java.util.Map;

import org.tmatesoft.svn.core.internal.wc.SVNFileType;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNErrorCode;

/**
 * @version 1.1.0
 * @author  TMate Software Ltd.
 */
public class FSWriteLock {

    private static final Map ourThreadLocksCache = new HashMap();

    private RandomAccessFile myLockFile;
    private FileLock myLock;
    private int myReferencesCount = 0;
    private FSFS myFSFS;
    private String myUUID;

    private FSWriteLock(String uuid, FSFS owner) {
        myUUID = uuid;
        myFSFS = owner;
    }

    public static synchronized FSWriteLock getWriteLock(FSFS owner) throws SVNException {
        String uuid = owner.getUUID();
        FSWriteLock lock = (FSWriteLock) ourThreadLocksCache.get(uuid);

        if (lock == null) {
            lock = new FSWriteLock(uuid, owner);
            ourThreadLocksCache.put(uuid, lock);
        }
        lock.myReferencesCount++;
        return lock;
    }

    public synchronized void lock() throws SVNException {
        File writeLockFile = myFSFS.getWriteLockFile();

        try {
            SVNFileType type = SVNFileType.getType(writeLockFile);
            if (type == SVNFileType.UNKNOWN || type == SVNFileType.NONE) {
                SVNFileUtil.createEmptyFile(writeLockFile);
            }

            myLockFile = new RandomAccessFile(writeLockFile, "rw");
            myLock = myLockFile.getChannel().lock();
        } catch (IOException ioe) {
            unlock();
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, "Can't get exclusive lock on file ''{0}'': {1}", new Object[] {
                    writeLockFile, ioe.getLocalizedMessage()
            });
            SVNErrorManager.error(err, ioe);
        }
    }

    public static synchronized void realease(FSWriteLock lock) {
        if (lock == null) {
            return;
        }
        if ((--lock.myReferencesCount) == 0) {
            ourThreadLocksCache.remove(lock.myUUID);
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
        SVNFileUtil.closeFile(myLockFile);
        myLockFile = null;
    }
}
