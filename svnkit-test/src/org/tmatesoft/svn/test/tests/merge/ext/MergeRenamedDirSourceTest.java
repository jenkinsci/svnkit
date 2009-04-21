/*
 * ====================================================================
 * Copyright (c) 2004-2009 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.test.tests.merge.ext;

import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.test.util.SVNTestDebugLog;
import org.tmatesoft.svn.test.wc.SVNTestFileDescriptor;
import org.tmatesoft.svn.test.wc.SVNWCDescriptor;

/**
 * @author TMate Software Ltd.
 * @version 1.2.0
 */
public abstract class MergeRenamedDirSourceTest extends AbstractExtMergeTest {

    protected abstract void doMerge(long startRevision, long endRevision) throws SVNException;

    private long myEndRevision;

    public final String getDumpFile() {
        return null;
    }

    public final SVNWCDescriptor getInitialFS() {
        SVNWCDescriptor fs = new SVNWCDescriptor();
        fs.addFile(new SVNTestFileDescriptor("A"));
        fs.addFile(new SVNTestFileDescriptor("A/file", "this is A/file"));
        return fs;
    }

    public final void run() throws SVNException {
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

        long endRevision = getEnvironment().modifyAndCommit(getBranchFile("B/file3"));

        getEnvironment().setEventHandler(SVNTestDebugLog.getEventHandler());

        myEndRevision = endRevision;
        doMerge(startRevision, endRevision);

        SVNTestDebugLog.log("\ncontents of merge target after merge:\n");
        getEnvironment().getFileContents(getTrunkFile("A/file"), System.out);
    }

    public final long getMyEndRevision() {
        return myEndRevision;
    }
}