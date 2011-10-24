package org.tmatesoft.svn.core.wc2;

public class SvnCopy extends SvnOperation<Long> {
    
    private SvnTarget source;
    private SvnTarget target;
    private boolean isMove;

    protected SvnCopy(SvnOperationFactory factory) {
        super(factory);
    }

    public SvnTarget getSource() {
        return source;
    }

    public SvnTarget getTarget() {
        return target;
    }

    public boolean isMove() {
        return isMove;
    }

    public void setSource(SvnTarget source) {
        this.source = source;
    }

    public void setTarget(SvnTarget target) {
        this.target = target;
    }

    public void setMove(boolean isMove) {
        this.isMove = isMove;
    }

}
