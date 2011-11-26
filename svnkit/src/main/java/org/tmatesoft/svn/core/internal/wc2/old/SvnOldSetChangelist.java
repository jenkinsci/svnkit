package org.tmatesoft.svn.core.internal.wc2.old;

import java.io.File;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.wc16.SVNChangelistClient16;
import org.tmatesoft.svn.core.internal.wc16.SVNWCClient16;
import org.tmatesoft.svn.core.wc.SVNWCClient;
import org.tmatesoft.svn.core.wc2.SvnScheduleForAddition;
import org.tmatesoft.svn.core.wc2.SvnSetChangelist;
import org.tmatesoft.svn.core.wc2.SvnTarget;

public class SvnOldSetChangelist extends SvnOldRunner<Long, SvnSetChangelist> {

    @Override
    protected Long run() throws SVNException {
        
    	SVNChangelistClient16 client = new SVNChangelistClient16(getOperation().getRepositoryPool(), getOperation().getOptions());
        client.setEventHandler(getOperation().getEventHandler());
        
        File[] paths = new File[getOperation().getTargets().size()];
        int i = 0;
        for (SvnTarget target : getOperation().getTargets()) {
            paths[i++] = target.getFile();
        }
        
        if (getOperation().isRemove()) {
        	client.doRemoveFromChangelist(paths, getOperation().getDepth(), getOperation().getChangelists());

        } else {
        	client.doAddToChangelist(paths, 
        			getOperation().getDepth(), 
                    getOperation().getChangelistName(), 
                    getOperation().getChangelists());

        }
        
        return Long.decode("1");
    }

}
