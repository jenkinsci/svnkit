package org.tmatesoft.svn.test.tests.merge.ext;

import java.io.File;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.wc.ISVNExtendedMergeCallback;
import org.tmatesoft.svn.core.wc.SVNRevision;

/**
 * @author Marc Strapetz
 */
public interface ISVNTestExtendedMergeCallback extends ISVNExtendedMergeCallback {
    public void prepareMerge(SVNURL source, File target, SVNRevision start, SVNRevision end) throws SVNException;
}