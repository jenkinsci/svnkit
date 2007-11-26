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

import java.io.OutputStream;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;
import java.util.Stack;

import org.tmatesoft.svn.core.SVNCommitInfo;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.SVNRevisionProperty;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.internal.util.SVNDate;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.io.ISVNDeltaConsumer;
import org.tmatesoft.svn.core.io.ISVNEditor;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.io.diff.SVNDiffWindow;
 
/**
 * @version 1.1.1
 * @author  TMate Software Ltd.
 */
public class FSCommitEditor implements ISVNEditor {

    private Map myPathsToLockTokens;
    private Collection myLockTokens;
    private String myBasePath;
    private FSTransactionInfo myTxn;
    private FSTransactionRoot myTxnRoot;
    private boolean isTxnOwner;
    private FSFS myFSFS;
    private FSRepository myRepository;
    private Stack myDirsStack;
    private FSDeltaConsumer myDeltaConsumer;
    private Map myCurrentFileProps;
    private String myCurrentFilePath;
    private FSCommitter myCommitter;
    private Map myRevProps;
    private String myAuthor;
    
    public FSCommitEditor(String path, String logMessage, String userName, Map lockTokens, boolean keepLocks, FSTransactionInfo txn, FSFS owner, FSRepository repository) {
        this(path, lockTokens, keepLocks, txn, owner, repository, null);
        myRevProps = new HashMap();
        if (userName != null) {
            myAuthor = userName;
            myRevProps.put(SVNRevisionProperty.AUTHOR, userName);
        }
        if (logMessage != null) {
            myRevProps.put(SVNRevisionProperty.LOG, logMessage);
        }
    }

    public FSCommitEditor(String path, Map lockTokens, boolean keepLocks, FSTransactionInfo txn, FSFS owner, FSRepository repository, Map revProps) {
        myPathsToLockTokens = !keepLocks ? lockTokens : null;
        myLockTokens = lockTokens != null ? lockTokens.values() : new LinkedList();
        myBasePath = path;
        myTxn = txn;
        isTxnOwner = txn == null;
        myRepository = repository;
        myFSFS = owner;
        myDirsStack = new Stack();
        myRevProps = revProps != null ? revProps : Collections.EMPTY_MAP;
    }

    public void targetRevision(long revision) throws SVNException {
        // does nothing
    }

    public void openRoot(long revision) throws SVNException {
        long youngestRev = myFSFS.getYoungestRevision();

        if (isTxnOwner) {
            myTxn = beginTransactionForCommit(youngestRev);
        } else {
            changeTransactionProperties(myTxn.getTxnId());
        }
        myTxnRoot = myFSFS.createTransactionRoot(myTxn);
        myCommitter = new FSCommitter(myFSFS, myTxnRoot, myTxn, myLockTokens, getAuthor());
        DirBaton dirBaton = new DirBaton(revision, myBasePath, false);
        myDirsStack.push(dirBaton);
    }

    private FSTransactionInfo beginTransactionForCommit(long baseRevision) throws SVNException {
        FSHooks.runStartCommitHook(myFSFS.getRepositoryRoot(), getAuthor());
        FSTransactionInfo txn = FSTransactionRoot.beginTransaction(baseRevision, FSTransactionRoot.SVN_FS_TXN_CHECK_LOCKS, myFSFS);
        changeTransactionProperties(txn.getTxnId());
        return txn;
    }

    private void changeTransactionProperties(String txnId) throws SVNException {
        for (Iterator iter = myRevProps.keySet().iterator(); iter.hasNext();) {
            String propName = (String) iter.next();
            String propValue = (String) myRevProps.get(propName);
            myFSFS.setTransactionProperty(txnId, propName, propValue);
        }
        }

    private String getAuthor() {
        if (myAuthor == null) {
            myAuthor = (String) myRevProps.get(SVNRevisionProperty.AUTHOR);
        }
        return myAuthor;
    }

