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
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Date;
import java.util.Map;

import org.tmatesoft.svn.core.ISVNDirEntryHandler;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNLogEntry;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNRevisionProperty;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationManager;
import org.tmatesoft.svn.core.internal.delta.SVNDeltaCombiner;
import org.tmatesoft.svn.core.internal.io.fs.FSFS;
import org.tmatesoft.svn.core.internal.io.fs.FSNodeHistory;
import org.tmatesoft.svn.core.internal.io.fs.FSRepositoryUtil;
import org.tmatesoft.svn.core.internal.io.fs.FSRevisionNode;
import org.tmatesoft.svn.core.internal.io.fs.FSRevisionRoot;
import org.tmatesoft.svn.core.internal.io.fs.FSRoot;
import org.tmatesoft.svn.core.internal.io.fs.FSTransactionInfo;
import org.tmatesoft.svn.core.internal.util.SVNTimeUtil;
import org.tmatesoft.svn.core.internal.wc.SVNAdminHelper;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.internal.wc.SVNNodeEditor;
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
    
    public void doCat(File repositoryRoot, String path, SVNRevision revision, OutputStream out) throws SVNException {
        if (path == null) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CL_INSUFFICIENT_ARGS, "Missing repository path argument");
            SVNErrorManager.error(err);
        }

        FSFS fsfs = open(repositoryRoot, revision);
        long revNum = SVNAdminHelper.getRevisionNumber(revision, fsfs.getYoungestRevision(), fsfs);
        FSRoot root = fsfs.createRevisionRoot(revNum);
        catFile(root, path, out);
    }

    public void doCat(File repositoryRoot, String path, String transactionName, OutputStream out) throws SVNException {
        if (path == null) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CL_INSUFFICIENT_ARGS, "Missing repository path argument");
            SVNErrorManager.error(err);
        }

        FSFS fsfs = open(repositoryRoot, transactionName);
        FSTransactionInfo txn = fsfs.openTxn(transactionName);
        FSRoot root = fsfs.createTransactionRoot(txn.getTxnId());
        catFile(root, path, out);
    }
    
    public Date doGetDate(File repositoryRoot, SVNRevision revision) throws SVNException {
        FSFS fsfs = open(repositoryRoot, revision);
        long revNum = SVNAdminHelper.getRevisionNumber(revision, fsfs.getYoungestRevision(), fsfs);
        Map revProps = fsfs.getRevisionProperties(revNum);
        String date = (String) revProps.get(SVNRevisionProperty.DATE);
        if (date != null) {
            return SVNTimeUtil.parseDate(date);
        }
        return null;
    }

    public Date doGetDate(File repositoryRoot, String transactionName) throws SVNException {
        FSFS fsfs = open(repositoryRoot, transactionName);
        FSTransactionInfo txn = fsfs.openTxn(transactionName);
        Map txnProps = fsfs.getTransactionProperties(txn.getTxnId());
        String date = (String) txnProps.get(SVNRevisionProperty.DATE);
        if (date != null) {
            return SVNTimeUtil.parseDate(date);
        }
        return null;
    }

    public String doGetLog(File repositoryRoot, SVNRevision revision) throws SVNException {
        FSFS fsfs = open(repositoryRoot, revision);
        long revNum = SVNAdminHelper.getRevisionNumber(revision, fsfs.getYoungestRevision(), fsfs);
        Map revProps = fsfs.getRevisionProperties(revNum);
        return (String) revProps.get(SVNRevisionProperty.LOG);
    }

    public String doGetLog(File repositoryRoot, String transactionName) throws SVNException {
        FSFS fsfs = open(repositoryRoot, transactionName);
        FSTransactionInfo txn = fsfs.openTxn(transactionName);
        Map txnProps = fsfs.getTransactionProperties(txn.getTxnId());
        return (String) txnProps.get(SVNRevisionProperty.LOG);
    }

    public void doGetChanged(File repositoryRoot, SVNRevision revision, ISVNChangeEntryHandler handler, boolean includeCopyInfo) throws SVNException {
        FSFS fsfs = open(repositoryRoot, revision);
        long revNum = SVNAdminHelper.getRevisionNumber(revision, fsfs.getYoungestRevision(), fsfs);
        FSRoot root = fsfs.createRevisionRoot(revNum);
        long baseRevision = revNum - 1;
        SVNNodeEditor editor = generateDeltaTree(fsfs, root, baseRevision);
        editor.traverseTree(includeCopyInfo, handler);
    }

    public void doGetChanged(File repositoryRoot, String transactionName, ISVNChangeEntryHandler handler, boolean includeCopyInfo) throws SVNException {
        FSFS fsfs = open(repositoryRoot, transactionName);
        FSTransactionInfo txn = fsfs.openTxn(transactionName);
        FSRoot root = fsfs.createTransactionRoot(txn.getTxnId());
        long baseRevision = txn.getBaseRevision(); 

        if (!SVNRevision.isValidRevisionNumber(baseRevision)) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_NO_SUCH_REVISION, "Transaction ''{0}'' is not based on a revision; how odd", transactionName);
            SVNErrorManager.error(err);
        }
        SVNNodeEditor editor = generateDeltaTree(fsfs, root, baseRevision);
        editor.traverseTree(includeCopyInfo, handler);
    }
    
    public void doGetChangedDirectories(File repositoryRoot, SVNRevision revision, ISVNDirEntryHandler handler) throws SVNException {
        FSFS fsfs = open(repositoryRoot, revision);
        long revNum = SVNAdminHelper.getRevisionNumber(revision, fsfs.getYoungestRevision(), fsfs);
        FSRoot root = fsfs.createRevisionRoot(revNum);
        long baseRevision = revNum - 1;
        SVNNodeEditor editor = generateDeltaTree(fsfs, root, baseRevision);
        editor.traverseChangedDirs(handler);
    }

    public void doGetChangedDirectories(File repositoryRoot, String transactionName, ISVNDirEntryHandler handler) throws SVNException {
        FSFS fsfs = open(repositoryRoot, transactionName);
        FSTransactionInfo txn = fsfs.openTxn(transactionName);
        FSRoot root = fsfs.createTransactionRoot(txn.getTxnId());
        long baseRevision = txn.getBaseRevision(); 

        if (!SVNRevision.isValidRevisionNumber(baseRevision)) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_NO_SUCH_REVISION, "Transaction ''{0}'' is not based on a revision; how odd", transactionName);
            SVNErrorManager.error(err);
        }
        SVNNodeEditor editor = generateDeltaTree(fsfs, root, baseRevision);
        editor.traverseChangedDirs(handler);
    }
    
    public void doGetHistory(File repositoryRoot, String path, SVNRevision revision, boolean includeIDs, ISVNHistoryHandler handler) throws SVNException {
        FSFS fsfs = open(repositoryRoot, revision);
        long revNum = SVNAdminHelper.getRevisionNumber(revision, fsfs.getYoungestRevision(), fsfs);
        path = path == null ? "/" : path;
        getHistory(fsfs, path, 0, revNum, true, includeIDs, handler);
    }
    
    private void getHistory(FSFS fsfs, String path, long start, long end, boolean crossCopies, boolean includeIDs, ISVNHistoryHandler handler) throws SVNException {
        if (!SVNRevision.isValidRevisionNumber(start)) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_NO_SUCH_REVISION, "Invalid start revision {0,number,integer}", new Long(start));
            SVNErrorManager.error(err);
        }
        if (!SVNRevision.isValidRevisionNumber(end)) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_NO_SUCH_REVISION, "Invalid end revision {0,number,integer}", new Long(end));
            SVNErrorManager.error(err);
        }
        
        if (start > end) {
            long tmpRev = start;
            start = end;
            end = tmpRev;
        }
        
        FSRevisionRoot root = fsfs.createRevisionRoot(end);
        FSNodeHistory history = FSNodeHistory.getNodeHistory(root, path);
        
        do {
            history = history.fsHistoryPrev(crossCopies, fsfs);
            if (history == null) {
                break;
            }
            
            long revision = history.getHistoryEntry().getRevision();
            if (revision < start) {
                break;
            }
            
            String id = null;
            if (includeIDs) {
                FSRevisionRoot revRoot = fsfs.createRevisionRoot(revision);
                FSRevisionNode node = revRoot.getRevisionNode(history.getHistoryEntry().getPath());
                id = node.getId().toString();
            }

            if (handler != null) {
                handler.handleRevision(revision, history.getHistoryEntry().getPath(), id);
            }
        } while (history != null);

    }
    
    private SVNNodeEditor generateDeltaTree(FSFS fsfs, FSRoot root, long baseRevision) throws SVNException {
        FSRevisionRoot baseRoot = fsfs.createRevisionRoot(baseRevision);
        SVNNodeEditor editor = new SVNNodeEditor(fsfs, baseRoot, this);
        FSRepositoryUtil.replay(fsfs, root, "", -1, false, editor);
        return editor;
    }
    
    private void catFile(FSRoot root, String path, OutputStream out) throws SVNException {
        SVNNodeKind kind = verifyPath(root, path);
        if (kind != SVNNodeKind.FILE) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_NOT_FILE, "Path ''{0}'' is not a file", path);
            SVNErrorManager.error(err);
        }
        
        if (out != null) {
            InputStream contents = null;
            try {
                contents = root.getFileStreamForPath(new SVNDeltaCombiner(), path);
                byte[] buffer = new byte[SVNAdminHelper.STREAM_CHUNK_SIZE];
                int len = 0;
                do {
                    checkCancelled();
                    len = contents.read(buffer);
                    out.write(buffer, 0, len);
                } while (len == SVNAdminHelper.STREAM_CHUNK_SIZE);
            } catch (IOException ioe) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, ioe.getLocalizedMessage());
                SVNErrorManager.error(err, ioe);
            } finally {
                SVNFileUtil.closeFile(contents);
            }
        }
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
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CL_INSUFFICIENT_ARGS, "Missing transaction name");
            SVNErrorManager.error(err);
        }

        return SVNAdminHelper.openRepository(repositoryRoot);
    }
}
