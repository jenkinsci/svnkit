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

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.io.SVNLocationEntry;
import org.tmatesoft.svn.core.internal.util.*;
import org.tmatesoft.svn.core.internal.io.fs.FSRevisionNode;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;

/**
 * @version 1.0
 * @author  TMate Software Ltd.
 */
public class FSNodeHistory
{	
	//path and revision of historical location
	private SVNLocationEntry historyEntry;
	
	//internal-use hints about where to resume the history search
	private SVNLocationEntry searchResumeEntry;
	
	//FALSE until the first call to fsHistoryPrev()
	private boolean isInteresting;
	
	public FSNodeHistory(SVNLocationEntry newHistoryEntry, boolean interest, SVNLocationEntry newSearchResumeEntry){
		historyEntry = newHistoryEntry;
		searchResumeEntry = newSearchResumeEntry;		
		isInteresting = interest;
	}
	//methods-accessors
	public SVNLocationEntry getHistoryEntry(){
		return historyEntry;
	}
	public void setHistoryEntry(SVNLocationEntry newHistoryEntry){
		historyEntry = newHistoryEntry;		
	}
	public SVNLocationEntry getSearchResumeEntry(){
		return searchResumeEntry;
	}
	public void setHintsEntry(SVNLocationEntry newSearchResumeEntry){
		searchResumeEntry = newSearchResumeEntry;
	}
	public boolean isInteresting(){
		return isInteresting;
	}
	public void setInterest(boolean someInterest){
		isInteresting = someInterest;
	}
	//methods connected to history entity
	
	//Find the youngest copyroot for path PARENT_PATH or its parents
	public static SVNLocationEntry findYoungestCopyroot(File reposRootDir, FSParentPath parPath) throws SVNException {		
		SVNLocationEntry parentEntry = null;		
		if(parPath.getParent() != null){
			parentEntry = FSNodeHistory.findYoungestCopyroot(reposRootDir, parPath.getParent());
		}
          /* Set our copyroot. */
        SVNLocationEntry myEntry = new SVNLocationEntry(parPath.getRevNode().getCopyRootRevision(), parPath.getRevNode().getCopyRootPath()); 
		if(parentEntry != null){
			if(myEntry.getRevision() >= parentEntry.getRevision()){
				return myEntry;
			}
            return parentEntry;
		}
        return myEntry;
	}
	
    public static boolean checkAncestryOfPegPath(File reposRootDir, String fsPath, long pegRev, long futureRev, FSRevisionNodePool revNodesPool) throws SVNException {
   		//FSRevisionNode root = FSReader.getRootRevNode(reposRootDir, futureRev);
        FSRoot root = FSRoot.createRevisionRoot(futureRev, revNodesPool.getRootRevisionNode(futureRev, reposRootDir)); 
        FSNodeHistory history = getNodeHistory(reposRootDir, root, fsPath, revNodesPool);
        fsPath = null;
   		SVNLocationEntry currentHistory = null;
   		while(true){  
   			history = history.fsHistoryPrev(reposRootDir, true, revNodesPool);    			
   			if(history == null){  
   				break;    			
   			}
   			currentHistory = new SVNLocationEntry(history.getHistoryEntry().getRevision(), history.getHistoryEntry().getPath()); 		
   			if(fsPath == null){
   				fsPath = currentHistory.getPath();
   			}
   			if(currentHistory.getRevision() <= pegRev){
   				break;    				
   			}
   		}
        /* We must have had at least one iteration above where we
         * reassigned fsPath. Else, the path wouldn't have existed at
         * futureRev. 
         */
   		if(fsPath == null){
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNKNOWN, "FATAL error occurred while checking ancestry of peg path");
            SVNErrorManager.error(err);
        }
        return (history != null && (fsPath.equals(currentHistory.getPath())));
    }
    
    //Retrun FSNodeHistory as an opaque node history object which represents
    //PATH under ROOT. ROOT must be a revision root  
    public static FSNodeHistory getNodeHistory(File reposRootDir, FSRoot root, String path, FSRevisionNodePool pool) throws SVNException{
        if(root.isTxnRoot()){
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_NOT_REVISION_ROOT);
            SVNErrorManager.error(err);
        }        
        /*And we require that the path exist in the root*/
        FSRevisionNode node = null;
