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

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.io.SVNLocationEntry;

/**
 * @version 1.0
 * @author  TMate Software Ltd.
 */
public class FSRoot {

    private boolean myIsTxnRoot;
    private String myTxnId;
    private int myTxnFlags;
    private long myRevision;
    
    private FSRevisionNode myRootRevNode;
    private long myRootOffset;
    private long myChangesOffset;

    private Map myCopyfromCache;
    private Map myRevNodesCache;
    
    private FSFS myFSFS;
    
    protected FSRoot(FSFS owner) {
        myFSFS = owner;
    }

    public FSRoot(FSFS owner, long revision) {
        this(owner);
        myRevision = revision;
        myIsTxnRoot = false;
        myTxnId = null;
        myTxnFlags = 0;
        myRootOffset = -1;
        myChangesOffset = -1;
    }

    public FSRoot(FSFS owner, String txnId, int flags) {
        this(owner);
        myTxnId = txnId;
        myTxnFlags = flags;
        myIsTxnRoot = true;
        myRevision = FSConstants.SVN_INVALID_REVNUM;
        myRootRevNode = null;
    }

    public boolean isTxnRoot() {
        return myIsTxnRoot;
    }

    public long getRevision() {
        return myRevision;
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

    public Map getCopyfromCache() {
        if (myCopyfromCache == null) {
            myCopyfromCache = new HashMap();
        }
        return myCopyfromCache;
    }
    
    public FSRevisionNode getRevisionNode(String path) throws SVNException {
        return null;
    }

    public FSRevisionNode getRootRevisionNode() throws SVNException {
        if (myRootRevNode == null && !isTxnRoot()) {
            FSFile file = myFSFS.getRevisionFile(getRevision());
            try {
                loadOffsets(file);
                file.seek(myRootOffset);
                Map headers = file.readHeader();
                myRootRevNode = FSRevisionNode.fromMap(headers);
            } finally {
                file.close();
            }
        }
        return myRootRevNode;
    }

    public void putRevNodeToCache(String path, FSRevisionNode node) throws SVNException {
        if (myRevNodesCache == null) {
            myRevNodesCache = new HashMap();
        }
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
        if (!path.startsWith("/")) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNKNOWN, "Invalid path ''{0}''", path);
            SVNErrorManager.error(err);
        }
        return (FSRevisionNode) myRevNodesCache.get(path);
    }

    public Map getChangedPaths() throws SVNException {
        if (isTxnRoot()) {
            FSFile file = myFSFS.getTransactionChangesFile(getTxnId());
            try {
                return fetchAllChanges(file, false);
            } finally {
                file.close();
            }
        }
        FSFile file = myFSFS.getRevisionFile(getRevision());
        loadOffsets(file);
        try {
            file.seek(myChangesOffset);
            return fetchAllChanges(file, true);
        } finally {
            file.close();
        }
    }

    private void foldChange(Map mapChanges, FSChange change) throws SVNException {
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
            copyfromEntry = (SVNLocationEntry) mapCopyfrom.get(change.getPath());
            path = change.getPath();
            if ((change.getNodeRevID() == null) && (FSPathChangeKind.FS_PATH_CHANGE_RESET != change.getKind())) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_CORRUPT, "Missing required node revision ID");
                SVNErrorManager.error(err);
            }
            if ((change.getNodeRevID() != null) && (!oldChange.getRevNodeId().equals(change.getNodeRevID())) && (oldChange.getChangeKind() != FSPathChangeKind.FS_PATH_CHANGE_DELETE)) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_CORRUPT, "Invalid change ordering: new node revision ID without delete");
                SVNErrorManager.error(err);
            }
            if (FSPathChangeKind.FS_PATH_CHANGE_DELETE == oldChange.getChangeKind()
                    && !(FSPathChangeKind.FS_PATH_CHANGE_REPLACE == change.getKind() || FSPathChangeKind.FS_PATH_CHANGE_RESET == change.getKind() || FSPathChangeKind.FS_PATH_CHANGE_ADD == change
                            .getKind())) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_CORRUPT, "Invalid change ordering: non-add change on deleted path");
                SVNErrorManager.error(err);
            }
            if (FSPathChangeKind.FS_PATH_CHANGE_MODIFY == change.getKind()) {
                if (change.getTextModification()) {
                    oldChange.setTextModified(true);
                }
                if (change.getPropModification()) {
                    oldChange.setPropertiesModified(true);
                }
            } else if (FSPathChangeKind.FS_PATH_CHANGE_ADD == change.getKind() || FSPathChangeKind.FS_PATH_CHANGE_REPLACE == change.getKind()) {
                oldChange.setChangeKind(FSPathChangeKind.FS_PATH_CHANGE_REPLACE);
                oldChange.setRevNodeId(change.getNodeRevID().copy());
                oldChange.setTextModified(change.getTextModification());
                oldChange.setPropertiesModified(change.getPropModification());
                if (change.getCopyfromEntry() == null) {
                    copyfromEntry = null;
                } else {
                    copyfromEntry = new SVNLocationEntry(change.getCopyfromEntry().getRevision(), change.getCopyfromEntry().getPath());
                }
            } else if (FSPathChangeKind.FS_PATH_CHANGE_DELETE == change.getKind()) {
                if (FSPathChangeKind.FS_PATH_CHANGE_ADD == oldChange.getChangeKind()) {
                    oldChange = null;
                    mapChanges.remove(change.getPath());
                } else {
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
            newChange = new FSPathChange(change.getNodeRevID().copy(), change.getKind(), change.getTextModification(), change.getPropModification());
            copyfromEntry = new SVNLocationEntry(change.getCopyfromEntry().getRevision(), change.getCopyfromEntry().getPath());
            path = change.getPath();
        }
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
                    if (change.getPath().equals(hashKeyPath)) {
                        continue;
                    }
                    if (SVNPathUtil.pathIsChild(change.getPath(), hashKeyPath) != null) {
                        curIter.remove();
                    }
                }
            }
            change = readChange(changesFile);
        }
        return changedPaths;
    }

    private FSChange readChange(FSFile raReader) throws SVNException {
        String changeLine = null;
        try {
            changeLine = raReader.readLine(4096);
        } catch (SVNException svne) {
            if (svne.getErrorMessage().getErrorCode() == SVNErrorCode.STREAM_UNEXPECTED_EOF) {
                return null;
            }
            throw svne;
        }
        if (changeLine == null || changeLine.length() == 0) {
            return null;
        }
        String copyfromLine = raReader.readLine(4096);
        return FSChange.fromString(changeLine, copyfromLine);
    }
    
    private void loadOffsets(FSFile file) throws SVNException {
        if (myRootOffset >= 0) {
            return;
        }
        ByteBuffer buffer = ByteBuffer.allocate(64);
        file.seek(file.size() - 64);
        try {
            file.read(buffer);
        } catch (IOException e) {
        }
        buffer.flip();
        if (buffer.get(buffer.limit() - 1) != '\n') {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_CORRUPT, "Revision file lacks trailing newline");
            SVNErrorManager.error(err);
        }
        int spaceIndex = -1;
        int eolIndex = -1;
        for(int i = buffer.limit() - 2; i >=0; i--) {
            byte b = buffer.get(i);
            if (b == ' ' && spaceIndex < 0) {
                spaceIndex = i;
            } else if (b == '\n' && eolIndex < 0) {
                eolIndex = i;
                break;
            }
        }
        if (eolIndex < 0) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_CORRUPT, "Final line in revision file longer than 64 characters");
            SVNErrorManager.error(err);
        }
        if (spaceIndex < 0) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_CORRUPT, "Final line in revision file missing space");
            SVNErrorManager.error(err);
        }
        CharsetDecoder decoder = Charset.forName("UTF-8").newDecoder();
        try {
            buffer.limit(buffer.limit() - 1);
            buffer.position(spaceIndex + 1);
            String line = decoder.decode(buffer).toString();
            myChangesOffset = Long.parseLong(line);

            buffer.limit(spaceIndex);
            buffer.position(eolIndex + 1);
            line = decoder.decode(buffer).toString();
            myRootOffset = Long.parseLong(line); 
        } catch (NumberFormatException nfe) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_CORRUPT, "Final line in revision file missing changes and root offsets");
            SVNErrorManager.error(err, nfe);
        } catch (CharacterCodingException e) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_CORRUPT, "Final line in revision file missing changes and root offsets");
            SVNErrorManager.error(err, e);
        }
    }
}
