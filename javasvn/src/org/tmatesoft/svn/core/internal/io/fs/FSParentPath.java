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

import java.io.File;

import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNErrorCode;

/**
 * @version 1.0
 * @author TMate Software Ltd.
 */
public class FSParentPath {

    // Copy id inheritance style
    public static final int COPY_ID_INHERIT_UNKNOWN = 0;
    public static final int COPY_ID_INHERIT_SELF = 1;
    public static final int COPY_ID_INHERIT_PARENT = 2;
    public static final int COPY_ID_INHERIT_NEW = 3;

    /*
     * A node along the path. This could be the final node, one of its parents,
     * or the root. Every parent path ends with an element for the root
     * directory
     */
    private FSRevisionNode myRevNode;

    /*
     * The name NODE has in its parent directory. This is zero for the root
     * directory, which (obviously) has no name in its parent
     */
    private String myEntryName;

    /* The parent of NODE, or zero if NODE is the root directory */
    private FSParentPath myParent;

    /* The copy ID inheritence style */
    /*
     * If copy ID inheritence style is copy_id_inherit_new, this is the path
     * which should be implicitly copied; otherwise, this is NULL
     */

    private FSCopyInheritance myCopyInheritance;

    /* constructors */

    public FSParentPath(FSParentPath newParentPath) {
        myRevNode = newParentPath.myRevNode;
        myEntryName = newParentPath.myEntryName;
        myParent = newParentPath.myParent;
        myCopyInheritance = newParentPath.myCopyInheritance;
    }

    public FSParentPath(FSRevisionNode newRevNode, String newEntry, FSParentPath newParentPath) {
        myRevNode = newRevNode;
        myEntryName = newEntry;
        myParent = newParentPath;
        if (newRevNode != null) {
            myCopyInheritance = new FSCopyInheritance(FSCopyInheritance.COPY_ID_INHERIT_UNKNOWN, newRevNode.getCopyFromPath());
        } else {
            myCopyInheritance = new FSCopyInheritance(FSCopyInheritance.COPY_ID_INHERIT_UNKNOWN, null);
        }
    }

    // methods-accessors
    public FSRevisionNode getRevNode() {
        return myRevNode;
    }

    public void setRevNode(FSRevisionNode newRevNode) {
        myRevNode = newRevNode;
    }

    public String getNameEntry() {
        return myEntryName;
    }

    public void setNameEntry(String newNameEntry) {
        myEntryName = newNameEntry;
    }

    public FSParentPath getParent() {
        return myParent;
    }

    public int getCopyStyle() {
        return myCopyInheritance.getStyle();
    }

    public void setCopyStyle(int newCopyStyle) {
        myCopyInheritance.setStyle(newCopyStyle);
    }

    public String getCopySourcePath() {
        return myCopyInheritance.getCopySourcePath();
    }

    public void setCopySourcePath(String newCopyPath) {
        myCopyInheritance.setCopySourcePath(newCopyPath);
    }

    public void setParentPath(FSRevisionNode newRevNode, String newEntry, FSParentPath newParentPath) {
        myRevNode = newRevNode;
        myEntryName = newEntry;
        myParent = newParentPath;
        myCopyInheritance = new FSCopyInheritance(FSCopyInheritance.COPY_ID_INHERIT_UNKNOWN, null);
    }

    /*
     * Return a text string describing the absolute path of parentPath.
     */
    public String getAbsPath() {
        String pathSoFar = "/";
        if (myParent != null) {
            pathSoFar = myParent.getAbsPath();
        }
        return getNameEntry() != null ? SVNPathUtil.concatToAbs(pathSoFar, getNameEntry()) : pathSoFar;
    }

    // Return value consist of :
    // 1: SVNLocationEntry.revision
    // copy inheritance style
    // 2: SVNLocationEntry.path
    // copy src path
    public static FSCopyInheritance getCopyInheritance(File reposRootDir, FSParentPath child, String txnID, FSRevisionNodePool pool) throws SVNException {
        /* Make some assertions about the function input. */
        if (child == null || child.getParent() == null || txnID == null) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNKNOWN, "FATAL error: invalid parameters");
            SVNErrorManager.error(err);
        }
        FSID childID = child.getRevNode().getId();
        FSID parentID = child.getParent().getRevNode().getId();
        FSID copyrootID = null;
        String childCopyID = childID.getCopyID();
        String parentCopyID = parentID.getCopyID();
        FSRevisionNode copyrootRoot = null;
        FSRevisionNode copyrootNode = null;

        // If this child is already mutable, we have nothing to do
        if (childID.isTxn()) {
            return new FSCopyInheritance(FSCopyInheritance.COPY_ID_INHERIT_SELF, null);
            //return new SVNLocationEntry(FSParentPath.COPY_ID_INHERIT_SELF, null);
        }
        // From this point on, we'll assume that the child will just take
        // its copy ID from its parent
        FSCopyInheritance copyInheritance = new FSCopyInheritance(FSCopyInheritance.COPY_ID_INHERIT_PARENT, null);
        //SVNLocationEntry constrEntry = new SVNLocationEntry(FSParentPath.COPY_ID_INHERIT_PARENT, null);

        // Special case: if the child's copy ID is '0', use the parent's
        // copy ID
        if (childCopyID.compareTo("0") == 0) {
            return copyInheritance;
            //return constrEntry;
        }

        // Compare the copy IDs of the child and its parent. If they are
        // the same, then the child is already on the same branch as the
        // parent, and should use the same mutability copy ID that the
        // parent will use
        if (childCopyID.compareTo(parentCopyID) == 0) {
            return copyInheritance;
            //return constrEntry;
        }

        // If the child is on the same branch that the parent is on, the
        // child should just use the same copy ID that the parent would use.
        // Else, the child needs to generate a new copy ID to use should it
        // need to be made mutable. We will claim that child is on the same
        // branch as its parent if the child itself is not a branch point,
        // or if it is a branch point that we are accessing via its original
        // copy destination path
//        SVNLocationEntry copyrootEntry = new SVNLocationEntry(child.getRevNode().getCopyRootRevision(), child.getRevNode().getCopyRootPath());
        long copyrootRevision = child.getRevNode().getCopyRootRevision();
        String copyrootPath = child.getRevNode().getCopyRootPath(); 
        copyrootRoot = pool.getRootRevisionNode(copyrootRevision, reposRootDir); 
        copyrootNode = pool.openPath(FSOldRoot.createRevisionRoot(copyrootRoot.getId().getRevision(), copyrootRoot, reposRootDir), copyrootPath, false, null, reposRootDir, false).getRevNode(); 
        copyrootID = copyrootNode.getId();
        if (copyrootID.compareTo(childID) == -1) {
            return copyInheritance;
        }

        //Determine if we are looking at the child via its original path or
        //as a subtree item of a copied tree
        String idPath = child.getRevNode().getCreatedPath();
        if (idPath.compareTo(child.getAbsPath()) == 0) {
            copyInheritance.setStyle(FSCopyInheritance.COPY_ID_INHERIT_SELF);
            return copyInheritance;
        }
        copyInheritance.setStyle(FSCopyInheritance.COPY_ID_INHERIT_NEW);
        copyInheritance.setCopySourcePath(idPath);
        return copyInheritance;
        //return new SVNLocationEntry(FSParentPath.COPY_ID_INHERIT_NEW, child.getRevNode().getCreatedPath());
    }
}
