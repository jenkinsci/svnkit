package org.tmatesoft.svn.core.internal.wc2;

import java.util.Date;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.util.SVNDate;
import org.tmatesoft.svn.core.wc.SVNClientManager;
import org.tmatesoft.svn.core.wc2.ISvnOperationRunner;
import org.tmatesoft.svn.core.wc2.SvnOperation;


public abstract class SvnOperationRunner<T extends SvnOperation> implements ISvnOperationRunner<T> {
    private T operation;
    private SvnWcGeneration wcGeneration;

    public void run(T operation) throws SVNException {
        setOperation(operation);
        run();
    }
    
    public void reset() {
        setOperation(null);
        setWcGeneration(null);
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

    protected SVNClientManager getClientManager() {
        return getOperation().getOperationFactory().getClientManager();
    }
    
    protected SVNDate getSvnDate(Date d) {
        if (d == null) {
            return SVNDate.NULL;
        }
        if (d instanceof SVNDate) {
            return (SVNDate) d;
        }
        return new SVNDate(d.getTime(), 0);
    }

}
