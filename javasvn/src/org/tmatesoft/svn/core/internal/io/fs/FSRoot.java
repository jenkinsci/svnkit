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

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;
import java.util.TreeMap;

import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.io.SVNLocationEntry;

/**
 * @version 1.0
 * @author  TMate Software Ltd.
 */
public abstract class FSRoot {

    private Map myCopyfromCache;
    private RevisionCache myRevNodesCache;
    private FSFS myFSFS;

    protected FSRevisionNode myRootRevisionNode;

    protected FSRoot(FSFS owner) {
        myFSFS = owner;
    }

    protected FSFS getOwner(){
        return myFSFS;
    }

    public Map getCopyfromCache() {
        if (myCopyfromCache == null) {
            myCopyfromCache = new HashMap();
        }
        return myCopyfromCache;
    }
    
    public FSRevisionNode getRevisionNode(String path) throws SVNException{
        /* Canonicalize the input PATH. */
        String canonPath = SVNPathUtil.canonicalizeAbsPath(path);
        /* look for the rev-node in our cache. */
        FSRevisionNode node = fetchRevNodeFromCache(canonPath);
        if(node == null){
            FSParentPath parentPath = openPath(path, true, false);
            node = parentPath.getRevNode();
        }
        return node;
    }

    public abstract FSRevisionNode getRootRevisionNode() throws SVNException;

    public abstract Map getChangedPaths() throws SVNException;

    public abstract FSCopyInheritance getCopyInheritance(FSParentPath child) throws SVNException;
    
    public FSParentPath openPath(String path, boolean lastEntryMustExist, boolean storeParents) throws SVNException {
        String canonPath = SVNPathUtil.canonicalizeAbsPath(path);
        FSRevisionNode here = getRootRevisionNode();
        String pathSoFar = "/";

        //Make a parentPath item for the root node, using its own current copy-id
        FSParentPath parentPath = new FSParentPath(here, null, null);
        parentPath.setCopyStyle(FSCopyInheritance.COPY_ID_INHERIT_SELF);
        String rest = canonPath.substring(1);// skip the leading '/'

        /* Whenever we are at the top of this loop:
         - HERE is our current directory,
         - REST is the path we're going to find in HERE, and 
         - PARENT_PATH includes HERE and all its parents.  */
        while (true) {
            String entry = SVNPathUtil.head(rest);
            String next = SVNPathUtil.removeHead(rest);
            pathSoFar = SVNPathUtil.concatToAbs(pathSoFar, entry);
            FSRevisionNode child = null;
            if (entry == null || "".equals(entry)) {
                child = here;
            } else {
                FSRevisionNode cachedRevNode = fetchRevNodeFromCache(pathSoFar);
                if (cachedRevNode != null) {
                    child = cachedRevNode;
                } else {
                    try {
                        child = here.getChildDirNode(entry, getOwner());
                    } catch (SVNException svne) {
                        if (svne.getErrorMessage().getErrorCode() == SVNErrorCode.FS_NOT_FOUND) {
                            /* If this was the last path component, and the caller
                             * said it was optional, then don't return an error;
                             * just put a null node pointer in the path.  
                             */
                            if (!lastEntryMustExist && (next == null || "".equals(next))) {
                                return new FSParentPath(null, entry, parentPath);
                            }
                            /* Build a better error message than FSReader.getChildDirNode()
                             * can provide, giving the root and full path name.  
                             */
                            SVNErrorManager.error(FSErrors.errorNotFound(this, path), svne);
                        }
                        throw svne;
                    }
                }
                
                parentPath.setParentPath(child, entry, storeParents ? new FSParentPath(parentPath) : null);
                FSCopyInheritance copyInheritance = getCopyInheritance(parentPath);
                if(copyInheritance != null){
                    parentPath.setCopyStyle(copyInheritance.getStyle());
                    parentPath.setCopySourcePath(copyInheritance.getCopySourcePath());
                }
                
                /* Cache the node we found (if it wasn't already cached). */
                if (cachedRevNode == null) {
                    putRevNodeToCache(pathSoFar, child);
                }
            }
            if (next == null || "".equals(next)) {
                break;
            }
            /* The path isn't finished yet; we'd better be in a directory.  */
            if (child.getType() != SVNNodeKind.DIR) {
                SVNErrorMessage err = FSErrors.errorNotDirectory(pathSoFar, getOwner().getRepositoryRoot());
                SVNErrorManager.error(err.wrap("Failure opening ''{0}''", path));
            }
            rest = next;
            here = child;
        }
        return parentPath;
    }

    public SVNNodeKind checkNodeKind(String path) throws SVNException {
        FSRevisionNode revNode = null;
        try {
            revNode = getRevisionNode(path);
        } catch (SVNException svne) {
            if (svne.getErrorMessage().getErrorCode() == SVNErrorCode.FS_NOT_FOUND) {
                return SVNNodeKind.NONE;
            }
            throw svne;
        }
        return revNode.getType();
    }

    public void putRevNodeToCache(String path, FSRevisionNode node) throws SVNException {
        if (!path.startsWith("/")) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNKNOWN, "Invalid path ''{0}''", path);
            SVNErrorManager.error(err);
        }
        if (myRevNodesCache == null) {
            myRevNodesCache = new RevisionCache(100);
        }
        myRevNodesCache.put(path, node);
    }

    public void removeRevNodeFromCache(String path) throws SVNException {
        if (!path.startsWith("/")) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNKNOWN, "Invalid path ''{0}''", path);
            SVNErrorManager.error(err);
        }
        if (myRevNodesCache == null) {
            return;
        }
        myRevNodesCache.delete(path);
    }

    public FSRevisionNode fetchRevNodeFromCache(String path) throws SVNException {
        if (myRevNodesCache == null) {
            return null;
        }
        if (!path.startsWith("/")) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNKNOWN, "Invalid path ''{0}''", path);
            SVNErrorManager.error(err);
        }
        return (FSRevisionNode) myRevNodesCache.fetch(path);
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

    protected Map fetchAllChanges(FSFile changesFile, boolean prefolded) throws SVNException {
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
    
    private static final class RevisionCache {
        private LinkedList myKeys;
        private Map myCache;
        private int mySizeLimit;
        
        public RevisionCache(int limit){
            mySizeLimit = limit;
            myKeys = new LinkedList();
            myCache = new TreeMap(); 
        }
        
        public void put(Object key, Object value){
            if(mySizeLimit <= 0){
                return;
            }
            if(myKeys.size() == mySizeLimit){
                Object cachedKey = myKeys.removeLast();
                myCache.remove(cachedKey);
            }
            myKeys.addFirst(key);
            myCache.put(key, value);
        }
        
        public void delete(Object key){
            myKeys.remove(key);
            myCache.remove(key);
        }
        
        public Object fetch(Object key){
            int ind = myKeys.indexOf(key);
            if(ind != -1){
                if(ind != 0){
                    Object cachedKey = myKeys.remove(ind);
                    myKeys.addFirst(cachedKey);
                } 
                return myCache.get(key);
            }
            return null;
        }
        
        public void clear(){
            myKeys.clear();
            myCache.clear();
        }
    }

}
