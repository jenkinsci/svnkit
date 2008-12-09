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

import java.io.File;

import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.wc.SVNCopyTask;
import org.tmatesoft.svn.core.wc.SVNEditorAction;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.test.util.SVNTestDebugLog;
import org.tmatesoft.svn.test.wc.SVNTestFileDescriptor;
import org.tmatesoft.svn.test.wc.SVNWCDescriptor;

/**
 * @author TMate Software Ltd.
 * @version 1.2.0
 */
public class MergeCopiedTargetsTest extends AbstractExtMergeTest {

    public ISVNTestExtendedMergeCallback getFeatureModeCallback() {
        return new FeatureModeCallback();
    }

    public ISVNTestExtendedMergeCallback getReleaseModeCallback() {
        return new ReleaseModeCallback();
    }

    public String getDumpFile() {
        return null;
    }

    public SVNWCDescriptor getInitialFS() {
        SVNWCDescriptor fs = new SVNWCDescriptor();
        fs.addFile(new SVNTestFileDescriptor("A"));
        fs.addFile(new SVNTestFileDescriptor("A/file", "this is A/file"));
        fs.addFile(new SVNTestFileDescriptor("B"));
        return fs;
    }

    public void run() throws SVNException {
        fill();

        createWCs();
        initializeMergeCallback();

        getEnvironment().copy(getTrunkFile("A/file"), SVNRevision.WORKING, getTrunkFile("A/file2"), false, false, true);
        getEnvironment().copy(getTrunkFile("A/file"), SVNRevision.WORKING, getTrunkFile("B/file"), false, false, true);
        getEnvironment().copy(getTrunkFile("A/file"), SVNRevision.WORKING, getTrunkFile("file"), false, false, true);
        getEnvironment().commit(getTrunkWC(), "made several copies of A/file", SVNDepth.INFINITY);

        getEnvironment().modifyAndCommit(getBranchFile("A/file"));

        getEnvironment().setEventHandler(SVNTestDebugLog.getEventHandler());

        mergeLastRevisions(getBranch(), getTrunkWC(), 1, SVNDepth.INFINITY, false, false);

        System.out.println();
        System.out.println("A/file");
        getEnvironment().getFileContents(getTrunkFile("A/file"), System.out);
        System.out.println();
        System.out.println("A/file2");
        getEnvironment().getFileContents(getTrunkFile("A/file2"), System.out);
        System.out.println();
        System.out.println("B/file");
        getEnvironment().getFileContents(getTrunkFile("B/file"), System.out);
        System.out.println();
        System.out.println("file");
        getEnvironment().getFileContents(getTrunkFile("file"), System.out);
    }

// ###############  FEATURE MODE  ###################    

    private class FeatureModeCallback implements ISVNTestExtendedMergeCallback {

        public void prepareMerge(SVNURL source, File target) throws SVNException {
        }

        public SVNCopyTask getTargetCopySource(SVNURL sourceUrl, long sourceRevision, long sourceMergeFromRevision, long sourceMergeToRevision, SVNURL targetUrl, long targetRevision) throws SVNException {
            return null;
        }

        public SVNURL[] getTrueMergeTargets(SVNURL sourceUrl, long sourceRevision, long sourceMergeFromRevision, long sourceMergeToRevision, SVNURL targetUrl, long targetRevision, SVNEditorAction action) throws SVNException {
            return new SVNURL[0];
        }

        public SVNURL transformLocation(SVNURL sourceUrl, long sourceRevision, long targetRevision) throws SVNException {
            return null;
        }

        public SVNWCDescriptor getExpectedState() throws SVNException {
            return null;
        }
    }

// ###############  RELEASE MODE  ###################

    private class ReleaseModeCallback implements ISVNTestExtendedMergeCallback {

        public SVNCopyTask getTargetCopySource(SVNURL sourceUrl, long sourceRevision, long sourceMergeFromRevision, long sourceMergeToRevision, SVNURL targetUrl, long targetRevision) {
            return null;
        }

        public SVNURL[] getTrueMergeTargets(SVNURL sourceUrl, long sourceRevision, long sourceMergeFromRevision, long sourceMergeToRevision, SVNURL targetUrl, long targetRevision, SVNEditorAction action) throws SVNException {
            if (sourceUrl.getPath().endsWith("branch/A/file")) {
                return new SVNURL[]{getTrunk().appendPath("file", false), getTrunk().appendPath("B/file", false), getTrunk().appendPath("A/file2", false)};
            }
            return null;
        }

        public SVNURL transformLocation(SVNURL sourceUrl, long sourceRevision, long targetRevision) throws SVNException {
            return null;
        }

        public void prepareMerge(SVNURL source, File target) throws SVNException {
        }

        public SVNWCDescriptor getExpectedState() throws SVNException {
            return null;
        }
    }
}