/*
 * ====================================================================
 * Copyright (c) 2004-2006 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html.
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.core.wc.admin;

import java.io.File;
import java.util.Map;

import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNLogEntry;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNRevisionProperty;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationManager;
import org.tmatesoft.svn.core.internal.io.fs.FSFS;
import org.tmatesoft.svn.core.internal.io.fs.FSRevisionRoot;
import org.tmatesoft.svn.core.internal.io.fs.FSRoot;
import org.tmatesoft.svn.core.internal.io.fs.FSTransactionInfo;
import org.tmatesoft.svn.core.internal.util.SVNTimeUtil;
import org.tmatesoft.svn.core.internal.wc.SVNAdminHelper;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.wc.ISVNOptions;
import org.tmatesoft.svn.core.wc.ISVNRepositoryPool;
import org.tmatesoft.svn.core.wc.SVNBasicClient;
import org.tmatesoft.svn.core.wc.SVNRevision;


/**
 * @version 1.1
 * @author  TMate Software Ltd.
 */
public class SVNLookClient extends SVNBasicClient {

    public SVNLookClient(ISVNAuthenticationManager authManager, ISVNOptions options) {
        super(authManager, options);
    }

    public SVNLookClient(ISVNRepositoryPool repositoryPool, ISVNOptions options) {
        super(repositoryPool, options);
    }

    public SVNLogEntry doGetInfo(File repositoryRoot, SVNRevision revision) throws SVNException {
        FSFS fsfs = open(repositoryRoot, revision);
        long revNum = SVNAdminHelper.getRevisionNumber(revision, fsfs.getYoungestRevision(), fsfs);
        Map revProps = fsfs.getRevisionProperties(revNum);
        String date = (String) revProps.get(SVNRevisionProperty.DATE);
        String author = (String) revProps.get(SVNRevisionProperty.AUTHOR);
        String logMessage = (String) revProps.get(SVNRevisionProperty.LOG);
        return new SVNLogEntry(null, revNum, author, SVNTimeUtil.parseDateString(date), logMessage); 
    }

    public SVNLogEntry doGetInfo(File repositoryRoot, String transactionName) throws SVNException {
        FSFS fsfs = open(repositoryRoot, transactionName);
        FSTransactionInfo txn = fsfs.openTxn(transactionName);
        
        Map txnProps = fsfs.getTransactionProperties(txn.getTxnId());
        String date = (String) txnProps.get(SVNRevisionProperty.DATE);
        String author = (String) txnProps.get(SVNRevisionProperty.AUTHOR);
        String logMessage = (String) txnProps.get(SVNRevisionProperty.LOG);
        return new SVNLogEntry(null, -1, author, SVNTimeUtil.parseDateString(date), logMessage); 
    }

    public long doGetYoungestRevision(File repositoryRoot) throws SVNException {
        FSFS fsfs = SVNAdminHelper.openRepository(repositoryRoot);
        return fsfs.getYoungestRevision();
    }
    
    public String doGetUUID(File repositoryRoot) throws SVNException {
        FSFS fsfs = SVNAdminHelper.openRepository(repositoryRoot);
        return fsfs.getUUID();
    }
    
    public String doGetAuthor(File repositoryRoot, SVNRevision revision) throws SVNException {
        FSFS fsfs = open(repositoryRoot, revision);
        long revNum = SVNAdminHelper.getRevisionNumber(revision, fsfs.getYoungestRevision(), fsfs);
        Map revProps = fsfs.getRevisionProperties(revNum);
        return (String) revProps.get(SVNRevisionProperty.AUTHOR);
    }

    public String doGetAuthor(File repositoryRoot, String transactionName) throws SVNException {
        FSFS fsfs = open(repositoryRoot, transactionName);
        FSTransactionInfo txn = fsfs.openTxn(transactionName);
        Map txnProps = fsfs.getTransactionProperties(txn.getTxnId());
        return (String) txnProps.get(SVNRevisionProperty.AUTHOR);
    }
    
    public void doCat(File repositoryRoot, String path, SVNRevision revision) throws SVNException {
        if (path == null) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CL_INSUFFICIENT_ARGS, "Missing repository path argument");
            SVNErrorManager.error(err);
        }

        FSFS fsfs = open(repositoryRoot, revision);
        long revNum = SVNAdminHelper.getRevisionNumber(revision, fsfs.getYoungestRevision(), fsfs);
        FSRevisionRoot root = fsfs.createRevisionRoot(revNum);
        
        SVNNodeKind kind = verifyPath(root, path);
        if (kind != SVNNodeKind.FILE) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_NOT_FILE, "Path ''{0}'' is not a file", path);
            SVNErrorManager.error(err);
        }
        
        //root.getFileStreamForPath(new, path)
    }
    
    private SVNNodeKind verifyPath(FSRoot root, String path) throws SVNException {
        SVNNodeKind kind = root.checkNodeKind(path);
        if (kind == SVNNodeKind.NONE) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_NOT_FOUND, "Path ''{0}'' does not exist", path);
            SVNErrorManager.error(err);
        }
        return kind;
    }
    
    private FSFS open(File repositoryRoot, SVNRevision revision) throws SVNException {
        if (revision == null || !revision.isValid()) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CL_ARG_PARSING_ERROR, "Invalid revision number supplied");
            SVNErrorManager.error(err);
        }
        
        FSFS fsfs = SVNAdminHelper.openRepository(repositoryRoot);
        return fsfs;
    }
    
    private FSFS open(File repositoryRoot, String transactionName) throws SVNException {
        if (transactionName == null) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CL_ARG_PARSING_ERROR, "Invalid transaction name supplied");
            SVNErrorManager.error(err);
        }

        return SVNAdminHelper.openRepository(repositoryRoot);
    }
}
