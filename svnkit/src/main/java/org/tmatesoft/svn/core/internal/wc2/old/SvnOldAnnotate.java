package org.tmatesoft.svn.core.internal.wc2.old;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.wc2.SvnAnnotate;

public class SvnOldAnnotate extends SvnOldRunner<Long, SvnAnnotate> {
    @Override
    protected Long run() throws SVNException {
        
        return Long.decode("1");
    }

}
