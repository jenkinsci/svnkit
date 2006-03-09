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

import java.io.File;
import java.util.Iterator;
import java.util.Map;
import java.util.HashMap;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.io.SVNLocationEntry;

/**
 * @version 1.0
 * @author  TMate Software Ltd.
 */
public class FSRoot {

    /* The kind of root this is */
    private boolean myIsTxnRoot;
    /* For transaction roots, the name of the transaction  */
    private String myTxnId;
    /* For transaction roots, flags describing the txn's behavior. */
    private int myTxnFlags;
    /* For revision roots, the number of the revision.  */
    private long myRevision;
    /* For revision roots, the node-rev representation of the root */
    private FSRevisionNode myRootRevNode;

    /* Cache structure for mapping String PATH to String COPYFROM_STRING, 
     * so that pathsChanged can remember all the copyfrom information in the changes file.
     * COPYFROM_STRING has the format "REV PATH", i.e SVNLocationEntry(), 
     * or there is no entry in map if the path was added without history*/
    private Map myCopyfromCache;

    //only for transactions 
    private Map myRevNodesCache;

    private File myReposRootDir;

    public static FSRoot createRevisionRoot(long revision, FSRevisionNode root, File reposRootDir) {
        return new FSRoot(revision, root, reposRootDir);
    }

    private FSRoot(long revision, FSRevisionNode root, File reposRootDir) {
        myRevision = revision;
        myRootRevNode = root;
        myIsTxnRoot = false;
        myTxnId = FSID.ID_INAPPLICABLE;
        myTxnFlags = 0;
        myReposRootDir = reposRootDir;
    }

    public static FSRoot createTransactionRoot(String txnId, int flags, File reposRootDir) {
        return new FSRoot(txnId, flags, reposRootDir);
    }

    private FSRoot(String txnId, int flags, File reposRootDir) {
        myTxnId = txnId;
        myTxnFlags = flags;
        myIsTxnRoot = true;
        myRevision = FSConstants.SVN_INVALID_REVNUM;
        myRootRevNode = null;
        myReposRootDir = reposRootDir;
    }

    public boolean isTxnRoot() {
        return myIsTxnRoot;
    }

    public long getRevision() {
        return myRevision;
    }

    public FSRevisionNode getRootRevisionNode() {
        return myRootRevNode;
    }

    public void setRootRevisionNode(FSRevisionNode root) {
        myRootRevNode = root;
    }

    public int getTxnFlags() {
        return myTxnFlags;
    }

    public void setTxnFlags(int txnFlags) {
        myTxnFlags = txnFlags;
    }

    public String getTxnId() {
        return myTxnId;
    }

