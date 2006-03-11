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
    
    private static final int REPOSITORY_FORMAT = 3;
    private static final int DB_FORMAT = 1;
    private static final String DB_TYPE = "fsfs";
    
    private String myUUID;
    
    private File myRepositoryRoot;
    private File myRevisionsRoot;
    private File myRevisionPropertiesRoot;
    private File myTransactionsRoot;
    private File myDBRoot;

    public FSFS(File repositoryRoot) {
        myRepositoryRoot = repositoryRoot;
        myDBRoot = new File(myRepositoryRoot, "db");
        myRevisionsRoot = new File(myDBRoot, "revs");
        myRevisionPropertiesRoot = new File(myDBRoot, "revprops");
        myTransactionsRoot = new File(myDBRoot, "transactions");
    }
    
    public void open() throws SVNException {
        // repo format /root/format
        FSFile formatFile = new FSFile(new File(myRepositoryRoot, "format"));
        int format = -1;
        try {
            format = formatFile.readInt();
        } finally {
            formatFile.close();
        }
        if (format != REPOSITORY_FORMAT) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.REPOS_UNSUPPORTED_VERSION, "Expected format ''{0,number,integer}'' of repository; found format ''{1,number,integer}''", new Object[]{new Integer(REPOSITORY_FORMAT), new Integer(format)});
            SVNErrorManager.error(err);
        }
        // fs format /root/db/format
        formatFile = new FSFile(new File(myDBRoot, "format"));
        try {
            format = formatFile.readInt();
        } finally {
            formatFile.close();
        }
        if (format != DB_FORMAT) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_UNSUPPORTED_FORMAT, "Expected FS format ''{0,number,integer}''; found format ''{1,number,integer}''", new Object[]{new Integer(DB_FORMAT), new Integer(format)});
            SVNErrorManager.error(err);
        }

        // fs type /root/db/fs-type
        formatFile = new FSFile(new File(myDBRoot, "fs-type"));
        String fsType = null;
        try {
            fsType = formatFile.readLine(128);    
        } finally {
            formatFile.close();
        }
        if (!DB_TYPE.equals(fsType)) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_UNKNOWN_FS_TYPE, "Unsupported fs type ''{0}''", fsType);
            SVNErrorManager.error(err);
        }

        File dbCurrentFile = new File(myDBRoot, "current");
        if(!(dbCurrentFile.exists() && dbCurrentFile.canRead())){
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, "Can''t open file ''{0}''", dbCurrentFile);
            SVNErrorManager.error(err);
        }
        
        // uuid
        formatFile = new FSFile(new File(myDBRoot, "uuid"));
        try {
            myUUID = formatFile.readLine(38);
        } finally {
            formatFile.close();
        }
    }
    
    public String getUUID() {
        return myUUID;
    }
    
    public FSRoot createRevisionRoot(long revision) {
        return new FSRoot(this, revision);
    }
    
    public FSRevisionNode createRevisionNode(FSID id) throws SVNException  {
        FSFile revisionFile = null;

        if (id.isTxn()) {
            File file = new File(getTransactionDir(id.getTxnID()), "node" + id.getNodeID() + "." + id.getCopyID());
            revisionFile = new FSFile(file);
        } else {
            revisionFile = getRevisionFile(id.getRevision());
            revisionFile.seek(id.getOffset());
        }

        Map headers = null;
        try {
            headers = revisionFile.readHeader();
        } finally{
            revisionFile.close();
        }
        return FSRevisionNode.fromMap(headers);
    }
    
    protected FSFile getRevisionFile(long revision)  throws SVNException {
        File revisionFile = new File(myRevisionsRoot, String.valueOf(revision));
        if (!revisionFile.exists()) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_NO_SUCH_REVISION, "No such revision {0,number,integer}", new Long(revision));
            SVNErrorManager.error(err);
        }
        return new FSFile(revisionFile);
    }

    protected File getTransactionDir(String txnID) {
        return new File(myTransactionsRoot, txnID + ".txn");
    }
    
    protected FSFile getTransactionChangesFile(String txnID) {
        File file = new File(getTransactionDir(txnID), "changes");
        return new FSFile(file);
    }

    protected FSFile getRevisionPropertiesFile(long revision) {
        File file = new File(myRevisionPropertiesRoot, String.valueOf(revision));
        return new FSFile(file);
    }
}
