package org.tmatesoft.svn.core.wc2;

import java.io.File;

import org.tmatesoft.svn.core.wc.SVNRevision;

public class SvnDiffSummarize extends SvnReceivingOperation<SvnDiffStatus> {

    private SvnTarget firstSource;
    private SvnTarget secondSource;
    
    private SvnTarget source;
    private SVNRevision startRevision;
    private SVNRevision endRevision;
    
    private boolean ignoreAncestry;

    protected SvnDiffSummarize(SvnOperationFactory factory) {
        super(factory);
    }
    public void setSource(SvnTarget source, SVNRevision start, SVNRevision end) {
        this.source = source;
        this.startRevision = start;
        this.endRevision = end;
        if (source != null) {
            setSources(null, null);
        }
    }
    
    public void setSources(SvnTarget source1, SvnTarget source2) {
        this.firstSource = source1;
        this.secondSource = source2;
        if (firstSource != null) {
            setSource(null, null, null);
        }
    }
    
    public SvnTarget getSource() {
        return source;
    }
    
    public SVNRevision getStartRevision() {
        return startRevision;
    }
    
    public SVNRevision getEndRevision() {
        return endRevision;
    }
    
    public SvnTarget getFirstSource() {
        return firstSource;
    }

    public SvnTarget getSecondSource() {
        return secondSource;
    }
    public boolean isIgnoreAncestry() {
        return ignoreAncestry;
    }
    public void setIgnoreAncestry(boolean ignoreAncestry) {
        this.ignoreAncestry = ignoreAncestry;
    }

    @Override
    protected File getOperationalWorkingCopy() {
        if (getSource() != null && getSource().isFile()) {
            return getSource().getFile();
        } else if (getFirstSource() != null && getFirstSource().isFile()) {
            return getFirstSource().getFile();
        } else if (getSecondSource() != null && getSecondSource().isFile()) {
            return getSecondSource().getFile();
        }
        
        return null;
    }

}
