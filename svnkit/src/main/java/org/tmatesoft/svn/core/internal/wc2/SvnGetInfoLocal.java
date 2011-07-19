package org.tmatesoft.svn.core.internal.wc2;

import java.io.File;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.wc17.SVNWCContext.ISVNWCNodeHandler;
import org.tmatesoft.svn.core.wc2.SvnOperation;

public class SvnGetInfoLocal extends SvnLocalOperationRunner implements ISVNWCNodeHandler {
    
    public SvnGetInfoLocal() {
        super(SvnWcGeneration.V17);
    }
    
    public void run(SvnOperation operation) throws SVNException {
        
    }

    public void nodeFound(File localAbspath) throws SVNException {
    }

}
