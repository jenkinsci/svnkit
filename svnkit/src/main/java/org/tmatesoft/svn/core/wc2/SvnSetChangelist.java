package org.tmatesoft.svn.core.wc2;

public class SvnSetChangelist extends SvnOperation<Long> {

    private String changelistName;
    private boolean remove;

    protected SvnSetChangelist(SvnOperationFactory factory) {
        super(factory);
    }
    
    public String getChangelistName() {
        return changelistName;
    }

    public void setChangelistName(String changelistName) {
        this.changelistName = changelistName;
    }

    public boolean isRemove() {
        return remove;
    }

    public void setRemove(boolean remove) {
        this.remove = remove;
    }

}
