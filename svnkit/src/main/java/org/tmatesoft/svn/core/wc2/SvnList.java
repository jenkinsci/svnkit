package org.tmatesoft.svn.core.wc2;

import org.tmatesoft.svn.core.SVNDirEntry;

public class SvnList extends SvnReceivingOperation<SVNDirEntry> {

    private boolean isFetchLocks;
    private int entryFields;
    
    protected SvnList(SvnOperationFactory factory) {
        super(factory);
        setEntryFields(SVNDirEntry.DIRENT_ALL);
    }

    public int getEntryFields() {
        return entryFields;
    }

    public void setEntryFields(int entryFields) {
        this.entryFields = entryFields;
    }

    public boolean isFetchLocks() {
        return isFetchLocks;
    }

    public void setFetchLocks(boolean isFetchLocks) {
        this.isFetchLocks = isFetchLocks;
    }

}
