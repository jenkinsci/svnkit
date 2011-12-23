package org.tmatesoft.svn.core.wc2;

import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNDirEntry;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.wc.SVNPropertyData;
import org.tmatesoft.svn.util.SVNLogType;

public class SvnList extends SvnReceivingOperation<SVNDirEntry> {
	
	private boolean fetchLocks;
	private SVNDepth depth;
	private int entryFields;
	private boolean recursive;
        
    protected SvnList(SvnOperationFactory factory) {
        super(factory);
    }
    
    public boolean isFetchLocks() {
        return fetchLocks;
    }

    public void setFetchLocks(boolean fetchLocks) {
        this.fetchLocks = fetchLocks;
    }
    
    public SVNDepth getDepth() {
        return depth;
    }

    public void setDepth(SVNDepth depth) {
        this.depth = depth;
    }
    
    public boolean isRecursive() {
        return recursive;
    }

    public void setRecursive(boolean recursive) {
        this.recursive = recursive;
    }
    
    public int getEntryFields() {
        return entryFields;
    }

    public void setEntryFields(int entryFields) {
        this.entryFields = entryFields;
    }

    @Override
    protected void ensureArgumentsAreValid() throws SVNException {
        super.ensureArgumentsAreValid();
        
        if (depth == null) {
        	if (isRecursive())
        		depth = SVNDepth.INFINITY;
        	else
        		depth = SVNDepth.IMMEDIATES;
        }
    }
    
    
        

}
