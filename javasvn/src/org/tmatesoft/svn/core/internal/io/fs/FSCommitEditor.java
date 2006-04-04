/*
 * ====================================================================
 * Copyright (c) 2004 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://tmate.org/svn/license.html.
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.core.internal.io.fs;

import java.io.OutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Collection;
import java.util.LinkedList;
import java.util.Iterator;
import java.util.Stack;
import java.io.File;
import java.util.Arrays;
import java.util.Date;

import org.tmatesoft.svn.core.SVNCommitInfo;
import org.tmatesoft.svn.core.SVNLock;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNRevisionProperty;
import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.io.ISVNEditor;
import org.tmatesoft.svn.core.io.ISVNLockHandler;
import org.tmatesoft.svn.core.io.diff.SVNDiffWindow;
import org.tmatesoft.svn.core.internal.delta.SVNDeltaCombiner;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.internal.util.SVNTimeUtil;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.io.diff.SVNDeltaProcessor;

/**
 * @version 1.0
 * @author TMate Software Ltd.
 */
public class FSCommitEditor implements ISVNEditor {

    private Map myPathsToLockTokens;
    private Collection myLockTokens;
    private String myAuthor;
    private String myBasePath;
    private String myLogMessage;
    private FSTransactionInfo myTxn;
    private FSTransactionRoot myTxnRoot;
    private boolean isTxnOwner;
    private FSFS myFSFS;
    private FSRepository myRepository;
    private Stack myDirsStack;
    private FSOutputStream myTargetStream;
    private SVNDeltaProcessor myDeltaProcessor;

    public FSCommitEditor(String path, String logMessage, String userName, Map lockTokens, boolean keepLocks, FSTransactionInfo txn, FSFS owner, FSRepository repository) {
        myPathsToLockTokens = !keepLocks ? lockTokens : null;
        myLockTokens = lockTokens != null ? lockTokens.values() : new LinkedList();
        myAuthor = userName;
        myBasePath = path;
        myLogMessage = logMessage;
        myTxn = txn;
        isTxnOwner = txn == null ? true : false;
        myRepository = repository;
        myFSFS = owner;
        myDirsStack = new Stack();
    }

    public void targetRevision(long revision) throws SVNException {
        // does nothing
    }

    public void openRoot(long revision) throws SVNException {
        long youngestRev = myFSFS.getYoungestRevision();

        if (isTxnOwner) {
            myTxn = beginTransactionForCommit(youngestRev);
        } else {
            if (myAuthor != null && !"".equals(myAuthor)) {
                myFSFS.setTransactionProperty(myTxn.getTxnId(), SVNRevisionProperty.AUTHOR, myAuthor);
            }
            if (myLogMessage != null && !"".equals(myLogMessage)) {
                myFSFS.setTransactionProperty(myTxn.getTxnId(), SVNRevisionProperty.LOG, myLogMessage);
            }
        }
        Map txnProps = myFSFS.getTransactionProperties(myTxn.getTxnId());
        int flags = 0;
        if (txnProps.get(SVNProperty.TXN_CHECK_OUT_OF_DATENESS) != null) {
            flags |= FSTransactionRoot.SVN_FS_TXN_CHECK_OUT_OF_DATENESS;
        }
        if (txnProps.get(SVNProperty.TXN_CHECK_LOCKS) != null) {
            flags |= FSTransactionRoot.SVN_FS_TXN_CHECK_LOCKS;
        }
        myTxnRoot = myFSFS.createTransactionRoot(myTxn.getTxnId(), flags);
        DirBaton dirBaton = new DirBaton(revision, myBasePath, false);
        myDirsStack.push(dirBaton);
    }

    private FSTransactionInfo beginTransactionForCommit(long baseRevision) throws SVNException {
        FSHooks.runStartCommitHook(myFSFS.getRepositoryRoot(), myAuthor);
        FSTransactionInfo txn = FSTransactionRoot.beginTransaction(baseRevision, FSTransactionRoot.SVN_FS_TXN_CHECK_LOCKS, myFSFS);

        if (myAuthor != null && !"".equals(myAuthor)) {
            myFSFS.setTransactionProperty(txn.getTxnId(), SVNRevisionProperty.AUTHOR, myAuthor);
        }

        if (myLogMessage != null && !"".equals(myLogMessage)) {
            myFSFS.setTransactionProperty(txn.getTxnId(), SVNRevisionProperty.LOG, myLogMessage);
        }
        return txn;
    }

