package org.tmatesoft.svn.core.wc2;

public class SvnRemoteMkDir extends AbstractSvnCommit {

    private boolean makeParents;
    
    public boolean isMakeParents() {
        return makeParents;
    }

    public void setMakeParents(boolean makeParents) {
        this.makeParents = makeParents;
    }

    protected SvnRemoteMkDir(SvnOperationFactory factory) {
        super(factory);
    }

}