    public void putRevNodeToCache(String path, FSRevisionNode node) throws SVNException {
        if (myRevNodesCache == null) {
            myRevNodesCache = new HashMap();
        }
        /* Assert valid input. */
        if (!path.startsWith("/")) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNKNOWN, "Invalid path ''{0}''", path);
            SVNErrorManager.error(err);
        }
        myRevNodesCache.put(path, node);
    }

    public void removeRevNodeFromCache(String path) throws SVNException {
        if (myRevNodesCache == null) {
            return;
        }
        /* Assert valid input. */
        if (!path.startsWith("/")) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNKNOWN, "Invalid path ''{0}''", path);
            SVNErrorManager.error(err);
        }
        myRevNodesCache.remove(path);
    }

    public FSRevisionNode fetchRevNodeFromCache(String path) throws SVNException {
        if (myRevNodesCache == null) {
            return null;
        }
        /* Assert valid input. */
        if (!path.startsWith("/")) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNKNOWN, "Invalid path ''{0}''", path);
            SVNErrorManager.error(err);
        }
        return (FSRevisionNode) myRevNodesCache.get(path);
    }

    /* Return MAP with hash containing descriptions of the paths changed under ROOT. 
     * The hash is keyed with String paths and has FSPathChange values.
     */
    public Map getChangedPaths() throws SVNException {
        if (isTxnRoot()) {
            File changesFile = FSRepositoryUtil.getTxnChangesFile(myTxnId, myReposRootDir);
            FSFile raChangesFile = new FSFile(changesFile);
            try {
                return fetchAllChanges(raChangesFile, false);
            } finally {
                raChangesFile.close();
            }
        }
        long changesOffset = FSReader.getChangesOffset(myReposRootDir, getRevision());
        File revFile = FSRepositoryUtil.getRevisionFile(myReposRootDir, getRevision());
        FSFile raRevFile = new FSFile(revFile);
        try {
            raRevFile.seek(changesOffset);
            return fetchAllChanges(raRevFile, true);
        } finally {
            raRevFile.close();
        }
    }

    public void foldChange(Map mapChanges, FSChange change) throws SVNException {
        if (change == null) {
            return;
        }
        mapChanges = mapChanges != null ? mapChanges : new HashMap();
        Map mapCopyfrom = getCopyfromCache();
        FSPathChange newChange = null;
        SVNLocationEntry copyfromEntry = null;
        String path = null;

        FSPathChange oldChange = (FSPathChange) mapChanges.get(change.getPath());
        if (oldChange != null) {
            /* Get the existing copyfrom entry for this path. */
            copyfromEntry = (SVNLocationEntry) mapCopyfrom.get(change.getPath());
            path = change.getPath();
            /* Sanity check:  only allow NULL node revision ID in the 'reset' case. */
            if ((change.getNodeRevID() == null) && (FSPathChangeKind.FS_PATH_CHANGE_RESET != change.getKind())) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_CORRUPT, "Missing required node revision ID");
                SVNErrorManager.error(err);
            }
            /* Sanity check: we should be talking about the same node
             revision ID as our last change except where the last change
             was a deletion*/
            if ((change.getNodeRevID() != null) && (!oldChange.getRevNodeId().equals(change.getNodeRevID())) && (oldChange.getChangeKind() != FSPathChangeKind.FS_PATH_CHANGE_DELETE)) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_CORRUPT, "Invalid change ordering: new node revision ID without delete");
                SVNErrorManager.error(err);
            }
            /* Sanity check: an add, replacement, or reset must be the first
             thing to follow a deletion*/
            if (FSPathChangeKind.FS_PATH_CHANGE_DELETE == oldChange.getChangeKind()
                    && !(FSPathChangeKind.FS_PATH_CHANGE_REPLACE == change.getKind() || FSPathChangeKind.FS_PATH_CHANGE_RESET == change.getKind() || FSPathChangeKind.FS_PATH_CHANGE_ADD == change
                            .getKind())) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_CORRUPT, "Invalid change ordering: non-add change on deleted path");
                SVNErrorManager.error(err);
            }
            /*Merging the changes*/
            if (FSPathChangeKind.FS_PATH_CHANGE_MODIFY == change.getKind()) {
                if (change.getTextModification()) {
                    oldChange.setTextModified(true);
                }
                if (change.getPropModification()) {
                    oldChange.setPropertiesModified(true);
                }
            } else if (FSPathChangeKind.FS_PATH_CHANGE_ADD == change.getKind() || FSPathChangeKind.FS_PATH_CHANGE_REPLACE == change.getKind()) {
                oldChange.setChangeKind(FSPathChangeKind.FS_PATH_CHANGE_REPLACE);
                oldChange.setRevNodeId(new FSID(change.getNodeRevID()));
                oldChange.setTextModified(change.getTextModification());
                oldChange.setPropertiesModified(change.getPropModification());
                if (change.getCopyfromEntry() == null) {
                    copyfromEntry = null;
                } else {
                    copyfromEntry = new SVNLocationEntry(change.getCopyfromEntry().getRevision(), change.getCopyfromEntry().getPath());
                }
            } else if (FSPathChangeKind.FS_PATH_CHANGE_DELETE == change.getKind()) {
                if (FSPathChangeKind.FS_PATH_CHANGE_ADD == oldChange.getChangeKind()) {
                    /*If the path was introduced in this transaction via an
                     add, and we are deleting it, just remove the path altogether*/
                    oldChange = null;
                    mapChanges.remove(change.getPath());
                } else {
                    /* A deletion overrules all previous changes. */
                    oldChange.setChangeKind(FSPathChangeKind.FS_PATH_CHANGE_DELETE);
                    oldChange.setPropertiesModified(change.getPropModification());
                    oldChange.setTextModified(change.getTextModification());
                }
                copyfromEntry = null;
                mapCopyfrom.remove(change.getPath());
            } else if (FSPathChangeKind.FS_PATH_CHANGE_RESET == change.getKind()) {
                oldChange = null;
                copyfromEntry = null;
                mapChanges.remove(change.getPath());
                mapCopyfrom.remove(change.getPath());
            }
            newChange = oldChange;
        } else {
            newChange = new FSPathChange(new FSID(change.getNodeRevID()), change.getKind(), change.getTextModification(), change.getPropModification());
            copyfromEntry = new SVNLocationEntry(change.getCopyfromEntry().getRevision(), change.getCopyfromEntry().getPath());
            path = change.getPath();
        }
        /* Add (or update) this path. */
        mapChanges.put(path, newChange);

        if (copyfromEntry == null) {
            mapCopyfrom.remove(path);
        } else {
            mapCopyfrom.put(path, copyfromEntry);
        }
    }

    private Map fetchAllChanges(FSFile changesFile, boolean prefolded) throws SVNException {
        Map changedPaths = new HashMap();
        FSChange change = readChange(changesFile);
        while (change != null) {
            foldChange(changedPaths, change);
            if ((FSPathChangeKind.FS_PATH_CHANGE_DELETE == change.getKind() || FSPathChangeKind.FS_PATH_CHANGE_REPLACE == change.getKind()) && !prefolded) {
                for (Iterator curIter = changedPaths.keySet().iterator(); curIter.hasNext();) {
                    String hashKeyPath = (String) curIter.next();
                    //If we come across our own path, ignore it                    
                    if (change.getPath().equals(hashKeyPath)) {
                        continue;
                    }
                    //If we come across a child of our path, remove it
                    if (SVNPathUtil.pathIsChild(change.getPath(), hashKeyPath) != null) {
                        curIter.remove();
                    }
                }
            }
            change = readChange(changesFile);
        }
        return changedPaths;
    }

    /* Read changes from revision file, RandomAccessFile reader must be already opened.
     * OffsetContainerClass before invoking 'readChanges' method contains offset to changes, after invoking 
     * 'readChanges' contains offset to next changes (if file has them) in raFile 
     */
    private FSChange readChange(FSFile raReader) throws SVNException {
        String changeLine = null;
        try {
            changeLine = raReader.readLine(4096);//FSReader.readNextLine(raReader, 4096);
        } catch (SVNException svne) {
            /* Check for a blank line. */
            if (svne.getErrorMessage().getErrorCode() == SVNErrorCode.STREAM_UNEXPECTED_EOF) {
                return null;
            }
            throw svne;
        }
        /* Check for a blank line. */
        if (changeLine == null || changeLine.length() == 0) {
            return null;
        }
        String[] piecesOfChangeLine = changeLine.split(" ", 5);
        if (piecesOfChangeLine.length < 5) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_CORRUPT, "Invalid changes line in rev-file");
            SVNErrorManager.error(err);
        }
        /* Get the node-id of the change. */
        String nodeRevStr = piecesOfChangeLine[0];
        FSID nodeRevID = FSReader.parseID(nodeRevStr);
        /* Get the change type. */
        String changesKindStr = piecesOfChangeLine[1];
        FSPathChangeKind changesKind = (FSPathChangeKind) FSConstants.ACTIONS_TO_CHANGE_KINDS.get(changesKindStr);
        if (changesKind == null) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_CORRUPT, "Invalid change kind in rev file");
            SVNErrorManager.error(err);
        }
        /* Get the text-mod flag. */
        String textModeStr = piecesOfChangeLine[2];
        boolean textModeBool = false;
        if (textModeStr.equals(FSConstants.FLAG_TRUE)) {
            textModeBool = true;
        } else if (textModeStr.equals(FSConstants.FLAG_FALSE)) {
            textModeBool = false;
        } else {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_CORRUPT, "Invalid text-mod flag in rev-file");
            SVNErrorManager.error(err);
        }
        /* Get the prop-mod flag. */
        String propModeStr = piecesOfChangeLine[3];
        boolean propModeBool = false;
        if (propModeStr.equals(new String(FSConstants.FLAG_TRUE))) {
            propModeBool = true;
        } else if (propModeStr.equals(new String(FSConstants.FLAG_FALSE))) {
            propModeBool = false;
        } else {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_CORRUPT, "Invalid prop-mod flag in rev-file");
            SVNErrorManager.error(err);
        }
        /* Get the changed path. */
        String pathStr = piecesOfChangeLine[4];
        /* Read the next line, the copyfrom line. */
        String copyfromLine = raReader.readLine(4096);//FSReader.readNextLine(raReader, 4096);
        SVNLocationEntry copyfromEntry = null;
        if (copyfromLine.length() == 0) {
            copyfromEntry = new SVNLocationEntry(FSConstants.SVN_INVALID_REVNUM, null);
        } else {
            String[] piecesOfCopyfromLine = copyfromLine.split(" ", 2);
            if (piecesOfCopyfromLine.length < 2) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_CORRUPT, "Invalid changes line in rev-file");
                SVNErrorManager.error(err);
            }
            copyfromEntry = new SVNLocationEntry(Long.parseLong(piecesOfCopyfromLine[0]), piecesOfCopyfromLine[1]);
        }
        return new FSChange(new String(pathStr), new FSID(nodeRevID), changesKind, textModeBool, propModeBool, copyfromEntry);
    }

    public Map getCopyfromCache() {
        if (myCopyfromCache == null) {
            myCopyfromCache = new HashMap();
        }
        return myCopyfromCache;
    }
}
