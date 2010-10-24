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
package org.tmatesoft.svn.core.internal.io.fs;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.tmatesoft.svn.core.ISVNCanceller;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.wc.ISVNEventHandler;
import org.tmatesoft.svn.core.wc.admin.ISVNAdminEventHandler;
import org.tmatesoft.svn.core.wc.admin.SVNAdminEvent;
import org.tmatesoft.svn.core.wc.admin.SVNAdminEventAction;
import org.tmatesoft.svn.util.SVNLogType;


/**
 * @version 1.3
 * @author  TMate Software Ltd.
 */
public class FSPacker {

    private ISVNCanceller myCanceller;
    private ISVNAdminEventHandler myNotifyHandler;
    
    public FSPacker(ISVNAdminEventHandler notifyHandler) {
        myCanceller = notifyHandler == null ? ISVNCanceller.NULL : notifyHandler;
        myNotifyHandler = notifyHandler;
    }
    
    public void pack(FSFS fsfs) throws SVNException {
        FSWriteLock writeLock = FSWriteLock.getWriteLockForDB(fsfs);
        synchronized (writeLock) {
            try {
                writeLock.lock();
                packImpl(fsfs);
            } finally {
                writeLock.unlock();
                FSWriteLock.release(writeLock);
            }
        }
    }
    
    private void packImpl(FSFS fsfs) throws SVNException {
        int format = fsfs.getDBFormat();
        if (format < FSFS.MIN_PACKED_FORMAT) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_UNSUPPORTED_FORMAT, "FS format too old to pack, please upgrade.");
            SVNErrorManager.error(err, SVNLogType.FSFS);
        }
        
        long maxFilesPerDirectory = fsfs.getMaxFilesPerDirectory(); 
        if (maxFilesPerDirectory <= 0) {
            return;
        }
        
        long minUnpackedRev = fsfs.getMinUnpackedRev();
        long youngestRev = fsfs.getYoungestRevision();
        long completedShards = (youngestRev + 1) / maxFilesPerDirectory;
        if (minUnpackedRev == completedShards * maxFilesPerDirectory) {
            return;
        }
        
        for (long i = minUnpackedRev / maxFilesPerDirectory; i < completedShards; i++) {
            myCanceller.checkCancelled();
            packShard(fsfs, i);
        }
    }
    
    private void packShard(FSFS fsfs, long shard) throws SVNException {
        File packDir = fsfs.getPackDir(shard);
        File packFile = fsfs.getPackFile(shard);
        File manifestFile = fsfs.getManifestFile(shard);
        File shardPath = new File(fsfs.getDBRevsDir(), String.valueOf(shard));
        
        firePackEvent(shard, true);
        
        SVNFileUtil.deleteAll(packDir, false, myCanceller);
        
        long startRev = shard * fsfs.getMaxFilesPerDirectory();
        long endRev = (shard + 1) * fsfs.getMaxFilesPerDirectory() - 1;
        long nextOffset = 0;
        OutputStream packFileOS = null;
        OutputStream manifestFileOS = null;
        try {
            packFileOS = SVNFileUtil.openFileForWriting(packFile);
            manifestFileOS = SVNFileUtil.openFileForWriting(manifestFile);
            for (long rev = startRev; rev <= endRev; rev++) {
                File path = new File(shardPath, String.valueOf(rev));
                String line = String.valueOf(nextOffset) + '\n';
                manifestFileOS.write(line.getBytes("UTF-8"));
                nextOffset += path.length();
                InputStream revIS = null;
                try {
                    revIS = SVNFileUtil.openFileForReading(path);
                    FSRepositoryUtil.copy(revIS, packFileOS, myCanceller);
                } finally {
                    SVNFileUtil.closeFile(revIS);
                }
            }
        } catch (IOException ioe) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, ioe.getMessage());
            SVNErrorManager.error(err, ioe, SVNLogType.FSFS);
        } finally {
            SVNFileUtil.closeFile(packFileOS);
            SVNFileUtil.closeFile(manifestFileOS);
        }
        
        File finalPath = fsfs.getMinUnpackedRevFile(); 
        File tmpFile = SVNFileUtil.createUniqueFile(fsfs.getDBRoot(), "tempfile", ".tmp", false);
        String line = String.valueOf((shard + 1) * fsfs.getMaxFilesPerDirectory()) + '\n';
        SVNFileUtil.writeToFile(tmpFile, line, "UTF-8");
        SVNFileUtil.rename(tmpFile, finalPath);
        SVNFileUtil.deleteAll(shardPath, true, myCanceller);
        
        firePackEvent(shard, false);
    }
    
    private void firePackEvent(long shard, boolean start) throws SVNException {
        if (myNotifyHandler != null) {
            SVNAdminEvent event = new SVNAdminEvent(start ? SVNAdminEventAction.PACK_START : SVNAdminEventAction.PACK_END, shard);
            myNotifyHandler.handleAdminEvent(event, ISVNEventHandler.UNKNOWN);
        }
    }

}
