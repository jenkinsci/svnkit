package org.tmatesoft.svn.core.wc2;

import org.tmatesoft.svn.core.SVNURL;

public class SvnRemoteCopy extends AbstractSvnCommit {
    
    private boolean move;
    private boolean makeParents;
    private SvnTarget source;
    private SVNURL targetUrl;

    protected SvnRemoteCopy(SvnOperationFactory factory) {
        super(factory);
    }

    public boolean isMove() {
        return move;
    }

    public void setMove(boolean move) {
        this.move = move;
    }

    public boolean isMakeParents() {
        return makeParents;
    }

    public void setMakeParents(boolean makeParents) {
        this.makeParents = makeParents;
    }

    public SvnTarget getSource() {
        return source;
    }

    public void setSource(SvnTarget source) {
        this.source = source;
    }

    public SVNURL getTargetUrl() {
        return targetUrl;
    }

    public void setTargetUrl(SVNURL targetUrl) {
        this.targetUrl = targetUrl;
    }
}