    public void openDir(String path, long revision) throws SVNException {
        DirBaton parentBaton = (DirBaton) myDirsStack.peek();
        String fullPath = SVNPathUtil.getAbsolutePath(SVNPathUtil.append(myBasePath, path));
        SVNNodeKind kind = myTxnRoot.checkNodeKind(fullPath);
        if (kind == SVNNodeKind.NONE) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_NOT_DIRECTORY, "Path ''{0}'' not present", path);
            SVNErrorManager.error(err);
        }

        DirBaton dirBaton = new DirBaton(revision, fullPath, parentBaton.isCopied());
        myDirsStack.push(dirBaton);
    }

    public void deleteEntry(String path, long revision) throws SVNException {
        String fullPath = SVNPathUtil.getAbsolutePath(SVNPathUtil.append(myBasePath, path));
        SVNNodeKind kind = myTxnRoot.checkNodeKind(fullPath);

        if (kind == SVNNodeKind.NONE) {
            return;
        }

        FSRevisionNode existingNode = myTxnRoot.getRevisionNode(fullPath);
        long createdRev = existingNode.getCreatedRevision();
        if (FSRepository.isValidRevision(revision) && revision < createdRev) {
            SVNErrorManager.error(FSErrors.errorOutOfDate(fullPath, kind));
        }
        myCommitter.deleteNode(fullPath);
    }

    public void absentDir(String path) throws SVNException {
        // does nothing
    }

    public void absentFile(String path) throws SVNException {
        // does nothing
    }

    public void addDir(String path, String copyFromPath, long copyFromRevision) throws SVNException {
        DirBaton parentBaton = (DirBaton) myDirsStack.peek();
        String fullPath = SVNPathUtil.getAbsolutePath(SVNPathUtil.append(myBasePath, path));
        boolean isCopied = false;
        if (copyFromPath != null && FSRepository.isInvalidRevision(copyFromRevision)) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_GENERAL, "Got source path but no source revision for ''{0}''", fullPath);
            SVNErrorManager.error(err);
        } else if (copyFromPath != null) {
            SVNNodeKind kind = myTxnRoot.checkNodeKind(fullPath);
            if (kind != SVNNodeKind.NONE && !parentBaton.isCopied()) {
                SVNErrorManager.error(FSErrors.errorOutOfDate(fullPath, kind));
            }
            copyFromPath = myRepository.getRepositoryPath(copyFromPath);

            FSRevisionRoot copyRoot = myFSFS.createRevisionRoot(copyFromRevision);
            myCommitter.makeCopy(copyRoot, copyFromPath, fullPath, true);
            isCopied = true;
        } else {
            myCommitter.makeDir(fullPath);
        }

        DirBaton dirBaton = new DirBaton(SVNRepository.INVALID_REVISION, fullPath, isCopied);
        myDirsStack.push(dirBaton);
    }

    public void changeDirProperty(String name, String value) throws SVNException {
        DirBaton dirBaton = (DirBaton) myDirsStack.peek();
        if (FSRepository.isValidRevision(dirBaton.getBaseRevision())) {
            FSRevisionNode existingNode = myTxnRoot.getRevisionNode(dirBaton.getPath());
            long createdRev = existingNode.getCreatedRevision();
            if (dirBaton.getBaseRevision() < createdRev) {
                SVNErrorManager.error(FSErrors.errorOutOfDate(dirBaton.getPath(), SVNNodeKind.DIR));
            }
        }
        myCommitter.changeNodeProperty(dirBaton.getPath(), name, value);
    }

    private void changeNodeProperties(String path, Map propNamesToValues) throws SVNException {
        FSParentPath parentPath = null;
        Map properties = null;
        boolean done = false;
        boolean haveRealChanges = false;
        for (Iterator propNames = propNamesToValues.keySet().iterator(); propNames.hasNext();) {
            String propName = (String)propNames.next();
            if (!SVNProperty.isRegularProperty(propName)) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.REPOS_BAD_ARGS,
                        "Storage of non-regular property ''{0}'' is disallowed through the repository interface, and could indicate a bug in your client", propName);
                SVNErrorManager.error(err);
            }

            if (!done) {
                parentPath = myTxnRoot.openPath(path, true, true);

                if ((myTxnRoot.getTxnFlags() & FSTransactionRoot.SVN_FS_TXN_CHECK_LOCKS) != 0) {
                    FSCommitter.allowLockedOperation(myFSFS, path, getAuthor(), myLockTokens, false, false);
                }

                myCommitter.makePathMutable(parentPath, path);
                properties = parentPath.getRevNode().getProperties(myFSFS);
                
                done = true;
            }

            String propValue = (String)propNamesToValues.get(propName);
            if (properties.isEmpty() && propValue == null) {
                continue;
            }

            if (propName.equals(SVNProperty.MERGE_INFO)) {
                //TODO: FIXME
            }

            if (propValue == null) {
                properties.remove(propName);
            } else {
                properties.put(propName, propValue);
            }
            
            if (!haveRealChanges) {
                haveRealChanges = true;
            }
        }

        if (haveRealChanges) {
            myTxnRoot.setProplist(parentPath.getRevNode(), properties);
            myCommitter.addChange(path, parentPath.getRevNode().getId(), FSPathChangeKind.FS_PATH_CHANGE_MODIFY, false, true, SVNRepository.INVALID_REVISION, null);
        }
    }
    
    public void closeDir() throws SVNException {
        flushPendingProperties();
        myDirsStack.pop();
    }

    public void addFile(String path, String copyFromPath, long copyFromRevision) throws SVNException {
        DirBaton parentBaton = (DirBaton) myDirsStack.peek();
        String fullPath = SVNPathUtil.getAbsolutePath(SVNPathUtil.append(myBasePath, path));
        if (copyFromPath != null && FSRepository.isInvalidRevision(copyFromRevision)) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_GENERAL, "Got source path but no source revision for ''{0}''", fullPath);
            SVNErrorManager.error(err);
        } else if (copyFromPath != null) {
            SVNNodeKind kind = myTxnRoot.checkNodeKind(fullPath);
            if (kind != SVNNodeKind.NONE && !parentBaton.isCopied()) {
                SVNErrorManager.error(FSErrors.errorOutOfDate(fullPath, kind));
            }
            copyFromPath = myRepository.getRepositoryPath(copyFromPath);

            FSRevisionRoot copyRoot = myFSFS.createRevisionRoot(copyFromRevision);
            myCommitter.makeCopy(copyRoot, copyFromPath, fullPath, true);
        } else {
            myCommitter.makeFile(fullPath);
        }
    }

    public void openFile(String path, long revision) throws SVNException {
        String fullPath = SVNPathUtil.getAbsolutePath(SVNPathUtil.append(myBasePath, path));
        FSRevisionNode revNode = myTxnRoot.getRevisionNode(fullPath);

        if (FSRepository.isValidRevision(revision) && revision < revNode.getCreatedRevision()) {
            SVNErrorManager.error(FSErrors.errorOutOfDate(fullPath, SVNNodeKind.FILE));
        }
    }

    public void applyTextDelta(String path, String baseChecksum) throws SVNException {
        flushPendingProperties();
        ISVNDeltaConsumer fsfsConsumer = getDeltaConsumer();
        fsfsConsumer.applyTextDelta(path, baseChecksum);
    }

    public OutputStream textDeltaChunk(String path, SVNDiffWindow diffWindow) throws SVNException {
        ISVNDeltaConsumer fsfsConsumer = getDeltaConsumer();
        return fsfsConsumer.textDeltaChunk(path, diffWindow);
    }

    public void textDeltaEnd(String path) throws SVNException {
        ISVNDeltaConsumer fsfsConsumer = getDeltaConsumer();
        fsfsConsumer.textDeltaEnd(path);
    }

    private FSDeltaConsumer getDeltaConsumer() {
        if (myDeltaConsumer == null) {
            myDeltaConsumer = new FSDeltaConsumer(myBasePath, myTxnRoot, myFSFS, myCommitter, getAuthor(), myLockTokens);
        }
        return myDeltaConsumer;
    }
    
    public void changeFileProperty(String path, String name, String value) throws SVNException {
        String fullPath = SVNPathUtil.getAbsolutePath(SVNPathUtil.append(myBasePath, path));
        Map props = getFilePropertiesStorage();
        if (!fullPath.equals(myCurrentFilePath)) {
            if (myCurrentFilePath != null) {
                changeNodeProperties(myCurrentFilePath, props);
                props.clear();
            }
            myCurrentFilePath = fullPath;
        }
        props.put(name, value);
    }

    private Map getFilePropertiesStorage() {
        if (myCurrentFileProps == null) {
            myCurrentFileProps = new HashMap();
        }
        return myCurrentFileProps;
    }
    
    private void flushPendingProperties() throws SVNException {
        if (myCurrentFilePath != null) {
            Map props = getFilePropertiesStorage();
            changeNodeProperties(myCurrentFilePath, props);
            props.clear();
            myCurrentFilePath = null;
        }
    }
    
    public void closeFile(String path, String textChecksum) throws SVNException {
        flushPendingProperties();
        
        if (textChecksum != null) {
            String fullPath = SVNPathUtil.getAbsolutePath(SVNPathUtil.append(myBasePath, path));
            FSRevisionNode revNode = myTxnRoot.getRevisionNode(fullPath);
            if (revNode.getTextRepresentation() != null && !textChecksum.equals(revNode.getTextRepresentation().getHexDigest())) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CHECKSUM_MISMATCH,
                        "Checksum mismatch for resulting fulltext\n({0}):\n   expected checksum:  {1}\n   actual checksum:    {2}\n", new Object[] {
                                fullPath, textChecksum, revNode.getTextRepresentation().getHexDigest()
                        });
                SVNErrorManager.error(err);
            }
        }
    }

    public SVNCommitInfo closeEdit() throws SVNException {
        if (myTxn == null) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.REPOS_BAD_ARGS, "No valid transaction supplied to closeEdit()");
            SVNErrorManager.error(err);
        }

        long committedRev = -1;
        SVNErrorMessage errorMessage = null;
        committedRev = finalizeCommit();
        try {
           FSHooks.runPostCommitHook(myFSFS.getRepositoryRoot(), committedRev);
        } catch (SVNException svne) {
            errorMessage = SVNErrorMessage.create(SVNErrorCode.REPOS_POST_COMMIT_HOOK_FAILED, svne.getErrorMessage().getFullMessage(), SVNErrorMessage.TYPE_WARNING);
        }

        Map revProps = myFSFS.getRevisionProperties(committedRev);
        String dateProp = (String) revProps.get(SVNRevisionProperty.DATE);
        Date datestamp = dateProp != null ? SVNDate.parseDateString(dateProp) : null;
        
        SVNCommitInfo info = new SVNCommitInfo(committedRev, getAuthor(), datestamp, errorMessage);
        releaseLocks();
        myRepository.closeRepository();
        return info;
    }

    private void releaseLocks() {
        if (myPathsToLockTokens != null) {
            for (Iterator paths = myPathsToLockTokens.keySet().iterator(); paths.hasNext();) {
                String path = (String) paths.next();
                String token = (String) myPathsToLockTokens.get(path);
                String absPath = !path.startsWith("/") ? SVNPathUtil.getAbsolutePath(SVNPathUtil.append(myBasePath, path)) : path;

                try {
                    myFSFS.unlockPath(absPath, token, getAuthor(), false, true);
                } catch (SVNException svne) {
                    // ignore exceptions
                }
            }
        }
    }

    private long finalizeCommit() throws SVNException {
        FSHooks.runPreCommitHook(myFSFS.getRepositoryRoot(), myTxn.getTxnId());
        return myCommitter.commitTxn();
    }

    public void abortEdit() throws SVNException {
        if (myDeltaConsumer != null) {
            myDeltaConsumer.abort();
        }

        if (myTxn == null || !isTxnOwner) {
            myRepository.closeRepository();
            return;
        }

        try {
            FSCommitter.abortTransaction(myFSFS, myTxn.getTxnId());
        } finally {
            myRepository.closeRepository();
        }

        myTxn = null;
        myTxnRoot = null;
    }

    private static class DirBaton {

        private long myBaseRevision;

        private String myPath;

        private boolean isCopied;

        public DirBaton(long revision, String path, boolean copied) {
            myBaseRevision = revision;
            myPath = path;
            isCopied = copied;
        }

        public boolean isCopied() {
            return isCopied;
        }

        public void setCopied(boolean isCopied) {
            this.isCopied = isCopied;
        }

        public long getBaseRevision() {
            return myBaseRevision;
        }

        public void setBaseRevision(long baseRevision) {
            myBaseRevision = baseRevision;
        }

        public String getPath() {
            return myPath;
        }

        public void setPath(String path) {
            myPath = path;
        }
    }

}