    public void openDir(String path, long revision) throws SVNException {
        DirBaton parentBaton = (DirBaton) myDirsStack.peek();
        String fullPath = SVNPathUtil.concatToAbs(myBasePath, path);
        SVNNodeKind kind = myTxnRoot.checkNodeKind(fullPath);
        if (kind == SVNNodeKind.NONE) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_NOT_DIRECTORY, "Path ''{0}'' not present", path);
            SVNErrorManager.error(err);
        }

        DirBaton dirBaton = new DirBaton(revision, fullPath, parentBaton.isCopied());
        myDirsStack.push(dirBaton);
    }

    public void deleteEntry(String path, long revision) throws SVNException {
        String fullPath = SVNPathUtil.concatToAbs(myBasePath, path);
        SVNNodeKind kind = myTxnRoot.checkNodeKind(fullPath);

        if (kind == SVNNodeKind.NONE) {
            return;
        }

        FSRevisionNode existingNode = myTxnRoot.getRevisionNode(fullPath);
        long createdRev = existingNode.getId().getRevision();
        if (FSRepository.isValidRevision(revision) && revision < createdRev) {
            SVNErrorManager.error(FSErrors.errorOutOfDate(fullPath, myTxnRoot.getTxnID()));
        }
        deleteNode(fullPath);
    }

    private void deleteNode(String path) throws SVNException {
        FSParentPath parentPath = myTxnRoot.openPath(path, true, true);

        if (parentPath.getParent() == null) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_ROOT_DIR, "The root directory cannot be deleted");
            SVNErrorManager.error(err);
        }

        if ((myTxnRoot.getTxnFlags() & FSTransactionRoot.SVN_FS_TXN_CHECK_LOCKS) != 0) {
            allowLockedOperation(path, myAuthor, myLockTokens, true, false);
        }

        makePathMutable(parentPath.getParent(), path);
        myTxnRoot.deleteEntry(parentPath.getParent().getRevNode(), parentPath.getNameEntry());
        myTxnRoot.removeRevNodeFromCache(parentPath.getAbsPath());
        addChange(path, parentPath.getRevNode().getId(), FSPathChangeKind.FS_PATH_CHANGE_DELETE, false, false, FSRepository.SVN_INVALID_REVNUM, null);
    }

    public void absentDir(String path) throws SVNException {
        // does nothing
    }

    public void absentFile(String path) throws SVNException {
        // does nothing
    }

    public void addDir(String path, String copyFromPath, long copyFromRevision) throws SVNException {
        DirBaton parentBaton = (DirBaton) myDirsStack.peek();
        String fullPath = SVNPathUtil.concatToAbs(myBasePath, path);
        boolean isCopied = false;
        if (copyFromPath != null && FSRepository.isInvalidRevision(copyFromRevision)) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_GENERAL, "Got source path but no source revision for ''{0}''", fullPath);
            SVNErrorManager.error(err);
        } else if (copyFromPath != null) {
            SVNNodeKind kind = myTxnRoot.checkNodeKind(fullPath);
            if (kind != SVNNodeKind.NONE && !parentBaton.isCopied()) {
                SVNErrorManager.error(FSErrors.errorOutOfDate(fullPath, myTxnRoot.getTxnID()));
            }
            copyFromPath = myRepository.getRepositoryPath(copyFromPath);

            FSRevisionRoot copyRoot = myFSFS.createRevisionRoot(copyFromRevision);// FSOldRoot.createRevisionRoot(copyFromRevision,
                                                                                    // myRevNodesPool.getRootRevisionNode(copyFromRevision,
                                                                                    // myReposRootDir),
                                                                                    // myReposRootDir);
            makeCopy(copyRoot, copyFromPath, fullPath, true);
            isCopied = true;
        } else {
            makeDir(fullPath);
        }

        DirBaton dirBaton = new DirBaton(FSRepository.SVN_INVALID_REVNUM, fullPath, isCopied);
        myDirsStack.push(dirBaton);
    }

    private void makeDir(String path) throws SVNException {
        String txnId = myTxnRoot.getTxnID();
        FSParentPath parentPath = myTxnRoot.openPath(path, false, true);

        if (parentPath.getRevNode() != null) {
            SVNErrorManager.error(FSErrors.errorAlreadyExists(myTxnRoot, path, myFSFS));
        }

        if ((myTxnRoot.getTxnFlags() & FSTransactionRoot.SVN_FS_TXN_CHECK_LOCKS) != 0) {
            allowLockedOperation(path, myAuthor, myLockTokens, true, false);
        }

        makePathMutable(parentPath.getParent(), path);
        FSRevisionNode subDirNode = makeEntry(parentPath.getParent().getRevNode(), parentPath.getParent().getAbsPath(), parentPath.getNameEntry(), true, txnId);

        myTxnRoot.putRevNodeToCache(parentPath.getAbsPath(), subDirNode);
        addChange(path, subDirNode.getId(), FSPathChangeKind.FS_PATH_CHANGE_ADD, false, false, FSRepository.SVN_INVALID_REVNUM, null);
    }

    private FSRevisionNode makeEntry(FSRevisionNode parent, String parentPath, String entryName, boolean isDir, String txnId) throws SVNException {
        if (!SVNPathUtil.isSinglePathComponent(entryName)) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_NOT_SINGLE_PATH_COMPONENT, "Attempted to create a node with an illegal name ''{0}''", entryName);
            SVNErrorManager.error(err);
        }

        if (parent.getType() != SVNNodeKind.DIR) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_NOT_DIRECTORY, "Attempted to create entry in non-directory parent");
            SVNErrorManager.error(err);
        }

        if (!parent.getId().isTxn()) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_NOT_MUTABLE, "Attempted to clone child of non-mutable node");
            SVNErrorManager.error(err);
        }

        FSRevisionNode newRevNode = new FSRevisionNode();
        newRevNode.setType(isDir ? SVNNodeKind.DIR : SVNNodeKind.FILE);
        newRevNode.setCreatedPath(SVNPathUtil.concatToAbs(parentPath, entryName));
        newRevNode.setCopyRootPath(parent.getCopyRootPath());
        newRevNode.setCopyRootRevision(parent.getCopyRootRevision());
        newRevNode.setCopyFromRevision(FSRepository.SVN_INVALID_REVNUM);
        newRevNode.setCopyFromPath(null);
        FSID newNodeId = createNode(newRevNode, parent.getId().getCopyID(), txnId);

        FSRevisionNode childNode = myFSFS.getRevisionNode(newNodeId);

        myTxnRoot.setEntry(parent, entryName, childNode.getId(), newRevNode.getType());
        return childNode;
    }

    private FSID createNode(FSRevisionNode revNode, String copyId, String txnId) throws SVNException {
        String nodeId = getNewTxnNodeId();
        FSID id = FSID.createTxnId(nodeId, copyId, txnId);
        revNode.setId(id);
        myFSFS.putTxnRevisionNode(id, revNode);
        return id;
    }

    private String getNewTxnNodeId() throws SVNException {
        String[] curIds = myTxnRoot.readNextIDs();
        String curNodeId = curIds[0];
        String curCopyId = curIds[1];
        String nextNodeId = FSTransactionRoot.generateNextKey(curNodeId);
        myFSFS.writeNextIDs(myTxnRoot.getTxnID(), nextNodeId, curCopyId);
        return "_" + nextNodeId;
    }

    private void makeCopy(FSRevisionRoot fromRoot, String fromPath, String toPath, boolean preserveHistory) throws SVNException {
        String txnId = myTxnRoot.getTxnID();
        FSRevisionNode fromNode = fromRoot.getRevisionNode(fromPath);

        FSParentPath toParentPath = myTxnRoot.openPath(toPath, false, true);
        if ((myTxnRoot.getTxnFlags() & FSTransactionRoot.SVN_FS_TXN_CHECK_LOCKS) != 0) {
            allowLockedOperation(toPath, myAuthor, myLockTokens, true, false);
        }

        if (toParentPath.getRevNode() != null && toParentPath.getRevNode().getId().equals(fromNode.getId())) {
            return;
        }

        FSPathChangeKind changeKind;
        if (toParentPath.getRevNode() != null) {
            changeKind = FSPathChangeKind.FS_PATH_CHANGE_REPLACE;
        } else {
            changeKind = FSPathChangeKind.FS_PATH_CHANGE_ADD;
        }

        makePathMutable(toParentPath.getParent(), toPath);
        String fromCanonPath = fromPath;
        copy(toParentPath.getParent().getRevNode(), toParentPath.getNameEntry(), fromNode, preserveHistory, fromRoot.getRevision(), fromCanonPath, txnId);

        if (changeKind == FSPathChangeKind.FS_PATH_CHANGE_REPLACE) {
            myTxnRoot.removeRevNodeFromCache(toParentPath.getAbsPath());
        }

        FSRevisionNode newNode = myTxnRoot.getRevisionNode(toPath);
        addChange(toPath, newNode.getId(), changeKind, false, false, fromRoot.getRevision(), fromCanonPath);
    }

    private void addChange(String path, FSID id, FSPathChangeKind changeKind, boolean textModified, boolean propsModified, long copyFromRevision, String copyFromPath) throws SVNException {
        OutputStream changesFile = null;
        try {
            changesFile = SVNFileUtil.openFileForWriting(myTxnRoot.getTransactionChangesFile(), true);

            FSPathChange pathChange = new FSPathChange(path, id, changeKind, textModified, propsModified, copyFromPath, copyFromRevision);
            myTxnRoot.writeChangeEntry(changesFile, pathChange);
        } catch (IOException ioe) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, ioe.getLocalizedMessage());
            SVNErrorManager.error(err, ioe);
        } finally {
            SVNFileUtil.closeFile(changesFile);
        }
    }

    private void copy(FSRevisionNode toNode, String entryName, FSRevisionNode fromNode, boolean preserveHistory, long fromRevision, String fromPath, String txnId) throws SVNException {
        FSID id = null;
        if (preserveHistory) {
            FSID srcId = fromNode.getId();
            FSRevisionNode toRevNode = FSRevisionNode.dumpRevisionNode(fromNode);
            String copyId = reserveCopyId(txnId);

            toRevNode.setPredecessorId(srcId);
            if (toRevNode.getCount() != -1) {
                toRevNode.setCount(toRevNode.getCount() + 1);
            }
            toRevNode.setCreatedPath(SVNPathUtil.concatToAbs(toNode.getCreatedPath(), entryName));
            toRevNode.setCopyFromPath(fromPath);
            toRevNode.setCopyFromRevision(fromRevision);

            toRevNode.setCopyRootPath(null);
            id = myTxnRoot.createSuccessor(srcId, toRevNode, copyId);
        } else {
            id = fromNode.getId();
        }

        myTxnRoot.setEntry(toNode, entryName, id, fromNode.getType());
    }

    public void changeDirProperty(String name, String value) throws SVNException {
        DirBaton dirBaton = (DirBaton) myDirsStack.peek();
        if (FSRepository.isValidRevision(dirBaton.getBaseRevision())) {
            FSRevisionNode existingNode = myTxnRoot.getRevisionNode(dirBaton.getPath());
            long createdRev = existingNode.getId().getRevision();
            if (dirBaton.getBaseRevision() < createdRev) {
                SVNErrorManager.error(FSErrors.errorOutOfDate(dirBaton.getPath(), myTxnRoot.getTxnID()));
            }
        }
        changeNodeProperty(dirBaton.getPath(), name, value);
    }

    private void changeNodeProperty(String path, String propName, String propValue) throws SVNException {
        if (!SVNProperty.isRegularProperty(propName)) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.REPOS_BAD_ARGS,
                    "Storage of non-regular property ''{0}'' is disallowed through the repository interface, and could indicate a bug in your client", propName);
            SVNErrorManager.error(err);
        }

        FSParentPath parentPath = myTxnRoot.openPath(path, true, true);

        if ((myTxnRoot.getTxnFlags() & FSTransactionRoot.SVN_FS_TXN_CHECK_LOCKS) != 0) {
            allowLockedOperation(path, myAuthor, myLockTokens, false, false);
        }

        makePathMutable(parentPath, path);
        Map properties = parentPath.getRevNode().getProperties(myFSFS);

        if (properties.isEmpty() && propValue == null) {
            return;
        }

        if (propValue == null) {
            properties.remove(propName);
        } else {
            properties.put(propName, propValue);
        }

        myTxnRoot.setProplist(parentPath.getRevNode(), properties);
        addChange(path, parentPath.getRevNode().getId(), FSPathChangeKind.FS_PATH_CHANGE_MODIFY, false, true, FSRepository.SVN_INVALID_REVNUM, null);
    }

    public void closeDir() throws SVNException {
        myDirsStack.pop();
    }

    public void addFile(String path, String copyFromPath, long copyFromRevision) throws SVNException {
        DirBaton parentBaton = (DirBaton) myDirsStack.peek();
        String fullPath = SVNPathUtil.concatToAbs(myBasePath, path);
        if (copyFromPath != null && FSRepository.isInvalidRevision(copyFromRevision)) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_GENERAL, "Got source path but no source revision for ''{0}''", fullPath);
            SVNErrorManager.error(err);
        } else if (copyFromPath != null) {
            SVNNodeKind kind = myTxnRoot.checkNodeKind(fullPath);
            if (kind != SVNNodeKind.NONE && !parentBaton.isCopied()) {
                SVNErrorManager.error(FSErrors.errorOutOfDate(fullPath, myTxnRoot.getTxnID()));
            }
            copyFromPath = myRepository.getRepositoryPath(copyFromPath);

            FSRevisionRoot copyRoot = myFSFS.createRevisionRoot(copyFromRevision);
            makeCopy(copyRoot, copyFromPath, fullPath, true);
        } else {
            makeFile(fullPath);
        }
    }

    private void makeFile(String path) throws SVNException {
        String txnId = myTxnRoot.getTxnID();
        FSParentPath parentPath = myTxnRoot.openPath(path, false, true);

        if (parentPath.getRevNode() != null) {
            SVNErrorManager.error(FSErrors.errorAlreadyExists(myTxnRoot, path, myFSFS));
        }

        if ((myTxnRoot.getTxnFlags() & FSTransactionRoot.SVN_FS_TXN_CHECK_LOCKS) != 0) {
            allowLockedOperation(path, myAuthor, myLockTokens, false, false);
        }

        makePathMutable(parentPath.getParent(), path);
        FSRevisionNode childNode = makeEntry(parentPath.getParent().getRevNode(), parentPath.getParent().getAbsPath(), parentPath.getNameEntry(), false, txnId);

        myTxnRoot.putRevNodeToCache(parentPath.getAbsPath(), childNode);
        addChange(path, childNode.getId(), FSPathChangeKind.FS_PATH_CHANGE_ADD, false, false, FSRepository.SVN_INVALID_REVNUM, null);
    }

    public void openFile(String path, long revision) throws SVNException {
        String fullPath = SVNPathUtil.concatToAbs(myBasePath, path);
        FSRevisionNode revNode = myTxnRoot.getRevisionNode(fullPath);

        if (FSRepository.isValidRevision(revision) && revision < revNode.getId().getRevision()) {
            SVNErrorManager.error(FSErrors.errorOutOfDate(fullPath, myTxnRoot.getTxnID()));
        }
    }

    public void applyTextDelta(String path, String baseChecksum) throws SVNException {
        String fullPath = SVNPathUtil.concatToAbs(myBasePath, path);
        FSParentPath parentPath = myTxnRoot.openPath(fullPath, true, true);

        if ((myTxnRoot.getTxnFlags() & FSTransactionRoot.SVN_FS_TXN_CHECK_LOCKS) != 0) {
            allowLockedOperation(fullPath, myAuthor, myLockTokens, false, false);
        }

        makePathMutable(parentPath, fullPath);
        FSRevisionNode node = parentPath.getRevNode();
        if (baseChecksum != null) {
            String md5HexChecksum = node.getFileChecksum();
            if (md5HexChecksum != null && !md5HexChecksum.equals(baseChecksum)) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CHECKSUM_MISMATCH, "Base checksum mismatch on ''{0}'':\n   expected:  {1}\n     actual:  {2}\n", new Object[] {
                        path, baseChecksum, md5HexChecksum
                });
                SVNErrorManager.error(err);
            }
        }

        InputStream sourceStream = null;
        OutputStream targetStream = null;
        try {
            sourceStream = FSInputStream.createDeltaStream(new SVNDeltaCombiner(), node, myFSFS);
            targetStream = FSOutputStream.createStream(node, myTxnRoot, myTargetStream);
            if (myDeltaProcessor == null) {
                myDeltaProcessor = new SVNDeltaProcessor();
            }
            myDeltaProcessor.applyTextDelta(sourceStream, targetStream, false);
        } catch (SVNException svne) {
            SVNFileUtil.closeFile(sourceStream);
            throw svne;
        } finally {
            myTargetStream = (FSOutputStream) targetStream;
        }

        addChange(fullPath, node.getId(), FSPathChangeKind.FS_PATH_CHANGE_MODIFY, true, false, FSRepository.SVN_INVALID_REVNUM, null);
    }

    private void makePathMutable(FSParentPath parentPath, String errorPath) throws SVNException {
        String txnId = myTxnRoot.getTxnID();

        if (parentPath.getRevNode().getId().isTxn()) {
            return;
        }
        FSRevisionNode clone = null;

        if (parentPath.getParent() != null) {
            makePathMutable(parentPath.getParent(), errorPath);
            FSID parentId = null;
            String copyId = null;

            switch (parentPath.getCopyStyle()) {
                case FSCopyInheritance.COPY_ID_INHERIT_PARENT:
                    parentId = parentPath.getParent().getRevNode().getId();
                    copyId = parentId.getCopyID();
                    break;
                case FSCopyInheritance.COPY_ID_INHERIT_NEW:
                    copyId = reserveCopyId(txnId);
                    break;
                case FSCopyInheritance.COPY_ID_INHERIT_SELF:
                    copyId = null;
                    break;
                case FSCopyInheritance.COPY_ID_INHERIT_UNKNOWN:
                default:
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNKNOWN, "FATAL error: can not make path ''{0}'' mutable", errorPath);
                    SVNErrorManager.error(err);
            }

            String copyRootPath = parentPath.getRevNode().getCopyRootPath();
            long copyRootRevision = parentPath.getRevNode().getCopyRootRevision();

            FSRoot copyrootRoot = myFSFS.createRevisionRoot(copyRootRevision);
            FSRevisionNode copyRootNode = copyrootRoot.getRevisionNode(copyRootPath);
            FSID childId = parentPath.getRevNode().getId();
            FSID copyRootId = copyRootNode.getId();
            boolean isParentCopyRoot = false;
            if (!childId.getNodeID().equals(copyRootId.getNodeID())) {
                isParentCopyRoot = true;
            }

            String clonePath = parentPath.getParent().getAbsPath();
            clone = myTxnRoot.cloneChild(parentPath.getParent().getRevNode(), clonePath, parentPath.getNameEntry(), copyId, isParentCopyRoot);

            myTxnRoot.putRevNodeToCache(parentPath.getAbsPath(), clone);
        } else {
            FSTransactionInfo txn = myTxnRoot.getTxn();

            if (txn.getRootID().equals(txn.getBaseID())) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNKNOWN, "FATAL error: txn ''{0}'' root id ''{1}'' matches base id ''{2}''", new Object[] {
                        txnId, txn.getRootID(), txn.getBaseID()
                });
                SVNErrorManager.error(err);
            }
            clone = myFSFS.getRevisionNode(txn.getRootID());
        }

        parentPath.setRevNode(clone);
    }

    private String reserveCopyId(String txnId) throws SVNException {
        String[] nextIds = myTxnRoot.readNextIDs();
        String copyId = FSTransactionRoot.generateNextKey(nextIds[1]);
        myFSFS.writeNextIDs(txnId, nextIds[0], copyId);
        return "_" + nextIds[1];
    }

    public OutputStream textDeltaChunk(String path, SVNDiffWindow diffWindow) throws SVNException {
        return myDeltaProcessor.textDeltaChunk(diffWindow);
    }

    public void textDeltaEnd(String path) throws SVNException {
        myDeltaProcessor.textDeltaEnd();
    }

    public void changeFileProperty(String path, String name, String value) throws SVNException {
        String fullPath = SVNPathUtil.concatToAbs(myBasePath, path);
        changeNodeProperty(fullPath, name, value);
    }

    public void closeFile(String path, String textChecksum) throws SVNException {
        if (textChecksum != null) {
            String fullPath = SVNPathUtil.concatToAbs(myBasePath, path);
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
        try {
            committedRev = finalizeCommit();
        } catch (SVNException svne) {
            // ignore post-commit hook failure
            if (svne.getErrorMessage().getErrorCode() != SVNErrorCode.REPOS_POST_COMMIT_HOOK_FAILED) {
                throw svne;
            }
        }

        Map revProps = myFSFS.getRevisionProperties(committedRev);
        String dateProp = (String) revProps.get(SVNRevisionProperty.DATE);
        String authorProp = (String) revProps.get(SVNRevisionProperty.AUTHOR);
        Date datestamp = dateProp != null ? SVNTimeUtil.parseDateString(dateProp) : null;
        SVNCommitInfo info = new SVNCommitInfo(committedRev, authorProp, datestamp);

        releaseLocks();
        myRepository.closeRepository();
        return info;
    }

    private void releaseLocks() {
        if (myPathsToLockTokens != null) {
            for (Iterator paths = myPathsToLockTokens.keySet().iterator(); paths.hasNext();) {
                String path = (String) paths.next();
                String token = (String) myPathsToLockTokens.get(path);
                String absPath = !path.startsWith("/") ? SVNPathUtil.concatToAbs(myBasePath, path) : path;

                try {
                    myFSFS.unlockPath(absPath, token, myAuthor, false);
                } catch (SVNException svne) {
                    // ignore exceptions
                }
            }
        }
    }

    private long finalizeCommit() throws SVNException {
        FSHooks.runPreCommitHook(myFSFS.getRepositoryRoot(), myTxn.getTxnId());

        long newRevision = commitTxn();

        try {
            FSHooks.runPostCommitHook(myFSFS.getRepositoryRoot(), newRevision);
        } catch (SVNException svne) {
            // ignore post-commit hook failure
        }
        return newRevision;
    }

    private long commitTxn() throws SVNException {
        long newRevision = FSRepository.SVN_INVALID_REVNUM;

        while (true) {
            long youngishRev = myFSFS.getYoungestRevision();
            FSRevisionRoot youngishRoot = myFSFS.createRevisionRoot(youngishRev);

            FSRevisionNode youngishRootNode = youngishRoot.getRevisionNode("/");

            mergeChanges(null, youngishRootNode);

            myTxn.setBaseRevision(youngishRev);

            FSWriteLock writeLock = FSWriteLock.getWriteLock(myFSFS);
            synchronized (writeLock) {
                try {
                    writeLock.lock();
                    newRevision = commit();
                } catch (SVNException svne) {
                    if (svne.getErrorMessage().getErrorCode() == SVNErrorCode.FS_TXN_OUT_OF_DATE) {
                        long youngestRev = myFSFS.getYoungestRevision();
                        if (youngishRev == youngestRev) {
                            throw svne;
                        }
                        continue;
                    }
                    throw svne;
                } finally {
                    writeLock.unlock();
                    FSWriteLock.realease(writeLock);
                }
            }
            return newRevision;
        }
    }

    private long commit() throws SVNException {
        long oldRev = myFSFS.getYoungestRevision();

        if (myTxn.getBaseRevision() != oldRev) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_TXN_OUT_OF_DATE, "Transaction out of date");
            SVNErrorManager.error(err);
        }

        verifyLocks();
        String[] ids = myFSFS.getNextRevisionIDs();
        String startNodeId = ids[0];
        String startCopyId = ids[1];

        long newRevision = oldRev + 1;
        OutputStream protoFileOS = null;
        FSID newRootId = null;
        File revisionPrototypeFile = myTxnRoot.getTransactionRevFile();
        long offset = revisionPrototypeFile.length();

        try {
            protoFileOS = SVNFileUtil.openFileForWriting(revisionPrototypeFile, true);
            FSID rootId = FSID.createTxnId("0", "0", myTxn.getTxnId());

            CountingStream revWriter = new CountingStream(protoFileOS, offset);
            newRootId = myTxnRoot.writeFinalRevision(newRootId, revWriter, newRevision, rootId, startNodeId, startCopyId);
            long changedPathOffset = myTxnRoot.writeFinalChangedPathInfo(revWriter);

            String offsetsLine = "\n" + newRootId.getOffset() + " " + changedPathOffset + "\n";
            protoFileOS.write(offsetsLine.getBytes("UTF-8"));
        } catch (IOException ioe) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, ioe.getLocalizedMessage());
            SVNErrorManager.error(err, ioe);
        } finally {
            SVNFileUtil.closeFile(protoFileOS);
        }

        Map txnProps = myFSFS.getTransactionProperties(myTxn.getTxnId());
        if (txnProps != null && !txnProps.isEmpty()) {
            if (txnProps.get(SVNProperty.TXN_CHECK_OUT_OF_DATENESS) != null) {
                myFSFS.setTransactionProperty(myTxn.getTxnId(), SVNProperty.TXN_CHECK_OUT_OF_DATENESS, null);
            }
            if (txnProps.get(SVNProperty.TXN_CHECK_LOCKS) != null) {
                myFSFS.setTransactionProperty(myTxn.getTxnId(), SVNProperty.TXN_CHECK_LOCKS, null);
            }
        }

        File dstRevFile = myFSFS.getNewRevisionFile(newRevision);
        SVNFileUtil.rename(revisionPrototypeFile, dstRevFile);
        File txnPropsFile = myFSFS.getTransactionPropertiesFile(myTxn.getTxnId());
        File dstRevPropsFile = myFSFS.getNewRevisionPropertiesFile(newRevision);
        SVNFileUtil.rename(txnPropsFile, dstRevPropsFile);

        try {
            myTxnRoot.writeFinalCurrentFile(newRevision, startNodeId, startCopyId);
        } catch (IOException ioe) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, ioe.getLocalizedMessage());
            SVNErrorManager.error(err, ioe);
        }
        purgeTxn();
        return newRevision;
    }

    private void verifyLocks() throws SVNException {
        Map changes = myTxnRoot.getChangedPaths();
        Object[] changedPaths = changes.keySet().toArray();
        Arrays.sort(changedPaths);

        String lastRecursedPath = null;
        for (int i = 0; i < changedPaths.length; i++) {
            String changedPath = (String) changedPaths[i];
            boolean recurse = true;

            if (lastRecursedPath != null && SVNPathUtil.pathIsChild(lastRecursedPath, changedPath) != null) {
                continue;
            }

            FSPathChange change = (FSPathChange) changes.get(changedPath);

            if (change.getChangeKind() == FSPathChangeKind.FS_PATH_CHANGE_MODIFY) {
                recurse = false;
            }
            allowLockedOperation(changedPath, myAuthor, myLockTokens, recurse, true);

            if (recurse) {
                lastRecursedPath = changedPath;
            }
        }
    }

    private void mergeChanges(FSRevisionNode ancestorNode, FSRevisionNode sourceNode) throws SVNException {
        String txnId = myTxn.getTxnId();
        FSRevisionNode txnRootNode = myTxnRoot.getRootRevisionNode();

        if (ancestorNode == null) {
            ancestorNode = myTxnRoot.getTxnBaseRootNode();
        }

        if (txnRootNode.getId().equals(ancestorNode.getId())) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNKNOWN, "FATAL error: no changes in transaction to commit");
            SVNErrorManager.error(err);
        } else {
            merge("/", txnRootNode, sourceNode, ancestorNode, txnId);
        }
    }

    private void merge(String targetPath, FSRevisionNode target, FSRevisionNode source, FSRevisionNode ancestor, String txnId) throws SVNException {
        FSID sourceId = source.getId();
        FSID targetId = target.getId();
        FSID ancestorId = ancestor.getId();

        if (ancestorId.equals(targetId)) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_GENERAL, "Bad merge; target ''{0}'' has id ''{1}'', same as ancestor", new Object[] {
                    targetPath, targetId
            });
            SVNErrorManager.error(err);
        }

        if (ancestorId.equals(sourceId) || sourceId.equals(targetId)) {
            return;
        }

        if (source.getType() != SVNNodeKind.DIR || target.getType() != SVNNodeKind.DIR || ancestor.getType() != SVNNodeKind.DIR) {
            SVNErrorManager.error(FSErrors.errorConflict(targetPath));
        }

        if (!FSRepresentation.compareRepresentations(target.getPropsRepresentation(), ancestor.getPropsRepresentation())) {
            SVNErrorManager.error(FSErrors.errorConflict(targetPath));
        }

        Map sourceEntries = source.getDirEntries(myFSFS);
        Map targetEntries = target.getDirEntries(myFSFS);
        Map ancestorEntries = ancestor.getDirEntries(myFSFS);

        for (Iterator ancestorEntryNames = ancestorEntries.keySet().iterator(); ancestorEntryNames.hasNext();) {
            String ancestorEntryName = (String) ancestorEntryNames.next();
            FSEntry ancestorEntry = (FSEntry) ancestorEntries.get(ancestorEntryName);
            FSEntry sourceEntry = (FSEntry) sourceEntries.get(ancestorEntryName);
            FSEntry targetEntry = (FSEntry) targetEntries.get(ancestorEntryName);
            if (sourceEntry != null && ancestorEntry.getId().equals(sourceEntry.getId())) {
                /*
                 * No changes were made to this entry while the transaction was
                 * in progress, so do nothing to the target.
                 */
            } else if (targetEntry != null && ancestorEntry.getId().equals(targetEntry.getId())) {
                if (sourceEntry != null) {
                    myTxnRoot.setEntry(target, ancestorEntryName, sourceEntry.getId(), sourceEntry.getType());
                } else {
                    myTxnRoot.deleteEntry(target, ancestorEntryName);
                }
            } else {
                if (sourceEntry == null || targetEntry == null) {
                    SVNErrorManager.error(FSErrors.errorConflict(SVNPathUtil.concatToAbs(targetPath, ancestorEntryName)));
                }

                if (sourceEntry.getType() == SVNNodeKind.FILE || targetEntry.getType() == SVNNodeKind.FILE || ancestorEntry.getType() == SVNNodeKind.FILE) {
                    SVNErrorManager.error(FSErrors.errorConflict(SVNPathUtil.concatToAbs(targetPath, ancestorEntryName)));
                }

                if (!sourceEntry.getId().getNodeID().equals(ancestorEntry.getId().getNodeID()) || !sourceEntry.getId().getCopyID().equals(ancestorEntry.getId().getCopyID())
                        || !targetEntry.getId().getNodeID().equals(ancestorEntry.getId().getNodeID()) || !targetEntry.getId().getCopyID().equals(ancestorEntry.getId().getCopyID())) {
                    SVNErrorManager.error(FSErrors.errorConflict(SVNPathUtil.concatToAbs(targetPath, ancestorEntryName)));
                }

                FSRevisionNode sourceEntryNode = myFSFS.getRevisionNode(sourceEntry.getId());
                FSRevisionNode targetEntryNode = myFSFS.getRevisionNode(targetEntry.getId());
                FSRevisionNode ancestorEntryNode = myFSFS.getRevisionNode(ancestorEntry.getId());
                String childTargetPath = SVNPathUtil.concatToAbs(targetPath, targetEntry.getName());
                merge(childTargetPath, targetEntryNode, sourceEntryNode, ancestorEntryNode, txnId);
            }

            sourceEntries.remove(ancestorEntryName);
        }

        for (Iterator sourceEntryNames = sourceEntries.keySet().iterator(); sourceEntryNames.hasNext();) {
            String sourceEntryName = (String) sourceEntryNames.next();
            FSEntry sourceEntry = (FSEntry) sourceEntries.get(sourceEntryName);
            FSEntry targetEntry = (FSEntry) targetEntries.get(sourceEntryName);
            if (targetEntry != null) {
                SVNErrorManager.error(FSErrors.errorConflict(SVNPathUtil.concatToAbs(targetPath, targetEntry.getName())));
            }
            myTxnRoot.setEntry(target, sourceEntry.getName(), sourceEntry.getId(), sourceEntry.getType());
        }
        long sourceCount = source.getCount();
        updateAncestry(sourceId, targetId, targetPath, sourceCount);
    }

    private void updateAncestry(FSID sourceId, FSID targetId, String targetPath, long sourcePredecessorCount) throws SVNException {
        if (!targetId.isTxn()) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_NOT_MUTABLE, "Unexpected immutable node at ''{0}''", targetPath);
            SVNErrorManager.error(err);
        }
        FSRevisionNode revNode = myFSFS.getRevisionNode(targetId);
        revNode.setPredecessorId(sourceId);
        revNode.setCount(sourcePredecessorCount != -1 ? sourcePredecessorCount + 1 : sourcePredecessorCount);
        myFSFS.putTxnRevisionNode(targetId, revNode);
    }

    public void abortEdit() throws SVNException {
        if (myTargetStream != null) {
            myTargetStream.closeStreams();
        }
        if (myTxn == null || !isTxnOwner) {
            myRepository.closeRepository();
            return;
        }
        purgeTxn();
        myRepository.closeRepository();
        File txnDir = myFSFS.getTransactionDir(myTxn.getTxnId());
        if (txnDir.exists()) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNKNOWN, "Transaction cleanup failed");
            SVNErrorManager.error(err);
        }
        myTxn = null;
        myTxnRoot = null;
    }

    private void allowLockedOperation(String path, final String username, final Collection lockTokens, boolean recursive, boolean haveWriteLock) throws SVNException {
        if (recursive) {
            ISVNLockHandler handler = new ISVNLockHandler() {

                private String myUsername = username;
                private Collection myTokens = lockTokens;

                public void handleLock(String path, SVNLock lock, SVNErrorMessage error) throws SVNException {
                    verifyLock(lock, myTokens, myUsername);
                }

                public void handleUnlock(String path, SVNLock lock, SVNErrorMessage error) throws SVNException {
                }
            };
            myFSFS.walkDigestFiles(myFSFS.getDigestFileFromRepositoryPath(path), handler, haveWriteLock);
        } else {
            SVNLock lock = myFSFS.getLockHelper(path, haveWriteLock);
            if (lock != null) {
                verifyLock(lock, lockTokens, username);
            }
        }
    }

    private void verifyLock(SVNLock lock, Collection lockTokens, String username) throws SVNException {
        if (username == null || "".equals(username)) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_NO_USER, "Cannot verify lock on path ''{0}''; no username available", lock.getPath());
            SVNErrorManager.error(err);
        } else if (username.compareTo(lock.getOwner()) != 0) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_LOCK_OWNER_MISMATCH, "User {0} does not own lock on path ''{1}'' (currently locked by {2})", new Object[] {
                    username, lock.getPath(), lock.getOwner()
            });
            SVNErrorManager.error(err);
        }
        for (Iterator tokens = lockTokens.iterator(); tokens.hasNext();) {
            String token = (String) tokens.next();
            if (token.equals(lock.getID())) {
                return;
            }
        }
        SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_BAD_LOCK_TOKEN, "Cannot verify lock on path ''{0}''; no matching lock-token available", lock.getPath());
        SVNErrorManager.error(err);
    }

    private void purgeTxn() {
        SVNFileUtil.deleteAll(myFSFS.getTransactionDir(myTxn.getTxnId()), true);
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
