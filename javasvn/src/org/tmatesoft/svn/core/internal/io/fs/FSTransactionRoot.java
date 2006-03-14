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

import java.util.Map;

import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;


/**
 * @version 1.0
 * @author  TMate Software Ltd.
 */
public class FSTransactionRoot extends FSRoot {
    private String myTxnID;
    private int myTxnFlags;

    public FSTransactionRoot(FSFS owner, String txnID, int flags) {
        super(owner);
        myTxnID = txnID;
        myTxnFlags = flags;
    }

    public FSCopyInheritance getCopyInheritance(FSParentPath child) throws SVNException{
        /* Make some assertions about the function input. */
        if (child == null || child.getParent() == null || myTxnID == null) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNKNOWN, "FATAL error: invalid txn name or child");
            SVNErrorManager.error(err);
        }
        FSID childID = child.getRevNode().getId();
        FSID parentID = child.getParent().getRevNode().getId();
        String childCopyID = childID.getCopyID();
        String parentCopyID = parentID.getCopyID();

        /* If this child is already mutable, we have nothing to do */
        if (childID.isTxn()) {
            return new FSCopyInheritance(FSCopyIDInheritanceStyle.COPY_ID_INHERIT_SELF, null);
        }
        
        /* From this point on, we'll assume that the child will just take
         * its copy ID from its parent
         */
        FSCopyInheritance copyInheritance = new FSCopyInheritance(FSCopyIDInheritanceStyle.COPY_ID_INHERIT_PARENT, null);

        /* Special case: if the child's copy ID is '0', use the parent's
         * copy ID
         */
        if (childCopyID.compareTo("0") == 0) {
            return copyInheritance;
        }

        /* Compare the copy IDs of the child and its parent. If they are
         * the same, then the child is already on the same branch as the
         * parent, and should use the same mutability copy ID that the
         * parent will use
         */
        if (childCopyID.compareTo(parentCopyID) == 0) {
            return copyInheritance;
        }

        /* If the child is on the same branch that the parent is on, the
         * child should just use the same copy ID that the parent would use.
         * Else, the child needs to generate a new copy ID to use should it
         * need to be made mutable. We will claim that child is on the same
         * branch as its parent if the child itself is not a branch point,
         * or if it is a branch point that we are accessing via its original
         * copy destination path
         */
        long copyrootRevision = child.getRevNode().getCopyRootRevision();
        String copyrootPath = child.getRevNode().getCopyRootPath(); 

        FSRoot copyrootRoot = getOwner().createRevisionRoot(copyrootRevision); 
        FSRevisionNode copyrootNode = copyrootRoot.getRevisionNode(copyrootPath); 
        FSID copyrootID = copyrootNode.getId();
        if (copyrootID.compareTo(childID) == -1) {
            return copyInheritance;
        }

        /* Determine if we are looking at the child via its original path or
         * as a subtree item of a copied tree
         */
        String idPath = child.getRevNode().getCreatedPath();
        if (idPath.compareTo(child.getAbsPath()) == 0) {
            copyInheritance.setStyle(FSCopyIDInheritanceStyle.COPY_ID_INHERIT_SELF);
            return copyInheritance;
        }

        copyInheritance.setStyle(FSCopyIDInheritanceStyle.COPY_ID_INHERIT_NEW);
        copyInheritance.setCopySourcePath(idPath);
        return copyInheritance;
    }

    public FSRevisionNode getRootRevisionNode() throws SVNException {
        if (myRootRevisionNode == null ) {
            FSTransaction txn = getTxn();
            myRootRevisionNode = getOwner().getRevisionNode(txn.getRootId());
        }
        return myRootRevisionNode;
    }

    public FSTransaction getTxn() throws SVNException {
        Map txnProps = getOwner().getTransactionProperties(myTxnID);
        FSID rootID = FSID.createTxnId("0", "0", myTxnID);
        FSRevisionNode revNode = getOwner().getRevisionNode(rootID);
        FSTransaction txn = new FSTransaction(FSTransactionKind.TXN_KIND_NORMAL, revNode.getId(), revNode.getPredecessorId(), null, txnProps);
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

}
