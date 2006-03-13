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
        //TODO: correct this
        return null;
    }

    public FSRevisionNode getRevisionNode(String path) throws SVNException{
        //TODO: correct this
        return null;
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
