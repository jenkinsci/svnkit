/*
 * ====================================================================
 * Copyright (c) 2004-2008 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.test.tests.merge.ext;

import java.util.Collection;
import java.util.LinkedList;
import java.io.File;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNCopyTask;
import org.tmatesoft.svn.core.wc.SVNEditorAction;
import org.tmatesoft.svn.test.sandboxes.SVNSandboxFile;

/**
 * @author TMate Software Ltd.
 * @version 1.2.0
 */
public class MergeSwitchedEntriesFailTest extends AbstractExtMergeTest {

    public ISVNTestExtendedMergeCallback getFeatureModeCallback() {
        return new FeatureModeCallback();
    }

    public ISVNTestExtendedMergeCallback getReleaseModeCallback() {
        return new ReleaseModeCallback();
    }

    public String getDumpFile() {
        return null;
    }

    public Collection getInitialFS() {
        Collection fs = new LinkedList();
        fs.add(new SVNSandboxFile("A"));
        fs.add(new SVNSandboxFile("A/file", "this is A/file", false));
        fs.add(new SVNSandboxFile("B"));
        return fs;
    }

    public void run() throws SVNException {
        fill();

        createWCs();
        initializeMergeCallback();

        getEnvironment().modifyAndCommit(getBranchFile("A/file"));
        getEnvironment().switchPath(getTrunkFile("B"), getBranch().appendPath("B", false), SVNRevision.HEAD, SVNDepth.INFINITY);

        getEnvironment().update(getTrunkWC(), SVNRevision.HEAD, SVNDepth.INFINITY);
        mergeLastRevisions(getBranch(), getTrunkWC(), 1, SVNDepth.INFINITY, false, false);
    }

// ###############  FEATURE MODE  ###################
    
    private class FeatureModeCallback implements ISVNTestExtendedMergeCallback {

        public void prepareMerge(SVNURL source, File target, SVNRevision start, SVNRevision end) throws SVNException {
        }

        public SVNCopyTask getTargetCopySource(SVNURL sourceUrl, long sourceRevision, long sourceMergeToRevision, SVNURL targetUrl, long targetRevision) throws SVNException {
            return null;
        }

        public SVNURL[] getTrueMergeTargets(SVNURL sourceUrl, long sourceRevision, long sourceMergeToRevision, SVNURL targetUrl, long targetRevision, SVNEditorAction action) throws SVNException {
            return new SVNURL[0];
        }

        public SVNURL transformLocation(SVNURL sourceUrl, long sourceRevision, long targetRevision) throws SVNException {
            return null;
        }
    }

// ###############  RELEASE MODE  ###################
    
    private class ReleaseModeCallback implements ISVNTestExtendedMergeCallback {

        public SVNURL transformLocation(SVNURL sourceUrl, long sourceRevision, long targetRevision) throws SVNException {
            return null;
        }

        public SVNCopyTask getTargetCopySource(SVNURL sourceUrl, long sourceRevision, long sourceMergeToRevision, SVNURL targetUrl, long targetRevision) throws SVNException {
            return null;
        }

        public SVNURL[] getTrueMergeTargets(SVNURL sourceUrl, long sourceRevision, long sourceMergeToRevision, SVNURL targetUrl, long targetRevision, SVNEditorAction action) throws SVNException {
            return new SVNURL[0];
        }

        public void prepareMerge(SVNURL source, File target, SVNRevision start, SVNRevision end) throws SVNException {
        }
    }
}
