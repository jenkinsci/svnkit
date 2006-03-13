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
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.internal.wc.SVNProperties;

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
    
    private FSFS myFSFS;
    
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
    
    public static FSRevisionNode fromMap(Map headers) throws SVNException {
        FSRevisionNode revNode = new FSRevisionNode();

        // Read the rev-node id.
        String revNodeId = (String) headers.get(FSConstants.HEADER_ID);
        if (revNodeId == null) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_CORRUPT, "Missing node-id in node-rev");
            SVNErrorManager.error(err);
        }

        FSID revnodeID = FSID.fromString(revNodeId);
        if (revnodeID == null) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_CORRUPT, "Corrupt node-id in node-rev");
            SVNErrorManager.error(err);
        }
        revNode.setId(revnodeID);

        // Read the type.
        SVNNodeKind nodeKind = SVNNodeKind.parseKind((String) headers.get(FSConstants.HEADER_TYPE));
        if (nodeKind == SVNNodeKind.NONE || nodeKind == SVNNodeKind.UNKNOWN) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_CORRUPT, "Missing kind field in node-rev");
            SVNErrorManager.error(err);
        }
        revNode.setType(nodeKind);

        // Read the 'count' field.
        String countString = (String) headers.get(FSConstants.HEADER_COUNT);
        if (countString == null) {
            revNode.setCount(0);
        } else {
            long cnt = -1;
            try {
                cnt = Long.parseLong(countString);
            } catch (NumberFormatException nfe) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_CORRUPT, "Corrupt count field in node-rev");
                SVNErrorManager.error(err);
            }
            revNode.setCount(cnt);
        }

        // Get the properties location (if any).
        String propsRepr = (String) headers.get(FSConstants.HEADER_PROPS);
        if (propsRepr != null) {
            FSReader.parseRepresentationHeader(propsRepr, revNode, revnodeID.getTxnID(), false);
        }

        // Get the data location (if any).
        String textRepr = (String) headers.get(FSConstants.HEADER_TEXT);
        if (textRepr != null) {
            FSReader.parseRepresentationHeader(textRepr, revNode, revnodeID.getTxnID(), true);
        }

        // Get the created path.
        String cpath = (String) headers.get(FSConstants.HEADER_CPATH);
        if (cpath == null) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_CORRUPT, "Missing cpath in node-rev");
            SVNErrorManager.error(err);
        }
        revNode.setCreatedPath(cpath);

        // Get the predecessor rev-node id (if any).
        String predId = (String) headers.get(FSConstants.HEADER_PRED);
        if (predId != null) {
            FSID predRevNodeId = FSID.fromString(predId);
            if (predRevNodeId == null) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_CORRUPT, "Corrupt predecessor node-id in node-rev");
                SVNErrorManager.error(err);
            }
            revNode.setPredecessorId(predRevNodeId);
        }

        // Get the copyroot.
        String copyroot = (String) headers.get(FSConstants.HEADER_COPYROOT);
        if (copyroot == null) {
            revNode.setCopyRootPath(revNode.getCreatedPath());
            revNode.setCopyRootRevision(revNode.getId().getRevision());
        } else {
            FSReader.parseCopyRoot(copyroot, revNode);
        }

        // Get the copyfrom.
        String copyfrom = (String) headers.get(FSConstants.HEADER_COPYFROM);
        if (copyfrom == null) {
            revNode.setCopyFromPath(null);
            revNode.setCopyFromRevision(FSConstants.SVN_INVALID_REVNUM);
        } else {
            FSReader.parseCopyFrom(copyfrom, revNode);
        }

        return revNode;
    }

    public void setFSFS(FSFS owner) {
        myFSFS = owner;
    }
    
    public FSRevisionNode getChildDirNode(String childName, File reposRootDir) throws SVNException {
        /* Make sure that NAME is a single path component. */
        if (!SVNPathUtil.isSinglePathComponent(childName)) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_NOT_SINGLE_PATH_COMPONENT, "Attempted to open node with an illegal name ''{0}''", childName);
            SVNErrorManager.error(err);
        }
        /* Now get the node that was requested. */
        Map entries = getDirEntries(reposRootDir);
        FSEntry entry = entries != null ? (FSEntry) entries.get(childName) : null;
        if (entry == null) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_NOT_FOUND, "Attempted to open non-existent child node ''{0}''", childName);
            SVNErrorManager.error(err);
        }
        return myFSFS.getRevisionNode(entry.getId());
    }

    public Map getDirEntries(File reposRootDir) throws SVNException {
        if (getType() != SVNNodeKind.DIR) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_NOT_DIRECTORY, "Can't get entries of non-directory");
            SVNErrorManager.error(err);
        }
        // first try to ask the object's cache for entries
        Map entries = new HashMap();
        Map dirContents = getDirContents();
        if (dirContents == null) {
            dirContents = getDirContents(reposRootDir);
            setDirContents(dirContents);
        }
        if (dirContents != null) {
            entries.putAll(dirContents);
        }
        return entries;
    }

    private Map getDirContents(File reposRootDir) throws SVNException {
        if (getTextRepresentation() != null && getTextRepresentation().isTxn()) {
            /*
             * The representation is mutable. Read the old directory contents
             * from the mutable children file, followed by the changes we've
             * made in this transaction.
             */
            File childrenFile = FSRepositoryUtil.getTxnRevNodeChildrenFile(getId(), reposRootDir);
            InputStream file = null;
            Map entries = null;
            try {
                file = SVNFileUtil.openFileForReading(childrenFile);
                Map rawEntries = SVNProperties.asMap(null, file, false, SVNProperties.SVN_HASH_TERMINATOR);
                rawEntries = SVNProperties.asMap(rawEntries, file, true, null);
                entries = FSReader.parsePlainRepresentation(rawEntries);
            } finally {
                SVNFileUtil.closeFile(file);
            }
            return entries;
        } else if (getTextRepresentation() != null) {
            InputStream is = null;
            FSRepresentation textRepresent = getTextRepresentation();
            try {
                is = FSInputStream.createPlainStream(textRepresent, reposRootDir);
                Map rawEntries = SVNProperties.asMap(null, is, false, SVNProperties.SVN_HASH_TERMINATOR);
                return FSReader.parsePlainRepresentation(rawEntries);
            } finally {
                SVNFileUtil.closeFile(is);
            }
        }
        return new HashMap();// returns an empty map, must not be null!!
    }

}
