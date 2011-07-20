package org.tmatesoft.svn.core.internal.wc2.ng;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.wc17.SVNWCContext;
import org.tmatesoft.svn.core.internal.wc2.SvnLocalOperationRunner;
import org.tmatesoft.svn.core.internal.wc2.SvnWcGeneration;
import org.tmatesoft.svn.core.wc2.SvnOperation;

public abstract class SvnNgOperationRunner<T extends SvnOperation> extends SvnLocalOperationRunner<T> {
    
    private SVNWCContext context;
    
    protected SvnNgOperationRunner() {
        super(SvnWcGeneration.V17);
    }

    public void run(T operation) throws SVNException {
        super.run(operation);
        try {
            setContext(createWCContext());
            run(getContext());
        } finally {
            if (getContext() != null) {
                getContext().close();
            }
        }
    }
    
    protected SVNWCContext getContext() {
        return context;
    }

    private void setContext(SVNWCContext context) {
        this.context = context;
    }

    protected abstract void run(SVNWCContext context) throws SVNException;

    private SVNWCContext createWCContext() {
        return null;
    }

}
