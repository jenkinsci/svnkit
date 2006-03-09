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
import org.tmatesoft.svn.core.SVNNodeKind;

public class FSRevisionNode {
    //id: a.b.r<revID>/offset
    private FSID myId;
    
    //type: 'dir' or 'file' 
    private SVNNodeKind myType;
    
    //count:  count of revs since base
    private long myCount;
    
    //(_)a.(_)b.tx-y

    //pred: a.b.r<revID>/offset
    private FSID myPredecessorId;
    
    //text: <rev> <offset> <length> <size> <digest>
    private FSRepresentation myTextRepresentation;
    
    //props: <rev> <offset> <length> <size> <digest>
    private FSRepresentation myPropsRepresentation;
    
    //cpath: <path>
    private String myCreatedPath;

    //copyfrom: <revID> <path>
    private long myCopyFromRevision;
    private String myCopyFromPath;

    //copyroot: <revID> <created-path>
    private long myCopyRootRevision;    
    private String myCopyRootPath;

    //for only node-revs representing dirs 
    private Map myDirContents;
    
    public FSRevisionNode(){
    }
    
    public void setId(FSID revNodeID){
        myId = revNodeID;
    }

    public void setType(SVNNodeKind nodeKind){
        myType = nodeKind;
    }

    public void setCount(long count){
        myCount = count;
    }

    public void setPredecessorId(FSID predRevNodeId){
        myPredecessorId = predRevNodeId;
    }

    public void setTextRepresentation(FSRepresentation textRepr){
        myTextRepresentation = textRepr;
    }

    public void setPropsRepresentation(FSRepresentation propsRepr){
        myPropsRepresentation = propsRepr;
    }

    public void setCreatedPath(String cpath){
        myCreatedPath = cpath;
    }
    
    public void setCopyFromRevision(long copyFromRev){
        myCopyFromRevision = copyFromRev;
    }
    
    public void setCopyFromPath(String copyFromPath){
        myCopyFromPath = copyFromPath;
    }

    public void setCopyRootRevision(long copyRootRev){
        myCopyRootRevision = copyRootRev;
    }
    
    public void setCopyRootPath(String copyRootPath){
        myCopyRootPath = copyRootPath;
    }

    public FSID getId(){
        return myId;
    }

    public SVNNodeKind getType(){
        return myType;
    }

    public long getCount(){
        return myCount;
    }

    public FSID getPredecessorId(){
        return myPredecessorId;
    }

    //text
    public FSRepresentation getTextRepresentation(){
        return myTextRepresentation;
    }

    //props
    public FSRepresentation getPropsRepresentation(){
        return myPropsRepresentation;
    }

    public String getCreatedPath(){
        return myCreatedPath;
    }
    
    public long getCopyFromRevision(){
        return myCopyFromRevision;
    }
    
    public String getCopyFromPath(){
        return myCopyFromPath;
    }

    public long getCopyRootRevision(){
        return myCopyRootRevision;
    }
    
    public String getCopyRootPath(){
        return myCopyRootPath;
    }
    
    public static FSRevisionNode dumpRevisionNode(FSRevisionNode revNode){
        FSRevisionNode clone = new FSRevisionNode();
        clone.setId(revNode.getId().copy());
        if(revNode.getPredecessorId() != null){
            clone.setPredecessorId(revNode.getPredecessorId().copy());
        }
        clone.setType(revNode.getType());
        clone.setCopyFromPath(revNode.getCopyFromPath());
        clone.setCopyFromRevision(revNode.getCopyFromRevision());
        clone.setCopyRootPath(revNode.getCopyRootPath());
        clone.setCopyRootRevision(revNode.getCopyRootRevision());
        clone.setCount(revNode.getCount());
        clone.setCreatedPath(revNode.getCreatedPath());
        if(revNode.getPropsRepresentation() != null){
            clone.setPropsRepresentation(new FSRepresentation(revNode.getPropsRepresentation()));
        }
        if(revNode.getTextRepresentation() != null){
            clone.setTextRepresentation(new FSRepresentation(revNode.getTextRepresentation()));
        }
        return clone;
    }

    public Map getDirContents() {
        return myDirContents;
    }

    public void setDirContents(Map dirContents) {
        myDirContents = dirContents;
    }
}
