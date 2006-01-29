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


/**
 * @version 1.0
 * @author  TMate Software Ltd.
 */
public class FSID {
    public static final String ID_INAPPLICABLE = "inapplicable";
    private String myNodeID;
    private String myCopyID;
    private String myTxnID;
    private long myRevision;
    private long myOffset;
    
    public FSID(){
        myNodeID = ID_INAPPLICABLE;
        myCopyID = ID_INAPPLICABLE;
        myTxnID = ID_INAPPLICABLE;
        myRevision = -1;
        myOffset = -1;
    }
    
    public boolean isTxn(){
        return isTxn(myTxnID);
    }
    
    public static boolean isTxn(String txnId){
        if(txnId != null && txnId != ID_INAPPLICABLE){
            return true;
        }
        return false;
    }
    
    public static FSID createTxnId(String nodeId, String copyId, String txnId){
        return new FSID(nodeId, txnId, copyId, FSConstants.SVN_INVALID_REVNUM, -1);
    }

    public static FSID createRevId(String nodeId, String copyId, long revision, long offset){
        return new FSID(nodeId, null, copyId, revision, offset);
    }
    
    public FSID(String nodeId, String txnId, String copyId, long revision, long offset){
        myNodeID = (nodeId == null) ? ID_INAPPLICABLE :  nodeId;
        myCopyID = (copyId == null) ? ID_INAPPLICABLE : copyId;
        myTxnID = (txnId == null) ? ID_INAPPLICABLE :  txnId; 
        myRevision = revision;
        myOffset = offset;
    }

    public FSID(FSID id){
        myNodeID = (id.getNodeID() == null) ? ID_INAPPLICABLE :  id.getNodeID();
        myCopyID = (id.getCopyID() == null) ? ID_INAPPLICABLE : id.getCopyID();
        myTxnID = (id.getTxnID() == null) ? ID_INAPPLICABLE :  id.getTxnID(); 
        myRevision = id.getRevision();
        myOffset = id.getOffset();
    }

    public void setNodeID(String nodeId){
        myNodeID = (nodeId == null) ? ID_INAPPLICABLE :  nodeId;
    }

    public void setCopyID(String copyId){
        myCopyID = (copyId == null) ? ID_INAPPLICABLE : copyId;
    }

    public void setTxnID(String txnId){
        myTxnID = (txnId == null) ? ID_INAPPLICABLE :  txnId;
    }
    
    public void setRevision(long rev){
        myRevision = rev;
    }

    public void setOffset(long offset){
        myOffset = offset;
    }

    public String getNodeID(){
        return myNodeID;
    }

    public String getTxnID(){
        return myTxnID;
    }

    public String getCopyID(){
        return myCopyID;
    }
    
    public long getRevision(){
        return myRevision;
    }

    public long getOffset(){
        return myOffset;
    }
    
    public boolean equals(Object obj){
        if (obj == null || obj.getClass() != FSID.class) {
            return false;
        }
        FSID id = (FSID)obj;
        if(this == id){
            return true;
        }
        if(!myNodeID.equals(id.getNodeID())){
            return false;
        }
        if(!myCopyID.equals(id.getCopyID())){
            return false;
        }
        if(!myTxnID.equals(id.getTxnID())){
            return false;
        }
        if(myRevision != id.getRevision() || myOffset != id.getOffset()){
            return false;
        }
        return true;
    }
    
    /*
     * Return values:
     *  0 - id1 equals to id2
     *  1 - id1 is related to id2 (id2 is a result of user's modifications)
     * -1 - id1 is not related to id2 (absolutely different items)  
     */
    public static int compareIds(FSID id1, FSID id2){
        if(areEqualIds(id1, id2)){
            return 0;
        }
        return checkIdsRelated(id1, id2) ? 1 : -1;
    }
    
    public static boolean checkIdsRelated(FSID id1, FSID id2){
        if(id1 == id2){
            return true;
        }
        /* If both node ids start with _ and they have differing transaction
         * IDs, then it is impossible for them to be related. 
         */
        if(id1.getNodeID().startsWith("_")){
            if(!id1.getTxnID().equals(id2.getTxnID())){
                return false;
            }
        }
        return id1.getNodeID().equals(id2.getNodeID());
    }
    
    private static boolean areEqualIds(FSID id1, FSID id2){
        if(id1 == id2){
            return true;
        }else if(id1 != null){
            return id1.equals(id2);
        }else if(id2 != null){
            return id2.equals(id1);
        }
        return true;
    }
    
    public String toString(){
        return myNodeID + "." + myCopyID + "." + (isTxn() ? "t" + myTxnID : "r" + myRevision + "/" + myOffset);
    }
}
