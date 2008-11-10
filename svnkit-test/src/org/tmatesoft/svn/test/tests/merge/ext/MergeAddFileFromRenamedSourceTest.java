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
import java.io.*;

import org.tmatesoft.svn.core.*;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNCopyTask;
import org.tmatesoft.svn.core.wc.SVNEditorAction;
import org.tmatesoft.svn.test.sandboxes.SVNSandboxFile;

/**
 * @author TMate Software Ltd.
 * @version 1.2.0
 */
public class MergeAddFileFromRenamedSourceTest extends AbstractExtMergeTest {

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
        return fs;
    }

    public void run() throws SVNException {
        fill();

        createWCs();
        initializeMergeCallback();

        getEnvironment().copy(getBranchFile("A"), SVNRevision.WORKING, getBranchFile("B"), true, false, true);
        getEnvironment().commit(getBranchWC(), "Moved A to B", SVNDepth.INFINITY);

        final File addedFile = getBranchFile("B/new");
        getEnvironment().addLine(addedFile, "This file has been added in the branch");
        getEnvironment().add(addedFile, false, SVNDepth.EMPTY, false);
        getEnvironment().commit(getBranchWC(), "Added B/new", SVNDepth.INFINITY);

        getEnvironment().update(getTrunkWC(), SVNRevision.HEAD, SVNDepth.INFINITY);
        mergeLastRevisions(getBranch(), getTrunkWC(), 1, SVNDepth.INFINITY, false, false);

        getEnvironment().getFileContents(getTrunkFile("A/new"), System.out);
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

        public SVNCopyTask getTargetCopySource(SVNURL sourceUrl, long sourceRevision, long sourceMergeToRevision, SVNURL targetUrl, long targetRevision) throws SVNException {
            return null;
        }

        public SVNURL[] getTrueMergeTargets(SVNURL sourceUrl, long sourceRevision, long sourceMergeToRevision, SVNURL targetUrl, long targetRevision, SVNEditorAction action) throws SVNException {
            if (sourceUrl.getPath().endsWith("branch/B/new")) {
                return new SVNURL[]{getTrunk().appendPath("A/new", false)};
            }

            return null;
        }

        public SVNURL transformLocation(SVNURL sourceUrl, long sourceRevision, long targetRevision) throws SVNException {
            return null;
        }

        public void prepareMerge(SVNURL source, File target, SVNRevision start, SVNRevision end) throws SVNException {
        }
    }
}