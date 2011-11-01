package org.tmatesoft.svn.core.internal.wc2.remote;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.TreeSet;

import org.tmatesoft.svn.core.ISVNLogEntryHandler;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNLogEntry;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.util.SVNEncodingUtil;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.internal.util.SVNURLUtil;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.wc17.db.Structure;
import org.tmatesoft.svn.core.internal.wc2.SvnRemoteOperationRunner;
import org.tmatesoft.svn.core.internal.wc2.SvnRepositoryAccess;
import org.tmatesoft.svn.core.internal.wc2.SvnRepositoryAccess.RepositoryInfo;
import org.tmatesoft.svn.core.internal.wc2.SvnRepositoryAccess.RevisionsPair;
import org.tmatesoft.svn.core.internal.wc2.SvnWcGeneration;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNRevisionRange;
import org.tmatesoft.svn.core.wc2.SvnCat;
import org.tmatesoft.svn.core.wc2.SvnLog;
import org.tmatesoft.svn.core.wc2.SvnTarget;
import org.tmatesoft.svn.util.SVNLogType;

public class SvnRemoteLog extends SvnRemoteOperationRunner<SVNLogEntry, SvnLog> implements ISVNLogEntryHandler {

	 public boolean isApplicable(SvnCat operation, SvnWcGeneration wcGeneration) throws SVNException {
		 return true;
	 }
	 
    @Override
    protected SVNLogEntry run() throws SVNException {
    	
    	String[] targetPaths = null;
    	SVNRevision pegRevision = getOperation().getFirstTarget().getPegRevision();
    	SvnTarget baseTarget = getOperation().getFirstTarget();
    	 
    	SVNRevision sessionRevision = SVNRevision.UNDEFINED;
        List<SVNRevisionRange> editedRevisionRanges = new LinkedList<SVNRevisionRange>();
        
        for (Iterator<SVNRevisionRange> revRangesIter = getOperation().getRevisionRanges().iterator(); revRangesIter.hasNext();) {
            SVNRevisionRange revRange = (SVNRevisionRange) revRangesIter.next();
        	if (revRange.getStartRevision().isValid() && !revRange.getEndRevision().isValid()) {
                revRange = new SVNRevisionRange(revRange.getStartRevision(), revRange.getStartRevision());
            } else if (!revRange.getStartRevision().isValid()) {
                SVNRevision start = SVNRevision.UNDEFINED;
                SVNRevision end = SVNRevision.UNDEFINED;
                if (!getOperation().getFirstTarget().getPegRevision().isValid()) {
                	if (getOperation().hasRemoteTargets()) {
                		start = SVNRevision.BASE;
                	} else {
                		start = SVNRevision.HEAD;
                	}
                } else {
                    start = getOperation().getFirstTarget().getPegRevision();
                }
                
                if (!revRange.getEndRevision().isValid()) {
                    end = SVNRevision.create(0);
                }
                revRange = new SVNRevisionRange(start, end);
            }
            if (!revRange.getStartRevision().isValid() || !revRange.getEndRevision().isValid()) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CLIENT_BAD_REVISION, "Missing required revision specification");
                SVNErrorManager.error(err, SVNLogType.WC);
            }
            
