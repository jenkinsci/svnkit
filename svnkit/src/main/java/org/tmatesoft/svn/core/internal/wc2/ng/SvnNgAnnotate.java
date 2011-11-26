
package org.tmatesoft.svn.core.internal.wc2.ng;

import java.io.File;
import java.util.Date;

import org.tmatesoft.svn.core.SVNAnnotationGenerator;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.internal.wc17.SVNWCContext;
import org.tmatesoft.svn.core.internal.wc17.db.Structure;
import org.tmatesoft.svn.core.internal.wc2.SvnRepositoryAccess.RepositoryInfo;
import org.tmatesoft.svn.core.internal.wc2.SvnRepositoryAccess.RevisionsPair;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.wc.ISVNAnnotateHandler;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc2.SvnAnnotate;
import org.tmatesoft.svn.core.wc2.SvnAnnotateItem;
import org.tmatesoft.svn.util.SVNLogType;

public class SvnNgAnnotate extends SvnNgOperationRunner<SvnAnnotateItem, SvnAnnotate> implements ISVNAnnotateHandler { 
	
	@Override
    protected SvnAnnotateItem run(SVNWCContext context) throws SVNException {
		
		if (getOperation().getStartRevision() == null || !getOperation().getStartRevision().isValid() ||
    			getOperation().getEndRevision() == null || !getOperation().getEndRevision().isValid()) {
    		SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CLIENT_BAD_REVISION);
            SVNErrorManager.error(err, SVNLogType.WC);
        }
		
		Structure<RepositoryInfo> repositoryInfo = getRepositoryAccess().createRepositoryFor(
				getOperation().getFirstTarget(), getOperation().getEndRevision(), getOperation().getFirstTarget().getPegRevision(),null);
    	
    	SVNRepository repository = repositoryInfo.<SVNRepository>get(RepositoryInfo.repository);
		repositoryInfo.release();
		
		Structure<RevisionsPair> pair = 
        		getRepositoryAccess().getRevisionNumber(repository, getOperation().getFirstTarget(), getOperation().getStartRevision(), null);
        long startRev = pair.lng(RevisionsPair.revNumber);
        pair = getRepositoryAccess().getRevisionNumber(repository, getOperation().getFirstTarget(), getOperation().getEndRevision(), pair);
        long endRev = pair.lng(RevisionsPair.revNumber);
        pair.release();
        
        if (endRev < startRev) {
            SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.CLIENT_BAD_REVISION, "Start revision must precede end revision"), SVNLogType.DEFAULT);
        }
        
        String path;
        File tmpFile;
    	if (getOperation().hasRemoteTargets()){
    		tmpFile = SVNFileUtil.createTempDirectory("annotate");
    		path = repository.getLocation().toDecodedString();
    	}
    	else {
    		tmpFile = new File(getOperation().getFirstTarget().getFile().getParentFile(), SVNFileUtil.getAdminDirectoryName());
            tmpFile = new File(tmpFile, "tmp/text-base");
            if (!tmpFile.isDirectory()) {
                tmpFile = SVNFileUtil.createTempDirectory("annotate");
            }
            path = getOperation().getFirstTarget().getFile().getAbsolutePath();
    		
    	}
    	
    	
    	SVNAnnotationGenerator generator = new SVNAnnotationGenerator(path, tmpFile, startRev, 
    			getOperation().isIgnoreMimeType(), getOperation().isUseMergeHistory(), getOperation().getDiffOptions(), getOperation().getInputEncoding(), this, this);
    	
       try {
    	   	repository.getFileRevisions("", startRev > 0 ? startRev - 1 : startRev, endRev, getOperation().isUseMergeHistory(), generator);
    	   	
    	   	if (!generator.isLastRevisionReported()) {
                generator.reportAnnotations(this, getOperation().getInputEncoding());
            }
            
        } finally {
            generator.dispose();
            SVNFileUtil.deleteAll(tmpFile, !"text-base".equals(tmpFile.getName()), null);
        }
       
        return getOperation().first();
    }
    
    public void handleLine(Date date, long revision, String author, String line, Date mergedDate, 
            long mergedRevision, String mergedAuthor, String mergedPath, int lineNumber) throws SVNException {
    	getOperation().receive(getOperation().getFirstTarget(), 
    			new SvnAnnotateItem(date, revision, author, line, mergedDate, mergedRevision, mergedAuthor, mergedPath, lineNumber)
    			);
    }
    
    public boolean handleRevision(Date date, long revision, String author, File contents) throws SVNException{
    	SvnAnnotateItem item = new SvnAnnotateItem(date, revision, author, contents);
    	getOperation().receive(getOperation().getFirstTarget(), item);
    	return item.getReturnResult();
    }
    
    public void handleLine(Date date, long revision, String author, String line) throws SVNException {
    }
    
    public void handleEOF() {
    	try {
			getOperation().receive(getOperation().getFirstTarget(), new SvnAnnotateItem(true));
		} catch (SVNException e) {
			
		}
    }
    
}
