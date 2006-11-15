/*
 * ====================================================================
 * Copyright (c) 2004-2006 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.core.internal.io.fs;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.SVNRevisionProperty;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.internal.util.SVNTimeUtil;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.internal.wc.SVNProperties;

/**
 * @version 1.1.0
 * @author  TMate Software Ltd.
 */
public class FSTransactionRoot extends FSRoot {

    public static final int SVN_FS_TXN_CHECK_OUT_OF_DATENESS = 0x00001;
    public static final int SVN_FS_TXN_CHECK_LOCKS = 0x00002;
    private static final int MAX_KEY_SIZE = 200;

    private String myTxnID;
    private int myTxnFlags;
    private File myTxnChangesFile;
    private File myTxnRevFile;

    public FSTransactionRoot(FSFS owner, String txnID, int flags) {
        super(owner);
        myTxnID = txnID;
        myTxnFlags = flags;

    }

    public FSCopyInheritance getCopyInheritance(FSParentPath child) throws SVNException {
        if (child == null || child.getParent() == null || myTxnID == null) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNKNOWN, "FATAL error: invalid txn name or child");
            SVNErrorManager.error(err);
        }
        FSID childID = child.getRevNode().getId();
        FSID parentID = child.getParent().getRevNode().getId();
        String childCopyID = childID.getCopyID();
        String parentCopyID = parentID.getCopyID();

        if (childID.isTxn()) {
            return new FSCopyInheritance(FSCopyInheritance.COPY_ID_INHERIT_SELF, null);
        }

        FSCopyInheritance copyInheritance = new FSCopyInheritance(FSCopyInheritance.COPY_ID_INHERIT_PARENT, null);

        if (childCopyID.compareTo("0") == 0) {
            return copyInheritance;
        }

        if (childCopyID.compareTo(parentCopyID) == 0) {
            return copyInheritance;
        }

        long copyrootRevision = child.getRevNode().getCopyRootRevision();
        String copyrootPath = child.getRevNode().getCopyRootPath();

        FSRoot copyrootRoot = getOwner().createRevisionRoot(copyrootRevision);
        FSRevisionNode copyrootNode = copyrootRoot.getRevisionNode(copyrootPath);
        FSID copyrootID = copyrootNode.getId();
        if (copyrootID.compareTo(childID) == -1) {
            return copyInheritance;
        }

        String idPath = child.getRevNode().getCreatedPath();
        if (idPath.compareTo(child.getAbsPath()) == 0) {
            copyInheritance.setStyle(FSCopyInheritance.COPY_ID_INHERIT_SELF);
            return copyInheritance;
        }

        copyInheritance.setStyle(FSCopyInheritance.COPY_ID_INHERIT_NEW);
        copyInheritance.setCopySourcePath(idPath);
        return copyInheritance;
    }

    public FSRevisionNode getRootRevisionNode() throws SVNException {
        if (myRootRevisionNode == null) {
            FSTransactionInfo txn = getTxn();
            myRootRevisionNode = getOwner().getRevisionNode(txn.getRootID());
        }
        return myRootRevisionNode;
    }

    public FSRevisionNode getTxnBaseRootNode() throws SVNException {
        FSTransactionInfo txn = getTxn();
        FSRevisionNode baseRootNode = getOwner().getRevisionNode(txn.getBaseID());
        return baseRootNode;
    }

    public FSTransactionInfo getTxn() throws SVNException {
        FSID rootID = FSID.createTxnId("0", "0", myTxnID);
        FSRevisionNode revNode = getOwner().getRevisionNode(rootID);
        FSTransactionInfo txn = new FSTransactionInfo(revNode.getId(), revNode.getPredecessorId());
        return txn;
    }

    public Map getChangedPaths() throws SVNException {
        FSFile file = getOwner().getTransactionChangesFile(myTxnID);
        try {
            return fetchAllChanges(file, false);
        } finally {
            file.close();
        }
    }

    public int getTxnFlags() {
        return myTxnFlags;
    }

    public void setTxnFlags(int txnFlags) {
        myTxnFlags = txnFlags;
    }

    public String getTxnID() {
        return myTxnID;
    }

    public Map unparseDirEntries(Map entries) {
        Map unparsedEntries = new HashMap();
        for (Iterator names = entries.keySet().iterator(); names.hasNext();) {
            String name = (String) names.next();
            FSEntry dirEntry = (FSEntry) entries.get(name);
            String unparsedVal = dirEntry.toString();
            unparsedEntries.put(name, unparsedVal);
        }
        return unparsedEntries;
    }

    public static FSTransactionInfo beginTransaction(long baseRevision, int flags, FSFS owner) throws SVNException {
        FSTransactionInfo txn = createTxn(baseRevision, owner);
        String commitTime = SVNTimeUtil.formatDate(new Date(System.currentTimeMillis()));
        owner.setTransactionProperty(txn.getTxnId(), SVNRevisionProperty.DATE, commitTime);

        if ((flags & SVN_FS_TXN_CHECK_OUT_OF_DATENESS) != 0) {
            owner.setTransactionProperty(txn.getTxnId(), SVNProperty.TXN_CHECK_OUT_OF_DATENESS, SVNProperty.toString(true));
        }

        if ((flags & FSTransactionRoot.SVN_FS_TXN_CHECK_LOCKS) != 0) {
            owner.setTransactionProperty(txn.getTxnId(), SVNProperty.TXN_CHECK_LOCKS, SVNProperty.toString(true));
        }

        return txn;
    }

    private static FSTransactionInfo createTxn(long baseRevision, FSFS owner) throws SVNException {
        String txnID = createTxnDir(baseRevision, owner);
        FSTransactionInfo txn = new FSTransactionInfo(baseRevision, txnID);
        FSRevisionRoot root = owner.createRevisionRoot(baseRevision);
        FSRevisionNode rootNode = root.getRootRevisionNode();
        owner.createNewTxnNodeRevisionFromRevision(txnID, rootNode);
        SVNFileUtil.createEmptyFile(new File(owner.getTransactionDir(txn.getTxnId()), FSFS.TXN_PATH_REV));
        SVNFileUtil.createEmptyFile(new File(owner.getTransactionDir(txn.getTxnId()), "changes"));
        owner.writeNextIDs(txnID, "0", "0");
        return txn;
    }

    private static String createTxnDir(long revision, FSFS owner) throws SVNException {
        File parent = owner.getTransactionsParentDir();
        File uniquePath = null;

        for (int i = 1; i < 99999; i++) {
            uniquePath = new File(parent, revision + "-" + i + FSFS.TXN_PATH_EXT);
            if (!uniquePath.exists() && uniquePath.mkdirs()) {
                return revision + "-" + i;
            }
        }
        SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_UNIQUE_NAMES_EXHAUSTED, "Unable to create transaction directory in ''{0}'' for revision {1,number,integer}", new Object[] {
                parent, new Long(revision)
        });
        SVNErrorManager.error(err);
        return null;
    }

    public void deleteEntry(FSRevisionNode parent, String entryName) throws SVNException {
        if (parent.getType() != SVNNodeKind.DIR) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_NOT_DIRECTORY, "Attempted to delete entry ''{0}'' from *non*-directory node", entryName);
            SVNErrorManager.error(err);
        }

        if (!parent.getId().isTxn()) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_NOT_MUTABLE, "Attempted to delete entry ''{0}'' from immutable directory node", entryName);
            SVNErrorManager.error(err);
        }

        if (!SVNPathUtil.isSinglePathComponent(entryName)) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_NOT_SINGLE_PATH_COMPONENT, "Attempted to delete a node with an illegal name ''{0}''", entryName);
            SVNErrorManager.error(err);
        }

        Map entries = parent.getDirEntries(getOwner());
        FSEntry dirEntry = (FSEntry) entries.get(entryName);

        if (dirEntry == null) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_NO_SUCH_ENTRY, "Delete failed--directory has no entry ''{0}''", entryName);
            SVNErrorManager.error(err);
        }
        getOwner().getRevisionNode(dirEntry.getId());
        deleteEntryIfMutable(dirEntry.getId());
        setEntry(parent, entryName, null, SVNNodeKind.UNKNOWN);
    }

    private void deleteEntryIfMutable(FSID id) throws SVNException {
        FSRevisionNode node = getOwner().getRevisionNode(id);
        if (!node.getId().isTxn()) {
            return;
        }

        if (node.getType() == SVNNodeKind.DIR) {
            Map entries = node.getDirEntries(getOwner());
            for (Iterator names = entries.keySet().iterator(); names.hasNext();) {
                String name = (String) names.next();
                FSEntry entry = (FSEntry) entries.get(name);
                deleteEntryIfMutable(entry.getId());
            }
        }

        removeRevisionNode(id);
    }

    private void removeRevisionNode(FSID id) throws SVNException {
        FSRevisionNode node = getOwner().getRevisionNode(id);

        if (!node.getId().isTxn()) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_NOT_MUTABLE, "Attempted removal of immutable node");
            SVNErrorManager.error(err);
        }

        if (node.getPropsRepresentation() != null && node.getPropsRepresentation().isTxn()) {
            SVNFileUtil.deleteFile(getTransactionRevNodePropsFile(id));
        }

        if (node.getTextRepresentation() != null && node.getTextRepresentation().isTxn() && node.getType() == SVNNodeKind.DIR) {
            SVNFileUtil.deleteFile(getTransactionRevNodeChildrenFile(id));
        }

        SVNFileUtil.deleteFile(getOwner().getTransactionRevNodeFile(id));
    }

    public void setProplist(FSRevisionNode node, Map properties) throws SVNException {
        if (!node.getId().isTxn()) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_NOT_MUTABLE, "Can't set proplist on *immutable* node-revision {0}", node.getId());
            SVNErrorManager.error(err);
        }

        File propsFile = getTransactionRevNodePropsFile(node.getId());
        SVNProperties.setProperties(properties, propsFile, SVNFileUtil.createUniqueFile(propsFile.getParentFile(), ".props", ".tmp"), SVNProperties.SVN_HASH_TERMINATOR);

        if (node.getPropsRepresentation() == null || !node.getPropsRepresentation().isTxn()) {
            FSRepresentation mutableRep = new FSRepresentation();
            mutableRep.setTxnId(node.getId().getTxnID());
            node.setPropsRepresentation(mutableRep);
            getOwner().putTxnRevisionNode(node.getId(), node);
        }
    }

    public FSID createSuccessor(FSID oldId, FSRevisionNode newRevNode, String copyId) throws SVNException {
        if (copyId == null) {
            copyId = oldId.getCopyID();
        }
        FSID id = FSID.createTxnId(oldId.getNodeID(), copyId, myTxnID);
        newRevNode.setId(id);
        if (newRevNode.getCopyRootPath() == null) {
            newRevNode.setCopyRootPath(newRevNode.getCreatedPath());
            newRevNode.setCopyRootRevision(newRevNode.getId().getRevision());
        }
        getOwner().putTxnRevisionNode(newRevNode.getId(), newRevNode);
        return id;
    }

    public void setEntry(FSRevisionNode parentRevNode, String entryName, FSID entryId, SVNNodeKind kind) throws SVNException {
        if (parentRevNode.getType() != SVNNodeKind.DIR) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_NOT_DIRECTORY, "Attempted to set entry in non-directory node");
            SVNErrorManager.error(err);
        }

        if (!parentRevNode.getId().isTxn()) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_NOT_MUTABLE, "Attempted to set entry in immutable node");
            SVNErrorManager.error(err);
        }

        FSRepresentation textRep = parentRevNode.getTextRepresentation();
        File childrenFile = getTransactionRevNodeChildrenFile(parentRevNode.getId());
        OutputStream dst = null;

        try {
            if (textRep == null || !textRep.isTxn()) {
                Map entries = parentRevNode.getDirEntries(getOwner());
                Map unparsedEntries = unparseDirEntries(entries);
                dst = SVNFileUtil.openFileForWriting(childrenFile);
                SVNProperties.setProperties(unparsedEntries, dst, SVNProperties.SVN_HASH_TERMINATOR);
                textRep = new FSRepresentation();
                textRep.setRevision(FSRepository.SVN_INVALID_REVNUM);
                textRep.setTxnId(myTxnID);
                parentRevNode.setTextRepresentation(textRep);
                getOwner().putTxnRevisionNode(parentRevNode.getId(), parentRevNode);
            } else {
                dst = SVNFileUtil.openFileForWriting(childrenFile, true);
            }
            Map dirContents = parentRevNode.getDirContents();
            if (entryId != null) {
                SVNProperties.appendProperty(entryName, kind + " " + entryId.toString(), dst);
                if (dirContents != null) {
                    dirContents.put(entryName, new FSEntry(entryId, kind, entryName));
                }
            } else {
                SVNProperties.appendPropertyDeleted(entryName, dst);
                if (dirContents != null) {
                    dirContents.remove(entryName);
                }
            }
        } finally {
            SVNFileUtil.closeFile(dst);
        }
    }

    public void writeChangeEntry(OutputStream changesFile, FSPathChange pathChange) throws SVNException, IOException {
        FSPathChangeKind changeKind = pathChange.getChangeKind();
        if (!(changeKind == FSPathChangeKind.FS_PATH_CHANGE_ADD || changeKind == FSPathChangeKind.FS_PATH_CHANGE_DELETE || changeKind == FSPathChangeKind.FS_PATH_CHANGE_MODIFY
                || changeKind == FSPathChangeKind.FS_PATH_CHANGE_REPLACE || changeKind == FSPathChangeKind.FS_PATH_CHANGE_RESET)) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_CORRUPT, "Invalid change type");
            SVNErrorManager.error(err);
        }
        String changeString = changeKind.toString();
        String idString = null;
        if (pathChange.getRevNodeId() != null) {
            idString = pathChange.getRevNodeId().toString();
        } else {
            idString = FSPathChangeKind.ACTION_RESET;
        }

        String output = idString + " " + changeString + " " + SVNProperty.toString(pathChange.isTextModified()) + " " + SVNProperty.toString(pathChange.arePropertiesModified()) + " "
                + pathChange.getPath() + "\n";
        changesFile.write(output.getBytes("UTF-8"));

        String copyfromPath = pathChange.getCopyPath();
        long copyfromRevision = pathChange.getCopyRevision();

        if (copyfromPath != null && copyfromRevision != FSRepository.SVN_INVALID_REVNUM) {
            String copyfromLine = copyfromRevision + " " + copyfromPath;
            changesFile.write(copyfromLine.getBytes("UTF-8"));
        }
        changesFile.write("\n".getBytes("UTF-8"));
    }

    public long writeFinalChangedPathInfo(final CountingStream protoFile) throws SVNException, IOException {
        long offset = protoFile.getPosition();
        Map changedPaths = getChangedPaths();

        for (Iterator paths = changedPaths.keySet().iterator(); paths.hasNext();) {
            String path = (String) paths.next();
            FSPathChange change = (FSPathChange) changedPaths.get(path);
            FSID id = change.getRevNodeId();

            if (change.getChangeKind() != FSPathChangeKind.FS_PATH_CHANGE_DELETE && !id.isTxn()) {
                FSRevisionNode revNode = getOwner().getRevisionNode(id);
                change.setRevNodeId(revNode.getId());
            }

            writeChangeEntry(protoFile, change);
        }
        return offset;
    }

    public String[] readNextIDs() throws SVNException {
        String[] ids = new String[2];
        String idsToParse = null;
        FSFile idsFile = new FSFile(getOwner().getNextIDsFile(myTxnID));

        try {
            idsToParse = idsFile.readLine(FSTransactionRoot.MAX_KEY_SIZE * 2 + 3);
        } finally {
            idsFile.close();
        }

        int delimiterInd = idsToParse.indexOf(' ');

        if (delimiterInd == -1) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_CORRUPT, "next-ids file corrupt");
            SVNErrorManager.error(err);
        }

        ids[0] = idsToParse.substring(0, delimiterInd);
        ids[1] = idsToParse.substring(delimiterInd + 1);
        return ids;
    }

    public void writeFinalCurrentFile(long newRevision, String startNodeId, String startCopyId) throws SVNException, IOException {
        String[] txnIds = readNextIDs();
        String txnNodeId = txnIds[0];
        String txnCopyId = txnIds[1];
        String newNodeId = FSTransactionRoot.addKeys(startNodeId, txnNodeId);
        String newCopyId = FSTransactionRoot.addKeys(startCopyId, txnCopyId);
        String line = newRevision + " " + newNodeId + " " + newCopyId + "\n";
        File currentFile = getOwner().getCurrentFile();
        File tmpCurrentFile = SVNFileUtil.createUniqueFile(currentFile.getParentFile(), ".txnfile", ".tmp");
        OutputStream currentOS = null;

        try {
            currentOS = SVNFileUtil.openFileForWriting(tmpCurrentFile);
            currentOS.write(line.getBytes("UTF-8"));
        } finally {
            SVNFileUtil.closeFile(currentOS);
        }
        SVNFileUtil.rename(tmpCurrentFile, currentFile);
    }

    public FSID writeFinalRevision(FSID newId, final CountingStream protoFile, long revision, FSID id, String startNodeId, String startCopyId) throws SVNException, IOException {
        newId = null;

        if (!id.isTxn()) {
            return newId;
        }
        FSFS owner = getOwner();
        FSRevisionNode revNode = owner.getRevisionNode(id);
        if (revNode.getType() == SVNNodeKind.DIR) {
            Map namesToEntries = revNode.getDirEntries(owner);
            for (Iterator entries = namesToEntries.values().iterator(); entries.hasNext();) {
                FSEntry dirEntry = (FSEntry) entries.next();
                newId = writeFinalRevision(newId, protoFile, revision, dirEntry.getId(), startNodeId, startCopyId);
                if (newId != null && newId.getRevision() == revision) {
                    dirEntry.setId(newId);
                }
            }
            if (revNode.getTextRepresentation() != null && revNode.getTextRepresentation().isTxn()) {
                Map unparsedEntries = unparseDirEntries(namesToEntries);
                FSRepresentation textRep = revNode.getTextRepresentation();
                textRep.setTxnId(null);
                textRep.setRevision(revision);
                try {
                    textRep.setOffset(protoFile.getPosition());
                    final MessageDigest checksum = MessageDigest.getInstance("MD5");
                    long size = writeHashRepresentation(unparsedEntries, protoFile, checksum);
                    String hexDigest = SVNFileUtil.toHexDigest(checksum);
                    textRep.setSize(size);
                    textRep.setHexDigest(hexDigest);
                    textRep.setExpandedSize(textRep.getSize());
                } catch (NoSuchAlgorithmException nsae) {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, "MD5 implementation not found: {0}", nsae.getLocalizedMessage());
                    SVNErrorManager.error(err, nsae);
                }
            }
        } else {
            if (revNode.getTextRepresentation() != null && revNode.getTextRepresentation().isTxn()) {
                FSRepresentation textRep = revNode.getTextRepresentation();
                textRep.setTxnId(null);
                textRep.setRevision(revision);
            }
        }

        if (revNode.getPropsRepresentation() != null && revNode.getPropsRepresentation().isTxn()) {
            Map props = revNode.getProperties(owner);
            FSRepresentation propsRep = revNode.getPropsRepresentation();
            try {
                propsRep.setOffset(protoFile.getPosition());
                final MessageDigest checksum = MessageDigest.getInstance("MD5");
                long size = writeHashRepresentation(props, protoFile, checksum);
                String hexDigest = SVNFileUtil.toHexDigest(checksum);
                propsRep.setSize(size);
                propsRep.setHexDigest(hexDigest);
                propsRep.setTxnId(null);
                propsRep.setRevision(revision);
                propsRep.setExpandedSize(size);
            } catch (NoSuchAlgorithmException nsae) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, "MD5 implementation not found: {0}", nsae.getLocalizedMessage());
                SVNErrorManager.error(err, nsae);
            }
        }

        long myOffset = protoFile.getPosition();
        String myNodeId = null;
        String nodeId = revNode.getId().getNodeID();

        if (nodeId.startsWith("_")) {
            myNodeId = FSTransactionRoot.addKeys(startNodeId, nodeId.substring(1));
        } else {
            myNodeId = nodeId;
        }

        String myCopyId = null;
        String copyId = revNode.getId().getCopyID();

        if (copyId.startsWith("_")) {
            myCopyId = FSTransactionRoot.addKeys(startCopyId, copyId.substring(1));
        } else {
            myCopyId = copyId;
        }

        if (revNode.getCopyRootRevision() == FSRepository.SVN_INVALID_REVNUM) {
            revNode.setCopyRootRevision(revision);
        }

        newId = FSID.createRevId(myNodeId, myCopyId, revision, myOffset);
        revNode.setId(newId);

        getOwner().writeTxnNodeRevision(protoFile, revNode);
        getOwner().putTxnRevisionNode(id, revNode);
        return newId;
    }

    public FSRevisionNode cloneChild(FSRevisionNode parent, String parentPath, String childName, String copyId, boolean isParentCopyRoot) throws SVNException {
        if (!parent.getId().isTxn()) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_NOT_MUTABLE, "Attempted to clone child of non-mutable node");
            SVNErrorManager.error(err);
        }

        if (!SVNPathUtil.isSinglePathComponent(childName)) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_NOT_SINGLE_PATH_COMPONENT, "Attempted to make a child clone with an illegal name ''{0}''", childName);
            SVNErrorManager.error(err);
        }

        FSRevisionNode childNode = parent.getChildDirNode(childName, getOwner());
        FSID newNodeId = null;

        if (childNode.getId().isTxn()) {
            newNodeId = childNode.getId();
        } else {
            if (isParentCopyRoot) {
                childNode.setCopyRootPath(parent.getCopyRootPath());
                childNode.setCopyRootRevision(parent.getCopyRootRevision());
            }
            childNode.setCopyFromPath(null);
            childNode.setCopyFromRevision(FSRepository.SVN_INVALID_REVNUM);
            childNode.setPredecessorId(childNode.getId());
            if (childNode.getCount() != -1) {
                childNode.setCount(childNode.getCount() + 1);
            }
            childNode.setCreatedPath(SVNPathUtil.concatToAbs(parentPath, childName));
            newNodeId = createSuccessor(childNode.getId(), childNode, copyId);
            setEntry(parent, childName, newNodeId, childNode.getType());
        }
        return getOwner().getRevisionNode(newNodeId);
    }

    private long writeHashRepresentation(Map hashContents, OutputStream protoFile, MessageDigest digest) throws IOException, SVNException {
        HashRepresentationStream targetFile = new HashRepresentationStream(protoFile, digest);
        String header = FSRepresentation.REP_PLAIN + "\n";
        protoFile.write(header.getBytes("UTF-8"));
        SVNProperties.setProperties(hashContents, targetFile, SVNProperties.SVN_HASH_TERMINATOR);
        String trailer = FSRepresentation.REP_TRAILER + "\n";
        protoFile.write(trailer.getBytes("UTF-8"));
        return targetFile.mySize;
    }

    public File getTransactionRevNodePropsFile(FSID id) {
        return new File(getOwner().getTransactionDir(id.getTxnID()), FSFS.PATH_PREFIX_NODE + id.getNodeID() + "." + id.getCopyID() + FSFS.TXN_PATH_EXT_PROPS);
    }

    public File getTransactionRevNodeChildrenFile(FSID id) {
        return new File(getOwner().getTransactionDir(id.getTxnID()), FSFS.PATH_PREFIX_NODE + id.getNodeID() + "." + id.getCopyID() + FSFS.TXN_PATH_EXT_CHILDREN);
    }

    public File getTransactionRevFile() {
        if (myTxnRevFile == null) {
            myTxnRevFile = new File(getOwner().getTransactionDir(myTxnID), FSFS.TXN_PATH_REV);
        }
        return myTxnRevFile;
    }

    public File getTransactionChangesFile() {
        if (myTxnChangesFile == null) {
            myTxnChangesFile = new File(getOwner().getTransactionDir(myTxnID), "changes");
        }
        return myTxnChangesFile;
    }

    public static String generateNextKey(String oldKey) throws SVNException {
        char[] nextKey = new char[oldKey.length() + 1];
        boolean carry = true;
        if (oldKey.length() > 1 && oldKey.charAt(0) == '0') {
            return null;
        }
        for (int i = oldKey.length() - 1; i >= 0; i--) {
            char c = oldKey.charAt(i);
            if (!((c >= '0' && c <= '9') || (c >= 'a' && c <= 'z'))) {
                return null;
            }
            if (carry) {
                if (c == 'z') {
                    nextKey[i] = '0';
                } else {
                    carry = false;
                    if (c == '9') {
                        nextKey[i] = 'a';
                    } else {
                        nextKey[i] = (char) (c + 1);
                    }
                }
            } else {
                nextKey[i] = c;
            }
        }
        int nextKeyLength = oldKey.length() + (carry ? 1 : 0);
        if (nextKeyLength >= MAX_KEY_SIZE) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNKNOWN, "FATAL error: new key length is greater than the threshold {0,number,integer}", new Integer(MAX_KEY_SIZE));
            SVNErrorManager.error(err);
        }
        if (carry) {
            System.arraycopy(nextKey, 0, nextKey, 1, oldKey.length());
            nextKey[0] = '1';
        }
        return new String(nextKey, 0, nextKeyLength);
    }

    private static String addKeys(String key1, String key2) {
        int i1 = key1.length() - 1;
        int i2 = key2.length() - 1;
        int carry = 0;
        int val;
        StringBuffer result = new StringBuffer();

        while (i1 >= 0 || i2 >= 0 || carry > 0) {
            val = carry;

            if (i1 >= 0) {
                val += key1.charAt(i1) <= '9' ? key1.charAt(i1) - '0' : key1.charAt(i1) - 'a' + 10;
            }

            if (i2 >= 0) {
                val += key2.charAt(i2) <= '9' ? key2.charAt(i2) - '0' : key2.charAt(i2) - 'a' + 10;
            }

            carry = val / 36;
            val = val % 36;

            char sym = val <= 9 ? (char) ('0' + val) : (char) (val - 10 + 'a');
            result.append(sym);

            if (i1 >= 0) {
                --i1;
            }

            if (i2 >= 0) {
                --i2;
            }
        }

        return result.reverse().toString();
    }

    private static class HashRepresentationStream extends OutputStream {

        long mySize;
        MessageDigest myChecksum;
        OutputStream myProtoFile;

        public HashRepresentationStream(OutputStream protoFile, MessageDigest digest) {
            super();
            mySize = 0;
            myChecksum = digest;
            myProtoFile = protoFile;
        }

        public void write(int b) throws IOException {
            myProtoFile.write(b);
            if (myChecksum != null) {
                myChecksum.update((byte) b);
            }
            mySize++;
        }

        public void write(byte[] b, int off, int len) throws IOException {
            myProtoFile.write(b, off, len);
            if (myChecksum != null) {
                myChecksum.update(b, off, len);
            }
            mySize += len;
        }

        public void write(byte[] b) throws IOException {
            myProtoFile.write(b);
            if (myChecksum != null) {
                myChecksum.update(b);
            }
            mySize += b.length;
        }
    }
}
