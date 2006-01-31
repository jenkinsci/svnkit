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

import java.io.OutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.util.Map;
import java.util.Collection;
import java.util.LinkedList;
import java.util.Iterator;
import java.util.Stack;
import java.io.File;
import java.util.Arrays;
import java.util.Date;

import org.tmatesoft.svn.core.SVNCommitInfo;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNRevisionProperty;
import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.io.ISVNEditor;
import org.tmatesoft.svn.core.io.diff.SVNDiffWindow;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.internal.util.SVNTimeUtil;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.io.SVNLocationEntry;
import org.tmatesoft.svn.core.io.diff.SVNDeltaProcessor;

/**
 * @version 1.0
 * @author  TMate Software Ltd.
 */
public class FSCommitEditor implements ISVNEditor {
    private Map myPathsToLockTokens;
    private Collection myLockTokens;
    private String myAuthor;
    private String myBasePath;
    private String myLogMessage;
    private FSTransactionInfo myTxn;
    private FSRoot myTxnRoot;
    private boolean isTxnOwner;
    private File myReposRootDir;
    private FSRevisionNodePool myRevNodesPool;
    private FSRepository myRepository;
    private Stack myDirsStack;
    private FSOutputStream myTargetStream;
    private SVNDeltaProcessor myDeltaProcessor;
    
    public FSCommitEditor(String path, String logMessage, String userName, Map lockTokens, boolean keepLocks, FSTransactionInfo txn, FSRepository repository){
        myPathsToLockTokens = !keepLocks ? lockTokens : null;  
        myLockTokens = lockTokens != null ? lockTokens.values() : new LinkedList();
        myAuthor = userName;
        myBasePath = path;
        myLogMessage = logMessage;
        myTxn = txn;
        isTxnOwner = txn == null ? true : false;
        myRepository = repository;
        myReposRootDir = myRepository.getReposRootDir();
        myRevNodesPool = myRepository.getRevisionNodePool();
        myDirsStack = new Stack();
    }
    
    public void targetRevision(long revision) throws SVNException {
        //does nothing
    }

    public void openRoot(long revision) throws SVNException {
        /* Ignore revision.  We always build our transaction against
         * HEAD. However, we will keep it in dir baton for out of dateness checks.  
         */
        long youngestRev = FSReader.getYoungestRevision(myReposRootDir);
        
        /* Unless we've been instructed to use a specific transaction, 
         * we'll make our own. 
         */
        if(isTxnOwner){
            myTxn = beginTransactionForCommit(youngestRev);
        }else{
            /* Even if we aren't the owner of the transaction, we might
             * have been instructed to set some properties. 
             */
            /* User (author). */
            if(myAuthor != null && !"".equals(myAuthor)){
                FSWriter.setTransactionProperty(myReposRootDir, myTxn.getTxnId(), SVNRevisionProperty.AUTHOR, myAuthor);
            }
            /* Log message. */
            if(myLogMessage != null && !"".equals(myLogMessage)){
                FSWriter.setTransactionProperty(myReposRootDir, myTxn.getTxnId(), SVNRevisionProperty.LOG, myLogMessage);
            }
        }
        Map txnProps = FSRepositoryUtil.getTransactionProperties(myReposRootDir, myTxn.getTxnId());
        int flags = 0;
        if(txnProps.get(SVNProperty.TXN_CHECK_OUT_OF_DATENESS) != null){
            flags |= FSConstants.SVN_FS_TXN_CHECK_OUT_OF_DATENESS;
        }
        if(txnProps.get(SVNProperty.TXN_CHECK_LOCKS) != null){
            flags |= FSConstants.SVN_FS_TXN_CHECK_LOCKS;
        }
        myTxnRoot = FSRoot.createTransactionRoot(myTxn.getTxnId(), flags);
        DirBaton dirBaton = new DirBaton(revision, myBasePath, false);  
        myDirsStack.push(dirBaton);
    }
    
    private FSTransactionInfo beginTransactionForCommit(long baseRevision) throws SVNException {
        /* Run start-commit hooks. */
        FSHooks.runStartCommitHook(myReposRootDir, myAuthor);
        /* Begin the transaction, ask for the fs to do on-the-fly lock checks. */
        FSTransactionInfo txn = FSWriter.beginTxn(baseRevision, FSConstants.SVN_FS_TXN_CHECK_LOCKS, myRevNodesPool, myReposRootDir);
        /* We pass the author and log message to the filesystem by adding
         * them as properties on the txn.  Later, when we commit the txn,
         * these properties will be copied into the newly created revision. 
         */
        /* User (author). */
        if(myAuthor != null && !"".equals(myAuthor)){
            FSWriter.setTransactionProperty(myReposRootDir, txn.getTxnId(), SVNRevisionProperty.AUTHOR, myAuthor);
        }
        /* Log message. */
        if(myLogMessage != null && !"".equals(myLogMessage)){
            FSWriter.setTransactionProperty(myReposRootDir, txn.getTxnId(), SVNRevisionProperty.LOG, myLogMessage);
        }
        return txn; 
    }

