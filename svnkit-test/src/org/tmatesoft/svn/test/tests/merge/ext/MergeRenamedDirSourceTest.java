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
import org.tmatesoft.svn.core.wc.SVNCopySource;
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
public class MergeRenamedDirSourceTest extends AbstractExtMergeTest {

    public ISVNTestExtendedMergeCallback getFeatureModeCallback() {
        return new FeatureModeCallback();
    }

    public ISVNTestExtendedMergeCallback getReleaseModeCallback() {
        return new ReleaseModeCallback();
    }

    private long myEndRevision;

    public String getDumpFile() {
        return null;
    }

    public SVNWCDescriptor getInitialFS() {
        SVNWCDescriptor fs = new SVNWCDescriptor();
        fs.addFile(new SVNTestFileDescriptor("A"));
        fs.addFile(new SVNTestFileDescriptor("A/file", "this is A/file"));
        return fs;
    }

    public void run() throws SVNException {
        fill();

        createWCs();
        initializeMergeCallback();

        getEnvironment().copy(getBranchFile("A"), SVNRevision.WORKING, getBranchFile("B"), true, false, true);
        getEnvironment().commit(getBranchWC(), "A renamed to B", SVNDepth.INFINITY);

        getEnvironment().copy(getBranchFile("B/file"), SVNRevision.WORKING, getBranchFile("B/file2"), true, false, true);
        long startRevision = getEnvironment().commit(getBranchWC(), "B/file renamed to B/file2", SVNDepth.INFINITY);

        getEnvironment().modifyAndCommit(getBranchFile("B/file2"));

        getEnvironment().copy(getBranchFile("B/file2"), SVNRevision.WORKING, getBranchFile("B/file3"), true, false, true);
        getEnvironment().commit(getBranchWC(), "B/file2 renamed to B/file3", SVNDepth.INFINITY);
        getEnvironment().update(getTrunkWC(), SVNRevision.HEAD, SVNDepth.INFINITY);

        long endRevision = getEnvironment().modifyAndCommit(getBranchFile("B/file3"));

        getEnvironment().setEventHandler(SVNTestDebugLog.getEventHandler());

        myEndRevision = endRevision;
        mergeLastRevisions(getBranch(), getTrunkWC(), endRevision - startRevision, SVNDepth.INFINITY, false, false);

        SVNTestDebugLog.log("\ncontents of merge target after merge:\n");
        getEnvironment().getFileContents(getTrunkFile("A/file"), System.out);
    }

// ###############  FEATURE MODE  ###################    

    private class FeatureModeCallback implements ISVNTestExtendedMergeCallback {

        public void prepareMerge(SVNURL source, File target, SVNRevision start, SVNRevision end) throws SVNException {
        }

        public SVNCopyTask getTargetCopySource(SVNURL sourceUrl, long sourceRevision, long sourceMergeFromRevision, long sourceMergeToRevision, SVNURL targetUrl, long targetRevision) throws SVNException {
            if (sourceUrl.getPath().endsWith("branch/B/file3")) {
                SVNCopySource source = new SVNCopySource(SVNRevision.create(myEndRevision), SVNRevision.create(myEndRevision), getTrunk().appendPath("A/file", false));
                return SVNCopyTask.create(source, true);
            }
            return null;
        }

        public SVNURL[] getTrueMergeTargets(SVNURL sourceUrl, long sourceRevision, long sourceMergeFromRevision, long sourceMergeToRevision, SVNURL targetUrl, long targetRevision, SVNEditorAction action) throws SVNException {
            if (action == SVNEditorAction.DELETE && sourceUrl.getPath().endsWith("branch/B/file2")) {
                return new SVNURL[0];
            }
            if (sourceUrl.getPath().endsWith("branch/B/file3")) {
                return new SVNURL[]{getTrunk().appendPath("A/file3", false)};
            }
            return null;
        }

        public SVNURL transformLocation(SVNURL sourceUrl, long sourceRevision, long targetRevision) throws SVNException {
            if (sourceUrl.getPath().endsWith("branch/B/file3") && sourceRevision == myEndRevision) {
                return getBranch().appendPath("B/file2", false);
            }
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
            if (action == SVNEditorAction.DELETE && sourceUrl.getPath().endsWith("branch/B/file2")) {
                return new SVNURL[0];
            }
            if (sourceUrl.getPath().endsWith("branch/B/file3")) {
                return new SVNURL[]{getTrunk().appendPath("A/file", false)};
            }
            return null;
        }

        public SVNURL transformLocation(SVNURL sourceUrl, long sourceRevision, long targetRevision) throws SVNException {
            if (sourceUrl.getPath().endsWith("branch/B/file3") && sourceRevision == myEndRevision) {
                return getBranch().appendPath("B/file2", false);
            }
            return null;
        }

        public void prepareMerge(SVNURL source, File target, SVNRevision start, SVNRevision end) throws SVNException {
        }

        public SVNWCDescriptor getExpectedState() throws SVNException {
            return null;
        }
    }
}