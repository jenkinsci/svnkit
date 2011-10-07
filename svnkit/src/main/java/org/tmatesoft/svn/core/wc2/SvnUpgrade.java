package org.tmatesoft.svn.core.wc2;

import org.tmatesoft.svn.core.internal.wc2.SvnWcGeneration;

public class SvnUpgrade extends SvnOperation<SvnWcGeneration> {
    
    private SvnWcGeneration format;

    protected SvnUpgrade(SvnOperationFactory factory) {
        super(factory);
    }

    public SvnWcGeneration getFormat() {
        return format;
    }

    public void setFormat(SvnWcGeneration format) {
        this.format = format;
    }

}
