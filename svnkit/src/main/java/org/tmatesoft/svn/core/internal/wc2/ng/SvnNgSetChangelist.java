package org.tmatesoft.svn.core.internal.wc2.ng;

import java.io.File;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.wc17.SVNWCContext;
import org.tmatesoft.svn.core.wc2.SvnSetChangelist;
import org.tmatesoft.svn.core.wc2.SvnTarget;


public class SvnNgSetChangelist extends SvnNgOperationRunner<Long, SvnSetChangelist> {

    @Override
    protected Long run(SVNWCContext context) throws SVNException {
        
    	for (SvnTarget target : getOperation().getTargets()) {
            checkCancelled();
            
            File path = target.getFile().getAbsoluteFile();
            
            context.getDb().opSetChangelist(path, getOperation().getChangelistName(), getOperation().getChangelists(), getOperation().getDepth(), this);
        }
        return null;
        
    }
    
    

}