    public void openDir(String path, long revision) throws SVNException {
        DirBaton parentBaton = (DirBaton)myDirsStack.peek();
        String fullPath = SVNPathUtil.concatToAbs(myBasePath, path); 
        /* Check path in our transaction.  If it does not exist,
         * return a 'Path not present' error. 
         */
        SVNNodeKind kind = myRepository.checkNodeKind(fullPath, myTxnRoot);
        if(kind == SVNNodeKind.NONE){
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_NOT_DIRECTORY, "Path ''{0}'' not present", path);
            SVNErrorManager.error(err);
        }
        /* Build a new dir baton for this directory */
        DirBaton dirBaton = new DirBaton(revision, fullPath, parentBaton.isCopied());  
        myDirsStack.push(dirBaton);
    }
    
    public void deleteEntry(String path, long revision) throws SVNException {
        String fullPath = SVNPathUtil.concatToAbs(myBasePath, path); 
        /* Check path in our transaction.  */
        SVNNodeKind kind = myRepository.checkNodeKind(fullPath, myTxnRoot);
        /* If path doesn't exist in the txn, that's fine (merge
         * allows this). 
         */
        if(kind == SVNNodeKind.NONE){
            return;
        }
        /* Now, make sure we're deleting the node that we think we're
         * deleting, else return an out-of-dateness error. 
         */
        FSRevisionNode existingNode = myRevNodesPool.getRevisionNode(myTxnRoot, fullPath, myReposRootDir);
        long createdRev = existingNode.getId().getRevision();
        if(FSRepository.isValidRevision(revision) && revision < createdRev){
            SVNErrorManager.error(FSErrors.errorOutOfDate(fullPath, myTxnRoot.getTxnId()));
        }
        /* Delete files and recursively delete
         * directories.  
         */
        deleteNode(myTxnRoot, fullPath);
    }
    
    /* Delete the node at path under root.  root must be a transaction
     * root. 
     */
    private void deleteNode(FSRoot root, String path) throws SVNException {
        String txnId = root.getTxnId();
        if(!root.isTxnRoot()){
            SVNErrorManager.error(FSErrors.errorNotTxn());
        }
        FSParentPath parentPath = myRevNodesPool.getParentPath(root, path, true, myReposRootDir);
        /* We can't remove the root of the filesystem.  */
        if(parentPath.getParent() == null){
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_ROOT_DIR, "The root directory cannot be deleted");
            SVNErrorManager.error(err);
        }
        /* Check to see if path (or any child thereof) is locked; if so,
         * check that we can use the existing lock(s). 
         */
        if((root.getTxnFlags() & FSConstants.SVN_FS_TXN_CHECK_LOCKS) != 0){
            FSReader.allowLockedOperation(path, myAuthor, myLockTokens, true, false, myReposRootDir);            
        }
        /* Make the parent directory mutable, and do the deletion.  */
        makePathMutable(root, parentPath.getParent(), path);
        FSWriter.deleteEntry(parentPath.getParent().getRevNode(), parentPath.getNameEntry(), txnId, myReposRootDir);
        /* Remove this node and any children from the path cache. */
        root.removeRevNodeFromCache(parentPath.getAbsPath());
        /* Make a record of this modification in the changes table. */
        addChange(txnId, path, parentPath.getRevNode().getId(), FSPathChangeKind.FS_PATH_CHANGE_DELETE, false, false, FSConstants.SVN_INVALID_REVNUM, null);
    }
    
    public void absentDir(String path) throws SVNException {
        //does nothing
    }

    public void absentFile(String path) throws SVNException {
        //does nothing
    }

    public void addDir(String path, String copyFromPath, long copyFromRevision) throws SVNException {
        DirBaton parentBaton = (DirBaton)myDirsStack.peek();
        String fullPath = SVNPathUtil.concatToAbs(myBasePath, path); 
        boolean isCopied = false;
        /* Sanity check. */  
        if(copyFromPath != null && FSRepository.isInvalidRevision(copyFromRevision)){
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_GENERAL, "Got source path but no source revision for ''{0}''", fullPath);
            SVNErrorManager.error(err);
        }else if(copyFromPath != null){
            /* Check path in our transaction.  Make sure it does not exist
             * unless its parent directory was copied (in which case, the
             * thing might have been copied in as well), else return an
             * out-of-dateness error. 
             */
            SVNNodeKind kind = myRepository.checkNodeKind(fullPath, myTxnRoot);
            if(kind != SVNNodeKind.NONE && !parentBaton.isCopied()){
                SVNErrorManager.error(FSErrors.errorOutOfDate(fullPath, myTxnRoot.getTxnId()));
            }
            copyFromPath = myRepository.getRepositoryPath(copyFromPath);
            /* Now use the copyFromPath as an absolute path within the
             * repository to make the copy from. 
             */      
            FSRoot copyRoot = FSRoot.createRevisionRoot(copyFromRevision, myRevNodesPool.getRootRevisionNode(copyFromRevision, myReposRootDir));
            makeCopy(copyRoot, copyFromPath, myTxnRoot, fullPath, true);
            isCopied = true;
        }else{
            /* No ancestry given, just make a new directory.  We don't
             * bother with an out-of-dateness check here because
             * makeDir() will error out if path already exists.
             */
            makeDir(myTxnRoot, fullPath);
        }
        /* Build a new dir baton for this directory. */
        DirBaton dirBaton = new DirBaton(FSConstants.SVN_INVALID_REVNUM, fullPath, isCopied);  
        myDirsStack.push(dirBaton);
    }
    
    /* Create a new directory named "path" in "root".  The new directory has
     * no entries, and no properties.  root must be the root of a
     * transaction, not a revision.  
     */
    private void makeDir(FSRoot root, String path) throws SVNException {
        String txnId = root.getTxnId();
        FSParentPath parentPath = myRevNodesPool.getParentPath(root, path, false, myReposRootDir);
        /* If there's already a sub-directory by that name, complain.  This
         * also catches the case of trying to make a subdirectory named `/'.  
         */
        if(parentPath.getRevNode() != null){
            SVNErrorManager.error(FSErrors.errorAlreadyExists(root, path, myReposRootDir));
        }
        /* Check (recursively) to see if some lock is 'reserving' a path at
         * that location, or even some child-path; if so, check that we can
         * use it. 
         */
        if((root.getTxnFlags() & FSConstants.SVN_FS_TXN_CHECK_LOCKS) != 0){
            FSReader.allowLockedOperation(path, myAuthor, myLockTokens, true, false, myReposRootDir);            
        }
        /* Create the subdirectory.  */
        makePathMutable(root, parentPath.getParent(), path);
        FSRevisionNode subDirNode = makeEntry(parentPath.getParent().getRevNode(), parentPath.getParent().getAbsPath(), parentPath.getNameEntry(), true, txnId);
        /* Add this directory to the path cache. */
        root.putRevNodeToCache(parentPath.getAbsPath(), subDirNode);
        /* Make a record of this modification in the changes table. */
        addChange(txnId, path, subDirNode.getId(), FSPathChangeKind.FS_PATH_CHANGE_ADD, false, false, FSConstants.SVN_INVALID_REVNUM, null);
    }
    
    /* Make a new entry named entryName in parent. If isDir is true, then the
     * node revision the new entry points to will be a directory, else it
     * will be a file. parent must be mutable, and must not have an entry named 
     * entryName.  
     */
    private FSRevisionNode makeEntry(FSRevisionNode parent, String parentPath, String entryName, boolean isDir, String txnId) throws SVNException {
        /* Make sure that entryName is a single path component. */
        if(!SVNPathUtil.isSinglePathComponent(entryName)){
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_NOT_SINGLE_PATH_COMPONENT, "Attempted to create a node with an illegal name ''{0}''", entryName);
            SVNErrorManager.error(err);
        }
        /* Make sure that parent is a directory */
        if(parent.getType() != SVNNodeKind.DIR){
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_NOT_DIRECTORY, "Attempted to create entry in non-directory parent");
            SVNErrorManager.error(err);
        }
        /* Check that the parent is mutable. */
        if(!parent.getId().isTxn()){
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_NOT_MUTABLE, "Attempted to clone child of non-mutable node");
            SVNErrorManager.error(err);
        }
        /* Create the new node's node-revision */
        FSRevisionNode newRevNode = new FSRevisionNode();
        newRevNode.setType(isDir ? SVNNodeKind.DIR : SVNNodeKind.FILE);
        newRevNode.setCreatedPath(SVNPathUtil.concatToAbs(parentPath, entryName));
        newRevNode.setCopyRootPath(parent.getCopyRootPath());
        newRevNode.setCopyRootRevision(parent.getCopyRootRevision());
        newRevNode.setCopyFromRevision(FSConstants.SVN_INVALID_REVNUM);
        newRevNode.setCopyFromPath(null);
        FSID newNodeId = createNode(newRevNode, parent.getId().getCopyID(), txnId);
        /* Get a new node for our new node */
        FSRevisionNode childNode = FSReader.getRevNodeFromID(myReposRootDir, newNodeId);
        /* We can safely call setEntry() because we already know that
         * parent is mutable, and we just created childNode, so we know it has
         * no ancestors (therefore, parent cannot be an ancestor of child) 
         */
        FSWriter.setEntry(parent, entryName, childNode.getId(), newRevNode.getType(), txnId, myReposRootDir);
        return childNode;
    }

    private FSID createNode(FSRevisionNode revNode, String copyId, String txnId) throws SVNException {
        /* Get a new node-id for this node. */
        String nodeId = getNewTxnNodeId(txnId);
        FSID id = FSID.createTxnId(nodeId, copyId, txnId);
        revNode.setId(id);
        FSWriter.putTxnRevisionNode(id, revNode, myReposRootDir);
        return id;
    }

    /* Get a new and unique to this transaction node-id for transaction
     * txnId.
     */
    private String getNewTxnNodeId(String txnId) throws SVNException {
        /* First read in the current next-ids file. */
        String[] curIds = FSReader.readNextIds(txnId, myReposRootDir);
        String curNodeId = curIds[0];
        String curCopyId = curIds[1];
        String nextNodeId = FSKeyGenerator.generateNextKey(curNodeId.toCharArray());
        FSWriter.writeNextIds(txnId, nextNodeId, curCopyId, myReposRootDir);
        return "_" + nextNodeId; 
    }
    
    /* Create a copy of fromPath in fromRoot named toPath in toRoot.
     * If fromPath is a directory, copy it recursively. If preserveHistory is true, 
     * then the copy is recorded in the copies table.
     */
    private void makeCopy(FSRoot fromRoot, String fromPath, FSRoot toRoot, String toPath, boolean preserveHistory) throws SVNException {
        String txnId = toRoot.getTxnId();
        if(fromRoot.isTxnRoot()){
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNSUPPORTED_FEATURE, "Copy from mutable tree not currently supported");
            SVNErrorManager.error(err);
        }
        /* Get the node for fromPath in fromRoot. */
        FSRevisionNode fromNode = myRevNodesPool.getRevisionNode(fromRoot, fromPath, myReposRootDir);
        /* Build up the parent path from toPath in toRoot.  If the last
         * component does not exist, it's not that big a deal.  We'll just
         * make one there. 
         */
        FSParentPath toParentPath = myRevNodesPool.getParentPath(toRoot, toPath, false, myReposRootDir);
        /* Check to see if path (or any child thereof) is locked; if so,
         * check that we can use the existing lock(s). 
         */
        if((toRoot.getTxnFlags() & FSConstants.SVN_FS_TXN_CHECK_LOCKS) != 0){
            FSReader.allowLockedOperation(toPath, myAuthor, myLockTokens, true, false, myReposRootDir);
        }
        /* If the destination node already exists as the same node as the
         * source (in other words, this operation would result in nothing
         * happening at all), just do nothing an return successfully,
         * proud that you saved yourself from a tiresome task. 
         */
        if(toParentPath.getRevNode() != null && toParentPath.getRevNode().getId().equals(fromNode.getId())){
            return;
        }
        if(!fromRoot.isTxnRoot()){
            FSPathChangeKind changeKind;
            /* If toPath already existed prior to the copy, note that this
             * operation is a replacement, not an addition. 
             */
            if(toParentPath.getRevNode() != null){
                changeKind = FSPathChangeKind.FS_PATH_CHANGE_REPLACE;
            }else{
                changeKind = FSPathChangeKind.FS_PATH_CHANGE_ADD;
            }
            /* Make sure the target node's parents are mutable.  */
            makePathMutable(toRoot, toParentPath.getParent(), toPath);
            /* Canonicalize the copyfrom path. */
            String fromCanonPath = SVNPathUtil.canonicalizeAbsPath(fromPath);
            copy(toParentPath.getParent().getRevNode(), toParentPath.getNameEntry(), fromNode, preserveHistory, fromRoot.getRevision(), fromCanonPath, txnId);
            if(changeKind == FSPathChangeKind.FS_PATH_CHANGE_REPLACE){
                toRoot.removeRevNodeFromCache(toParentPath.getAbsPath());
            }
            /* Make a record of this modification in the changes table. */
            FSRevisionNode newNode = myRevNodesPool.getRevisionNode(toRoot, toPath, myReposRootDir);
            addChange(txnId, toPath, newNode.getId(), changeKind, false, false, fromRoot.getRevision(), fromCanonPath);
        }else{
            /* Copying from transaction roots not currently available.
               Note that when copying from mutable trees, you have to make sure that
               you aren't creating a cyclic graph filesystem, and a simple
               referencing operation won't cut it.  Currently, we should not
               be able to reach this clause, and the interface reports that
               this only works from immutable trees anyway, but this requirement 
               need not be necessary in the future.
            */
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNSUPPORTED_FEATURE, "Copying from transaction roots not currently available");
            SVNErrorManager.error(err);
        }
    }

    /* Populating the 'changes' table. Add a change to the changes table in FS, keyed on transaction id,
     * and indicated that a change of kind "changeKind" occurred on
     * path (whose node revision id is - or was, in the case of a
     * deletion, - "id"), and optionally that text or props modifications
     * occurred.  If the change resulted from a copy, copyFromRevision and
     * copyFromPath specify under which revision and path the node was
     * copied from.  If this was not part of a copy, copyFromrevision should
     * be FSConstants.SVN_INVALID_REVNUM. 
     */
    private void addChange(String txnId, String path, FSID id, FSPathChangeKind changeKind, boolean textModified, boolean propsModified, long copyFromRevision, String copyFromPath) throws SVNException {
        OutputStream changesFile = null;
        try{
            changesFile = SVNFileUtil.openFileForWriting(FSRepositoryUtil.getTxnChangesFile(txnId, myReposRootDir), true);
            SVNLocationEntry copyfromEntry = null;
            if(FSRepository.isValidRevision(copyFromRevision)){
                copyfromEntry = new SVNLocationEntry(copyFromRevision, copyFromPath);
            }
            FSPathChange pathChange = new FSPathChange(id, changeKind, textModified, propsModified);
            FSWriter.writeChangeEntry(changesFile, path, pathChange, copyfromEntry);
        }catch(IOException ioe){
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, ioe.getLocalizedMessage());
            SVNErrorManager.error(err, ioe);
        }finally{
            SVNFileUtil.closeFile(changesFile);
        }
    }
    
    private void copy(FSRevisionNode toNode, String entryName, FSRevisionNode fromNode, boolean preserveHistory, long fromRevision, String fromPath, String txnId) throws SVNException {
        FSID id = null;
        if(preserveHistory){
            FSID srcId = fromNode.getId();
            /* Make a copy of the original node revision. */
            FSRevisionNode toRevNode = FSRevisionNode.dumpRevisionNode(fromNode);
            /* Reserve a copy ID for this new copy. */
            String copyId = reserveCopyId(txnId);
            /* Create a successor with its predecessor pointing at the copy
             * source. 
             */
            toRevNode.setPredecessorId(new FSID(srcId));
            if(toRevNode.getCount() != -1){
                toRevNode.setCount(toRevNode.getCount() + 1);
            }
            toRevNode.setCreatedPath(SVNPathUtil.concatToAbs(toNode.getCreatedPath(), entryName));
            toRevNode.setCopyFromPath(fromPath);
            toRevNode.setCopyFromRevision(fromRevision);
            /* Set the copyroot equal to our own id. */
            toRevNode.setCopyRootPath(null);
            id = FSWriter.createSuccessor(srcId, toRevNode, copyId, txnId, myReposRootDir);
        }else{
            /* don't preserve history */
            id = fromNode.getId();
        }
        /* Set the entry in toNode to the new id. */
        FSWriter.setEntry(toNode, entryName, id, fromNode.getType(), txnId, myReposRootDir);
    }
    
    public void changeDirProperty(String name, String value) throws SVNException {
        DirBaton dirBaton = (DirBaton)myDirsStack.peek();
        if(FSRepository.isValidRevision(dirBaton.getBaseRevision())){
            /* Subversion rule:  propchanges can only happen on a directory
             * which is up-to-date. 
             */
            FSRevisionNode existingNode = myRevNodesPool.getRevisionNode(myTxnRoot, dirBaton.getPath(), myReposRootDir);
            long createdRev = existingNode.getId().getRevision();
            if(dirBaton.getBaseRevision() < createdRev){
                SVNErrorManager.error(FSErrors.errorOutOfDate(dirBaton.getPath(), myTxnRoot.getTxnId()));
            }
        }
        changeNodeProperty(myTxnRoot, dirBaton.getPath(), name, value);
    }

    /* Change, add, or delete a node's property value.  The node affect is
     * path under root, the property value to modify is propName, and propValue
     * points to either a string value to set the new contents to, or null
     * if the property should be deleted. 
     */
    private void changeNodeProperty(FSRoot root, String path, String propName, String propValue) throws SVNException {
        /* Validate the property. */
        if(!SVNProperty.isRegularProperty(propName)){
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.REPOS_BAD_ARGS, "Storage of non-regular property ''{0}'' is disallowed through the repository interface, and could indicate a bug in your client", propName);
            SVNErrorManager.error(err);
        }
        if(!root.isTxnRoot()){
            SVNErrorManager.error(FSErrors.errorNotTxn());
        }
        String txnId = root.getTxnId();
        FSParentPath parentPath = myRevNodesPool.getParentPath(root, path, true, myReposRootDir);
        /* Check (non-recursively) to see if path is locked; if so, check
         * that we can use it. 
         */
        if((root.getTxnFlags() & FSConstants.SVN_FS_TXN_CHECK_LOCKS) != 0){
            FSReader.allowLockedOperation(path, myAuthor, myLockTokens, false, false, myReposRootDir);            
        }
        makePathMutable(root, parentPath, path);
        Map properties = FSReader.getProperties(parentPath.getRevNode(), myReposRootDir);
        /* If there's no proplist, but we're just deleting a property, exit now. */
        if(properties.isEmpty() && propValue == null){
            return;
        }
        /* Set the property. */
        if(propValue == null){
            properties.remove(propName);
        }else{
            properties.put(propName, propValue);
        }
        /* Overwrite the node's proplist. */
        FSWriter.setProplist(parentPath.getRevNode(), properties, myReposRootDir);
        /* Make a record of this modification in the changes table. */
        addChange(txnId, path, parentPath.getRevNode().getId(), FSPathChangeKind.FS_PATH_CHANGE_MODIFY, false, true, FSConstants.SVN_INVALID_REVNUM, null);
    }
    
    public void closeDir() throws SVNException {
        myDirsStack.pop();
    }

    public void addFile(String path, String copyFromPath, long copyFromRevision) throws SVNException {
        DirBaton parentBaton = (DirBaton)myDirsStack.peek();
        String fullPath = SVNPathUtil.concatToAbs(myBasePath, path); 
        /* Sanity check. */  
        if(copyFromPath != null && FSRepository.isInvalidRevision(copyFromRevision)){
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_GENERAL, "Got source path but no source revision for ''{0}''", fullPath);
            SVNErrorManager.error(err);
        }else if(copyFromPath != null){
            /* Check path in our transaction.  Make sure it does not exist
             * unless its parent directory was copied (in which case, the
             * thing might have been copied in as well), else return an
             * out-of-dateness error. 
             */
            SVNNodeKind kind = myRepository.checkNodeKind(fullPath, myTxnRoot);
            if(kind != SVNNodeKind.NONE && !parentBaton.isCopied()){
                SVNErrorManager.error(FSErrors.errorOutOfDate(fullPath, myTxnRoot.getTxnId()));
            }
            copyFromPath = myRepository.getRepositoryPath(copyFromPath);
            /* Now use the copyFromPath as an absolute path within the
             * repository to make the copy from. 
             */      
            FSRoot copyRoot = FSRoot.createRevisionRoot(copyFromRevision, myRevNodesPool.getRootRevisionNode(copyFromRevision, myReposRootDir));
            makeCopy(copyRoot, copyFromPath, myTxnRoot, fullPath, true);
        }else{
            /* No ancestry given, just make a new, empty file.  Note that we
             * don't perform an existence check here like the copy-from case
             * does -- that's because makeFile() already errors out
             * if the file already exists.
             */
            makeFile(myTxnRoot, fullPath);
        }
    }

    /* Create an empty file path under the root.
     */
    private void makeFile(FSRoot root, String path) throws SVNException {
        String txnId = root.getTxnId();
        FSParentPath parentPath = myRevNodesPool.getParentPath(root, path, false, myReposRootDir);
        /* If there's already a file by that name, complain.
         * This also catches the case of trying to make a file named `/'.  
         */
        if(parentPath.getRevNode() != null){
            SVNErrorManager.error(FSErrors.errorAlreadyExists(root, path, myReposRootDir));
        }
        /* Check (non-recursively) to see if path is locked;  if so, check
         * that we can use it. 
         */
        if((root.getTxnFlags() & FSConstants.SVN_FS_TXN_CHECK_LOCKS) != 0){
            FSReader.allowLockedOperation(path, myAuthor, myLockTokens, false, false, myReposRootDir);            
        }
        /* Create the file.  */
        makePathMutable(root, parentPath.getParent(), path);
        FSRevisionNode childNode = makeEntry(parentPath.getParent().getRevNode(), parentPath.getParent().getAbsPath(), parentPath.getNameEntry(), false, txnId);
        /* Add this file to the path cache. */
        root.putRevNodeToCache(parentPath.getAbsPath(), childNode);
        /* Make a record of this modification in the changes table. */
        addChange(txnId, path, childNode.getId(), FSPathChangeKind.FS_PATH_CHANGE_ADD, false, false, FSConstants.SVN_INVALID_REVNUM, null);
    }

    public void openFile(String path, long revision) throws SVNException {
        String fullPath = SVNPathUtil.concatToAbs(myBasePath, path); 
        /* Get this node's node-rev (doubles as an existence check). */
        FSRevisionNode revNode = myRevNodesPool.getRevisionNode(myTxnRoot, fullPath, myReposRootDir);
        /* If the node our caller has is an older revision number than the
         * one in our transaction, return an out-of-dateness error. 
         */
        if(FSRepository.isValidRevision(revision) && revision < revNode.getId().getRevision()){
            SVNErrorManager.error(FSErrors.errorOutOfDate(fullPath, myTxnRoot.getTxnId()));
        }
    }

    public void applyTextDelta(String path, String baseChecksum) throws SVNException {
        /* Call getParentPath() with the flag entryMustExist set to true, as we 
         * want this to return an error if the node for which we are searching 
         * doesn't exist. 
         */
        String txnId = myTxnRoot.getTxnId();
        String fullPath = SVNPathUtil.concatToAbs(myBasePath, path);
        FSParentPath parentPath = myRevNodesPool.getParentPath(myTxnRoot, fullPath, true, myReposRootDir);
        /* Check (non-recursively) to see if path is locked; if so, check
         * that we can use it. 
         */
        if((myTxnRoot.getTxnFlags() & FSConstants.SVN_FS_TXN_CHECK_LOCKS) != 0){
            FSReader.allowLockedOperation(fullPath, myAuthor, myLockTokens, false, false, myReposRootDir);
        }
        /* Now, make sure this path is mutable. */
        makePathMutable(myTxnRoot, parentPath, fullPath);
        FSRevisionNode node = parentPath.getRevNode();
        if(baseChecksum != null){
            /* Until we finalize the node, its textRepresentation points to the old
             * contents, in other words, the base text. 
             */
            String md5HexChecksum = FSRepositoryUtil.getFileChecksum(node);
            if(md5HexChecksum != null && !md5HexChecksum.equals(baseChecksum)){
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CHECKSUM_MISMATCH, "Base checksum mismatch on ''{0}'':\n   expected:  {1}\n     actual:  {2}\n", new Object[]{path, baseChecksum, md5HexChecksum});
                SVNErrorManager.error(err);
            }
        }
        /* Make a readable "source" stream out of the current contents of
         * root/path.
         */
        InputStream sourceStream = null;
        OutputStream targetStream = null;
        try{
            sourceStream = FSInputStream.createDeltaStream(node, myReposRootDir);
            targetStream = FSOutputStream.createStream(node, txnId, myReposRootDir);
            myDeltaProcessor = new SVNDeltaProcessor();
            myDeltaProcessor.applyTextDelta(sourceStream, targetStream, false);
        }catch(SVNException svne){
            SVNFileUtil.closeFile(sourceStream);
            throw svne;
        }finally{
            myTargetStream = (FSOutputStream)targetStream;
        }
        /* Make a record of this modification in the changes table. */
        addChange(txnId, fullPath, node.getId(), FSPathChangeKind.FS_PATH_CHANGE_MODIFY, true, false, FSConstants.SVN_INVALID_REVNUM, null);
    }
    
    /* Make the node referred to by parentPath mutable, if it isn't
     * already.  root must be the root from which
     * parentPath descends.  Clone any parent directories as needed.
     * Adjust the dag nodes in parentPath to refer to the clones.  Use
     * errorPath in error messages.  */
    private void makePathMutable(FSRoot root, FSParentPath parentPath, String errorPath) throws SVNException {
        String txnId = root.getTxnId();
        /* Is the node mutable already?  */
        if(parentPath.getRevNode().getId().isTxn()){
            return;
        }
        FSRevisionNode clone = null;
        /* Are we trying to clone the root, or somebody's child node?  */
        if(parentPath.getParent() != null){
            /* We're trying to clone somebody's child.  Make sure our parent
             * is mutable.  
             */
            makePathMutable(root, parentPath.getParent(), errorPath);
            FSID parentId = null;
            String copyId = null;
            switch(parentPath.getCopyStyle()){
                case FSParentPath.COPY_ID_INHERIT_PARENT:
                    parentId = parentPath.getParent().getRevNode().getId();
                    copyId = parentId.getCopyID();
                    break;
                case FSParentPath.COPY_ID_INHERIT_NEW:
                    copyId = reserveCopyId(txnId);
                    break;
                case FSParentPath.COPY_ID_INHERIT_SELF:
                    break;
                case FSParentPath.COPY_ID_INHERIT_UNKNOWN:
                    /* uh-oh -- somebody didn't calculate copy-ID
                     * inheritance data. 
                     */                    
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNKNOWN, "FATAL error: can not make path ''{0}'' mutable", errorPath);
                    SVNErrorManager.error(err);
            }
            /* Determine what copyroot our new child node should use. */
            String copyRootPath = parentPath.getRevNode().getCopyRootPath();
            long copyRootRevision = parentPath.getRevNode().getCopyRootRevision();
            FSRevisionNode copyRootNode = myRevNodesPool.getRevisionNode(copyRootRevision, copyRootPath, myReposRootDir);
            FSID childId = parentPath.getRevNode().getId();
            FSID copyRootId = copyRootNode.getId();
            boolean isParentCopyRoot = false;
            if(!childId.getNodeID().equals(copyRootId.getNodeID())){
                isParentCopyRoot = true;
            }
            /* Now make this node mutable.  */
            String clonePath = parentPath.getParent().getAbsPath();
            clone = FSWriter.cloneChild(parentPath.getParent().getRevNode(), clonePath, parentPath.getNameEntry(), copyId, txnId, isParentCopyRoot, myReposRootDir);
            /* Update the path cache. */
            root.putRevNodeToCache(parentPath.getAbsPath(), clone);
        }else{
            /* We're trying to clone the root directory.  */
            if(root.isTxnRoot()){
                /* Get the node id's of the root directories of the transaction 
                 * and its base revision.  
                 */
                FSTransaction txn = FSReader.getTxn(txnId, myReposRootDir);
                /* If they're the same, we haven't cloned the transaction's 
                 * root directory yet. 
                 */
                if(txn.getRootId().equals(txn.getBaseId())){
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNKNOWN, "FATAL error: txn ''{0}'' root id ''{1}'' matches base id ''{2}''", new Object[]{txnId, txn.getRootId(), txn.getBaseId()});
                    SVNErrorManager.error(err);
                }
                /* One way or another, root_id now identifies a cloned root node. */
                clone = FSReader.getRevNodeFromID(myReposRootDir, txn.getRootId());
            }else{
                /* If it's not a transaction root, we can't change its contents.  */
                SVNErrorManager.error(FSErrors.errorNotMutable(root.getRevision(), errorPath, myReposRootDir));
            }
        }
        /* Update the parentPath link to refer to the clone.  */
        parentPath.setRevNode(clone);
    }
    
    private String reserveCopyId(String txnId) throws SVNException {
        /* First read in the current next-ids file. */
        String[] nextIds = FSReader.readNextIds(txnId, myReposRootDir);
        String copyId = FSKeyGenerator.generateNextKey(nextIds[1].toCharArray());
        FSWriter.writeNextIds(txnId, nextIds[0], copyId, myReposRootDir);
        return "_" + nextIds[1];
    }
    
    public OutputStream textDeltaChunk(String path, SVNDiffWindow diffWindow) throws SVNException {
        return myDeltaProcessor.textDeltaChunk(diffWindow);
    }

    public void textDeltaEnd(String path) throws SVNException {
        myDeltaProcessor.textDeltaEnd();
    }

    public void changeFileProperty(String path, String name, String value) throws SVNException {
        String fullPath = SVNPathUtil.concatToAbs(myBasePath, path);
        changeNodeProperty(myTxnRoot, fullPath, name, value);
    }

    public void closeFile(String path, String textChecksum) throws SVNException {
        if(textChecksum != null){
            String fullPath = SVNPathUtil.concatToAbs(myBasePath, path);
            FSRevisionNode revNode = myRevNodesPool.getRevisionNode(myTxnRoot, fullPath, myReposRootDir);
            if(revNode.getTextRepresentation() != null && !textChecksum.equals(revNode.getTextRepresentation().getHexDigest())){
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CHECKSUM_MISMATCH, "Checksum mismatch for resulting fulltext\n({0}):\n   expected checksum:  {1}\n   actual checksum:    {2}\n", new Object[]{fullPath, textChecksum, revNode.getTextRepresentation().getHexDigest()});
                SVNErrorManager.error(err);
            }
        }
    }

    public SVNCommitInfo closeEdit() throws SVNException {
        /* If no transaction has been created (i.e. if openRoot() wasn't
         * called before closeEdit()), abort the operation here with an
         * error. 
         */
        if(myTxn == null){
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.REPOS_BAD_ARGS, "No valid transaction supplied to closeEdit()");
            SVNErrorManager.error(err);
        }
        /* Commit. */
        long committedRev = -1;
        try{
            committedRev = finalizeCommit(myTxn);
        }catch(SVNException svne){
            //ignore post-commit hook failure
            if(svne.getErrorMessage().getErrorCode() != SVNErrorCode.REPOS_POST_COMMIT_HOOK_FAILED){
                throw svne;
            }
        }
        /* Return new revision information to the caller. */
        String dateProp = FSRepositoryUtil.getRevisionProperty(myReposRootDir, committedRev, SVNRevisionProperty.DATE);
        String authorProp = FSRepositoryUtil.getRevisionProperty(myReposRootDir, committedRev, SVNRevisionProperty.AUTHOR);
        Date datestamp = dateProp != null ? SVNTimeUtil.parseDateString(dateProp) : null;
        SVNCommitInfo info = new SVNCommitInfo(committedRev, authorProp, datestamp);
        /* Unlock paths if it was specified */
        releaseLocks();
        myRepository.closeRepository();
        return info;
    }
    
    private void releaseLocks(){
        /* Maybe unlock the paths. */
        if(myPathsToLockTokens != null){
            for(Iterator paths = myPathsToLockTokens.keySet().iterator(); paths.hasNext();){
                String relPath = (String)paths.next();
                String absPath = SVNPathUtil.concatToAbs(myBasePath, relPath);
                String token = (String)myPathsToLockTokens.get(absPath);
                /* We may get errors here if the lock was broken or stolen
                 * after the commit succeeded.  This is fine and should be
                 * ignored. 
                 */
                try{
                    FSWriter.unlockPath(absPath, token, myAuthor, false, myReposRootDir);
                }catch(SVNException svne){
                    //ignore exceptions
                }
            }
        }
    }
    
    private long finalizeCommit(FSTransactionInfo txn) throws SVNException {
        /* Run pre-commit hooks. */
        FSHooks.runPreCommitHook(myReposRootDir, txn.getTxnId());
        /* Commit. */
        long newRevision = commitTxn(txn);
        /* Run post-commit hooks. */
        try{
            FSHooks.runPostCommitHook(myReposRootDir, newRevision);
        }catch(SVNException svne){
            //ignore post-commit hook failure
        }
        return newRevision;
    }
    
    private long commitTxn(FSTransactionInfo txn) throws SVNException {
       /* How do commits work in Subversion?
        *
        * When you're ready to commit, here's what you have:
        *
        *    1. A transaction, with a mutable tree hanging off it.
        *    2. A base revision, against which a txn tree was made.
        *    3. A latest revision, which may be newer than the base rev.
        *
        * The problem is that if latest != base, then one can't simply
        * attach the txn root as the root of the new revision, because that
        * would lose all the changes between base and latest.  It is also
        * not acceptable to insist that base == latest; in a busy
        * repository, commits happen too fast to insist that everyone keep
        * their entire tree up-to-date at all times.  Non-overlapping
        * changes should not interfere with each other.
        *
        * The solution is to merge the changes between base and latest into
        * the txn tree [see the method merge()].  The txn tree is the
        * only one of the three trees that is mutable, so it has to be the
        * one to adjust.
        *
        * You might have to adjust it more than once, if a new latest
        * revision gets committed while you were merging in the previous
        * one.  For example:
        *
        *    1. Jane starts txn T, based at revision 6.
        *    2. Someone commits (or already committed) revision 7.
        *    3. Jane's starts merging the changes between 6 and 7 into T.
        *    4. Meanwhile, someone commits revision 8.
        *    5. Jane finishes the 6-->7 merge.  T could now be committed
        *       against a latest revision of 7, if only that were still the
        *       latest.  Unfortunately, 8 is now the latest, so... 
        *    6. Jane starts merging the changes between 7 and 8 into T.
        *    7. Meanwhile, no one commits any new revisions.  Whew.
        *    8. Jane commits T, creating revision 9, whose tree is exactly
        *       T's tree, except immutable now.
        *
        * Lather, rinse, repeat.
        */
        /* Initialize output params. */
        long newRevision = FSConstants.SVN_INVALID_REVNUM;
        while(true){
            /* Get the *current* youngest revision, in one short-lived
             * transaction.  (We don't want the revisions table
             * locked while we do the main merge.)  We call it "youngish"
             * because new revisions might get committed after we've
             * obtained it. 
             */
            long youngishRev = FSReader.getYoungestRevision(myReposRootDir);
            FSRoot youngishRoot = FSRoot.createRevisionRoot(youngishRev, null);
            /* Get the node for the youngest revision, also in one
             * transaction.  Later we'll use it as the source
             * argument to a merge, and if the merge succeeds, this youngest
             * root node will become the new base root for the svn txn that
             * was the target of the merge (but note that the youngest rev
             * may have changed by then). 
             */
            FSRevisionNode youngishRootNode = myRevNodesPool.getRevisionNode(youngishRoot, "/", myReposRootDir);
            /* Try to merge.  If the merge succeeds, the base root node of
             * target's txn will become the same as youngishRootNode, so
             * any future merges will only be between that node and whatever
             * the root node of the youngest rev is by then. 
             */ 
            mergeChanges(null, youngishRootNode, txn);
            txn.setBaseRevision(youngishRev);
            /* Try to commit. */
            FSWriteLock writeLock = FSWriteLock.getWriteLock(myReposRootDir);
            synchronized(writeLock){//multi-threaded synchronization within the JVM 
                try{
                    writeLock.lock();//multi-processed synchronization
                    newRevision = commit(txn);
                }catch(SVNException svne){
                    if(svne.getErrorMessage().getErrorCode() == SVNErrorCode.FS_TXN_OUT_OF_DATE){
                        /* Did someone else finish committing a new revision while we
                         * were in mid-merge or mid-commit?  If so, we'll need to
                         * loop again to merge the new changes in, then try to
                         * commit again.  Or if that's not what happened, then just
                         * return the error. 
                         */
                        long youngestRev = FSReader.getYoungestRevision(myReposRootDir);
                        if(youngishRev == youngestRev){
                            throw svne;
                        }
                        continue;
                    }
                    throw svne;
                }finally{
                    writeLock.unlock();
                    FSWriteLock.realease(writeLock);//release the lock
                }
            }
            /* Set the return value */
            return newRevision;
        }
    }
    
    private long commit(FSTransactionInfo txn) throws SVNException {
        /* Get the current youngest revision. */
        long oldRev = FSReader.getYoungestRevision(myReposRootDir);
        /* Check to make sure this transaction is based off the most recent
         * revision. 
         */
        if(txn.getBaseRevision() != oldRev){
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_TXN_OUT_OF_DATE, "Transaction out of date");
            SVNErrorManager.error(err);
        }
        /* Locks may have been added (or stolen) between the calling of
         * previous methods and commitTxn(), so we need
         * to re-examine every changed-path in the txn and re-verify all
         * discovered locks. 
         */
        verifyLocks(txn.getTxnId());
        /* Get the next node-id and copy-id to use. */
        String[] ids = FSReader.getNextRevisionIds(myReposRootDir);
        String startNodeId = ids[0];
        String startCopyId = ids[1];
        /* We are going to be one better than this puny old revision. */
        long newRevision = oldRev + 1;
        RandomAccessFile protoFile = null;
        FSID newRootId = null;
        File revisionPrototypeFile = FSRepositoryUtil.getTxnRevFile(txn.getTxnId(), myReposRootDir);
        try{
            protoFile = SVNFileUtil.openRAFileForWriting(revisionPrototypeFile, true);
            /* Write out all the node-revisions and directory contents. */
            FSID rootId = FSID.createTxnId("0", "0", txn.getTxnId());
            newRootId = FSWriter.writeFinalRevision(newRootId, protoFile, newRevision, rootId, startNodeId, startCopyId, myReposRootDir);
            /* Write the changed-path information. */
            long changedPathOffset = FSWriter.writeFinalChangedPathInfo(protoFile, txn.getTxnId(), myReposRootDir);
            /* Write the final line. */
            String offsetsLine = "\n" + newRootId.getOffset() + " " + changedPathOffset + "\n";
            protoFile.write(offsetsLine.getBytes());
        }catch(IOException ioe){
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, ioe.getLocalizedMessage());
            SVNErrorManager.error(err, ioe);
        }finally{
            SVNFileUtil.closeFile(protoFile);
        }
        /* Remove any temporary txn props representing 'flags'. */
        Map txnProps = FSRepositoryUtil.getTransactionProperties(myReposRootDir, txn.getTxnId());
        if(txnProps != null && !txnProps.isEmpty()){
            if(txnProps.get(SVNProperty.TXN_CHECK_OUT_OF_DATENESS) != null){
                FSWriter.setTransactionProperty(myReposRootDir, txn.getTxnId(), SVNProperty.TXN_CHECK_OUT_OF_DATENESS, null);
            }
            if(txnProps.get(SVNProperty.TXN_CHECK_LOCKS) != null){
                FSWriter.setTransactionProperty(myReposRootDir, txn.getTxnId(), SVNProperty.TXN_CHECK_LOCKS, null);
            }
        }
        /* Move the finished rev file into place. */
        File dstRevFile = FSRepositoryUtil.getNewRevisionFile(myReposRootDir, newRevision); 
        SVNFileUtil.rename(revisionPrototypeFile, dstRevFile);
        /* Move the revprops file into place. */
        File txnPropsFile = FSRepositoryUtil.getTxnPropsFile(txn.getTxnId(), myReposRootDir);
        File dstRevPropsFile = FSRepositoryUtil.getNewRevisionPropertiesFile(myReposRootDir, newRevision);
        SVNFileUtil.rename(txnPropsFile, dstRevPropsFile);
        /* Update the 'current' file. */
        try{
            FSWriter.writeFinalCurrentFile(txn.getTxnId(), newRevision, startNodeId, startCopyId, myReposRootDir);
        }catch(IOException ioe){
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, ioe.getLocalizedMessage());
            SVNErrorManager.error(err, ioe);
        }
        /* Remove this transaction directory. */
        purgeTxn();
        return newRevision;
    }
    
    private void verifyLocks(String txnId) throws SVNException {
        /* Fetch the changes for this transaction. */
        Map changes = FSReader.fetchTxnChanges(null, txnId, null, myReposRootDir);
        /* Make an array of the changed paths, and sort them depth-first-ily.  */
        Object[] changedPaths = changes.keySet().toArray();
        Arrays.sort(changedPaths);
        /* Now, traverse the array of changed paths, verify locks. Note
         * that if we need to do a recursive verification a path, we'll skip
         * over children of that path when we get to them. 
         */
        String lastRecursedPath = null;
        for(int i = 0; i < changedPaths.length; i++){
            String changedPath = (String)changedPaths[i];
            boolean recurse = true;
            /* If this path has already been verified as part of a recursive
             * check of one of its parents, no need to do it again.  
             */
            if(lastRecursedPath != null && SVNPathUtil.pathIsChild(lastRecursedPath, changedPath) != null){
                continue;
            }
            /* Fetch the change associated with our path.  */
            FSPathChange change = (FSPathChange)changes.get(changedPath);
            /* What does it mean to succeed at lock verification for a given
             * path?  For an existing file or directory getting modified
             * (text, props), it means we hold the lock on the file or
             * directory.  For paths being added or removed, we need to hold
             * the locks for that path and any children of that path.
             */
            if(change.getChangeKind() == FSPathChangeKind.FS_PATH_CHANGE_MODIFY){
                recurse = false;
            }
            FSReader.allowLockedOperation(changedPath, myAuthor, myLockTokens, recurse, true, myReposRootDir);
            /* If we just did a recursive check, remember the path we
             * checked (so children can be skipped).  
             */
            if(recurse){
                lastRecursedPath = changedPath;
            }
        }
    }
    
    /* Merge changes between an ancestor and source node into
     * txn.  The ancestor is either ancestorNode, or if
     * that is null, txn's base node.

     * If the merge is successful, txn's base will become
     * sourceNode, and its root node will have a new ID, a
     * successor of sourceNode. 
     */
    private void mergeChanges(FSRevisionNode ancestorNode, FSRevisionNode sourceNode, FSTransactionInfo txn) throws SVNException {
        String txnId = txn.getTxnId();
        FSRevisionNode txnRootNode = FSReader.getTxnRootNode(txnId, myReposRootDir);
        if(ancestorNode == null){
            ancestorNode = FSReader.getTxnBaseRootNode(txnId, myReposRootDir);
        }
        if(txnRootNode.getId().equals(ancestorNode.getId())){
            /* If no changes have been made in txn since its current base,
             * then it can't conflict with any changes since that base.  So
             * we just set *both* its base and root to source, making txn
             * in effect a repeat of source. 
             * This would, of course, be a mighty silly thing
             * for the caller to do, and we might want to consider whether
             * this response is really appropriate. 
             */
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNKNOWN, "FATAL error: no changes in transaction to commit");
            SVNErrorManager.error(err);
        }else{
            merge("/", txnRootNode, sourceNode, ancestorNode, txnId);
        }
    }
    
    private void merge(String targetPath, FSRevisionNode target, FSRevisionNode source, FSRevisionNode ancestor, String txnId) throws SVNException {
        FSID sourceId = source.getId();
        FSID targetId = target.getId();
        FSID ancestorId = ancestor.getId();
        /* It's improper to call this routine with ancestor == target. */
        if(ancestorId.equals(targetId)){
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_GENERAL, "Bad merge; target ''{0}'' has id ''{1}'', same as ancestor", new Object[]{targetPath, targetId});
            SVNErrorManager.error(err);
        }
        /* Base cases:
         * Either no change made in source, or same change as made in target.
         * Both mean nothing to merge here.
         */
        if(ancestorId.equals(sourceId) || sourceId.equals(targetId)){
            return;
        }
        /* Else proceed, knowing all three are distinct node revisions.
        *
        * How to merge from this point: 
        *
        * if (not all 3 are directories)
        *   {
        *     early exit with conflict;
        *   }
        *
        * // Property changes may only be made to up-to-date
        * // directories, because once the client commits the prop
        * // change, it bumps the directory's revision, and therefore
        * // must be able to depend on there being no other changes to
        * // that directory in the repository.
        * if (target's property list differs from ancestor's)
        *    conflict;
        *
        * For each entry NAME in the directory ANCESTOR:
        *
        *   Let ANCESTOR-ENTRY, SOURCE-ENTRY, and TARGET-ENTRY be the IDs of
        *   the name within ANCESTOR, SOURCE, and TARGET respectively.
        *   (Possibly null if NAME does not exist in SOURCE or TARGET.)
        *
        *   If ANCESTOR-ENTRY == SOURCE-ENTRY, then:
        *     No changes were made to this entry while the transaction was in
        *     progress, so do nothing to the target.
        *
        *   Else if ANCESTOR-ENTRY == TARGET-ENTRY, then:
        *     A change was made to this entry while the transaction was in
        *     process, but the transaction did not touch this entry.  Replace
        *     TARGET-ENTRY with SOURCE-ENTRY.
        *
        *   Else:
        *     Changes were made to this entry both within the transaction and
        *     to the repository while the transaction was in progress.  They
        *     must be merged or declared to be in conflict.
        *
        *     If SOURCE-ENTRY and TARGET-ENTRY are both null, that's a
        *     double delete; flag a conflict.
        *
        *     If any of the three entries is of type file, declare a conflict.
        *
        *     If either SOURCE-ENTRY or TARGET-ENTRY is not a direct
        *     modification of ANCESTOR-ENTRY (determine by comparing the
        *     node-id fields), declare a conflict.  A replacement is
        *     incompatible with a modification or other replacement--even
        *     an identical replacement.
        *
        *     Direct modifications were made to the directory ANCESTOR-ENTRY
        *     in both SOURCE and TARGET.  Recursively merge these
        *     modifications.
        *
        * For each leftover entry NAME in the directory SOURCE:
        *
        *   If NAME exists in TARGET, declare a conflict.  Even if SOURCE and
        *   TARGET are adding exactly the same thing, two additions are not
        *   auto-mergeable with each other.
        *
        *   Add NAME to TARGET with the entry from SOURCE.
        *
        * Now that we are done merging the changes from SOURCE into the
        * directory TARGET, update TARGET's predecessor to be SOURCE.
        */
        if(source.getType() != SVNNodeKind.DIR || target.getType() != SVNNodeKind.DIR || ancestor.getType() != SVNNodeKind.DIR){
            SVNErrorManager.error(FSErrors.errorConflict(targetPath));
        }
        /* Possible early merge failure: if target and ancestor have
         * different property lists, then the merge should fail.
         * Propchanges can *only* be committed on an up-to-date directory.
         */
        if(!FSRepresentation.compareRepresentations(target.getPropsRepresentation(), ancestor.getPropsRepresentation())){
            SVNErrorManager.error(FSErrors.errorConflict(targetPath));
        }
        Map sourceEntries = FSReader.getDirEntries(source, myReposRootDir);
        Map targetEntries = FSReader.getDirEntries(target, myReposRootDir);
        Map ancestorEntries = FSReader.getDirEntries(ancestor, myReposRootDir);
        /* for each entry in ancestorEntries... */
        for(Iterator ancestorEntryNames = ancestorEntries.keySet().iterator(); ancestorEntryNames.hasNext();){
            String ancestorEntryName = (String)ancestorEntryNames.next();
            FSEntry ancestorEntry = (FSEntry)ancestorEntries.get(ancestorEntryName);
            FSEntry sourceEntry = (FSEntry)sourceEntries.get(ancestorEntryName);
            FSEntry targetEntry = (FSEntry)targetEntries.get(ancestorEntryName);
            if(sourceEntry != null && ancestorEntry.getId().equals(sourceEntry.getId())){
                /* No changes were made to this entry while the transaction was
                 * in progress, so do nothing to the target. 
                 */
            }else if(targetEntry != null && ancestorEntry.getId().equals(targetEntry.getId())){
                /* A change was made to this entry while the transaction was in
                 * process, but the transaction did not touch this entry. 
                 */
                if(sourceEntry != null){
                    FSWriter.setEntry(target, ancestorEntryName, sourceEntry.getId(), sourceEntry.getType(), txnId, myReposRootDir);
                }else{
                    FSWriter.deleteEntry(target, ancestorEntryName, txnId, myReposRootDir);
                }
            }else{
                /* Changes were made to this entry both within the transaction
                 * and to the repository while the transaction was in progress.
                 * They must be merged or declared to be in conflict. 
                 */
                /* If SOURCE-ENTRY and TARGET-ENTRY are both null, that's a
                 * double delete; flag a conflict. 
                 */
                if(sourceEntry == null || targetEntry == null){
                    SVNErrorManager.error(FSErrors.errorConflict(SVNPathUtil.concatToAbs(targetPath, ancestorEntryName)));
                }
                /* If any of the three entries is of type file, flag a conflict. */
                if(sourceEntry.getType() == SVNNodeKind.FILE || targetEntry.getType() == SVNNodeKind.FILE || ancestorEntry.getType() == SVNNodeKind.FILE){
                    SVNErrorManager.error(FSErrors.errorConflict(SVNPathUtil.concatToAbs(targetPath, ancestorEntryName)));
                }
                /* If either SOURCE-ENTRY or TARGET-ENTRY is not a direct
                 * modification of ANCESTOR-ENTRY, declare a conflict. 
                 */
                if(!sourceEntry.getId().getNodeID().equals(ancestorEntry.getId().getNodeID()) ||
                   !sourceEntry.getId().getCopyID().equals(ancestorEntry.getId().getCopyID()) ||
                   !targetEntry.getId().getNodeID().equals(ancestorEntry.getId().getNodeID()) ||
                   !targetEntry.getId().getCopyID().equals(ancestorEntry.getId().getCopyID())){
                    SVNErrorManager.error(FSErrors.errorConflict(SVNPathUtil.concatToAbs(targetPath, ancestorEntryName)));
                }
                /* Direct modifications were made to the directory
                 * ANCESTOR-ENTRY in both SOURCE and TARGET.  Recursively
                 * merge these modifications. 
                 */
                FSRevisionNode sourceEntryNode = FSReader.getRevNodeFromID(myReposRootDir, sourceEntry.getId());
                FSRevisionNode targetEntryNode = FSReader.getRevNodeFromID(myReposRootDir, targetEntry.getId());
                FSRevisionNode ancestorEntryNode = FSReader.getRevNodeFromID(myReposRootDir, ancestorEntry.getId());
                String childTargetPath = SVNPathUtil.concatToAbs(targetPath, targetEntry.getName());
                merge(childTargetPath, targetEntryNode, sourceEntryNode, ancestorEntryNode, txnId);
            }
            /* We've taken care of any possible implications entry could have.
             * Remove it from sourceEntries, so it's easy later to loop
             * over all the source entries that didn't exist in
             * ancestorEntries. 
             */
            sourceEntries.remove(ancestorEntryName);
        }
        /* For each entry in source but not in ancestor */
        for(Iterator sourceEntryNames = sourceEntries.keySet().iterator(); sourceEntryNames.hasNext();){
            String sourceEntryName = (String)sourceEntryNames.next();
            FSEntry sourceEntry = (FSEntry)sourceEntries.get(sourceEntryName);
            FSEntry targetEntry = (FSEntry)targetEntries.get(sourceEntryName);
            /* If NAME exists in TARGET, declare a conflict. */
            if(targetEntry != null){
                SVNErrorManager.error(FSErrors.errorConflict(SVNPathUtil.concatToAbs(targetPath, targetEntry.getName())));
            }
            FSWriter.setEntry(target, sourceEntry.getName(), sourceEntry.getId(), sourceEntry.getType(), txnId, myReposRootDir);
        }
        long sourceCount = source.getCount();
        updateAncestry(sourceId, targetId, targetPath, sourceCount);
    }
    
    private void updateAncestry(FSID sourceId, FSID targetId, String targetPath, long sourcePredecessorCount) throws SVNException {
        if(!targetId.isTxn()){
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_NOT_MUTABLE, "Unexpected immutable node at ''{0}''", targetPath);
            SVNErrorManager.error(err);
        }
        FSRevisionNode revNode = FSReader.getRevNodeFromID(myReposRootDir, targetId);
        revNode.setPredecessorId(sourceId);
        revNode.setCount(sourcePredecessorCount != -1 ? sourcePredecessorCount + 1 : sourcePredecessorCount);
        FSWriter.putTxnRevisionNode(targetId, revNode, myReposRootDir);
    }
    
    public void abortEdit() throws SVNException {
        if(myTargetStream != null){
            myTargetStream.closeStreams();
        }
        if(myTxn == null || !isTxnOwner){
            myRepository.closeRepository();
            return;
        }
        purgeTxn();
        myRepository.closeRepository();
        File txnDir = FSRepositoryUtil.getTxnDir(myTxn.getTxnId(), myReposRootDir);
        if(txnDir.exists()){
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNKNOWN, "Transaction cleanup failed");
            SVNErrorManager.error(err);
        }
        myTxn = null;
        myTxnRoot = null;
    }
    
    private void purgeTxn() {
        /* Now, purge the transaction: remove the directory 
         * associated with this transaction. 
         */
        SVNFileUtil.deleteAll(FSRepositoryUtil.getTxnDir(myTxn.getTxnId(), myReposRootDir), true);
    }
    
    private class DirBaton {
        /* the revision I'm based on  */
        private long myBaseRevision;
        /* the -absolute- path to this dir in the fs */
        private String myPath;
        /* was this directory added with history? */
        private boolean isCopied;
        
        public DirBaton(long revision, String path, boolean copied) {
            myBaseRevision = revision;
            myPath = path;
            isCopied = copied;
        }
        
        public boolean isCopied() {
            return isCopied;
        }
        
        public void setCopied(boolean isCopied) {
            this.isCopied = isCopied;
        }
        
        public long getBaseRevision() {
            return myBaseRevision;
        }
        
        public void setBaseRevision(long baseRevision) {
            myBaseRevision = baseRevision;
        }
        
        public String getPath() {
            return myPath;
        }
        
        public void setPath(String path) {
            myPath = path;
        }
    }
}
