/*
 * ====================================================================
 * Copyright (c) 2004-2006 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html.
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.core.internal.wc;

import java.io.OutputStream;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;

import org.tmatesoft.svn.core.ISVNDirEntryHandler;
import org.tmatesoft.svn.core.SVNCommitInfo;
import org.tmatesoft.svn.core.SVNDirEntry;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.internal.io.fs.FSFS;
import org.tmatesoft.svn.core.internal.io.fs.FSRoot;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.io.ISVNEditor;
import org.tmatesoft.svn.core.io.SVNLocationEntry;
import org.tmatesoft.svn.core.io.diff.SVNDiffWindow;
import org.tmatesoft.svn.core.wc.ISVNEventHandler;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.admin.ISVNChangeEntryHandler;
import org.tmatesoft.svn.core.wc.admin.SVNChangeEntry;

/**
 * 
 * @version 1.1
 * @author TMate Software Ltd.
 */
public class SVNNodeEditor implements ISVNEditor {

    private Node myCurrentNode;
    private Node myRootNode;
    private FSRoot myBaseRoot;
    private FSFS myFSFS;
    private Map myFiles;
    private ISVNEventHandler myCancelHandler;
    
    public SVNNodeEditor(FSFS fsfs, FSRoot baseRoot, ISVNEventHandler handler) {
        myBaseRoot = baseRoot;
        myFSFS = fsfs;
        myCancelHandler = handler;
        myFiles = new HashMap();
    }

    public void abortEdit() throws SVNException {
    }

    public void absentDir(String path) throws SVNException {
    }

    public void absentFile(String path) throws SVNException {
    }

    public void addDir(String path, String copyFromPath, long copyFromRevision) throws SVNException {
        myCurrentNode = addOrOpen(path, SVNChangeEntry.TYPE_ADDED, SVNNodeKind.DIR, myCurrentNode, copyFromPath, copyFromRevision);
    }

    public void addFile(String path, String copyFromPath, long copyFromRevision) throws SVNException {
        Node node = addOrOpen(path, SVNChangeEntry.TYPE_ADDED, SVNNodeKind.FILE, myCurrentNode, copyFromPath, copyFromRevision);
        myFiles.put(path, node);
    }

    public void changeDirProperty(String name, String value) throws SVNException {
        myCurrentNode.myHasPropModifications = true;
    }

    public void changeFileProperty(String path, String name, String value) throws SVNException {
        Node fileNode = (Node) myFiles.get(path);
        fileNode.myHasPropModifications = true;
    }

    public void closeDir() throws SVNException {
        myCurrentNode = myCurrentNode.myParent;
    }

    public SVNCommitInfo closeEdit() throws SVNException {
        return null;
    }

    public void closeFile(String path, String textChecksum) throws SVNException {
        myFiles.remove(path);
    }

