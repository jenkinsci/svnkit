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

import java.util.Map;
import java.util.HashMap;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;

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
    
    public static FSRoot createRevisionRoot(long revision, FSRevisionNode root) {
        return new FSRoot(revision, root);
    }
    
    private FSRoot(long revision, FSRevisionNode root) {
        myRevision = revision;
        myRootRevNode = root;
        myIsTxnRoot = false;
        myTxnId = FSID.ID_INAPPLICABLE;
        myTxnFlags = 0;
        myCopyfromCache = new HashMap();
    }

    public static FSRoot createTransactionRoot(String txnId, int flags){
        return new FSRoot(txnId, flags);
    }
    
    private FSRoot(String txnId, int flags) {
        myTxnId = txnId;
        myTxnFlags = flags;
        myIsTxnRoot = true;
        myRevision = FSConstants.SVN_INVALID_REVNUM;
        myRootRevNode = null;
        myCopyfromCache = new HashMap();
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

    public Map getCopyfromCache(){
    	return myCopyfromCache;
    }
    
    public void setCopyfromCache(Map newCopyfromCache){
    	myCopyfromCache = newCopyfromCache;
    }
    
    public void putRevNodeToCache(String path, FSRevisionNode node) throws SVNException {
        if(myRevNodesCache == null){
            myRevNodesCache = new HashMap();
        }
        /* Assert valid input. */
        if(!path.startsWith("/")){
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNKNOWN, "Invalid path ''{0}''", path);
            SVNErrorManager.error(err);
        }
        myRevNodesCache.put(path, node);
    }

    public void removeRevNodeFromCache(String path) throws SVNException {
        if(myRevNodesCache == null){
            return;
        }
        /* Assert valid input. */
        if(!path.startsWith("/")){
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNKNOWN, "Invalid path ''{0}''", path);
            SVNErrorManager.error(err);
        }
        myRevNodesCache.remove(path);
    }
    
    public FSRevisionNode fetchRevNodeFromCache(String path) throws SVNException {
        if(myRevNodesCache == null){
            return null;
        }
        /* Assert valid input. */
        if(!path.startsWith("/")){
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNKNOWN, "Invalid path ''{0}''", path);
            SVNErrorManager.error(err);
        }
        return (FSRevisionNode)myRevNodesCache.get(path);
    }
}
