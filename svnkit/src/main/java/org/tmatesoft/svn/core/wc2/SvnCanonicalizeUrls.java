package org.tmatesoft.svn.core.wc2;

public class SvnCanonicalizeUrls extends SvnOperation<Void> {

    private boolean omitDefaultPort;
    private boolean ignoreExternals;
    
    protected SvnCanonicalizeUrls(SvnOperationFactory factory) {
        super(factory);
    }

    public boolean isOmitDefaultPort() {
        return omitDefaultPort;
    }

    public void setOmitDefaultPort(boolean omitDefaultPort) {
        this.omitDefaultPort = omitDefaultPort;
    }

    public boolean isIgnoreExternals() {
        return ignoreExternals;
    }
    
    public void setIgnoreExternals(boolean ignoreExternals) {
        this.ignoreExternals = ignoreExternals;
    }

}