//        SVNNodeKind kind = FSReader.checkNodeKind(path, root.getRootRevisionNode(), reposRootDir);      
        SVNNodeKind kind = null;      
        try{
            node = pool.openPath(root, path, false, null, reposRootDir, false).getRevNode(); //FSReader.getRevisionNode(reposRootDir, path, root, 0);
        }catch(SVNException svne){
            if(svne.getErrorMessage().getErrorCode() == SVNErrorCode.FS_NOT_FOUND){
                kind = SVNNodeKind.NONE;
            }
            throw svne;
        }   
        kind = node.getType();
    	if(kind == SVNNodeKind.NONE){
    		SVNErrorManager.error(FSErrors.errorNotFound(root, path));
    	}    	
    	return new FSNodeHistory(new SVNLocationEntry(root.getRevision(), SVNPathUtil.canonicalizeAbsPath(path)), 
    			false, new SVNLocationEntry(FSConstants.SVN_INVALID_REVNUM, null));
    }
    	
    private FSNodeHistory historyPrev(File reposRootDir, boolean crossCopies, FSRevisionNodePool revNodesPool) throws SVNException {
    	String path = historyEntry.getPath();
    	long revision = historyEntry.getRevision();
    	boolean reported = isInteresting;
    	
    	//If our last history report left us hints about where to pickup
        //the chase, then our last report was on the destination of a
        //copy.  If we are crossing copies, start from those locations,
        //otherwise, we're all done here
    	if(searchResumeEntry != null && searchResumeEntry.getPath() != null && FSRepository.isValidRevision(searchResumeEntry.getRevision())){
    		reported = false;
    		if(!crossCopies){
    			return null;
    		}
    		path = searchResumeEntry.getPath();
    		revision = searchResumeEntry.getRevision();
    	}
    	//Construct a ROOT for the current revision
    	//FSRevisionNode root = revNodesPool.getRootRevisionNode(revision, reposRootDir);
    	FSRoot root = FSRoot.createRevisionRoot(revision, revNodesPool.getRootRevisionNode(revision, reposRootDir));
        //Open path/revision and get all necessary info: node-id, ...
    	FSParentPath parentPath = revNodesPool.getParentPath(root, path, true, reposRootDir);
    	FSRevisionNode revNode = parentPath.getRevNode();
        SVNLocationEntry commitEntry = new SVNLocationEntry(revNode.getId().getRevision(), revNode.getCreatedPath()); 
        /* The Subversion filesystem is written in such a way that a given
         * line of history may have at most one interesting history point
         * per filesystem revision.  Either that node was edited (and
         * possibly copied), or it was copied but not edited.  And a copy
         * source cannot be from the same revision as its destination.  So,
         * if our history revision matches its node's commit revision, we
         * know that ... 
         */
    	FSNodeHistory prevHist = null;
    	if(revision == commitEntry.getRevision()){
            /* ... we either have not yet reported on this revision (and
             * need now to do so) ... 
             */
            if(!reported){
    			prevHist = new FSNodeHistory(commitEntry, true, new SVNLocationEntry(FSConstants.SVN_INVALID_REVNUM, null));
    			return prevHist;
    		}
            /* ... or we *have* reported on this revision, and must now
             * progress toward this node's predecessor (unless there is
             * no predecessor, in which case we're all done!). 
             */
            FSID predId = revNode.getPredecessorId();
            if(predId == null){
                return prevHist;
            }
            //Replace NODE and friends with the information from its predecessor
            revNode = FSReader.getRevNodeFromID(reposRootDir, predId);
            commitEntry = new SVNLocationEntry(revNode.getId().getRevision(), revNode.getCreatedPath());
    	}
		//Find the youngest copyroot in the path of this node, including itself
    	SVNLocationEntry copyrootEntry = findYoungestCopyroot(reposRootDir, parentPath);
		SVNLocationEntry srcEntry = new SVNLocationEntry(FSConstants.SVN_INVALID_REVNUM, null);
		long dstRev = FSConstants.SVN_INVALID_REVNUM;
		if(copyrootEntry.getRevision() > commitEntry.getRevision()){
            FSRevisionNode copyrootRoot = revNodesPool.getRootRevisionNode(copyrootEntry.getRevision(), reposRootDir);
            revNode = revNodesPool.getRevisionNode(copyrootRoot, copyrootEntry.getPath(), reposRootDir);
            String copyDst = revNode.getCreatedPath();
            /* If our current path was the very destination of the copy,
	         * then our new current path will be the copy source.  If our
	         * current path was instead the *child* of the destination of
	         * the copy, then figure out its previous location by taking its
	         * path relative to the copy destination and appending that to
	         * the copy source.  Finally, if our current path doesn't meet
	         * one of these other criteria ... ### for now just fallback to
	         * the old copy hunt algorithm. 
             */			
			String reminder = null;
			if(path.equals(copyDst)){
				reminder = "";
			}else{
				reminder = SVNPathUtil.pathIsChild(copyDst, path);
			}
			if(reminder != null){
                /* If we get here, then our current path is the destination 
	             * of, or the child of the destination of, a copy.  Fill
	             * in the return values and get outta here.  
                 */
				String copySrc = revNode.getCopyFromPath();
				srcEntry = new SVNLocationEntry(revNode.getCopyFromRevision(), SVNPathUtil.concatToAbs(copySrc, reminder));
				dstRev = copyrootEntry.getRevision();
			}
		}
		//If we calculated a copy source path and revision, we'll make a
	    //'copy-style' history object.
		if(srcEntry.getPath() != null && FSRepository.isValidRevision(srcEntry.getRevision())){
            /* It's possible for us to find a copy location that is the same
	         * as the history point we've just reported.  If that happens,
  	         * we simply need to take another trip through this history
	         * search 
	         */
			boolean retry = false;
			if((dstRev == revision) && reported){
				retry = true;
			}
			return new FSNodeHistory(new SVNLocationEntry(dstRev, path), retry ? false : true, new SVNLocationEntry(srcEntry.getRevision(), srcEntry.getPath()));
		}
        return new FSNodeHistory(commitEntry, true, new SVNLocationEntry(FSConstants.SVN_INVALID_REVNUM, null));
	}
    
    public FSNodeHistory fsHistoryPrev(File reposRootDir, boolean crossCopies, FSRevisionNodePool revNodesPool)throws SVNException{    
        /* Special case: the root directory changes in every single
         * revision, no exceptions.  And, the root can't be the target (or
         * child of a target -- duh) of a copy.  So, if that's our path,
         * then we need only decrement our revision by 1, and there you go. 
         */
    	if("/".equals(historyEntry.getPath())){
    		if(!isInteresting){
    			return new FSNodeHistory(new SVNLocationEntry(historyEntry.getRevision(), "/"), true,
    					new SVNLocationEntry(FSConstants.SVN_INVALID_REVNUM, null));
    		}else if(historyEntry.getRevision() > 0){
    			return new FSNodeHistory(new SVNLocationEntry(historyEntry.getRevision() - 1, "/"), true,
    					new SVNLocationEntry(FSConstants.SVN_INVALID_REVNUM, null));    			
    		}
    	}else{
            /* Get a trail, and get to work. */
            FSNodeHistory prevHist = this;
    		while(true){
    		    prevHist = prevHist.historyPrev(reposRootDir, crossCopies, revNodesPool);
    			if(prevHist == null){
    				return null;
    			}
    			if(prevHist.isInteresting){
    				return prevHist;
    			}
    		}
    	}
    	return null;
    }
}

