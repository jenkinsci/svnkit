package org.tmatesoft.svn.core.internal.wc2.old;

import java.util.Collection;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.wc.SVNDiffClient;
import org.tmatesoft.svn.core.wc2.SvnSuggestMergeSources;

public class SvnOldSuggestMergeSources extends SvnOldRunner<Collection<SVNURL>, SvnSuggestMergeSources> {

    @Override
    protected Collection<SVNURL> run() throws SVNException {
        SVNDiffClient dc = new SVNDiffClient(getOperation().getAuthenticationManager(), getOperation().getOptions());
        if (getOperation().getFirstTarget().isURL()) {
            return dc.doSuggestMergeSources(getOperation().getFirstTarget().getURL(), getOperation().getFirstTarget().getPegRevision());
        }
        return dc.doSuggestMergeSources(getOperation().getFirstTarget().getFile(), getOperation().getFirstTarget().getPegRevision());
   }

}
