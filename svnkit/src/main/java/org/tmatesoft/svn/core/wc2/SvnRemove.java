package org.tmatesoft.svn.core.wc2;

public class SvnRemove extends SvnOperation<SvnRemove> {
    
    private boolean force;
    private boolean dryRun;
    private boolean deleteFiles;

    protected SvnRemove(SvnOperationFactory factory) {
        super(factory);
    }

    public boolean isForce() {
        return force;
    }

    public void setForce(boolean force) {
        this.force = force;
    }

    public boolean isDryRun() {
        return dryRun;
    }

    public void setDryRun(boolean dryRun) {
        this.dryRun = dryRun;
    }

    public void setDeleteFiles(boolean deleteFiles) {
        this.deleteFiles = deleteFiles;
    }

    public boolean isDeleteFiles() {
        return deleteFiles;
    }

    @Override
    protected void initDefaults() {
        super.initDefaults();
        setDeleteFiles(true);
    }
    
    

}
