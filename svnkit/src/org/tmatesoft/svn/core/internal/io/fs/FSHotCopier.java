/*
 * ====================================================================
 * Copyright (c) 2004-2008 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html.
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.core.internal.io.fs;

import java.io.File;

import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;


/**
 * @version 1.1.2
 * @author  TMate Software Ltd.
 */
public class FSHotCopier {

    private FSFS myOwner;
    
    public void runHotCopy(File srcPath, File dstPath) throws SVNException {
        FSWriteLock dbLogsLock = FSWriteLock.getDBLogsLock(myOwner, false);
        synchronized (dbLogsLock) {
            try {
                dbLogsLock.lock();
                createRepositoryLayout(srcPath, dstPath);
                try {
                    createReposDir(new File(dstPath, FSFS.LOCKS_DIR));
                } catch (SVNException svne) {
                    SVNErrorMessage err = svne.getErrorMessage().wrap("Creating lock dir");
                    SVNErrorManager.error(err);
                }
                createDBLock(dstPath);
                createDBLogsLock(dstPath);
                hotCopy(srcPath, dstPath);
                SVNFileUtil.writeVersionFile(new File(dstPath, FSFS.REPOS_FORMAT_FILE), myOwner.getReposFormat());
            } finally {
                dbLogsLock.unlock();
                FSWriteLock.release(dbLogsLock);
            }
        }
    }
    
    private void createRepositoryLayout(File srcPath, File dstPath) throws SVNException {
        File[] children = srcPath.listFiles();
        for (int i = 0; i < children.length; i++) {
            File child = children[i];
            String childName = child.getName(); 
            if (childName.equals(FSFS.DB_DIR) || childName.equals(FSFS.LOCKS_DIR) || 
                    childName.equals(FSFS.REPOS_FORMAT_FILE)) {
                continue;
            }

            File dstChildPath = new File(dstPath, childName);
            if (child.isDirectory()) {
                createReposDir(dstChildPath);
                createRepositoryLayout(child, dstChildPath);
            } else if (child.isFile()) {
                SVNFileUtil.copyFile(child, dstChildPath, true);
            }
        }
    }
    
    private void createReposDir(File dir) throws SVNException {
        if (dir.exists()) {
            File[] dstChildren = dir.listFiles();
            if (dstChildren.length > 0) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.DIR_NOT_EMPTY, 
                        "''{0}'' exists and is non-empty", dir);
                SVNErrorManager.error(err);
            }
        } else {
            dir.mkdirs();
        }
    }
    
    private void createDBLock(File dstPath) throws SVNException {
        try {
            SVNFileUtil.createFile(new File(dstPath, FSFS.DB_LOCK_FILE), FSFS.PRE_12_COMPAT_UNNEEDED_FILE_CONTENTS, 
            "US-ASCII");
        } catch (SVNException svne) {
            SVNErrorMessage err = svne.getErrorMessage().wrap("Creating db lock file");
            SVNErrorManager.error(err);
        }
    }

    private void createDBLogsLock(File dstPath) throws SVNException {
        try {
            SVNFileUtil.createFile(new File(dstPath, FSFS.DB_LOGS_LOCK_FILE), FSFS.PRE_12_COMPAT_UNNEEDED_FILE_CONTENTS, 
            "US-ASCII");
        } catch (SVNException svne) {
            SVNErrorMessage err = svne.getErrorMessage().wrap("Creating db logs lock file");
            SVNErrorManager.error(err);
        }
    }

    private void hotCopy(File srcPath, File dstPath) throws SVNException {
        int format = myOwner.readDBFormat();
        FSRepositoryUtil.checkReposDBForma(format);
        //SVNFileUtil.copyFile(src, dst, safe)
    }
}
