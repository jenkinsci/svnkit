package org.tmatesoft.svn.core.wc2;

import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.wc.ISVNAnnotateHandler;
import org.tmatesoft.svn.core.wc.SVNDiffOptions;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.util.SVNLogType;

public class SvnAnnotate extends SvnReceivingOperation<SvnAnnotateItem> {
    
    private boolean useMergeHistory;
    private boolean force;
    private boolean ignoreMimeType;
    private ISVNAnnotateHandler handler;
    private SVNRevision startRevision;
    private SVNRevision endRevision;
    private String inputEncoding;
    private SVNDiffOptions diffOptions;
    
    protected SvnAnnotate(SvnOperationFactory factory) {
        super(factory);
    }
    
    public ISVNAnnotateHandler getHandler() {
        return handler;
    }

    public void setHandler(ISVNAnnotateHandler handler) {
        this.handler = handler;
    }

    public boolean isUseMergeHistory() {
        return useMergeHistory;
    }

    public void setUseMergeHistory(boolean useMergeHistory) {
        this.useMergeHistory = useMergeHistory;
    }

    public boolean isForce() {
        return force;
    }

    public void setForce(boolean force) {
        this.force = force;
    }
    
    public boolean isIgnoreMimeType() {
        return ignoreMimeType;
    }

    public void setIgnoreMimeType(boolean ignoreMimeType) {
        this.ignoreMimeType = ignoreMimeType;
    }
    
    public SVNRevision getStartRevision() {
        return startRevision;
    }

    public void setStartRevision(SVNRevision startRevision) {
        this.startRevision = startRevision;
    }
    
    public SVNRevision getEndRevision() {
        return endRevision;
    }

    public void setEndRevision(SVNRevision endRevision) {
        this.endRevision = endRevision;
    }
    
    public String getInputEncoding() {
        return inputEncoding;
    }

    public void setInputEncoding(String inputEncoding) {
        this.inputEncoding = inputEncoding;
    }
    
    public SVNDiffOptions getDiffOptions() {
        return diffOptions;
    }
    
    public void setDiffOptions(SVNDiffOptions diffOptions) {
    	this.diffOptions = diffOptions;
    }
    
    @Override
    protected void ensureArgumentsAreValid() throws SVNException {
        super.ensureArgumentsAreValid();
        
        /*
        if (startRevision == null || !startRevision.isValid() || endRevision == null || !endRevision.isValid() ) {
        	SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CLIENT_BAD_REVISION);
            SVNErrorManager.error(err, SVNLogType.WC);
        }
        */
        
        if (startRevision == null || !startRevision.isValid()) {
            startRevision = SVNRevision.create(1);
        }
        if (endRevision == null || !endRevision.isValid()) {
            endRevision = getFirstTarget().getPegRevision();
        }
        
        
        if (startRevision == SVNRevision.WORKING || endRevision == SVNRevision.WORKING) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNSUPPORTED_FEATURE, "Blame of the WORKING revision is not supported");
            SVNErrorManager.error(err, SVNLogType.WC);
        }
    }

}
