package org.tmatesoft.svn.core.wc2;

import java.util.Collection;

import org.tmatesoft.svn.core.SVNException;

public class SvnReceivingOperation<T> extends SvnOperation<T> implements ISvnObjectReceiver<T> {

    private ISvnObjectReceiver<T> receiver;
    private T first;
    private T last;
    private Collection<T> receivedObjects;
    
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
        last = object;
        
        if (getReceivedObjects() != null) {
            getReceivedObjects().add(object);
        }
        if (getReceiver() != null) {
            getReceiver().receive(target, object);
        }
    }
    
    public T first() {
        return this.first;
    }
    
    public T last() {
        return this.last;
    }
    
    public void setReceivingContainer(Collection<T> receivingContainer) {
        this.receivedObjects = receivingContainer;
    }
    
    public Collection<T> getReceivedObjects() {
        return this.receivedObjects;
    }

    @Override
    protected void initDefaults() {
        super.initDefaults();
        this.first = null;
    }

}
