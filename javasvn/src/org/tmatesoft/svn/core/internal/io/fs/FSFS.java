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
import java.util.Map;

import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;


/**
 * @version 1.0
 * @author  TMate Software Ltd.
 */
public class FSFS {
    private String myUUID;
    
    private File myRepositoryRoot;

    public FSFS(File repositoryRoot) {
        myRepositoryRoot = repositoryRoot;
    }
    
    // checks format, loads uuid.
    public void open() throws SVNException {

        checkRepositoryInfo();
        
        // Read and cache repository UUID
        if(myUUID == null){
            myUUID = FSRepositoryUtil.getRepositoryUUID(myRepositoryRoot);    
        }
    }
    
    public String getUUID(){
        return myUUID;
    }
    
    protected void checkRepositoryInfo() throws SVNException {
        /* Check repos format (the format file must exist!) */
        int formatNumber = FSRepositoryUtil.getFormat(FSRepositoryUtil.getRepositoryFormatFile(myRepositoryRoot), true, -1);
        if (formatNumber != FSConstants.SVN_REPOS_FORMAT_NUMBER) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.REPOS_UNSUPPORTED_VERSION, "Expected format ''{0,number,integer}'' of repository; found format ''{1,number,integer}''", new Object[]{new Integer(FSConstants.SVN_REPOS_FORMAT_NUMBER), new Integer(formatNumber)});
            SVNErrorManager.error(err);
        }

        /* Check FS type for 'fsfs' */
        File fsTypeFile = FSRepositoryUtil.getFSTypeFile(myRepositoryRoot);
        FSFile reader = new FSFile(fsTypeFile);
        String fsType = null;

        try{
            fsType = reader.readLine(128);    
        }finally{
            reader.close();
        }
        
        if (fsType == null || fsType.length() == 0 || !fsType.equals(FSConstants.SVN_REPOS_FSFS_FORMAT)) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_UNKNOWN_FS_TYPE, "Unsupported fs type");
            SVNErrorManager.error(err);
        }

        /* Attempt to open the 'current' file of this repository */
        File dbCurrentFile = FSRepositoryUtil.getFSCurrentFile(myRepositoryRoot);
        if(!(dbCurrentFile.exists() && dbCurrentFile.canRead())){
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, "Can't open file ''{0}''", dbCurrentFile);
            SVNErrorManager.error(err);
        }

        /*
         * Check the FS format number (db/format). Treat an absent format
         * file as format 1. Do not try to create the format file on the fly,
         * because the repository might be read-only for us, or we might have a
         * umask such that even if we did create the format file, subsequent
         * users would not be able to read it. See thread starting at
         * http://subversion.tigris.org/servlets/ReadMsg?list=dev&msgNo=97600
         * for more.
         */
        int dbFormatNumber = FSRepositoryUtil.getFormat(FSRepositoryUtil.getFSFormatFile(myRepositoryRoot), false, FSConstants.SVN_FS_FORMAT_NUMBER);
        if (dbFormatNumber != FSConstants.SVN_FS_FORMAT_NUMBER) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_UNSUPPORTED_FORMAT, "Expected FS format ''{0,number,integer}''; found format ''{1,number,integer}''", new Object[]{new Integer(FSConstants.SVN_FS_FORMAT_NUMBER), new Integer(dbFormatNumber)});
            SVNErrorManager.error(err);
        }
    }
    
    public FSRoot createRevisionRoot(long revision) {
        return null;
    }
    
    public FSRevisionNode createRevisionNode(FSID id) throws SVNException  {
        FSFile revFileReader = null;

        if (id.isTxn()) {
            /* This is a transaction node-rev. */
            File revFile = FSRepositoryUtil.getTxnRevNodeFile(id, myRepositoryRoot);
            revFileReader = new FSFile(revFile);
        } else {
            /* This is a revision node-rev. */
            revFileReader = getRevisionFile(id.getRevision());
            revFileReader.seek(id.getOffset());
        }

        Map headers = null;
        try{
            headers = revFileReader.readHeader();
        }finally{
            revFileReader.close();
        }
        
        return FSRevisionNode.fromMap(headers);
    }
    
    protected FSFile getRevisionFile(long revision)  throws SVNException {
        File revFile = new File(FSRepositoryUtil.getRevisionsDir(myRepositoryRoot), String.valueOf(revision));
        if (!revFile.exists()) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_NO_SUCH_REVISION, "No such revision {0,number,integer}", new Long(revision));
            SVNErrorManager.error(err);
        }
        return new FSFile(revFile);
    }

    protected FSFile getRevisionPropertiesFile(long revision) {
        return null;
    }
    
}
