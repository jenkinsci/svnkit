package org.tmatesoft.svn.core.wc2;

import org.tmatesoft.svn.core.SVNException;

public class SvnReceivingOperation<T> extends SvnOperation<T> implements ISvnObjectReceiver<T> {

    private ISvnObjectReceiver<T> receiver;
    private T first;
    
    protected SvnReceivingOperation(SvnOperationFactory factory) {
        super(factory);
    }
    
    public void setReceiver(ISvnObjectReceiver<T> receiver) {
        this.receiver = receiver;
    }
    
    public ISvnObjectReceiver<T> getReceiver() {
        return this.receiver;
    }

    public void receive(SvnTarget target, T object) throws SVNException {
        if (first == null) {
            first = object;
        }
        if (getReceiver() != null) {
            getReceiver().receive(target, object);
        }
    }
    
    public T first() {
        return this.first;
    }

    @Override
    public void initDefaults() {
        super.initDefaults();
        this.first = null;
    }

}
