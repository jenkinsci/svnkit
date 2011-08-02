package org.tmatesoft.svn.core.wc2;

import org.tmatesoft.svn.core.SVNURL;

public class SvnRelocate extends SvnOperation<SVNURL> {
    
    private SVNURL fromUrl;
    private SVNURL toUrl;
    private boolean ignoreExternals;
    private boolean recursive;

    protected SvnRelocate(SvnOperationFactory factory) {
        super(factory);
    }

    public SVNURL getFromUrl() {
        return fromUrl;
    }

    public SVNURL getToUrl() {
        return toUrl;
    }

    public boolean isIgnoreExternals() {
        return ignoreExternals;
    }

    public void setFromUrl(SVNURL fromUrl) {
        this.fromUrl = fromUrl;
    }

    public void setToUrl(SVNURL toUrl) {
        this.toUrl = toUrl;
    }

    public void setIgnoreExternals(boolean ignoreExternals) {
        this.ignoreExternals = ignoreExternals;
    }

    /**
     * Only relevant for 1.6 working copies.
     */
    public void setRecursive(boolean recursive) {
        this.recursive = recursive;
    }
    
    public boolean isRecursive() {
        return this.recursive;
    }

    @Override
    protected void initDefaults() {
        super.initDefaults();
        setRecursive(true);
    }   
}
