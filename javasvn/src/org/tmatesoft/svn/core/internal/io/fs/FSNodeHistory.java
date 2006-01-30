/*
 * Created on 12.11.2005
 *
 * TODO To change the template for this generated file go to
 * Window - Preferences - Java - Code Style - Code Templates
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
	public boolean getInterest(){
		return isInteresting;
	}
	public void setInterest(boolean someInterest){
		isInteresting = someInterest;
	}
	//methods connected to history entity
	
	//Find the youngest copyroot for path PARENT_PATH or its parents
	public static SVNLocationEntry findYoungestCopyroot(File reposRootDir, FSParentPath parPath)throws SVNException{		
		SVNLocationEntry parentEntry = null;		
		
		if(parPath.getParent() != null){
			parentEntry = FSNodeHistory.findYoungestCopyroot(reposRootDir, parPath.getParent());
		}
        SVNLocationEntry myEntry = new SVNLocationEntry(parPath.getRevNode().getCopyRootRevision(), parPath.getRevNode().getCopyRootPath()); 
		if(parentEntry != null){
			if(myEntry.getRevision() >= parentEntry.getRevision()){
				return myEntry;
			}
            return parentEntry;
		}
        return myEntry;
	}
	
    public static boolean checkAncestryOfPegPath(File reposRootDir, String fsPath, long pegRev, long futureRev, FSRevisionNodePool revNodesPool)throws SVNException{
        fsPath = fsPath == null ? new String("") : fsPath;
        long youngestRev = FSReader.getYoungestRevision(reposRootDir);
        if(FSRepository.isInvalidRevision(pegRev) || pegRev > youngestRev){
            return false;
        }
        if(FSRepository.isInvalidRevision(futureRev) || futureRev > youngestRev){
            return false;
        }
   		FSRevisionNode root = FSReader.getRootRevNode(reposRootDir, futureRev);
        FSNodeHistory history = null;
        history = getNodeHistory(reposRootDir, root, fsPath);
        fsPath = null;
   		SVNLocationEntry currentHistory = null;
   		while(true){  
   			history = history.fsHistoryPrev(reposRootDir, true, revNodesPool);    			
   			if(history == null){  
   				break;    			
   			}
   			currentHistory = new SVNLocationEntry(history.getHistoryEntry().getRevision(), history.getHistoryEntry().getPath()); 		
   			if(fsPath == null){
   				fsPath = new String(currentHistory.getPath());
   			}
   			if(currentHistory.getRevision() <= pegRev){
   				break;    				
   			}
   		}
   		return (history != null && (fsPath.compareTo(currentHistory.getPath()) == 0));
    }
    
    //Retrun FSNodeHistory as an opaque node history object which represents
    //PATH under ROOT. ROOT must be a revision root  
    /*TODO update method for using it with transaction*/
    public static FSNodeHistory getNodeHistory(File reposRootDir, FSRevisionNode root, String path) throws SVNException{
        FSRoot rootNode = FSRoot.createRevisionRoot(root.getId().getRevision(), root);
        if(rootNode.isTxnRoot()){
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_NOT_REVISION_ROOT);
            SVNErrorManager.error(err);
        }        
        /*And we require that the path exist in the root*/
    	SVNNodeKind kind = FSReader.checkNodeKind(path, rootNode.getRootRevisionNode(), reposRootDir);    	
    	if(kind == SVNNodeKind.NONE){
    		SVNErrorManager.error(FSErrors.errorNotFound(rootNode, path));
    	}    	
    	return new FSNodeHistory(new SVNLocationEntry(rootNode.getRootRevisionNode().getId().getRevision(), path), 
    			false, new SVNLocationEntry(FSConstants.SVN_INVALID_REVNUM, null));
    }
    	
    private FSNodeHistory historyPrev(File reposRootDir, /*FSNodeHistory hist,*/ boolean crossCopies, FSRevisionNodePool revNodesPool)throws SVNException{
    	String path = historyEntry.getPath();//String path = hist.getHistoryEntry().getPath();
    	SVNLocationEntry commitEntry;
    	long revision = historyEntry.getRevision();//long revision = hist.getHistoryEntry().getRevision();
    	boolean reported = isInteresting;//boolean reported = hist.getInterest();
    	
    	//If our last history report left us hints about where to pickup
        //the chase, then our last report was on the destination of a
        //copy.  If we are crossing copies, start from those locations,
        //otherwise, we're all done here
//    	if((hist.getSearchResumeEntry() != null) && (hist.getSearchResumeEntry().getPath() != null) && FSRepository.isValidRevision(hist.getSearchResumeEntry().getRevision()) ){
    	if(searchResumeEntry != null && FSRepository.isValidRevision(searchResumeEntry.getRevision())){
    		reported = false;
    		if(crossCopies == false){
    			return null;
    		}
    		path = searchResumeEntry.getPath();//path = hist.getSearchResumeEntry().getPath();
    		revision = searchResumeEntry.getRevision();//revision = hist.getSearchResumeEntry().getRevision();
    	}
    	//Construct a ROOT for the current revision
    	FSRevisionNode root = revNodesPool.getRootRevisionNode(revision, reposRootDir);/*FSReader.getRootRevNode(reposRootDir, revision);*/
    	//Open path/revision and get all necessary info: node-id, ...
    	FSParentPath parentPath = revNodesPool.getParentPath(FSRoot.createRevisionRoot(root.getId().getRevision(), root), path, true, reposRootDir);
    	FSRevisionNode revNode = parentPath.getRevNode();
    	commitEntry = new SVNLocationEntry(revNode.getId().getRevision(), revNode.getCreatedPath()); 
    	//The Subversion filesystem is written in such a way that a given
        //line of history may have at most one interesting history point
        //per filesystem revision.  Either that node was edited (and
        //possibly copied), or it was copied but not edited.  And a copy
        //source cannot be from the same revision as its destination.  So,
        //if our history revision matches its node's commit revision, we
        //know that ...
    	FSNodeHistory prevHist = null;
    	if(revision == commitEntry.getRevision()){
    		if(reported == false){
    			prevHist = new FSNodeHistory(new SVNLocationEntry(commitEntry.getRevision(), commitEntry.getPath()),
    					true, new SVNLocationEntry(FSConstants.SVN_INVALID_REVNUM, null));
    			return prevHist;
    		}
    	    //... or we *have* reported on this revision, and must now
            //progress toward this node's predecessor (unless there is
            //no predecessor, in which case we're all done!)
            FSID predId = revNode.getPredecessorId();
            if(predId == null || predId.getRevision() < 0 ){
                return prevHist;
            }
            //Replace NODE and friends with the information from its predecessor
            revNode = FSReader.getRevNodeFromID(reposRootDir, predId);
            commitEntry = new SVNLocationEntry(revNode.getId().getRevision(), revNode.getCreatedPath());
    	}
		//Find the youngest copyroot in the path of this node, including itself
    	SVNLocationEntry copyrootEntry = FSNodeHistory.findYoungestCopyroot(reposRootDir, parentPath);
		SVNLocationEntry srcEntry = new SVNLocationEntry(FSConstants.SVN_INVALID_REVNUM, null);
		long dstRev = FSConstants.SVN_INVALID_REVNUM;
		if(copyrootEntry.getRevision() > commitEntry.getRevision()){
            FSRevisionNode copyrootRoot = revNodesPool.getRootRevisionNode(copyrootEntry.getRevision(), reposRootDir);
//			FSRevisionNode copyrootRoot = FSReader.getRootRevNode(reposRootDir, copyrootEntry.getRevision());
            revNode = revNodesPool.getRevisionNode(copyrootRoot, copyrootEntry.getPath(), reposRootDir);
//			revNode = FSReader.getRevisionNode(reposRootDir, copyrootEntry.getPath(), copyrootRoot, 0);
            String copyDst = revNode.getCreatedPath();
          /* If our current path was the very destination of the copy,
	         then our new current path will be the copy source.  If our
	         current path was instead the *child* of the destination of
	         the copy, then figure out its previous location by taking its
	         path relative to the copy destination and appending that to
	         the copy source.  Finally, if our current path doesn't meet
	         one of these other criteria ... ### for now just fallback to
	         the old copy hunt algorithm. 
	       */			
			String reminder = new String();
			if(path.equals(copyDst)){
				reminder = "/";
			}else{
				reminder = SVNPathUtil.pathIsChild(copyDst, path);
			}
			if(reminder != null){
	          /* If we get here, then our current path is the destination 
	             of, or the child of the destination of, a copy.  Fill
	             in the return values and get outta here.  
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
	         as the history point we've just reported.  If that happens,
	         we simply need to take another trip through this history
	         search 
	      */
			boolean retry = false;
			if(dstRev == revision && reported){
				retry = true;
			}
			return new FSNodeHistory(new SVNLocationEntry(dstRev, path), retry ? false : true, new SVNLocationEntry(srcEntry.getRevision(), srcEntry.getPath()));
		}
        return new FSNodeHistory(commitEntry, true, new SVNLocationEntry(FSConstants.SVN_INVALID_REVNUM, null));
	}
    
    public FSNodeHistory fsHistoryPrev(File reposRootDir, boolean crossCopies, FSRevisionNodePool revNodesPool)throws SVNException{    
    	//if("/".compareTo(hist.getHistoryEntry().getPath()) == 0){
    	if("/".equals(historyEntry.getPath())){
    		//if(hist.getInterest() == false){
    		if(isInteresting == false){
    			return new FSNodeHistory(new SVNLocationEntry(/*hist.getHistoryEntry().getRevision()*/historyEntry.getRevision(), "/"), true,
    					new SVNLocationEntry(FSConstants.SVN_INVALID_REVNUM, null));
    		}else if(/*hist.getHistoryEntry().getRevision()*/historyEntry.getRevision() > 0){
    			return new FSNodeHistory(new SVNLocationEntry(/*hist.getHistoryEntry().getRevision()*/historyEntry.getRevision() - 1, "/"), true,
    					new SVNLocationEntry(FSConstants.SVN_INVALID_REVNUM, null));    			
    		}
    	}else{
    		FSNodeHistory prevHist = this;
    		while(true){
    		    prevHist = prevHist.historyPrev(reposRootDir, crossCopies, revNodesPool);
    			if(prevHist == null){
    				return null;
    			}
    			if(prevHist.getInterest() == true){
    				return prevHist;
    			}
    		}
    	}
    	return null;
    }
}