    public void deleteEntry(String path, long revision) throws SVNException {
        String name = SVNPathUtil.tail(path);
        Node node = null;

        if (myCurrentNode != null && myCurrentNode.myChildren != null) {
            for (Iterator children = myCurrentNode.myChildren.iterator(); children.hasNext();) {
                Node child = (Node) children;
                if (child.myName.equals(name)) {
                    node = child;
                    break;
                }
            }
        }

        if (node == null) {
            if (myCurrentNode != null) {
                node = new Node();
                node.myParent = myCurrentNode;

                if (myCurrentNode.myChildren == null) {
                    myCurrentNode.myChildren = new LinkedList();
                }
                myCurrentNode.myChildren.add(node);
            }
        }

        node.myAction = SVNChangeEntry.TYPE_DELETED;
        SVNLocationEntry baseLocation = findRealBaseLocation(node);
        FSRoot baseRoot = null;
        if (!SVNRevision.isValidRevisionNumber(baseLocation.getRevision())) {
            baseRoot = myBaseRoot;
        } else {
            baseRoot = myFSFS.createRevisionRoot(baseLocation.getRevision()); 
        }
        
        SVNNodeKind kind = baseRoot.checkNodeKind(baseLocation.getPath());
        if (kind == SVNNodeKind.NONE) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_NOT_FOUND, "''{0}'' not found in filesystem", path);
            SVNErrorManager.error(err);
        }
        
        node.myKind = kind;
    }

    public void openDir(String path, long revision) throws SVNException {
        myCurrentNode = addOrOpen(path, SVNChangeEntry.TYPE_REPLACED, SVNNodeKind.DIR, myCurrentNode, null, -1);
    }

    public void openFile(String path, long revision) throws SVNException {
        Node node = addOrOpen(path, SVNChangeEntry.TYPE_REPLACED, SVNNodeKind.FILE, myCurrentNode, null, -1);
        myFiles.put(path, node);
    }

    public void openRoot(long revision) throws SVNException {
        myRootNode = myCurrentNode = new Node();
        myCurrentNode.myName = "";
        myCurrentNode.myParent = null;
        myCurrentNode.myKind = SVNNodeKind.DIR;
        myCurrentNode.myAction = SVNChangeEntry.TYPE_REPLACED;
    }

    public void targetRevision(long revision) throws SVNException {
    }

    public void applyTextDelta(String path, String baseChecksum) throws SVNException {
        Node fileNode = (Node) myFiles.get(path);
        fileNode.myHasTextModifications = true;
    }

    public OutputStream textDeltaChunk(String path, SVNDiffWindow diffWindow) throws SVNException {
        return null;
    }

    public void textDeltaEnd(String path) throws SVNException {
    }

    public void traverseTree(boolean includeCopyInfo, ISVNChangeEntryHandler handler) throws SVNException {
        if (myRootNode != null) {
            traverseChangedTreeImpl(myRootNode, "/", includeCopyInfo, handler);
        }
    }
    
    public void traverseChangedDirs(ISVNDirEntryHandler handler) throws SVNException {
        if (myRootNode != null) {
            traverseChangedDirsImpl(myRootNode, "/", handler);
        }
    }

    private void traverseChangedDirsImpl(Node node, String path, ISVNDirEntryHandler handler) throws SVNException {
        if (myCancelHandler != null) {
            myCancelHandler.checkCancelled();
        }
        
        if (node == null || node.myKind != SVNNodeKind.DIR) {
            return;
        }

        boolean proceed = node.myHasPropModifications;
        if (!proceed && node.myChildren != null) {
            for (Iterator children = node.myChildren.iterator(); children.hasNext() && !proceed;) {
                Node child = (Node) children.next();
                if (child.myKind == SVNNodeKind.FILE || child.myHasTextModifications || child.myAction == SVNChangeEntry.TYPE_ADDED || child.myAction == SVNChangeEntry.TYPE_DELETED) {
                    proceed = true;
                }
            }
        }
        
        if (proceed && handler != null) {
            SVNDirEntry entry = new SVNDirEntry(null, node.myName, SVNNodeKind.DIR, -1, false, -1, null, null);
            handler.handleDirEntry(entry);
        }
        
        if (node.myChildren == null || node.myChildren.size() == 0) {
            return;
        }
        
        for (Iterator children = node.myChildren.iterator(); children.hasNext();) {
            Node childNode = (Node) children.next();
            String fullPath = SVNPathUtil.concatToAbs(path, childNode.myName);
            traverseChangedDirsImpl(childNode, fullPath, handler);
        }
    }
    
    private void traverseChangedTreeImpl(Node node, String path, boolean includeCopyInfo, ISVNChangeEntryHandler handler) throws SVNException {
        if (myCancelHandler != null) {
            myCancelHandler.checkCancelled();
        }
        
        if (node == null) {
            return;
        }
        
        SVNChangeEntry changeEntry = null;
        if (node.myAction == SVNChangeEntry.TYPE_ADDED) {
            String copyFromPath = includeCopyInfo ? node.myCopyFromPath : null;
            long copyFromRevision = includeCopyInfo ? node.myCopyFromRevision : -1;
            changeEntry = new SVNChangeEntry(path, node.myAction, copyFromPath, copyFromRevision, false, false);
        } else if (node.myAction == SVNChangeEntry.TYPE_DELETED) {
            changeEntry = new SVNChangeEntry(path, node.myAction, null, -1, false, false);
        } else if (node.myAction == SVNChangeEntry.TYPE_REPLACED) {
            if (node.myHasPropModifications || node.myHasTextModifications) {
                changeEntry = new SVNChangeEntry(path, SVNChangeEntry.TYPE_UPDATED, null, -1, node.myHasTextModifications, node.myHasPropModifications);
            }
        }
        
        if (changeEntry != null && handler != null) {
            handler.handleEntry(changeEntry);
        }
        
        if (node.myChildren == null || node.myChildren.size() == 0) {
            return;
        }
        
        for (Iterator children = node.myChildren.iterator(); children.hasNext();) {
            Node childNode = (Node) children.next();
            String fullPath = SVNPathUtil.concatToAbs(path, childNode.myName);
            traverseChangedTreeImpl(childNode, fullPath, includeCopyInfo, handler);
        }
    }
    
    private SVNLocationEntry findRealBaseLocation(Node node) throws SVNException {
        if (node.myAction == SVNChangeEntry.TYPE_ADDED && node.myCopyFromPath != null && SVNRevision.isValidRevisionNumber(node.myCopyFromRevision)) {
            return new SVNLocationEntry(node.myCopyFromRevision, node.myCopyFromPath);
        }
        
        if (node.myParent != null) {
            SVNLocationEntry location = findRealBaseLocation(node.myParent);
            return new SVNLocationEntry(location.getRevision(), SVNPathUtil.concatToAbs(location.getPath(), node.myName));
        }

        return new SVNLocationEntry(-1, "/");
    }

    private Node addOrOpen(String path, char action, SVNNodeKind kind, Node parent, String copyFromPath, long copyFromRevision) {
        if (parent.myChildren == null) {
            parent.myChildren = new LinkedList();
        }

        Node node = new Node();
        node.myName = SVNPathUtil.tail(path);
        node.myAction = action;
        node.myKind = kind;
        node.myCopyFromPath = copyFromPath;
        node.myCopyFromRevision = copyFromRevision;
        node.myParent = parent;
        parent.myChildren.add(node);
        return node;
    }

    private class Node {

        SVNNodeKind myKind;
        char myAction;
        boolean myHasTextModifications;
        boolean myHasPropModifications;
        String myName;
        long myCopyFromRevision;
        String myCopyFromPath;
        Node myParent;
        LinkedList myChildren;
    }
}
