package org.tmatesoft.svn.core.wc2;

import java.io.File;

public class SvnImport extends AbstractSvnCommit {

    private boolean applyAutoProperties;
    private boolean useGlobalIgnores;
    private boolean force;
    private boolean includeIgnored;
    
    private File source;
    
    public boolean isApplyAutoProperties() {
        return applyAutoProperties;
    }

    public void setApplyAutoProperties(boolean applyAutoProperties) {
        this.applyAutoProperties = applyAutoProperties;
    }

    public File getSource() {
        return source;
    }

    public void setSource(File source) {
        this.source = source;
    }

    public boolean isForce() {
        return force;
    }

    public void setForce(boolean force) {
        this.force = force;
    }

    public boolean isIncludeIgnored() {
        return includeIgnored;
    }

    public void setIncludeIgnored(boolean includeIgnored) {
        this.includeIgnored = includeIgnored;
    }

    protected SvnImport(SvnOperationFactory factory) {
        super(factory);
    }

    public boolean isUseGlobalIgnores() {
        return useGlobalIgnores;
    }

    public void setUseGlobalIgnores(boolean useGlobalIgnores) {
        this.useGlobalIgnores = useGlobalIgnores;
    }

}
