package org.tmatesoft.svn.core.wc2;

import java.io.OutputStream;

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

}
