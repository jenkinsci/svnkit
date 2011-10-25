package org.tmatesoft.svn.core.internal.wc2.remote;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import org.tmatesoft.svn.core.ISVNLogEntryHandler;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNLogEntry;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.wc17.db.Structure;
import org.tmatesoft.svn.core.internal.wc2.SvnRemoteOperationRunner;
import org.tmatesoft.svn.core.internal.wc2.SvnWcGeneration;
import org.tmatesoft.svn.core.internal.wc2.SvnRepositoryAccess.RepositoryInfo;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNRevisionRange;
import org.tmatesoft.svn.core.wc2.SvnCat;
import org.tmatesoft.svn.core.wc2.SvnLog;
import org.tmatesoft.svn.util.SVNLogType;

public class SvnRemoteLog extends SvnRemoteOperationRunner<SVNLogEntry, SvnLog> implements ISVNLogEntryHandler {

	 public boolean isApplicable(SvnCat operation, SvnWcGeneration wcGeneration) throws SVNException {
		 return true;
	 }
	 
    @Override
    protected SVNLogEntry run() throws SVNException {
    	 
    	SVNRevision sessionRevision = SVNRevision.UNDEFINED;
        List<SVNRevisionRange> editedRevisionRanges = new LinkedList<SVNRevisionRange>();
        for (Iterator<SVNRevisionRange> revRangesIter = getOperation().getRevisionRanges().iterator(); revRangesIter.hasNext();) {
            SVNRevisionRange revRange = (SVNRevisionRange) revRangesIter.next();
        	if (revRange.getStartRevision().isValid() && !revRange.getEndRevision().isValid()) {
                revRange = new SVNRevisionRange(revRange.getStartRevision(), revRange.getStartRevision());
            } else if (!revRange.getStartRevision().isValid()) {
                SVNRevision start = SVNRevision.UNDEFINED;
                SVNRevision end = SVNRevision.UNDEFINED;
                if (!getOperation().getPegRevision().isValid()) {
                    start = SVNRevision.HEAD;
                } else {
                    start = getOperation().getPegRevision();
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
            if (isRevisionLocalToWc(revRange.getStartRevision()) || isRevisionLocalToWc(revRange.getEndRevision())) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CLIENT_BAD_REVISION, "Revision type requires a working copy path, not a URL");
                SVNErrorManager.error(err, SVNLogType.WC);
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
        if (isRevisionLocalToWc(getOperation().getPegRevision())) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CLIENT_BAD_REVISION, "Revision type requires a working copy path, not a URL");
            SVNErrorManager.error(err, SVNLogType.WC);
        }
        
        SVNRepository repository;
        if (sessionRevision.isValid()) {
        
        	Structure<RepositoryInfo> repositoryInfo = 
                    getRepositoryAccess().createRepositoryFor(
                            getOperation().getFirstTarget(), 
                            sessionRevision, 
                            getOperation().getPegRevision(), 
                            null);
        	repository = repositoryInfo.<SVNRepository>get(RepositoryInfo.repository);
            repositoryInfo.release();
        }
        else {
        	repository = getRepositoryAccess().createRepository(getOperation().getFirstTarget().getURL(), null, true);
        }
        
        for (Iterator<SVNRevisionRange> revRangesIter = editedRevisionRanges.iterator(); revRangesIter.hasNext();) {
        	getOperation().getEventHandler().checkCancelled();
            SVNRevisionRange revRange = (SVNRevisionRange) revRangesIter.next();
            getOperation().getEventHandler().checkCancelled();
            
            
            
            Structure<RepositoryInfo> repositoryInfo = 
                    getRepositoryAccess().createRepositoryFor(
                            getOperation().getFirstTarget(), 
                            revRange.getStartRevision(), 
                            getOperation().getPegRevision(), 
                            null);
            long startRev = repositoryInfo.lng(RepositoryInfo.revision);
            repositoryInfo.release();
            
            repositoryInfo = 
                    getRepositoryAccess().createRepositoryFor(
                            getOperation().getFirstTarget(), 
                            revRange.getEndRevision(), 
                            getOperation().getPegRevision(), 
                            null);
            
            long endRev = repositoryInfo.lng(RepositoryInfo.revision);
            repositoryInfo.release();
            
            repository.log(
            		getOperation().getTargetPaths(), 
            		startRev, 
            		endRev,
            		getOperation().isDiscoverChangedPaths(), 
            		getOperation().isStopOnCopy(), 
            		getOperation().getLimit(), 
            		getOperation().isUseMergeHistory(), 
            		getOperation().getRevisionProperties(), 
            		this);
        }
        
    
    	return null;
    }
    
    public void handleLogEntry(SVNLogEntry logEntry) throws SVNException {
		getOperation().getEventHandler().checkCancelled();
		getOperation().receive(null, logEntry);
	}
    
    
 }