package org.tmatesoft.svn.core.internal.wc2.remote;

import org.tmatesoft.svn.core.ISVNDirEntryHandler;
import org.tmatesoft.svn.core.SVNDirEntry;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.wc2.SvnRemoteOperationRunner;
import org.tmatesoft.svn.core.internal.wc2.SvnWcGeneration;
import org.tmatesoft.svn.core.wc2.SvnList;

public class SvnRemoteList extends SvnRemoteOperationRunner<SVNDirEntry, SvnList>  implements ISVNDirEntryHandler {
	
	public boolean isApplicable(SvnList operation, SvnWcGeneration wcGeneration) throws SVNException {
		 return true;
	 }

    @Override
    protected SVNDirEntry run() throws SVNException {
    	
        
    	
    	
        return getOperation().first();
    }
    
    public void handleDirEntry(SVNDirEntry dirEntry) throws SVNException {
    	getOperation().receive(getOperation().getFirstTarget(), dirEntry);
    }
    
    
}
