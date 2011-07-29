package org.tmatesoft.svn.core.internal.wc2;

import org.tmatesoft.svn.core.SVNCancelException;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.wc17.SVNWCContext;
import org.tmatesoft.svn.core.wc.ISVNEventHandler;
import org.tmatesoft.svn.core.wc.SVNEvent;
import org.tmatesoft.svn.core.wc2.ISvnOperationRunner;
import org.tmatesoft.svn.core.wc2.SvnOperation;


public abstract class SvnOperationRunner<T extends SvnOperation> implements ISvnOperationRunner<T>, ISVNEventHandler {
    private T operation;
    private SvnWcGeneration wcGeneration;
    private SVNWCContext wcContext;
    
    public void run(T operation) throws SVNException {
        setOperation(operation);
        run();
    }
    
    public void reset() {
        setOperation(null);
        setWcGeneration(null);
        setWcContext(null);
    }
    
    public void setWcContext(SVNWCContext context) {
        this.wcContext = context;
    }
    
    protected SVNWCContext getWcContext() {
        return this.wcContext;
    }

    public void setWcGeneration(SvnWcGeneration wcGeneration) {
        this.wcGeneration = wcGeneration;
    }
    
    public SvnWcGeneration getWcGeneration() {
        return this.wcGeneration;
    }

    protected abstract void run() throws SVNException;

    protected void setOperation(T operation) {
        this.operation = operation;
    }

    protected T getOperation() {
        return this.operation;
    }
    
    public void checkCancelled() throws SVNCancelException {
        if (getOperation() != null && getOperation().getCanceller() != null) {
            getOperation().getCanceller().checkCancelled();
        }
    }
    
    public void handleEvent(SVNEvent event, double progress) throws SVNException {
        if (getOperation() != null && getOperation().getEventHandler() != null) {
            getOperation().getEventHandler().handleEvent(event, progress);
        }
    }
}