package org.tmatesoft.svn.core.wc2;

public class SvnReceivingOperation<T extends SvnObject> extends SvnOperation {

    private ISvnObjectReceiver<T> receiver;
    
    protected SvnReceivingOperation(SvnOperationFactory factory) {
        super(factory);
    }
    
    public void setReceiver(ISvnObjectReceiver<T> receiver) {
        this.receiver = receiver;
    }
    
    public ISvnObjectReceiver<T> getReceiver() {
        return this.receiver;
    }

}
