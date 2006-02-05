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

import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNLock;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.io.SVNLocationEntry;
import org.tmatesoft.svn.core.io.ISVNLockHandler;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.internal.util.SVNTimeUtil;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.wc.SVNProperties;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;

import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.io.File;
import java.util.Date;
import java.util.LinkedList;
import java.util.Iterator;
import java.util.Map;
import java.util.Collection;
import java.util.HashMap;
import java.util.Set;

public class FSReader {
    
    public static SVNNodeKind checkNodeKind(String path, FSRevisionNode root, File reposRootDir) throws SVNException {
        FSRevisionNode node = null;
        try{
            node = FSReader.getRevisionNode(reposRootDir, path, root, 0);
        }catch(SVNException svne){
            if(svne.getErrorMessage().getErrorCode() == SVNErrorCode.FS_NOT_FOUND){
                return SVNNodeKind.NONE;
            }
            throw svne;
        }   
        return node.getType();
    }    

    /* Read the 'current' file for filesystem reposRootDir and return the next
     * available node id and the next available copy id. 
     *
     * String[0] - current node-id
     * String[1] - current copy-id
     */
    public static String[] getNextRevisionIds(File reposRootDir) throws SVNException{
        String[] ids = new String[2];
        File currentFile = FSRepositoryUtil.getFSCurrentFile(reposRootDir);
        String idsLine = FSReader.readSingleLine(currentFile, 80);
        if(idsLine == null || idsLine.length() == 0){
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_CORRUPT, "Corrupt current file");
            SVNErrorManager.error(err);
        }
        String[] parsedIds = idsLine.split(" ");
        if(parsedIds.length < 3){
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_CORRUPT, "Corrupt current file");
            SVNErrorManager.error(err);
        }
        ids[0] = parsedIds[1];
        ids[1] = parsedIds[2];
        return ids;
    }
    
    public static long getCreatedRevision(File reposRootDir, FSRevisionNodePool revNodesPool, String path, long revision)throws SVNException{
        if(path == null){
            return FSConstants.SVN_INVALID_REVNUM;
        }
        path = SVNPathUtil.canonicalizeAbsPath(path);
        long youngestRev = FSReader.getYoungestRevision(reposRootDir);
        if(FSRepository.isInvalidRevision(revision) || revision > youngestRev){
            revision = youngestRev; 
        }
        FSRoot root = FSRoot.createRevisionRoot(revision, revNodesPool.getRootRevisionNode(revision, reposRootDir));
        FSNodeHistory hist = null;
        try{
            hist = FSNodeHistory.getNodeHistory(reposRootDir, root, path);
        }catch(SVNException ex){
            /*if path is not found on specified revision*/
            return FSConstants.SVN_INVALID_REVNUM;
        }
        long histRev = hist.getHistoryEntry().getRevision();
        while(true){
            hist = hist.fsHistoryPrev(reposRootDir, true, revNodesPool);
            if(hist == null){
                return histRev;
            }
            boolean ancestry = FSNodeHistory.checkAncestryOfPegPath(reposRootDir, path, hist.getHistoryEntry().getRevision(), revision, revNodesPool);
            if(ancestry == false){
                return histRev;
            }
            histRev = hist.getHistoryEntry().getRevision();
        }
    }                                       
    
    public static Map fetchTxnChanges(Map changedPaths, String txnId, Map copyFromCache, File reposRootDir) throws SVNException {
        changedPaths = changedPaths == null ? new HashMap() : changedPaths;
        File changesFile = FSRepositoryUtil.getTxnChangesFile(txnId, reposRootDir);
        RandomAccessFile raChangesFile = null;
        try{
            raChangesFile = SVNFileUtil.openRAFileForReading(changesFile);
            fetchAllChanges(changedPaths, raChangesFile, false, copyFromCache);
        }finally{
            SVNFileUtil.closeFile(raChangesFile);
        }
        return changedPaths;
    }
    
    /* Return Object[] consist of two Maps:
     * Object[0]: pathChanged Map
     * Object[1]: copyfromCache Map*/    
    public static Object[] fetchAllChanges(Map changedPaths, RandomAccessFile changesFile, boolean prefolded, Map mapCopyfrom)throws SVNException{        
        changedPaths = changedPaths != null ? changedPaths : new HashMap();  
        mapCopyfrom = mapCopyfrom != null ? mapCopyfrom : new HashMap();
        FSChange change = FSReader.readChange(changesFile);        
        while(change != null){
            foldChange(changedPaths, change, mapCopyfrom);
            if((FSPathChangeKind.FS_PATH_CHANGE_DELETE == change.getKind() || 
                FSPathChangeKind.FS_PATH_CHANGE_REPLACE == change.getKind()) && 
                prefolded == false){                                
                Collection keySet = changedPaths.keySet();
                Iterator curIter = keySet.iterator();
                while(curIter.hasNext()){
                    String hashKeyPath = (String)curIter.next();
                    //If we come across our own path, ignore it                    
                    if(change.getPath().equals(hashKeyPath)){
                        continue;
                    }
                    //If we come across a child of our path, remove it
                    if(SVNPathUtil.pathIsChild(change.getPath(), hashKeyPath) != null){
                        changedPaths.remove(hashKeyPath);
                    }
                }                
            }
            change = FSReader.readChange(changesFile);
        }
        Object[] result = new Object[2];
        result[0] = changedPaths;
        result[1] = mapCopyfrom;
        return result;        
    }
    
    /* Merge the internal-use-only FSChange into a hash of FSPathChanges, 
     * collapsing multiple changes into a single summarising change per path.  
     * Also keep copyfromCache (here it is a parameter Map mapCopyfrom) up to date with new adds and replaces
     * return Object[] arr, where 
     * arr[0]: map of changed paths
     * arr[1]: map of copyfrom's of specified path; 
     * */
    private static Object[] foldChange(Map mapChanges, FSChange change, Map mapCopyfrom) throws SVNException{
        if(change == null){
            return null;            
        }
        mapChanges = mapChanges != null ? mapChanges : new HashMap();
        mapCopyfrom = mapCopyfrom != null ? mapCopyfrom : new HashMap();
        FSPathChange newChange = null;
        SVNLocationEntry copyfromEntry = null;
        String path = null;
        
        FSPathChange oldChange = (FSPathChange)mapChanges.get(change.getPath());
        if(oldChange != null){
            /* Get the existing copyfrom entry for this path. */
            copyfromEntry = (SVNLocationEntry)mapCopyfrom.get(change.getPath());
            path = change.getPath();
            /* Sanity check:  only allow NULL node revision ID in the 'reset' case. */
            if((change.getNodeRevID() == null) && 
                    (FSPathChangeKind.FS_PATH_CHANGE_RESET != change.getKind())){
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_CORRUPT, "Missing required node revision ID");
                SVNErrorManager.error(err);
            }
            /* Sanity check: we should be talking about the same node
            revision ID as our last change except where the last change
            was a deletion*/
            if((change.getNodeRevID() != null) && 
                    (!oldChange.getRevNodeId().equals(change.getNodeRevID())) && 
                    (oldChange.getChangeKind() != FSPathChangeKind.FS_PATH_CHANGE_DELETE)){
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_CORRUPT, "Invalid change ordering: new node revision ID without delete");
                SVNErrorManager.error(err);
            }
            /* Sanity check: an add, replacement, or reset must be the first
            thing to follow a deletion*/            
            if(FSPathChangeKind.FS_PATH_CHANGE_DELETE == oldChange.getChangeKind() && 
                    !( FSPathChangeKind.FS_PATH_CHANGE_REPLACE == change.getKind() || 
                       FSPathChangeKind.FS_PATH_CHANGE_RESET == change.getKind()   ||
                       FSPathChangeKind.FS_PATH_CHANGE_ADD == change.getKind()) ){
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_CORRUPT, "Invalid change ordering: non-add change on deleted path");
                SVNErrorManager.error(err);
            }    
            /*Merging the changes*/
            if(FSPathChangeKind.FS_PATH_CHANGE_MODIFY == change.getKind()){
                if(change.getTextModification()){
                    oldChange.setTextModified(true);
                }
                if(change.getPropModification()){
                    oldChange.setPropertiesModified(true);
                }                
            }else if(FSPathChangeKind.FS_PATH_CHANGE_ADD == change.getKind() || FSPathChangeKind.FS_PATH_CHANGE_REPLACE == change.getKind()){
                oldChange.setChangeKind(FSPathChangeKind.FS_PATH_CHANGE_REPLACE);
                oldChange.setRevNodeId(new FSID(change.getNodeRevID()));
                oldChange.setTextModified(change.getTextModification());
                oldChange.setPropertiesModified(change.getPropModification());
                if(change.getCopyfromEntry() == null){
                    copyfromEntry = null;
                }else{
                    copyfromEntry = new SVNLocationEntry(change.getCopyfromEntry().getRevision(), change.getCopyfromEntry().getPath());
                }                
            }else if(FSPathChangeKind.FS_PATH_CHANGE_DELETE == change.getKind()){
                if(FSPathChangeKind.FS_PATH_CHANGE_ADD == oldChange.getChangeKind()){
                    /*If the path was introduced in this transaction via an
                    add, and we are deleting it, just remove the path altogether*/
                    oldChange = null;
                    mapChanges.remove(change.getPath());
                }else{
                    /* A deletion overrules all previous changes. */
                    oldChange.setChangeKind(FSPathChangeKind.FS_PATH_CHANGE_DELETE);
                    oldChange.setPropertiesModified(change.getPropModification());
                    oldChange.setTextModified(change.getTextModification());
                }
                copyfromEntry = null;
                mapCopyfrom.remove(change.getPath());
            }else if(FSPathChangeKind.FS_PATH_CHANGE_RESET == change.getKind()){
                oldChange = null;
                copyfromEntry = null;
                mapChanges.remove(change.getPath());
                mapCopyfrom.remove(change.getPath());                
            }
            newChange = oldChange;
        }else{
            newChange = new FSPathChange(new FSID(change.getNodeRevID()), change.getKind(), change.getTextModification(), change.getPropModification());
            if(change.getCopyfromEntry().getRevision() != FSConstants.SVN_INVALID_REVNUM){
                copyfromEntry = new SVNLocationEntry(change.getCopyfromEntry().getRevision(), change.getCopyfromEntry().getPath());
            }else{
                copyfromEntry = null;
            }
            path = change.getPath();
        }
        /* Add (or update) this path. */
        mapChanges.put(path, newChange);        
  
        if(copyfromEntry == null){
            mapCopyfrom.remove(path);
        }else{
            mapCopyfrom.put(path, copyfromEntry);            
        }      
        
        Object[] result = new Object[2];
        result[0] = mapChanges;
        result[1] = mapCopyfrom;
        return result;
    }
    
    public static InputStream getFileContentsInputStream(FSRoot root, String path, FSRevisionNodePool pool, File reposRootDir) throws SVNException {
        FSRevisionNode fileNode = pool.getRevisionNode(root, path, reposRootDir);
        return FSInputStream.createDeltaStream(fileNode, reposRootDir);
    }

    /* Given a representation 'rep', open the correct file and seek to the 
     * correction location. 
     */
    public static RandomAccessFile openAndSeekRepresentation(FSRepresentation rep, File reposRootDir) throws SVNException {
        if(!rep.isTxn()){
            return openAndSeekRevision(rep.getRevision(), rep.getOffset(), reposRootDir);
        }
        return openAndSeekTransaction(rep, reposRootDir);
    }

    /* Open the representation for a node-revision in a transaction in 
     * filesystem. Seek to an offset location before returning. Only appropriate 
     * for file contents, nor props or directory contents. 
     */
    private static RandomAccessFile openAndSeekTransaction(FSRepresentation rep, File reposRootDir) throws SVNException {
        RandomAccessFile file = null;
        try{
            file = SVNFileUtil.openRAFileForReading(FSRepositoryUtil.getTxnRevFile(rep.getTxnId(), reposRootDir));
            file.seek(rep.getOffset());
        }catch(IOException ioe){
            SVNFileUtil.closeFile(file);
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, ioe.getLocalizedMessage());
            SVNErrorManager.error(err, ioe);
        }catch(SVNException svne){
            SVNFileUtil.closeFile(file);
            throw svne;
        }
        return file; 
    }

    /* Open the revision file for a revision in a filesystem. Seek to an offset 
     * location before returning. 
     */
    private static RandomAccessFile openAndSeekRevision(long revision, long offset, File reposRootDir) throws SVNException {
        RandomAccessFile file = null;
        try{
            file = SVNFileUtil.openRAFileForReading(FSRepositoryUtil.getRevisionFile(reposRootDir, revision));
            file.seek(offset);
        }catch(IOException ioe){
            SVNFileUtil.closeFile(file);
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, "Can't set position pointer in file ''{0}'': {1}", new Object[]{FSRepositoryUtil.getRevisionFile(reposRootDir, revision), ioe.getLocalizedMessage()});
            SVNErrorManager.error(err, ioe);
        }catch(SVNException svne){
            SVNFileUtil.closeFile(file);
            throw svne;
        }
        return file; 
    }
    
    /* String[0] - is to be the fetched out node-id
     * String[1] - is to be the fetched out copy-id
     */
    public static String[] readNextIds(String txnId, File reposRootDir) throws SVNException {
        String[] ids = new String[2];
        String idsToParse = null;
        idsToParse = FSReader.readSingleLine(FSRepositoryUtil.getTxnNextIdsFile(txnId, reposRootDir), FSConstants.MAX_KEY_SIZE*2 + 3);
        String[] parsedIds = idsToParse.split(" ");
        if(parsedIds.length < 2){
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_CORRUPT, "next-ids file corrupt");
            SVNErrorManager.error(err);
        }
        ids[0] = parsedIds[0];
        ids[1] = parsedIds[1];
        return ids;
    }
    
    public static void allowLockedOperation(String path, final String username, final Collection lockTokens, boolean recursive, boolean haveWriteLock, File reposRootDir) throws SVNException {
        path = SVNPathUtil.canonicalizeAbsPath(path);
        if(recursive){
            ISVNLockHandler handler = new ISVNLockHandler(){
                private String myUsername = username;
                private Collection myTokens = lockTokens;
                
                public void handleLock(String path, SVNLock lock, SVNErrorMessage error) throws SVNException {
                    verifyLock(lock, myTokens, myUsername);
                }
                
                public void handleUnlock(String path, SVNLock lock, SVNErrorMessage error) throws SVNException{
                }
            };
            /* Discover all locks at or below the path. */
            walkDigestFiles(FSRepositoryUtil.getDigestFileFromRepositoryPath(path, reposRootDir), handler, haveWriteLock, reposRootDir);
        }else{
            /* Discover and verify any lock attached to the path. */
            SVNLock lock = getLockHelper(path, haveWriteLock, reposRootDir);
            if(lock != null){
                verifyLock(lock, lockTokens, username);
            }
        }
    }
    
    /* A recursive function that calls getLocksHandler for
     * all locks in and under PATH in FS.
     * haveWriteLock should be true if the caller (directly or indirectly)
     * has the FS write lock. 
     */
    public static void walkDigestFiles(File digestFile, ISVNLockHandler getLocksHandler, boolean haveWriteLock, File reposRootDir) throws SVNException {
        Collection children = new LinkedList();
        /* First, send up any locks in the current path. */
        SVNLock lock = fetchLockFromDigestFile(digestFile, null, children, reposRootDir);
        if(lock != null){
            Date current = new Date(System.currentTimeMillis());
            /* Don't report an expired lock. */
            if(lock.getExpirationDate() == null || current.compareTo(lock.getExpirationDate()) < 0){
                getLocksHandler.handleLock(null, lock, null);
            }else if(haveWriteLock) {
                /* Only remove the lock if we have the write lock.
                 * Read operations shouldn't change the filesystem. 
                 */
                FSWriter.deleteLock(lock, reposRootDir);
            }
        }
        /* Now, recurse on this thing's child entries (if any; bail otherwise). */
        if(children.isEmpty()){
            return;
        }
        for(Iterator entries = children.iterator(); entries.hasNext();){
            String digestName = (String)entries.next();
            File childDigestFile = FSRepositoryUtil.getDigestFileFromDigest(digestName, reposRootDir);
            walkDigestFiles(childDigestFile, getLocksHandler, haveWriteLock, reposRootDir);
        }
    }
    
    /* Utility function:  verify that a lock can be used. */
    private static void verifyLock(SVNLock lock, Collection lockTokens, String username) throws SVNException {
        if(username == null || "".equals(username)){
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_NO_USER, "Cannot verify lock on path ''{0}''; no username available", lock.getPath());
            SVNErrorManager.error(err);
        }else if(username.compareTo(lock.getOwner()) != 0){
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_LOCK_OWNER_MISMATCH, "User {0} does not own lock on path ''{1}'' (currently locked by {2})", new Object[]{username, lock.getPath(), lock.getOwner()});
            SVNErrorManager.error(err);
        }
        for(Iterator tokens = lockTokens.iterator(); tokens.hasNext();){
            String token = (String)tokens.next();
            if(token.equals(lock.getID())){
                return;
            }
        }
        SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_BAD_LOCK_TOKEN, "Cannot verify lock on path ''{0}''; no matching lock-token available", lock.getPath());
        SVNErrorManager.error(err);
    }
    
    public static SVNLock getLock(String repositoryPath, boolean haveWriteLock, File reposRootDir) throws SVNException {
        SVNLock lock = fetchLockFromDigestFile(null, repositoryPath, null, reposRootDir);
        if(lock == null){
            SVNErrorManager.error(FSErrors.errorNoSuchLock(repositoryPath, reposRootDir));
        }
        Date current = new Date(System.currentTimeMillis());
        /* Don't return an expired lock. */
        if(lock.getExpirationDate() != null && current.compareTo(lock.getExpirationDate()) > 0){
            /* Only remove the lock if we have the write lock.
             * Read operations shouldn't change the filesystem. 
             */
            if(haveWriteLock){
                FSWriter.deleteLock(lock, reposRootDir);
            }
            SVNErrorManager.error(FSErrors.errorLockExpired(lock.getID(), reposRootDir));
        }
        return lock;
    }
    
    public static SVNLock getLockHelper(String repositoryPath, boolean haveWriteLock, File reposRootDir) throws SVNException {
        repositoryPath = SVNPathUtil.canonicalizeAbsPath(repositoryPath);
        SVNLock lock = null;
        try{
            lock = getLock(repositoryPath, haveWriteLock, reposRootDir);
        }catch(SVNException svne){
            /* They've deliberately decided that this function doesn't tell the
             * caller *why* the lock is unavailable.  
             */
            if(svne.getErrorMessage().getErrorCode() == SVNErrorCode.FS_NO_SUCH_LOCK || 
                    svne.getErrorMessage().getErrorCode() == SVNErrorCode.FS_LOCK_EXPIRED){
                return null;
            }
            throw svne;
        }
        return lock;
    }
    
    public static SVNLock fetchLockFromDigestFile(File digestFile, String repositoryPath, Collection children, File reposRootDir) throws SVNException {        
        File digestLockFile = digestFile == null ? FSRepositoryUtil.getDigestFileFromRepositoryPath(repositoryPath, reposRootDir) : digestFile;
        SVNProperties props = new SVNProperties(digestLockFile, null);
        Map lockProps = null;
        try{
            lockProps = props.asMap();
        } catch(SVNException svne){
            SVNErrorMessage err = svne.getErrorMessage().wrap("Can't parse lock/entries hashfile ''{0}''", digestLockFile);
            SVNErrorManager.error(err);
        }
        SVNLock lock = null;
        String lockPath = (String)lockProps.get(FSConstants.PATH_LOCK_KEY);
        if(lockPath != null){
            String lockToken = (String)lockProps.get(FSConstants.TOKEN_LOCK_KEY);
            if(lockToken == null){
                SVNErrorManager.error(FSErrors.errorCorruptLockFile(lockPath, reposRootDir));
            }
            String lockOwner = (String)lockProps.get(FSConstants.OWNER_LOCK_KEY);
            if(lockOwner == null){
                SVNErrorManager.error(FSErrors.errorCorruptLockFile(lockPath, reposRootDir));
            }
            String davComment = (String)lockProps.get(FSConstants.IS_DAV_COMMENT_LOCK_KEY);
            if(davComment == null){
                SVNErrorManager.error(FSErrors.errorCorruptLockFile(lockPath, reposRootDir));
            }
            String creationTime = (String)lockProps.get(FSConstants.CREATION_DATE_LOCK_KEY);
            if(creationTime == null){
                SVNErrorManager.error(FSErrors.errorCorruptLockFile(lockPath, reposRootDir));
            }
            Date creationDate = SVNTimeUtil.parseDateString(creationTime);
            String expirationTime = (String)lockProps.get(FSConstants.EXPIRATION_DATE_LOCK_KEY);
            Date expirationDate = null;
            if(expirationTime != null){
                expirationDate = SVNTimeUtil.parseDateString(expirationTime);
            }
            String comment = (String)lockProps.get(FSConstants.COMMENT_LOCK_KEY);
            lock = new SVNLock(lockPath, lockToken, lockOwner, comment, creationDate, expirationDate);
        }
        String childEntries = (String)lockProps.get(FSConstants.CHILDREN_LOCK_KEY);
        if(children != null && childEntries != null){
            String[] digests = childEntries.split("\n");
            for(int i = 0; i < digests.length; i++){
                children.add(digests[i]);
            }
        }
        return lock;
    }
    
    public static FSTransaction getTxn(String txnId, File reposRootDir) throws SVNException {
        FSTransaction txn = getTxn(txnId, false, reposRootDir);
        if(txn.getKind() != FSTransactionKind.TXN_KIND_NORMAL){
            SVNErrorManager.error(FSErrors.errorTxnNotMutable(txnId, reposRootDir));
        }
        return txn;
    }

    /* If expectDead is true, this transaction must be a dead one, 
     * else an error is returned. If expectDead is false, an error is 
     * thrown if the transaction is *not* dead. 
     */
    private static FSTransaction getTxn(String txnId, boolean expectDead, File reposRootDir) throws SVNException {
        FSTransaction txn = fetchTxn(txnId, reposRootDir);
        if(expectDead && txn.getKind() != FSTransactionKind.TXN_KIND_DEAD){
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_TRANSACTION_NOT_DEAD, "Transaction is not dead: ''{0}''", txnId);
            SVNErrorManager.error(err);
        }
        if(!expectDead && txn.getKind() == FSTransactionKind.TXN_KIND_DEAD){
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_TRANSACTION_DEAD, "Transaction is dead: ''{0}''", txnId);
            SVNErrorManager.error(err);
        }
        return txn;
    }

    private static FSTransaction fetchTxn(String txnId, File reposRootDir) throws SVNException {
        Map txnProps = FSRepositoryUtil.getTransactionProperties(reposRootDir, txnId);
        FSID rootId = FSID.createTxnId("0", "0", txnId);
        FSRevisionNode revNode = getRevNodeFromID(reposRootDir, rootId);
        return new FSTransaction(FSTransactionKind.TXN_KIND_NORMAL, revNode.getId(), revNode.getPredecessorId(), null, txnProps);
    }
    
    /*
     * If root is not null, tries to find the rev-node for repositoryPath 
     * in the provided root, otherwise if root is null, uses the provided 
     * revision to get the root first. 
     */
    public static FSRevisionNode getRevisionNode(File reposRootDir, String repositoryPath, FSRevisionNode root, long revision) throws SVNException {
        String absPath = SVNPathUtil.canonicalizeAbsPath(repositoryPath);
        String nextPathComponent = null;
        FSRevisionNode parent = root != null ? root : FSReader.getRootRevNode(reposRootDir, revision);
        FSRevisionNode child = null;
        String pathSoFar = "/";
        //skip the leading '/'
        absPath = absPath.substring(1);
        while (true) {
            nextPathComponent = SVNPathUtil.head(absPath);
            absPath = SVNPathUtil.removeHead(absPath);
            pathSoFar = SVNPathUtil.concatToAbs(pathSoFar, nextPathComponent);
            if ("".equals(nextPathComponent)) {
                child = parent;
            } else {
                try{
                    child = FSReader.getChildDirNode(nextPathComponent, parent, reposRootDir);
                }catch(SVNException svne){
                    if(svne.getErrorMessage().getErrorCode() == SVNErrorCode.FS_NOT_FOUND){
                        /* Build a better error message than getChildDirNode()
                         * can provide, giving the root and full path name.  
                         */
                        SVNErrorManager.error(FSErrors.errorNotFound(FSRoot.createRevisionRoot(revision, root), repositoryPath), svne);
                    }
                    throw svne;
                }
            }
            parent = child;
            if ("".equals(absPath)) {
                break;
            }
            /* The path isn't finished yet; we'd better be in a directory.  */
            if(child.getType() != SVNNodeKind.DIR){
                SVNErrorMessage err = FSErrors.errorNotDirectory(pathSoFar, reposRootDir);
                SVNErrorManager.error(err.wrap("Failure opening ''{0}''", repositoryPath));
            }
        }
        return parent;
    }
    
    public static FSRevisionNode getChildDirNode(String childName, FSRevisionNode parent, File reposRootDir) throws SVNException {
        /* Make sure that NAME is a single path component. */
        if (!SVNPathUtil.isSinglePathComponent(childName)) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_NOT_SINGLE_PATH_COMPONENT, "Attempted to open node with an illegal name ''{0}''", childName);
            SVNErrorManager.error(err);
        }
        /* Now get the node that was requested. */
        Map entries = getDirEntries(parent, reposRootDir); 
        FSEntry entry = entries != null ? (FSEntry) entries.get(childName) : null;
        if(entry == null){
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_NOT_FOUND, "Attempted to open non-existent child node ''{0}''", childName);
            SVNErrorManager.error(err);
        }
        return getRevNodeFromID(reposRootDir, entry.getId());
    }

    public static Map getDirEntries(FSRevisionNode parent, File reposRootDir) throws SVNException {
        if (parent.getType() != SVNNodeKind.DIR) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_NOT_DIRECTORY, "Can't get entries of non-directory");
            SVNErrorManager.error(err);
        }
        //first try to ask the object's cache for entries
        Map entries = new HashMap();
        Map dirContents = parent.getDirContents(); 
        if(dirContents == null){
            dirContents = getDirContents(parent, reposRootDir);
            parent.setDirContents(dirContents);
        }
        if(dirContents != null){
            entries.putAll(dirContents);
        }
        return entries;
    }

    public static Map getProperties(FSRevisionNode revNode, File reposRootDir) throws SVNException {
        return getProplist(revNode, reposRootDir);
    }

    private static Map getDirContents(FSRevisionNode revNode, File reposRootDir) throws SVNException {
        if(revNode.getTextRepresentation() != null && revNode.getTextRepresentation().isTxn()){
            /* The representation is mutable.  Read the old directory
             * contents from the mutable children file, followed by the
             * changes we've made in this transaction. 
             */
            File childrenFile = FSRepositoryUtil.getTxnRevNodeChildrenFile(revNode.getId(), reposRootDir);
            InputStream file = null;
            Map entries = null;
            try{
                file = SVNFileUtil.openFileForReading(childrenFile);
                Map rawEntries = SVNProperties.asMap(null, file, false, SVNProperties.SVN_HASH_TERMINATOR);
                rawEntries = SVNProperties.asMap(rawEntries, file, true, null);
                entries = parsePlainRepresentation(rawEntries);
            }finally{
                SVNFileUtil.closeFile(file);
            }
            return entries;
        }else if(revNode.getTextRepresentation() != null){
            InputStream is = null;
            FSRepresentation textRepresent = revNode.getTextRepresentation(); 
            try {
                is = FSInputStream.createPlainStream(textRepresent, reposRootDir);//readPlainRepresentation(textRepresent, reposRootDir);
                Map rawEntries = SVNProperties.asMap(null, is, false, SVNProperties.SVN_HASH_TERMINATOR);
                return parsePlainRepresentation(rawEntries);
            } finally {
                SVNFileUtil.closeFile(is);
            }
        }
        return new HashMap();//returns an empty map, must not be null!!
    }

    private static Map getProplist(FSRevisionNode revNode, File reposRootDir) throws SVNException {
        Map properties = new HashMap();
        if(revNode.getPropsRepresentation() != null && revNode.getPropsRepresentation().isTxn()){
            File propsFile = FSRepositoryUtil.getTxnRevNodePropsFile(revNode.getId(), reposRootDir);
            InputStream file = null;
            try{
                file = SVNFileUtil.openFileForReading(propsFile);
                properties = SVNProperties.asMap(properties, file, false, SVNProperties.SVN_HASH_TERMINATOR);
            }finally{
                SVNFileUtil.closeFile(file);
            }
        }else if(revNode.getPropsRepresentation() != null){
            InputStream is = null;
            FSRepresentation propsRepresent = revNode.getPropsRepresentation();
            try {
                is = FSInputStream.createPlainStream(propsRepresent, reposRootDir);
                properties = SVNProperties.asMap(properties, is, false, SVNProperties.SVN_HASH_TERMINATOR);
            } finally {
                SVNFileUtil.closeFile(is);
            }
        }
        return properties;//no properties? return an empty map 
    }

    /*
     * Now this routine is intended only for parsing dir entries since
     * the static method asMap() of SVNProperties does all the job for 
     * reading properties.
     */
    private static Map parsePlainRepresentation(Map entries) throws SVNException {
        Map representationMap = new HashMap();
        Object[] names = entries.keySet().toArray();
        for(int i = 0; i < names.length; i++){
            String name = (String)names[i];
            FSEntry nextRepEntry = parseRepEntryValue(name, (String)entries.get(names[i]));
            if(nextRepEntry == null){
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_CORRUPT, "Directory entry corrupt");
                SVNErrorManager.error(err);
            }
            representationMap.put(name, nextRepEntry);
        }
        return representationMap;
    }

    private static FSEntry parseRepEntryValue(String name, String value) {
        if(value == null){
            return null;
        }
        String[] values = value.split(" ");
        if (values == null || values.length < 2) {
            return null;
        }
        SVNNodeKind type = SVNNodeKind.parseKind(values[0]);
        FSID id = parseID(values[1]);
        if ((type != SVNNodeKind.DIR && type != SVNNodeKind.FILE) || id == null) {
            return null;
        }
        return new FSEntry(id, type, name);
    }

    public static FSRevisionNode getTxnRootNode(String txnId, File reposRootDir) throws SVNException {
        FSTransaction txn = getTxn(txnId, reposRootDir);
        FSRevisionNode txnRootNode = getRevNodeFromID(reposRootDir, txn.getRootId()); 
        return txnRootNode;
    }

    public static FSRevisionNode getTxnBaseRootNode(String txnId, File reposRootDir) throws SVNException {
        FSTransaction txn = getTxn(txnId, reposRootDir);
        FSRevisionNode txnBaseNode = getRevNodeFromID(reposRootDir, txn.getBaseId()); 
        return txnBaseNode;
    }
    
    public static FSRevisionNode getRootRevNode(File reposRootDir, long revision) throws SVNException {
        FSID id = FSID.createRevId(FSID.ID_INAPPLICABLE, FSID.ID_INAPPLICABLE, revision, getRootOffset(reposRootDir, revision));
        return getRevNodeFromID(reposRootDir, id);
    }

    public static FSRevisionNode getRevNodeFromID(File reposRootDir, FSID id) throws SVNException {
        Map headers = null;
        RandomAccessFile raRevFile = null;
        try{
            if(id.isTxn()){
                /* This is a transaction node-rev. */
                File revFile = FSRepositoryUtil.getTxnRevNodeFile(id, reposRootDir);
                raRevFile = SVNFileUtil.openRAFileForReading(revFile);
            }else{
                /* This is a revision node-rev. */
                raRevFile = FSReader.openAndSeekRevision(id.getRevision(), id.getOffset(), reposRootDir);
            }
        }catch(SVNException svne){
            SVNFileUtil.closeFile(raRevFile);
            SVNErrorManager.error(FSErrors.errorDanglingId(id, reposRootDir));
        }
        headers = readRevNodeHeaders(raRevFile);
        //actually the file should have been already closed in readRevNodeHeaders(raRevFile) 
        SVNFileUtil.closeFile(raRevFile);
        FSRevisionNode revNode = new FSRevisionNode();

        // Read the rev-node id.
        String revNodeId = (String) headers.get(FSConstants.HEADER_ID);
        if (revNodeId == null) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_CORRUPT, "Missing node-id in node-rev");
            SVNErrorManager.error(err);
        }

        FSID revnodeId = parseID(revNodeId);    
        if(revnodeId == null){
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_CORRUPT, "Corrupt node-id in node-rev");
            SVNErrorManager.error(err);
        }
        revNode.setId(revnodeId);

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
            parseRepresentationHeader(propsRepr, revNode, id.getTxnID(), false);
        }

        // Get the data location (if any).
        String textRepr = (String) headers.get(FSConstants.HEADER_TEXT);
        if (textRepr != null) {
            parseRepresentationHeader(textRepr, revNode, id.getTxnID(), true);
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
            FSID predRevNodeId = parseID(predId);
            if(predRevNodeId == null){
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
            parseCopyRoot(copyroot, revNode);
        }

        // Get the copyfrom.
        String copyfrom = (String) headers.get(FSConstants.HEADER_COPYFROM);
        if (copyfrom == null) {
            revNode.setCopyFromPath(null);
            revNode.setCopyFromRevision(FSConstants.SVN_INVALID_REVNUM);
        } else {
            parseCopyFrom(copyfrom, revNode);
        }
        return revNode;
    }

    // should it fail if revId is invalid?
    public static void parseCopyFrom(String copyfrom, FSRevisionNode revNode) throws SVNException {
        if (copyfrom == null || copyfrom.length() == 0) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_CORRUPT, "Malformed copyfrom line in node-rev");
            SVNErrorManager.error(err);
        }
        String[] cpyfrom = copyfrom.split(" ");
        if (cpyfrom.length < 2) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_CORRUPT, "Malformed copyfrom line in node-rev");
            SVNErrorManager.error(err);
        }
        long rev = -1;
        try {
            rev = Long.parseLong(cpyfrom[0]);
        } catch (NumberFormatException nfe) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_CORRUPT, "Malformed copyfrom line in node-rev");
            SVNErrorManager.error(err);
        }
        revNode.setCopyFromRevision(rev);
        revNode.setCopyFromPath(cpyfrom[1]);
    }

    // should it fail if revId is invalid?
    public static void parseCopyRoot(String copyroot, FSRevisionNode revNode) throws SVNException {
        if (copyroot == null || copyroot.length() == 0) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_CORRUPT, "Malformed copyroot line in node-rev");
            SVNErrorManager.error(err);
        }

        String[] cpyroot = copyroot.split(" ");
        if (cpyroot.length < 2) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_CORRUPT, "Malformed copyroot line in node-rev");
            SVNErrorManager.error(err);
        }
        long rev = -1;
        try {
            rev = Long.parseLong(cpyroot[0]);
        } catch (NumberFormatException nfe) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_CORRUPT, "Malformed copyroot line in node-rev");
            SVNErrorManager.error(err);
        }
        revNode.setCopyRootRevision(rev);
        revNode.setCopyRootPath(cpyroot[1]);
    }

    public static FSID parseID(String revNodeId) {
        /* Now, we basically just need to "split" this data on `.'
         * characters.
         */
        String[] idParts = revNodeId.split("\\.");
        if(idParts.length != 3){
            return null;
        }
        /* Node Id */
        String nodeId = idParts[0];  
        /* Copy Id */
        String copyId = idParts[1];
        if(idParts[2].charAt(0) == 'r'){
            /* This is a revision type ID */
            int slashInd = idParts[2].indexOf('/');
            long rev = -1;
            long offset = -1;
            try {
                rev = Long.parseLong(idParts[2].substring(1, slashInd));
                offset = Long.parseLong(idParts[2].substring(slashInd + 1));
            } catch (NumberFormatException nfe) {
                return null;
            }
            return FSID.createRevId(nodeId, copyId, rev, offset);
        }else if(idParts[2].charAt(0) == 't'){
            /* This is a transaction type ID */
            String txnId = idParts[2].substring(1);
            return FSID.createTxnId(nodeId, copyId, txnId);
        }
        return null;
    }

    // isData - if true - text, otherwise - props
    private static void parseRepresentationHeader(String representation, FSRevisionNode revNode, String txnId, boolean isData) throws SVNException {
        if (revNode == null) {
            return;
        }
        FSRepresentation rep = new FSRepresentation();
        String[] offsets = representation.split(" ");
        long rev = -1;
        try {
            rev = Long.parseLong(offsets[0]);
        } catch (NumberFormatException nfe) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_CORRUPT, "Malformed text rep offset line in node-rev");
            SVNErrorManager.error(err);
        }
        rep.setRevision(rev);
        if(FSRepository.isInvalidRevision(rep.getRevision())){
            rep.setTxnId(txnId);
            if(isData){
                revNode.setTextRepresentation(rep);
            }else{
                revNode.setPropsRepresentation(rep);
            }
            //is it a mutable representation?
            if(!isData || revNode.getType() == SVNNodeKind.DIR){
                return;
            }
        }
        if (offsets == null || offsets.length == 0 || offsets.length < 5) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_CORRUPT, "Malformed text rep offset line in node-rev");
            SVNErrorManager.error(err);
        }
        long offset = -1;
        try {
            offset = Long.parseLong(offsets[1]);
            if (offset < 0) {
                throw new NumberFormatException();
            }
        } catch (NumberFormatException nfe) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_CORRUPT, "Malformed text rep offset line in node-rev");
            SVNErrorManager.error(err);
        }
        rep.setOffset(offset);
        long size = -1;
        try {
            size = Long.parseLong(offsets[2]);
            if (size < 0) {
                throw new NumberFormatException();
            }
        } catch (NumberFormatException nfe) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_CORRUPT, "Malformed text rep offset line in node-rev");
            SVNErrorManager.error(err);
        }
        rep.setSize(size);
        long expandedSize = -1;
        try {
            expandedSize = Long.parseLong(offsets[3]);
            if (expandedSize < 0) {
                throw new NumberFormatException();
            }
        } catch (NumberFormatException nfe) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_CORRUPT, "Malformed text rep offset line in node-rev");
            SVNErrorManager.error(err);
        }
        rep.setExpandedSize(expandedSize);
        String hexDigest = offsets[4];
        if (hexDigest.length() != 2 * FSConstants.MD5_DIGESTSIZE || SVNFileUtil.fromHexDigest(hexDigest) == null) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_CORRUPT, "Malformed text rep offset line in node-rev");
            SVNErrorManager.error(err);
        }
        rep.setHexDigest(hexDigest);
        if (isData) {
            revNode.setTextRepresentation(rep);
        } else {
            revNode.setPropsRepresentation(rep);
        }
    }

    public static long getRootOffset(File reposRootDir, long revision) throws SVNException {
        Long[] offsets = readRootAndChangesOffset(reposRootDir, revision);
        return offsets[0].longValue();
    }

    public static long getChangesOffset(File reposRootDir, long revision) throws SVNException {
        Long[] offsets = readRootAndChangesOffset(reposRootDir, revision);
        return offsets[1].longValue();
    }

    // Read in a rev-node given its offset in a rev-file.
    private static Map readRevNodeHeaders(RandomAccessFile revFile) throws SVNException {
        Map map = new HashMap();
        try {
            while (true) {
                String line = readNextLine(revFile, 1024);
                if (line == null || line.length() == 0) {
                    break;
                }
                int colonIndex = line.indexOf(':');
                if (colonIndex < 0 || colonIndex == line.length() - 1 || colonIndex == line.length() - 2) {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_CORRUPT, "Found malformed header in revision file ''{0}''", revFile);
                    SVNErrorManager.error(err);
                }
                String localName = line.substring(0, colonIndex);
                String localValue = line.substring(colonIndex + 1);
                map.put(localName, localValue.trim());
            }
        } finally {
            SVNFileUtil.closeFile(revFile);
        }
        return map;
    }

    /* limitBytes MUST NOT be 0! it defines the maximum number of bytes 
     * that should be read (not counting EOL)
     */ 
    public static String readNextLine(RandomAccessFile raFile, int limitBytes) throws SVNException {
        StringBuffer lineBuffer = new StringBuffer();
        int r = -1;
        try {
            for(int i = 0; i < limitBytes; i++){
                r = raFile.read();
                if(r == -1){//unexpected EOF?
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.STREAM_UNEXPECTED_EOF);
                    SVNErrorManager.error(err);
                }else if(r == '\n'){
                    return lineBuffer.toString();
                }
                lineBuffer.append((char)r);
            }
        } catch (IOException ioe) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, "Can't read length line in stream: {0}", ioe.getLocalizedMessage());
            SVNErrorManager.error(err, ioe);
        }
        SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, "Can't read length line in stream");
        SVNErrorManager.error(err);
        return null;
    }

    //to read single line files only
    public static String readSingleLine(File file, int limit) throws SVNException {
        if (file == null) {
            return null;
        }
        InputStream is = null;
        String line = null;
        try {
            is = SVNFileUtil.openFileForReading(file);
            line = readSingleLine(is, limit);
        } catch (SVNException svne) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.MALFORMED_FILE, "Can't read length line in file ''{0}'': {1}", new Object[]{file, svne.getLocalizedMessage()});
            SVNErrorManager.error(err, svne);
        } finally {
            SVNFileUtil.closeFile(is);
        }
        return line;
    }

    // to read lines only from svn files ! (eol-specific)
    public static String readSingleLine(InputStream is, int limit) throws SVNException {
        int r = -1;
        StringBuffer lineBuffer = new StringBuffer();
        for(int i = 0; i < limit; i++){
            try{
                r = is.read();
            }catch(IOException ioe){
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, ioe.getLocalizedMessage());
                SVNErrorManager.error(err, ioe);
            }
            if(r == -1){//unexpected EOF?
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.STREAM_UNEXPECTED_EOF);
                SVNErrorManager.error(err);
            }else if(r == '\n' || r == '\r'){
                return lineBuffer.toString();
            }
            lineBuffer.append((char)r);
        }
        SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.MALFORMED_FILE, "Can't read length line in stream");
        SVNErrorManager.error(err);
        return null;
    }

    private static Long[] readRootAndChangesOffset(File reposRootDir, long revision) throws SVNException {
        File revFile = FSRepositoryUtil.getRevisionFile(reposRootDir, revision); // getRevFile(revision);
        int size = 64;
        byte[] buffer = new byte[size];
        RandomAccessFile raRevFile = null;
        try {
            /*
             * svn: We will assume that the last line containing the two offsets
             * will never be longer than 64 characters. Read in this last block,
             * from which we will identify the last line.
             */
            raRevFile = SVNFileUtil.openRAFileForReading(revFile);
            long offset = raRevFile.length() - size;
            raRevFile.seek(offset);
            size = raRevFile.read(buffer);
            if (size <= 0) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.STREAM_UNEXPECTED_EOF);
                SVNErrorManager.error(err);
            }
        } catch (IOException ioe) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, ioe.getLocalizedMessage());
            SVNErrorManager.error(err, ioe);
        }finally{
            SVNFileUtil.closeFile(raRevFile);
        }
        // The last byte should be a newline.
        if (buffer[size - 1] != '\n') {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_CORRUPT, "Revision file lacks trailing newline");
            SVNErrorManager.error(err);
        }
        String bytesAsString = new String(buffer, 0, size);
        if (bytesAsString.indexOf('\n') == bytesAsString.lastIndexOf('\n')) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_CORRUPT, "Final line in revision file is longer than 64 characters");
            SVNErrorManager.error(err);
        }
        String[] lines = bytesAsString.split("\n");
        String lastLine = lines[lines.length - 1];
        String[] offsetsValues = lastLine.split(" ");
        if (offsetsValues.length < 2) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_CORRUPT, "Final line in revision file missing space");
            SVNErrorManager.error(err);
        }
        long rootOffset = -1;
        try {
            rootOffset = Long.parseLong(offsetsValues[0]);
        } catch (NumberFormatException nfe) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_CORRUPT, "Can't parse root offset in revision file");
            SVNErrorManager.error(err);
        }

        long changesOffset = -1;
        try {
            changesOffset = Long.parseLong(offsetsValues[1]);
        } catch (NumberFormatException nfe) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_CORRUPT, "Can't parse changes offset in revision file");
            SVNErrorManager.error(err);
        }
        Long[] offsets = new Long[2];
        offsets[0] = new Long(rootOffset);
        offsets[1] = new Long(changesOffset);
        return offsets;
    }

    public static PathInfo readPathInfoFromReportFile(InputStream reportFile) throws IOException {
        int firstByte = reportFile.read();
        if (firstByte == -1 || firstByte == '-') {
            return null;
        }
        String path = readStringFromReportFile(reportFile);
        String linkPath = reportFile.read() == '+' ? readStringFromReportFile(reportFile) : null;
        long revision = readRevisionFromReportFile(reportFile);
        boolean startEmpty = reportFile.read() == '+' ? true : false;
        String lockToken = reportFile.read() == '+' ? readStringFromReportFile(reportFile) : null;
        return new PathInfo(path, linkPath, lockToken, revision, startEmpty);
    }

    public static String readStringFromReportFile(InputStream reportFile) throws IOException {
        int length = readNumberFromReportFile(reportFile);
        if (length == 0) {
            return "";
        }
        byte[] buffer = new byte[length];
        reportFile.read(buffer);
        return new String(buffer);
    }

    public static int readNumberFromReportFile(InputStream reportFile) throws IOException {
        int b;
        StringBuffer result = new StringBuffer();
        while ((b = reportFile.read()) != ':') {
            result.append((char)b);
        }
        return Integer.parseInt(result.toString(), 10);
    }

    public static long readRevisionFromReportFile(InputStream reportFile) throws IOException {
        if (reportFile.read() == '+') {
            return readNumberFromReportFile(reportFile);
        }
        return FSConstants.SVN_INVALID_REVNUM;
    }
  
    /* Read changes from revision file, RandomAccessFile reader must be already opened.
     * OffsetContainerClass before invoking 'readChanges' method contains offset to changes, after invoking 
     * 'readChanges' contains offset to next changes (if file has them) in raFile */
    public static FSChange readChange(RandomAccessFile raReader) throws SVNException {
        String line = null;
        try{
            line = FSReader.readNextLine(raReader, 4096);
        }catch(SVNException svne){
            /* Check for a blank line. */
            if(svne.getErrorMessage().getErrorCode() == SVNErrorCode.STREAM_UNEXPECTED_EOF){
                return null;
            }
            throw svne;
        }
        /* Check for a blank line. */
        if(line.length() == 0){
            return null;
        }        
        String [] piecesOfLine = line.split(" ");
        if(piecesOfLine == null || piecesOfLine.length < 5){
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_CORRUPT, "Invalid changes line in rev-file");
            SVNErrorManager.error(err);
        }
        /* Get the node-id of the change. */
        String nodeRevStr = piecesOfLine[0];
        FSID nodeRevID = parseID(nodeRevStr);
        /* Get the change type. */
        String changesKindStr = piecesOfLine[1];
        FSPathChangeKind changesKind = (FSPathChangeKind)FSConstants.ACTIONS_TO_CHANGE_KINDS.get(changesKindStr);
        if(changesKind == null){
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_CORRUPT, "Invalid change kind in rev file");
            SVNErrorManager.error(err);
        }
        /* Get the text-mod flag. */
        String textModeStr = piecesOfLine[2];
        boolean textModeBool = false;
        if(textModeStr.equals(FSConstants.FLAG_TRUE)){
            textModeBool = true;
        }else if(textModeStr.equals(FSConstants.FLAG_FALSE)){
            textModeBool = false;
        }else{
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_CORRUPT, "Invalid text-mod flag in rev-file");
            SVNErrorManager.error(err);
        }
        /* Get the prop-mod flag. */
        String propModeStr = piecesOfLine[3];
        boolean propModeBool = false;
        if(propModeStr.equals(new String(FSConstants.FLAG_TRUE))){
            propModeBool = true;
        }else if(propModeStr.equals(new String(FSConstants.FLAG_FALSE))){
            propModeBool = false;
        } else{
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_CORRUPT, "Invalid prop-mod flag in rev-file");
            SVNErrorManager.error(err);
        }
        /* Get the changed path. */
        String pathStr = piecesOfLine[4];
        /* Read the next line, the copyfrom line. */
        String nextLine = FSReader.readNextLine(raReader, 4096);
        SVNLocationEntry copyfromEntry = null;
        if(nextLine.length() == 0){
            copyfromEntry = new SVNLocationEntry(FSConstants.SVN_INVALID_REVNUM, null);
        }else{
            String [] piecesOfNextLine = nextLine.split(" ");
            if(piecesOfNextLine == null || piecesOfNextLine.length < 2){
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_CORRUPT, "Invalid changes line in rev-file");
                SVNErrorManager.error(err);
            }
            copyfromEntry = new SVNLocationEntry(Long.parseLong(piecesOfNextLine[0]), piecesOfNextLine[1]);
        }
        return new FSChange(new String(pathStr), new FSID(nodeRevID), changesKind, textModeBool, propModeBool, copyfromEntry);
    }

    public static long getYoungestRevision(File reposRootDir) throws SVNException {
        File dbCurrentFile = FSRepositoryUtil.getFSCurrentFile(reposRootDir);
        String firstLine = readSingleLine(dbCurrentFile, 80);
        String splittedLine[] = firstLine.split(" ");
        long latestRev = -1;
        try {
            latestRev = Long.parseLong(splittedLine[0]);
        } catch (NumberFormatException nfe) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_CORRUPT, "Can't parse revision number in file ''{0}'': {1}", new Object[]{dbCurrentFile, nfe.getLocalizedMessage()});
            SVNErrorManager.error(err, nfe);
        }
        return latestRev;
    }
    
    /* Store as keys in returned Map the paths of all node in ROOT that show a
     * significant change.  "Significant" means that the text or
     * properties of the node were changed, or that the node was added or
     * deleted.
     * Keys are String paths and values are FSLogChangedPath.
     */
    public static Map detectChanged(File reposRootDir, FSRevisionNodePool revNodesPool, FSRoot root)throws SVNException{
        Map returnChanged = new HashMap();
        Map changes = FSReader.getFSpathChanged(reposRootDir, root);        
        if(changes.size() == 0){
            return changes;
        }
        Set hashKeys = changes.keySet();
        Iterator chgIter = hashKeys.iterator();
        while(chgIter.hasNext()){
            char action;
            String hashPathKey = (String)chgIter.next();
            FSPathChange change = (FSPathChange)changes.get(hashPathKey);
            String path = hashPathKey;                      
            if(change.getChangeKind() == FSPathChangeKind.FS_PATH_CHANGE_RESET){
                continue;
            }else if(change.getChangeKind() == FSPathChangeKind.FS_PATH_CHANGE_ADD){
                action = 'A';
            }else if(change.getChangeKind() == FSPathChangeKind.FS_PATH_CHANGE_DELETE){
                action = 'D';
            }else if(change.getChangeKind() == FSPathChangeKind.FS_PATH_CHANGE_REPLACE){
                action = 'R';
            }else{
                action = 'M';
            }
            FSLogChangedPath itemCopyfrom = new FSLogChangedPath(action, new SVNLocationEntry(FSConstants.SVN_INVALID_REVNUM, null));
            if(action == 'A' || action == 'R'){                                
                SVNLocationEntry copyfromEntry = FSReader.copiedFrom(reposRootDir, root.getRootRevisionNode(), path, revNodesPool);
                if(copyfromEntry.getPath() != null && FSRepository.isValidRevision(copyfromEntry.getRevision())){
                    itemCopyfrom = new FSLogChangedPath(action, copyfromEntry);
                }                
            }
            returnChanged.put(path, itemCopyfrom);
        }
        return returnChanged;
    }
    
    /* Return MAP with hash containing descriptions of the paths changed under ROOT. 
     * The hash is keyed with String paths and has FSPathChange values
     */    
    public static Map getFSpathChanged(File reposRootDir, FSRoot root) throws SVNException{   
        Map changedPaths = new HashMap();
        if(root.isTxnRoot()){
            return FSReader.fetchTxnChanges(changedPaths, root.getTxnId(), null, reposRootDir);
        }           
        long changesOffset = FSReader.getChangesOffset(reposRootDir, root.getRevision());
        File revFile = FSRepositoryUtil.getRevisionFile(reposRootDir, root.getRevision());
        RandomAccessFile raRevFile = null;
        Object[] result = null;
        try{
            raRevFile = SVNFileUtil.openRAFileForReading(revFile);
            raRevFile.seek(changesOffset);
            result = FSReader.fetchAllChanges(changedPaths, raRevFile, true, root.getCopyfromCache());
        }catch(IOException ioe){
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, ioe.getLocalizedMessage());
            SVNErrorManager.error(err, ioe);
        }finally{
            SVNFileUtil.closeFile(raRevFile);
        }
        root.setCopyfromCache((Map)result[1]);
        return (Map)result[0];      
    }
    
    /* Discover the copy ancestry of PATH under ROOT.  Return a relevant
     * ancestor/revision combination in PATH(SVNLocationEntry) and REVISON(SVNLocationEntry)*/
    public static SVNLocationEntry copiedFrom(File reposRootDir, FSRevisionNode root, String path, FSRevisionNodePool revNodesPool) throws SVNException {
        FSRevisionNode node = revNodesPool.getRevisionNode(root, path, reposRootDir);
        return new SVNLocationEntry(node.getCopyFromRevision(), node.getCopyFromPath());
    }
}
