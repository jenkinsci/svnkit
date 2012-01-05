package org.tmatesoft.svn.core.wc2;

import java.io.OutputStream;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.wc.SVNRevision;

public class SvnCat extends SvnOperation<Void> {

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

        //here we assume we have one target

        SVNRevision resolvedPegRevision;
        SVNRevision resolvedRevision;

        if (getFirstTarget().getPegRevision() == SVNRevision.UNDEFINED) {
            resolvedPegRevision = getFirstTarget().getResolvedPegRevision(SVNRevision.HEAD, SVNRevision.WORKING);
            if (getRevision() == null || getRevision() == SVNRevision.UNDEFINED) {
                resolvedRevision = getFirstTarget().isURL() ? SVNRevision.HEAD : SVNRevision.BASE;
            } else {
                resolvedRevision = getRevision();
            }
        } else {
            resolvedPegRevision = getFirstTarget().getPegRevision();
            if (getRevision() == null || getRevision() == SVNRevision.UNDEFINED) {
                resolvedRevision = resolvedPegRevision;
            } else {
                resolvedRevision = getRevision();
            }
        }

        setRevision(resolvedRevision);
        setSingleTarget(
                getFirstTarget().isURL() ?
                        SvnTarget.fromURL(getFirstTarget().getURL(), resolvedPegRevision) :
                        SvnTarget.fromFile(getFirstTarget().getFile(), resolvedPegRevision));
    }
}
