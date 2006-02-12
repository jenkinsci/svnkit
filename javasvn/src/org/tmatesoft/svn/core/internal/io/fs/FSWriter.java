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
import java.io.IOException;
import java.io.OutputStream;
import java.io.RandomAccessFile; 
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.HashMap;
import java.util.Date;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNLock;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.internal.wc.SVNProperties;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.internal.util.SVNTimeUtil;
import org.tmatesoft.svn.core.SVNRevisionProperty;
import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.io.SVNLocationEntry;

/**
 * @version 1.0
 * @author  TMate Software Ltd.
 */
public class FSWriter {

    public static void unlockPath(String path, String token, String username, boolean breakLock, File reposRootDir) throws SVNException {
        /* Setup an array of paths in anticipation of the ra layers handling
         * multiple locks in one request (1.3 most likely).  This is only
         * used by FSHooks.runPost[Unl/L]ockHook(). 
         */
        String[] paths = {path};
        if(!breakLock && username == null){
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_NO_USER, "Cannot unlock path ''{0}'', no authenticated username available", path);
            SVNErrorManager.error(err);
        }
        path = SVNPathUtil.canonicalizeAbsPath(path);
        /* Run pre-unlock hook.  This could throw error, preventing
         * unlock() from happening. 
         */
        FSHooks.runPreUnlockHook(reposRootDir, path, username);
        /* Unlock. */
        FSWriteLock writeLock = FSWriteLock.getWriteLock(reposRootDir);
        synchronized(writeLock){//multi-threaded synchronization within the JVM 
            try{
                writeLock.lock();//multi-processed synchronization
                unlock(path, token, username, breakLock, reposRootDir);
            }finally{
                writeLock.unlock();
                FSWriteLock.realease(writeLock);//release the lock
            }
        }
        /* Run post-unlock hook. */
        try{
            FSHooks.runPostUnlockHook(reposRootDir, paths, username);
        }catch(SVNException svne){
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.REPOS_POST_UNLOCK_HOOK_FAILED, "Unlock succeeded, but post-unlock hook failed");
            err.setChildErrorMessage(svne.getErrorMessage());
            SVNErrorManager.error(err, svne);
        }
    }
    
    public static SVNLock lockPath(String path, String token, String username, String comment, Date expirationDate, long currentRevision, boolean stealLock, FSRepository repository, File reposRootDir) throws SVNException {
        /* Setup an array of paths in anticipation of the ra layers handling
         * multiple locks in one request (1.3 most likely).  This is only
         * used by FSHooks.runPost[Unl/L]ockHook(). 
         */
        String[] paths = {path};
        if(username == null){
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_NO_USER, "Cannot lock path ''{0}'', no authenticated username available.", path);
            SVNErrorManager.error(err);
        }
        path = SVNPathUtil.canonicalizeAbsPath(path);
        /* Run pre-lock hook.  This could throw an error, preventing
         * lock() from happening. 
         */
        FSHooks.runPreLockHook(reposRootDir, path, username);
        /* Lock. */
        SVNLock lock = null;
        FSWriteLock writeLock = FSWriteLock.getWriteLock(reposRootDir);
        synchronized(writeLock){ 
            try{
                writeLock.lock();
                lock = lock(path, token, username, comment, expirationDate, currentRevision, stealLock, repository, reposRootDir);
            }finally{
                writeLock.unlock();
                FSWriteLock.realease(writeLock);
            }
        }
        /* Run post-lock hook. */
        try{
            FSHooks.runPostLockHook(reposRootDir, paths, username);
        }catch(SVNException svne){
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.REPOS_POST_LOCK_HOOK_FAILED, "Lock succeeded, but post-lock hook failed");
            err.setChildErrorMessage(svne.getErrorMessage());
            SVNErrorManager.error(err, svne);
        }
        return lock;
    }
    
    private static SVNLock lock(String path, String token, String username, String comment, Date expirationDate, long currentRevision, boolean stealLock, FSRepository repository, File reposRootDir) throws SVNException {
        /* Until we implement directory locks someday, we only allow locks
         * on files or non-existent paths. 
         */
        FSRevisionNodePool revNodesPool = repository.getRevisionNodePool();
        long youngestRev = FSReader.getYoungestRevision(reposRootDir);
        FSRevisionNode rootNode = revNodesPool.getRootRevisionNode(youngestRev, reposRootDir);
        SVNNodeKind kind = repository.checkNodeKind(path, FSRoot.createRevisionRoot(youngestRev, rootNode));
        if(kind == SVNNodeKind.DIR){
            SVNErrorManager.error(FSErrors.errorNotFile(path, reposRootDir));
        }else if(kind == SVNNodeKind.NONE){
            /* While locking implementation easily supports the locking of
             * nonexistent paths, we deliberately choose not to allow such madness. 
             */
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_NOT_FOUND, "Path ''{0}'' doesn't exist in HEAD revision", path);
            SVNErrorManager.error(err);
        }        
        /* We need to have a username. */
        if(username == null || "".equals(username)){
            SVNErrorManager.error(FSErrors.errorNoUser(reposRootDir));
        }
        /* Is the caller attempting to lock an out-of-date working file? */
        if(FSRepository.isValidRevision(currentRevision)){
            FSRevisionNode node = revNodesPool.getRevisionNode(rootNode, path, reposRootDir);
            long createdRev = node.getId().getRevision();
            /* SVN_INVALID_REVNUM means the path doesn't exist.  So
             * apparently somebody is trying to lock something in their
             * working copy, but somebody else has deleted the thing
             * from HEAD.  That counts as being 'out of date'. 
             */     
            //TODO: I don't understand this check for invalidness...  
            if(FSRepository.isInvalidRevision(createdRev)){
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_OUT_OF_DATE, "Path ''{0}'' doesn't exist in HEAD revision", path);
                SVNErrorManager.error(err);
            }
            if(currentRevision < createdRev){
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_OUT_OF_DATE, "Lock failed: newer version of ''{0}'' exists", path);
                SVNErrorManager.error(err);
            }
        }
        SVNLock existingLock = FSReader.getLockHelper(path, true, reposRootDir);
        if(existingLock != null){
            if(!stealLock){
                /* Sorry, the path is already locked. */
                SVNErrorManager.error(FSErrors.errorPathAlreadyLocked(existingLock.getPath(), existingLock.getOwner(), reposRootDir));
            }else{
                /* STEAL_LOCK was passed, so username is "stealing" the
                 * lock from existingLock's owner.  Destroy the existing lock. 
                 */
                FSWriter.deleteLock(existingLock, reposRootDir);
            }
        }
        /* Create our new lock */
        SVNLock lock = null;
        if(token == null){
            lock = new SVNLock(path, FSRepositoryUtil.generateLockToken(), username, comment, new Date(System.currentTimeMillis()), expirationDate);
        }else{
            lock = new SVNLock(path, token, username, comment, new Date(System.currentTimeMillis()), expirationDate);
        }
        setLock(lock, reposRootDir);
        return lock;
    }
    
    private static void unlock(String path, String token, String username, boolean breakLock, File reposRootDir) throws SVNException {
        /* This could return FS_NO_SUCH_LOCK or FS_LOCK_EXPIRED. */
        SVNLock lock = FSReader.getLock(path, true, reposRootDir);
        /* Unless breaking the lock, we do some checks. */
        if(!breakLock){
            /* Sanity check:  the incoming token should match lock.getID(). */
            if(token == null || !token.equals(lock.getID())){
                SVNErrorManager.error(FSErrors.errorNoSuchLock(lock.getPath(), reposRootDir));
            }
            /* There better be a username provided. */
            if(username == null || "".equals(username)){
                SVNErrorManager.error(FSErrors.errorNoUser(reposRootDir));
            }
            /* And that username better be the same as the lock's owner. */
            if(!username.equals(lock.getOwner())){
                SVNErrorManager.error(FSErrors.errorLockOwnerMismatch(username, lock.getOwner(), reposRootDir));
            }
        }
        /* Remove lock and lock token files. */
        deleteLock(lock, reposRootDir);
    }
    
    /* Update the current file to hold the correct next node and copy ids
     * from transaction. The current revision is set to newRevision. 
     */
    public static void writeFinalCurrentFile(String txnId, long newRevision, String startNodeId, String startCopyId, File reposRootDir) throws SVNException, IOException {
        /* To find the next available ids, we add the id that used to be in
         * the current file, to the next ids from the transaction file. 
         */
        String[] txnIds = FSReader.readNextIds(txnId, reposRootDir);
        String txnNodeId = txnIds[0];
        String txnCopyId = txnIds[1];
        String newNodeId = FSKeyGenerator.addKeys(startNodeId, txnNodeId);
        String newCopyId = FSKeyGenerator.addKeys(startCopyId, txnCopyId);
        /* Now we can just write out this line. */
        String line = newRevision + " " + newNodeId + " " + newCopyId + "\n";
        File currentFile = FSRepositoryUtil.getFSCurrentFile(reposRootDir);
        File tmpCurrentFile = SVNFileUtil.createUniqueFile(currentFile.getParentFile(), currentFile.getName(), ".tmp");
        OutputStream currentOS = null;
        try{
            currentOS = SVNFileUtil.openFileForWriting(tmpCurrentFile);
            currentOS.write(line.getBytes());
        }finally{
            SVNFileUtil.closeFile(currentOS);
        }
        SVNFileUtil.rename(tmpCurrentFile, currentFile);
    }
    
    /* Write the changed path info from transaction to the permanent rev-file. 
     * Returns offset in the file of the beginning of this information. 
     */
    public static long writeFinalChangedPathInfo(final RandomAccessFile protoFile, String txnId, File reposRootDir) throws SVNException, IOException {
        long offset = protoFile.getFilePointer();
        Map copyfromCache = new HashMap();
        Map changedPaths = FSReader.fetchTxnChanges(null, txnId, copyfromCache, reposRootDir);
        /* Iterate through the changed paths one at a time, and convert the
         * temporary node-id into a permanent one for each change entry. 
         */
        for(Iterator paths = changedPaths.keySet().iterator(); paths.hasNext();){
            String path = (String)paths.next();
            FSPathChange change = (FSPathChange)changedPaths.get(path);
            FSID id = change.getRevNodeId();
            /* If this was a delete of a mutable node, then it is OK to
             * leave the change entry pointing to the non-existant temporary
             * node, since it will never be used. 
             */
            if(change.getChangeKind() != FSPathChangeKind.FS_PATH_CHANGE_DELETE && !id.isTxn()){
                FSRevisionNode revNode = FSReader.getRevNodeFromID(reposRootDir, id);
                /* noderev has the permanent node-id at this point, so we just
                 * substitute it for the temporary one. 
                 */
                change.setRevNodeId(revNode.getId());
            }
            /* Find the cached copyfrom information. */
            SVNLocationEntry copyfromEntry = (SVNLocationEntry)copyfromCache.get(path);
            /* Write out the new entry into the final rev-file. */
            OutputStream protoFileAdapter = new OutputStream(){
                public void write(int b) throws IOException{
                    protoFile.write(b);
                }
            };
            writeChangeEntry(protoFileAdapter, path, change, copyfromEntry);
        }
        return offset;
    }
    
    /* Copy a node-revision specified by id from a transaction into the prototype
     * file (that will be a permanent rev-file). If this is a directory, all
     * children are copied as well. startNodeId and startCopyId are the first 
     * available node and copy ids.
     */
    public static FSID writeFinalRevision(FSID newId, final RandomAccessFile protoFile, long revision, FSID id, String startNodeId, String startCopyId, File reposRootDir) throws SVNException, IOException {
        newId = null;
        /* Check to see if this is a transaction node. */
        if(!id.isTxn()){
            return newId;
        }
        FSRevisionNode revNode = FSReader.getRevNodeFromID(reposRootDir, id);
        if(revNode.getType() == SVNNodeKind.DIR){
            /* This is a directory.  Write out all the children first. */
            Map namesToEntries = FSReader.getDirEntries(revNode, reposRootDir);
            for(Iterator entries = namesToEntries.values().iterator(); entries.hasNext();){
                FSEntry dirEntry = (FSEntry)entries.next();
                newId = writeFinalRevision(newId, protoFile, revision, dirEntry.getId(), startNodeId, startCopyId, reposRootDir);
                if(newId != null && newId.getRevision() == revision){
                    dirEntry.setId(new FSID(newId));
                }
            }
            if(revNode.getTextRepresentation() != null && revNode.getTextRepresentation().isTxn()){
                /* Write out the contents of this directory as a text rep. */
                Map unparsedEntries = FSRepositoryUtil.unparseDirEntries(namesToEntries);
                FSRepresentation textRep = revNode.getTextRepresentation(); 
                textRep.setTxnId(FSID.ID_INAPPLICABLE);
                textRep.setRevision(revision);
                try{
                    textRep.setOffset(protoFile.getFilePointer());
                    final MessageDigest checksum = MessageDigest.getInstance("MD5");
                    long size = HashRepresentationWriter.writeHashRepresentation(unparsedEntries, protoFile, checksum);
                    String hexDigest = SVNFileUtil.toHexDigest(checksum);
                    textRep.setSize(size);
                    textRep.setHexDigest(hexDigest);
                    textRep.setExpandedSize(textRep.getSize());
                }catch(NoSuchAlgorithmException nsae){
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, "MD5 implementation not found: {0}", nsae.getLocalizedMessage());
                    SVNErrorManager.error(err, nsae);
                }
            }
        }else{
            /* This is a file.  We should make sure the data rep, if it
             * exists in a "this" state, gets rewritten to our new revision
             * num. 
             */
            if(revNode.getTextRepresentation() != null && revNode.getTextRepresentation().isTxn()){
               FSRepresentation textRep = revNode.getTextRepresentation();
               textRep.setTxnId(FSID.ID_INAPPLICABLE);
               textRep.setRevision(revision);
            }
        }
        /* Fix up the property reps. */
        if(revNode.getPropsRepresentation() != null && revNode.getPropsRepresentation().isTxn()){
            Map props = FSReader.getProperties(revNode, reposRootDir);
            FSRepresentation propsRep = revNode.getPropsRepresentation();
            try{
                propsRep.setOffset(protoFile.getFilePointer());
                final MessageDigest checksum = MessageDigest.getInstance("MD5");
                long size = HashRepresentationWriter.writeHashRepresentation(props, protoFile, checksum);
                String hexDigest = SVNFileUtil.toHexDigest(checksum);
                propsRep.setSize(size);
                propsRep.setHexDigest(hexDigest);
                propsRep.setTxnId(FSID.ID_INAPPLICABLE);
                propsRep.setRevision(revision);
                propsRep.setExpandedSize(size);
            }catch(NoSuchAlgorithmException nsae){
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, "MD5 implementation not found: {0}", nsae.getLocalizedMessage());
                SVNErrorManager.error(err, nsae);
            }
        }
        /* Convert our temporary ID into a permanent revision one. */
        long myOffset = protoFile.getFilePointer();
        String myNodeId = null;
        String nodeId = revNode.getId().getNodeID();
        if(nodeId.startsWith("_")){
            myNodeId = FSKeyGenerator.addKeys(startNodeId, nodeId.substring(1));
        }else{
            myNodeId = nodeId;
        }
        String myCopyId = null;
        String copyId = revNode.getId().getCopyID();
        if(copyId.startsWith("_")){
            myCopyId = FSKeyGenerator.addKeys(startCopyId, copyId.substring(1));
        }else{
            myCopyId = copyId;
        }
        if(revNode.getCopyRootRevision() == FSConstants.SVN_INVALID_REVNUM){
            revNode.setCopyRootRevision(revision);
        }
        newId = FSID.createRevId(myNodeId, myCopyId, revision, myOffset);
        revNode.setId(newId);
        /* Write out our new node-revision. */
        OutputStream protoFileAdapter = new OutputStream(){
            public void write(int b) throws IOException{
                protoFile.write(b);
            }
        };
        writeTxnNodeRevision(protoFileAdapter, revNode);
        putTxnRevisionNode(id, revNode, reposRootDir);
        /* Return our ID that references the revision file. */
        return newId;
    }
    
    public static void removeRevisionNode(FSID id, File reposRootDir) throws SVNException {
        /* Fetch the node. */
        FSRevisionNode node = FSReader.getRevNodeFromID(reposRootDir, id);
        /* If immutable, do nothing and return immediately. */
        if(!node.getId().isTxn()){
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_NOT_MUTABLE, "Attempted removal of immutable node");
            SVNErrorManager.error(err);
        }
        /* Delete the node revision: */
        /* Delete any mutable property representation. */
        if(node.getPropsRepresentation() != null && node.getPropsRepresentation().isTxn()){
            SVNFileUtil.deleteFile(FSRepositoryUtil.getTxnRevNodePropsFile(id, reposRootDir));
        }
        /* Delete any mutable data representation. */
        if(node.getTextRepresentation() != null && node.getTextRepresentation().isTxn() && node.getType() == SVNNodeKind.DIR){
            SVNFileUtil.deleteFile(FSRepositoryUtil.getTxnRevNodeChildrenFile(id, reposRootDir));
        }
        SVNFileUtil.deleteFile(FSRepositoryUtil.getTxnRevNodeFile(id, reposRootDir));
    }
    
    public static FSRevisionNode cloneChild(FSRevisionNode parent, String parentPath, String childName, String copyId, String txnId, boolean isParentCopyRoot, File reposRootDir) throws SVNException {
        /* First check that the parent is mutable. */
        if(!parent.getId().isTxn()){
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_NOT_MUTABLE, "Attempted to clone child of non-mutable node");
            SVNErrorManager.error(err);
        }
        /* Make sure that NAME is a single path component. */
        if(!SVNPathUtil.isSinglePathComponent(childName)){
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_NOT_SINGLE_PATH_COMPONENT, "Attempted to make a child clone with an illegal name ''{0}''", childName); 
            SVNErrorManager.error(err);
        }
        /* Find the node named childName in parent's entries list if it exists. */
        /* parent's current entry named childName */
        FSRevisionNode childNode = FSReader.getChildDirNode(childName, parent, reposRootDir);
        /* node id we'll put into new node */
        FSID newNodeId = null;
        /* Check for mutability in the node we found.  If it's mutable, we
         * don't need to clone it. 
         */
        if(childNode.getId().isTxn()){
            /* This has already been cloned */
            newNodeId = childNode.getId(); 
        }else{
            if(isParentCopyRoot){
                childNode.setCopyRootPath(parent.getCopyRootPath());
                childNode.setCopyRootRevision(parent.getCopyRootRevision());
            }
            childNode.setCopyFromPath(null);
            childNode.setCopyFromRevision(FSConstants.SVN_INVALID_REVNUM);
            childNode.setPredecessorId(childNode.getId());
            if(childNode.getCount() != -1){
                childNode.setCount(childNode.getCount() + 1);
            }
            childNode.setCreatedPath(SVNPathUtil.concatToAbs(parentPath, childName));
            newNodeId = createSuccessor(childNode.getId(), childNode, copyId, txnId, reposRootDir);
            /* Replace the id in the parent's entry list with the id which
             * refers to the mutable clone of this child. 
             */
            setEntry(parent, childName, newNodeId, childNode.getType(), txnId, reposRootDir);
        }
        /* Initialize the youngster. */
        return FSReader.getRevNodeFromID(reposRootDir, newNodeId);
    }
    
    public static void setEntry(FSRevisionNode parentRevNode, String entryName, FSID entryId, SVNNodeKind kind, String txnId, File reposRootDir) throws SVNException {
        /* Check it's a directory. */
        if(parentRevNode.getType() != SVNNodeKind.DIR){
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_NOT_DIRECTORY, "Attempted to set entry in non-directory node");
            SVNErrorManager.error(err);
        }
        /* Check it's mutable. */
        if(!parentRevNode.getId().isTxn()){
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_NOT_MUTABLE, "Attempted to set entry in immutable node");
            SVNErrorManager.error(err);
        }
        FSRepresentation textRep = parentRevNode.getTextRepresentation();
        File childrenFile = FSRepositoryUtil.getTxnRevNodeChildrenFile(parentRevNode.getId(), reposRootDir);
        OutputStream dst = null;
        try{
            if(textRep == null || !textRep.isTxn()){
                /* Before we can modify the directory, we need to dump its old
                 * contents into a mutable representation file. 
                 */
                Map entries = FSReader.getDirEntries(parentRevNode, reposRootDir);
                Map unparsedEntries = FSRepositoryUtil.unparseDirEntries(entries);
                dst = SVNFileUtil.openFileForWriting(childrenFile);
                SVNProperties.setProperties(unparsedEntries, dst, SVNProperties.SVN_HASH_TERMINATOR);
                /* Mark the node-rev's data rep as mutable. */
                textRep = new FSRepresentation();
                textRep.setRevision(FSConstants.SVN_INVALID_REVNUM);
                textRep.setTxnId(txnId);
                parentRevNode.setTextRepresentation(textRep);
                putTxnRevisionNode(parentRevNode.getId(), parentRevNode, reposRootDir);
            }else{
                /* The directory rep is already mutable, so just open it for append. */
                dst = SVNFileUtil.openFileForWriting(childrenFile, true);
            }
            /* Make a note if we have this directory cached. */
            Map dirContents = parentRevNode.getDirContents();
            /* Append an incremental hash entry for the entry change, and 
             * update the cached directory if necessary. 
             */
            if(entryId != null){
                SVNProperties.appendProperty(entryName, kind + " " + entryId.toString(), dst);
                if(dirContents != null){
                    dirContents.put(entryName, new FSEntry(new FSID(entryId), kind, entryName));
                }
            }else{
                SVNProperties.appendPropertyDeleted(entryName, dst);
                if(dirContents != null){
                    dirContents.remove(entryName);
                }
            }
        }finally{
            SVNFileUtil.closeFile(dst);            
        }
    }

    /* Delete the directory entry named entryName from parent. parent must be 
     * mutable. entryName must be a single path component. Throws an exception if there is no 
     * entry entryName in parent.  
     */
    public static void deleteEntry(FSRevisionNode parent, String entryName, String txnId, File reposRootDir) throws SVNException {
        /* Make sure parent is a directory. */
        if(parent.getType() != SVNNodeKind.DIR){
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_NOT_DIRECTORY, "Attempted to delete entry ''{0}'' from *non*-directory node", entryName); 
            SVNErrorManager.error(err);
        }
        /* Make sure parent is mutable. */
        if(!parent.getId().isTxn()){
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_NOT_MUTABLE, "Attempted to delete entry ''{0}'' from immutable directory node", entryName);
            SVNErrorManager.error(err);
        }
        /* Make sure that entryName is a single path component. */
        if(!SVNPathUtil.isSinglePathComponent(entryName)){
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_NOT_SINGLE_PATH_COMPONENT, "Attempted to delete a node with an illegal name ''{0}''", entryName);
            SVNErrorManager.error(err);
        }
        /* Get a dirent hash for this directory. */
        Map entries = FSReader.getDirEntries(parent, reposRootDir);
        /* Find name in the entries hash. */
        FSEntry dirEntry = (FSEntry)entries.get(entryName);
        /* If we never found id in entries (perhaps because there are no
         * entries or maybe because just there's no such id in the existing 
         * entries... it doesn't matter), throw an exception.  
         */
        if(dirEntry == null){
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_NO_SUCH_ENTRY, "Delete failed--directory has no entry ''{0}''", entryName);
            SVNErrorManager.error(err);
        }
        /* Use the id to get the entry's node.  */
        /* TODO: Well, I don't understand this place - why svn devs try to get 
         * the node revision here, - just to act only as a sanity check or what?
         * The read out node-rev is not used then. The node is got then in 
         * ...delete_if_mutable. So, that is already a check, but when it's really 
         * needed.   
         */
        FSReader.getRevNodeFromID(reposRootDir, dirEntry.getId());
        /* If mutable, remove it and any mutable children from fs. */
        deleteEntryIfMutable(dirEntry.getId(), txnId, reposRootDir);
        /* Remove this entry from its parent's entries list. */
        setEntry(parent, entryName, null, SVNNodeKind.UNKNOWN, txnId, reposRootDir);
    }
    
    private static void deleteEntryIfMutable(FSID id, String txnId, File reposRootDir) throws SVNException {
        /* Get the node. */
        FSRevisionNode node = FSReader.getRevNodeFromID(reposRootDir, id);
        /* If immutable, do nothing and return immediately. */
        if(!node.getId().isTxn()){
            return;
        }
        /* Else it's mutable.  Recurse on directories... */
        if(node.getType() == SVNNodeKind.DIR){
            /* Loop over hash entries */
            Map entries = FSReader.getDirEntries(node, reposRootDir);
            for(Iterator names = entries.keySet().iterator(); names.hasNext();){
                String name = (String)names.next();
                FSEntry entry = (FSEntry)entries.get(name);
                deleteEntryIfMutable(entry.getId(), txnId, reposRootDir);
            }
        }
        /* ... then delete the node itself, after deleting any mutable
         * representations and strings it points to. 
         */
        removeRevisionNode(id, reposRootDir);
    }
    
    public static FSID createSuccessor(FSID oldId, FSRevisionNode newRevNode, String copyId, String txnId, File reposRootDir) throws SVNException {
        if(copyId == null){
            copyId = oldId.getCopyID();
        }
        FSID id = FSID.createTxnId(oldId.getNodeID(), copyId, txnId);
        newRevNode.setId(id);
        if(newRevNode.getCopyRootPath() == null){
            newRevNode.setCopyRootPath(newRevNode.getCreatedPath());
            newRevNode.setCopyRootRevision(newRevNode.getId().getRevision());
        }
        putTxnRevisionNode(newRevNode.getId(), newRevNode, reposRootDir);
        return id;
    }
    
    public static FSTransactionInfo beginTxn(long baseRevision, int flags, FSRevisionNodePool revNodesPool, File reposRootDir) throws SVNException {
        FSTransactionInfo txn = createTxn(baseRevision, revNodesPool, reposRootDir);
        /* Put a datestamp on the newly created txn, so we always know
         * exactly how old it is.  (This will help sysadmins identify
         * long-abandoned txns that may need to be manually removed.)  When
         * a txn is promoted to a revision, this property will be
         * automatically overwritten with a revision datestamp. 
         */
        String commitTime = SVNTimeUtil.formatDate(new Date(System.currentTimeMillis()));
        setTransactionProperty(reposRootDir, txn.getTxnId(), SVNRevisionProperty.DATE, commitTime);
        /* Set temporary txn props that represent the requested 'flags'
         * behaviors. 
         */
        if((flags & FSConstants.SVN_FS_TXN_CHECK_OUT_OF_DATENESS) != 0){
            setTransactionProperty(reposRootDir, txn.getTxnId(), SVNProperty.TXN_CHECK_OUT_OF_DATENESS, SVNProperty.toString(true));
        }
        if((flags & FSConstants.SVN_FS_TXN_CHECK_LOCKS) != 0){
            setTransactionProperty(reposRootDir, txn.getTxnId(), SVNProperty.TXN_CHECK_LOCKS, SVNProperty.toString(true));
        }
        return txn;
    }
    
    //create txn dir & necessary files in the fs
    public static FSTransactionInfo createTxn(long baseRevision, FSRevisionNodePool revNodesPool, File reposRootDir) throws SVNException {
        /* Get the txn id. */
        String txnId = createTxnDir(baseRevision, reposRootDir); 
        FSTransactionInfo txn = new FSTransactionInfo(baseRevision, txnId);
        FSRevisionNode root = revNodesPool.getRootRevisionNode(baseRevision, reposRootDir);
        /* Create a new root node for this transaction. */
        FSWriter.createNewTxnNodeRevisionFromRevision(txn.getTxnId(), root, reposRootDir);
        /* Create an empty rev file. */
        SVNFileUtil.createEmptyFile(FSRepositoryUtil.getTxnRevFile(txn.getTxnId(), reposRootDir));
        /* Create an empty changes file. */
        SVNFileUtil.createEmptyFile(FSRepositoryUtil.getTxnChangesFile(txn.getTxnId(), reposRootDir));
        /* Write the next-ids file. */
        writeNextIds(txn.getTxnId(), "0", "0", reposRootDir);
        return txn;
    }
    
    /* Copy a source revision node into the current transaction txnId. */
    public static void createNewTxnNodeRevisionFromRevision(String txnId, FSRevisionNode sourceNode, File reposRootDir) throws SVNException {
        if(sourceNode.getId().isTxn()){
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_CORRUPT, "Copying from transactions not allowed");
            SVNErrorManager.error(err);
        }
        FSRevisionNode revNode = FSRevisionNode.dumpRevisionNode(sourceNode); 
        revNode.setPredecessorId(sourceNode.getId());
        revNode.setCount(revNode.getCount() + 1);
        revNode.setCopyFromPath(null);
        revNode.setCopyFromRevision(FSConstants.SVN_INVALID_REVNUM);
        /* For the transaction root, the copyroot never changes. */
        revNode.setId(FSID.createTxnId(sourceNode.getId().getNodeID(), sourceNode.getId().getCopyID(), txnId));
        putTxnRevisionNode(revNode.getId(), revNode, reposRootDir);
    }

    public static void putTxnRevisionNode(FSID id, FSRevisionNode revNode, File reposRootDir) throws SVNException{
        if(!id.isTxn()){
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_CORRUPT, "Attempted to write to non-transaction");
            SVNErrorManager.error(err);
        }
        OutputStream revNodeFile = null;
        try{
            revNodeFile = SVNFileUtil.openFileForWriting(FSRepositoryUtil.getTxnRevNodeFile(id, reposRootDir));
            writeTxnNodeRevision(revNodeFile, revNode);
        }catch(IOException ioe){
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, ioe.getLocalizedMessage());
            SVNErrorManager.error(err, ioe);
        }finally{
            SVNFileUtil.closeFile(revNodeFile);
        }
    }

    /* Write the revision node revNode into the file. */
    private static void writeTxnNodeRevision(OutputStream revNodeFile, FSRevisionNode revNode) throws IOException{
        String id = FSConstants.HEADER_ID + ": " + revNode.getId() + "\n";
        revNodeFile.write(id.getBytes());
        String type = FSConstants.HEADER_TYPE + ": " + revNode.getType() + "\n";
        revNodeFile.write(type.getBytes());
        if(revNode.getPredecessorId() != null){
            String predId = FSConstants.HEADER_PRED + ": " + revNode.getPredecessorId() + "\n";
            revNodeFile.write(predId.getBytes());
        }
        String count = FSConstants.HEADER_COUNT + ": " + revNode.getCount() + "\n";
        revNodeFile.write(count.getBytes());
        if(revNode.getTextRepresentation() != null){
            String textRepresentation = FSConstants.HEADER_TEXT + ": " + (FSID.isTxn(revNode.getTextRepresentation().getTxnId()) && revNode.getType() == SVNNodeKind.DIR ? "-1" : revNode.getTextRepresentation().toString()) + "\n";
            revNodeFile.write(textRepresentation.getBytes());
        }
        if(revNode.getPropsRepresentation() != null){
            String propsRepresentation = FSConstants.HEADER_PROPS + ": " + (FSID.isTxn(revNode.getPropsRepresentation().getTxnId()) ? "-1" : revNode.getPropsRepresentation().toString()) + "\n";
            revNodeFile.write(propsRepresentation.getBytes());
        }
        String cpath = FSConstants.HEADER_CPATH + ": " + revNode.getCreatedPath() + "\n";
        revNodeFile.write(cpath.getBytes());
        if(revNode.getCopyFromPath() != null){
            String copyFromPath = FSConstants.HEADER_COPYFROM + ": " + revNode.getCopyFromRevision() + " " + revNode.getCopyFromPath() + "\n";
            revNodeFile.write(copyFromPath.getBytes());
        }
        if(revNode.getCopyRootRevision() != revNode.getId().getRevision() || !revNode.getCopyRootPath().equals(revNode.getCreatedPath())){
            String copyroot = FSConstants.HEADER_COPYROOT + ": " + revNode.getCopyRootRevision() + " " + revNode.getCopyRootPath() + "\n";
            revNodeFile.write(copyroot.getBytes());
        }
        revNodeFile.write("\n".getBytes());
    }
    
    /* Write a single change entry - path, path change info, and copyfrom
     * string into the changes file.
     */
    public static void writeChangeEntry(OutputStream changesFile, String path, FSPathChange pathChange, SVNLocationEntry copyfromEntry) throws SVNException, IOException {
        FSPathChangeKind changeKind = pathChange.getChangeKind();
        if(!(changeKind == FSPathChangeKind.FS_PATH_CHANGE_ADD || changeKind == FSPathChangeKind.FS_PATH_CHANGE_DELETE || 
             changeKind == FSPathChangeKind.FS_PATH_CHANGE_MODIFY || changeKind == FSPathChangeKind.FS_PATH_CHANGE_REPLACE || changeKind == FSPathChangeKind.FS_PATH_CHANGE_RESET)){
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_CORRUPT, "Invalid change type");
            SVNErrorManager.error(err);
        }
        String changeString = changeKind.toString();
        String idString = null;
        if(pathChange.getRevNodeId() != null){
            idString = pathChange.getRevNodeId().toString();
        }else{
            idString = FSConstants.ACTION_RESET;
        }
        String output = idString + " " + changeString + " " + SVNProperty.toString(pathChange.isTextModified()) + " " + SVNProperty.toString(pathChange.arePropertiesModified()) + " " + path + "\n"; 
        changesFile.write(output.getBytes());
        if(copyfromEntry != null){
            String copyfromLine = copyfromEntry.getRevision() + " " + copyfromEntry.getPath();
            changesFile.write(copyfromLine.getBytes());
        }
        changesFile.write("\n".getBytes());
    }
    
    /* Write out the currently available next nodeId and copyId
     * for transaction id in filesystem. 
     */
    public static void writeNextIds(String txnId, String nodeId, String copyId, File reposRootDir) throws SVNException {
        OutputStream nextIdsFile = null;
        try{
            nextIdsFile = SVNFileUtil.openFileForWriting(FSRepositoryUtil.getTxnNextIdsFile(txnId, reposRootDir));
            String ids = nodeId + " " + copyId + "\n";
            nextIdsFile.write(ids.getBytes());
        }catch(IOException ioe){
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, ioe.getLocalizedMessage());
            SVNErrorManager.error(err, ioe);  
        }finally{
            SVNFileUtil.closeFile(nextIdsFile);
        }
    }
    
    /* Create a unique directory for a transaction in FS based on the 
     * provided revision. Return the ID for this transaction. 
     */
    public static String createTxnDir(long revision, File reposRootDir) throws SVNException {
        File parent = FSRepositoryUtil.getTransactionsDir(reposRootDir);
        File uniquePath = null;
        /* Try to create directories named "<txndir>/<rev>-<uniquifier>.txn". */
        for (int i = 1; i < 99999; i++) {
            uniquePath = new File(parent, revision + "-" + i + FSConstants.TXN_PATH_EXT);
            if (!uniquePath.exists() && uniquePath.mkdirs()) {
                /* We succeeded.  Return the basename minus the ".txn" extension. */
                return revision + "-" + i;
            }
        }
        SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_UNIQUE_NAMES_EXHAUSTED, "Unable to create transaction directory in ''{0}'' for revision {1,number,integer}", new Object[]{parent, new Long(revision)});
        SVNErrorManager.error(err);
        return null;
    }
    
    public static void writePathInfoToReportFile(OutputStream tmpFileOS, String target, String path, String linkPath, String lockToken, long revision, boolean startEmpty) throws IOException {
        String anchorRelativePath = SVNPathUtil.append(target, path);
        String linkPathRep = linkPath != null ? "+" + linkPath.length() + ":" + linkPath : "-";
        String revisionRep = FSRepository.isValidRevision(revision) ? "+" + revision + ":" : "-";
        String lockTokenRep = lockToken != null ? "+" + lockToken.length() + ":" + lockToken : "-";
        String startEmptyRep = startEmpty ? "+" : "-";
        String fullRepresentation = "+" + anchorRelativePath.length() + ":" + anchorRelativePath + linkPathRep + revisionRep + startEmptyRep + lockTokenRep;
        tmpFileOS.write(fullRepresentation.getBytes());
    }
    
    /* Delete LOCK from FS in the actual OS filesystem. */
    public static void deleteLock(SVNLock lock, File reposRootDir) throws SVNException {
        String reposPath = lock.getPath();
        String childToKill = null;
        Collection children = new ArrayList();
        while(true){
            FSReader.fetchLockFromDigestFile(null, reposPath, children, reposRootDir);
            if(childToKill != null){
                children.remove(childToKill);
            }
            /* Delete the lock.*/
            if(children.size() == 0){
                /* Special case:  no goodz, no file.  And remember to nix
                 * the entry for it in its parent. 
                 */
                childToKill = FSRepositoryUtil.getDigestFromRepositoryPath(reposPath);
                File digestFile = FSRepositoryUtil.getDigestFileFromRepositoryPath(reposPath, reposRootDir);
                SVNFileUtil.deleteFile(digestFile);
            }else{
                FSWriter.writeDigestLockFile(null, children, reposPath, reposRootDir);
                childToKill = null;
            }
            /* Prep for next iteration, or bail if we're done. */
            if("/".equals(reposPath)){
                break;
            }
            reposPath = SVNPathUtil.removeTail(reposPath);
            if("".equals(reposPath)){
                reposPath = "/";
            }
            children.clear(); 
        }
    }
    
    private static void writeDigestLockFile(SVNLock lock, Collection children, String repositoryPath, File reposRootDir) throws SVNException {
        if(!ensureDirExists(FSRepositoryUtil.getDBLocksDir(reposRootDir), true)){
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNKNOWN, "Can't create a directory at ''{0}''", FSRepositoryUtil.getDBLocksDir(reposRootDir));
            SVNErrorManager.error(err);
        }
        File digestLockFile = FSRepositoryUtil.getDigestFileFromRepositoryPath(repositoryPath, reposRootDir);
        File lockDigestSubdir = FSRepositoryUtil.getDigestSubdirectoryFromDigest(FSRepositoryUtil.getDigestFromRepositoryPath(repositoryPath), reposRootDir);
        if(!ensureDirExists(lockDigestSubdir, true)){
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNKNOWN, "Can't create a directory at ''{0}''", lockDigestSubdir);
            SVNErrorManager.error(err);
        }
        Map props = new HashMap();
        if(lock != null){
            props.put(FSConstants.PATH_LOCK_KEY, lock.getPath());
            props.put(FSConstants.OWNER_LOCK_KEY, lock.getOwner());
            props.put(FSConstants.TOKEN_LOCK_KEY, lock.getID());
            props.put(FSConstants.IS_DAV_COMMENT_LOCK_KEY, "0");
            if(lock.getComment() != null){
                props.put(FSConstants.COMMENT_LOCK_KEY, lock.getComment());
            }
            if(lock.getCreationDate() != null){
                props.put(FSConstants.CREATION_DATE_LOCK_KEY, SVNTimeUtil.formatDate(lock.getCreationDate()));
            }
            if(lock.getExpirationDate() != null){
                props.put(FSConstants.EXPIRATION_DATE_LOCK_KEY, SVNTimeUtil.formatDate(lock.getExpirationDate()));
            }
        }
        if(children != null && children.size() > 0){
            Object[] digests = children.toArray();
            StringBuffer value = new StringBuffer();
            for(int i = 0; i < digests.length; i++){
                value.append(digests[i]);
                value.append('\n');
            }
            props.put(FSConstants.CHILDREN_LOCK_KEY, value.toString());
        }
        try{
            SVNProperties.setProperties(props, digestLockFile);
        }catch(SVNException svne){
            SVNErrorMessage err = svne.getErrorMessage().wrap("Cannot write lock/entries hashfile ''{0}''", digestLockFile);
            SVNErrorManager.error(err, svne);
        }
    }

    public static void setTransactionProperty(File reposRootDir, String txnId, String propertyName, String propertyValue) throws SVNException {
        SVNProperties revProps = new SVNProperties(FSRepositoryUtil.getTxnPropsFile(txnId, reposRootDir), null);
        revProps.setPropertyValue(propertyName, propertyValue);
    }

    public static void setRevisionProperty(File reposRootDir, long revision, String propertyName, String propertyNewValue, String propertyOldValue, String userName, String action) throws SVNException {
        FSHooks.runPreRevPropChangeHook(reposRootDir, propertyName, propertyNewValue, userName, revision, action);
        SVNProperties revProps = new SVNProperties(FSRepositoryUtil.getRevisionPropertiesFile(reposRootDir, revision), null);
        revProps.setPropertyValue(propertyName, propertyNewValue);
        FSHooks.runPostRevPropChangeHook(reposRootDir, propertyName, propertyOldValue, userName, revision, action);
    }
    
    public static void setProplist(FSRevisionNode node, Map properties, File reposRootDir) throws SVNException {
        /* Sanity check: this node better be mutable! */
        if(!node.getId().isTxn()){
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_NOT_MUTABLE, "Can't set proplist on *immutable* node-revision {0}", node.getId());
            SVNErrorManager.error(err);
        }
        /* Dump the property list to the mutable property file. */
        File propsFile = null;
        propsFile = FSRepositoryUtil.getTxnRevNodePropsFile(node.getId(), reposRootDir);
        SVNProperties.setProperties(properties, propsFile);
        /* Mark the node-rev's prop rep as mutable, if not already done. */
        if(node.getPropsRepresentation() == null || !node.getPropsRepresentation().isTxn()){
            FSRepresentation mutableRep = new FSRepresentation();
            mutableRep.setTxnId(node.getId().getTxnID());
            node.setPropsRepresentation(mutableRep);
            putTxnRevisionNode(node.getId(), node, reposRootDir);
        }
    }
    
    public static boolean ensureDirExists(File dir, boolean create){
        if(!dir.exists() && create == true){
            return dir.mkdirs();
        }else if(!dir.exists()){
            return false;
        }
        return true;
    }
    
    private static void setLock(SVNLock lock, File reposRootDir) throws SVNException {
        if(lock == null){
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNKNOWN, "FATAL error: attempted to set a null lock");
            SVNErrorManager.error(err);
        }
        String lastChild = "";
        String path = lock.getPath();
        Collection children = new ArrayList();
        while(true){            
            String digestFileName = FSRepositoryUtil.getDigestFromRepositoryPath(path);
            SVNLock fetchedLock = FSReader.fetchLockFromDigestFile(null, path, children, reposRootDir);
            /* We're either writing a new lock (first time through only) or
             * a new entry (every time but the first). 
             */
            if(lock != null){
                fetchedLock = lock;
                lock = null;
                lastChild = digestFileName;
            }else{
                /* If we already have an entry for this path, we're done. */
                if(!children.isEmpty() && children.contains(lastChild)){
                    break;
                }
                children.add(lastChild);
            }
            writeDigestLockFile(fetchedLock, children, path, reposRootDir);
            /* Prep for next iteration, or bail if we're done. */
            if("/".equals(path)){
                break;
            }
            path = SVNPathUtil.removeTail(path);
            if("".equals(path)){
                path = "/";
            }
            children.clear();
        } 
    }

    private static class HashRepresentationWriter extends OutputStream{
        long mySize = 0;
        MessageDigest myChecksum;
        RandomAccessFile myProtoFile;

        public HashRepresentationWriter(RandomAccessFile protoFile, MessageDigest digest){
            super();
            myChecksum = digest;
            myProtoFile = protoFile;
        }
        
        public void write(int b) throws IOException{
            myProtoFile.write(b);
            if(myChecksum != null){
                myChecksum.update((byte)b);
            }
            mySize++;
        }
        
        public static long writeHashRepresentation(Map hashContents, RandomAccessFile protoFile, MessageDigest digest) throws IOException, SVNException {
            HashRepresentationWriter targetFile = new HashRepresentationWriter(protoFile, digest);
            String header = FSConstants.REP_PLAIN + "\n";
            protoFile.write(header.getBytes());
            SVNProperties.setProperties(hashContents, targetFile, SVNProperties.SVN_HASH_TERMINATOR);
            String trailer = FSConstants.REP_TRAILER + "\n";
            protoFile.write(trailer.getBytes());
            return targetFile.mySize;
        }
    }
}
