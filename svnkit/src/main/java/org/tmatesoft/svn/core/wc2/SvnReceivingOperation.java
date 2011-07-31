package org.tmatesoft.svn.core.wc2;

import java.util.Collection;

import org.tmatesoft.svn.core.SVNException;

public class SvnReceivingOperation<T> extends SvnOperation<T> implements ISvnObjectReceiver<T> {

    private ISvnObjectReceiver<T> receiver;
    private T first;
    private Collection<T> receivedItems;
    
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
        if (getReceivedItems() != null) {
            getReceivedItems().add(object);
        }
        if (getReceiver() != null) {
            getReceiver().receive(target, object);
        }
    }
    
    public T first() {
        return this.first;
    }
    
    public void setReceivingContainer(Collection<T> receivingContainer) {
        this.receivedItems = receivingContainer;
    }
    
    public Collection<T> getReceivedItems() {
        return this.receivedItems;
    }

    @Override
    protected void initDefaults() {
        super.initDefaults();
        this.first = null;
    }

}
