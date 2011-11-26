package org.tmatesoft.svn.core.wc2;

import org.tmatesoft.svn.core.wc.ISVNAnnotateHandler;
import org.tmatesoft.svn.core.wc.SVNDiffOptions;
import org.tmatesoft.svn.core.wc.SVNRevision;

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
    
    

}
