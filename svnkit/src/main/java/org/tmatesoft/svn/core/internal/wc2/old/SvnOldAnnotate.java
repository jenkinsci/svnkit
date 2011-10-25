package org.tmatesoft.svn.core.internal.wc2.old;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.wc16.SVNWCClient16;
import org.tmatesoft.svn.core.wc2.SvnAnnotate;
import org.tmatesoft.svn.core.wc2.SvnCat;

public class SvnOldAnnotate extends SvnOldRunner<Long, SvnAnnotate> {
    @Override
    protected Long run() throws SVNException {
        
        return Long.decode("1");
    }

}