            if (getOperation().hasRemoteTargets()) {
	            if (isRevisionLocalToWc(revRange.getStartRevision()) || isRevisionLocalToWc(revRange.getEndRevision())) {
	                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CLIENT_BAD_REVISION, "Revision type requires a working copy path, not a URL");
	                SVNErrorManager.error(err, SVNLogType.WC);
	            }
            }
            
            editedRevisionRanges.add(revRange);
            
            if (!sessionRevision.isValid()) {
                SVNRevision start = revRange.getStartRevision();
                SVNRevision end = revRange.getEndRevision();
                if (SVNRevision.isValidRevisionNumber(start.getNumber()) && SVNRevision.isValidRevisionNumber(end.getNumber())) {
                    sessionRevision = start.getNumber() > end.getNumber() ? start : end;
                } else if (start.getDate() != null && end.getDate() != null) {
                    sessionRevision = start.getDate().compareTo(end.getDate()) > 0 ? start : end;
                }
            }
        }
        
        if (getOperation().hasRemoteTargets()) {
        	if (!pegRevision.isValid()) {
        		pegRevision = SVNRevision.BASE;
        	}
        	if (getOperation().getTargetPaths() == null) {
        		targetPaths = new String[] {""};
        	} else {
        	    targetPaths = getOperation().getTargetPaths();
        	}
        } else {
        	if (!pegRevision.isValid()) {
        		pegRevision = SVNRevision.WORKING;
        	}
        	
        	SVNURL[] targetUrls = new SVNURL[getOperation().getTargets().size()];
            Collection<String> wcPaths = new ArrayList<String>();
            int i = 0;
            
            Structure<RepositoryInfo> repositoryInfo = 
                    getRepositoryAccess().createRepositoryFor(
                    		baseTarget, 
                            sessionRevision, 
                            pegRevision, 
                            null);
            SVNRepository repository = repositoryInfo.<SVNRepository>get(RepositoryInfo.repository);
            repositoryInfo.release();
            
            for (SvnTarget target : getOperation().getTargets()) {
            	checkCancelled();
                File path = target.getFile();
                wcPaths.add(path.getAbsolutePath().replace(File.separatorChar, '/'));
                
                Structure<SvnRepositoryAccess.UrlInfo> locationsInfo = getRepositoryAccess().getURLFromPath(target, target.getPegRevision(), repository);
                targetUrls[i++] = locationsInfo.<SVNURL>get(SvnRepositoryAccess.UrlInfo.url);
            }
            
            if (targetUrls.length == 0) {
            	return null;
            }
            
            Collection<String> targets = new TreeSet<String>();
            SVNURL baseURL = SVNURLUtil.condenceURLs(targetUrls, targets, true);
            if (baseURL == null) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.ILLEGAL_TARGET, "target log paths belong to different repositories");
                SVNErrorManager.error(err, SVNLogType.WC);
                return null;
            }
            if (targets.isEmpty()) {
                targets.add("");
            }
            
            targetPaths = (String[]) targets.toArray(new String[targets.size()]);
            for (i = 0; i < targetPaths.length; i++) {
                targetPaths[i] = SVNEncodingUtil.uriDecode(targetPaths[i]);
            }
            
            if (isRevisionLocalToWc(pegRevision)) {
            	String rootWCPath = SVNPathUtil.condencePaths((String[]) wcPaths.toArray(new String[wcPaths.size()]), null, true);
            	baseTarget = SvnTarget.fromFile(new File(rootWCPath), pegRevision);
            }
            
            
        }
        Structure<RepositoryInfo> repositoryInfo = 
                    getRepositoryAccess().createRepositoryFor(
                    		baseTarget, 
                            sessionRevision, 
                            pegRevision, 
                            null);
        SVNRepository repository = repositoryInfo.<SVNRepository>get(RepositoryInfo.repository);
        repositoryInfo.release();
        
        
        for (Iterator<SVNRevisionRange> revRangesIter = editedRevisionRanges.iterator(); revRangesIter.hasNext();) {
        	checkCancelled();
            SVNRevisionRange revRange = (SVNRevisionRange) revRangesIter.next();
            checkCancelled();
            
            Structure<RevisionsPair> pair = getRepositoryAccess().getRevisionNumber(repository, baseTarget, revRange.getStartRevision(), null);
            long startRev = pair.lng(RevisionsPair.revNumber);
            pair = getRepositoryAccess().getRevisionNumber(repository, baseTarget, revRange.getEndRevision(), pair);
            long endRev = pair.lng(RevisionsPair.revNumber);
            pair.release();
            
            repository.log(
            		targetPaths, 
            		startRev, 
            		endRev,
            		getOperation().isDiscoverChangedPaths(), 
            		getOperation().isStopOnCopy(), 
            		getOperation().getLimit(), 
            		getOperation().isUseMergeHistory(), 
            		getOperation().getRevisionProperties(), 
            		this);
        }
        
    
        return getOperation().first();
    }
    
    public void handleLogEntry(SVNLogEntry logEntry) throws SVNException {
    	checkCancelled();
		getOperation().receive(null, logEntry);
	}
    
 }