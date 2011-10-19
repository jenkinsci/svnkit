package org.tmatesoft.svn.core.wc2;

import java.io.OutputStream;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.wc.SVNRevision;

public class SvnCat extends SvnOperation<Long> {

    private boolean expandKeywords;
    private OutputStream output;

    protected SvnCat(SvnOperationFactory factory) {
        super(factory);
    }
    
    public boolean isExpandKeywords() {
        return expandKeywords;
    }

    public void setExpandKeywords(boolean expandKeywords) {
        this.expandKeywords = expandKeywords;
    }

    public OutputStream getOutput() {
        return output;
    }

    public void setOutput(OutputStream output) {
        this.output = output;
    }

    @Override
    protected void ensureArgumentsAreValid() throws SVNException {
        super.ensureArgumentsAreValid();
        if (getPegRevision() == SVNRevision.UNDEFINED) {
            setPegRevision(hasRemoteTargets() ? SVNRevision.HEAD : SVNRevision.WORKING);
            if (getRevision() == SVNRevision.UNDEFINED) {
                setRevision(hasRemoteTargets() ? SVNRevision.HEAD : SVNRevision.BASE);
            }
        } else {
            if (getRevision() == SVNRevision.UNDEFINED) {
                setRevision(getPegRevision());
            }
        }
    }

    
}
