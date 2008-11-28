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

import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNPropertyValue;
import org.tmatesoft.svn.core.wc.SVNRevisionRange;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.test.wc.SVNWCDescriptor;
import org.tmatesoft.svn.test.wc.SVNTestFileDescriptor;
import org.tmatesoft.svn.test.util.SVNTestDebugLog;

/**
 * @author TMate Software Ltd.
 * @version 1.2.0
 */
public class MergeWithDepthSupportTest extends AbstractExtMergeTest {

    public ISVNTestExtendedMergeCallback getReleaseModeCallback() {
        return null;
    }

    public ISVNTestExtendedMergeCallback getFeatureModeCallback() {
        return null;
    }

    public SVNWCDescriptor getInitialFS() {
        SVNWCDescriptor wc = new SVNWCDescriptor();
        wc.addFile("A");
        wc.addFile("A/dir");
        wc.addFile("A/file", "this is A/file");
        wc.addFile("file", "this is file");
        wc.addFile("B");
        wc.addFile("B/dir");
        wc.addFile("B/file", "this is B/file");
        return wc;
    }

    public String getDumpFile() {
        return null;
    }

    public void run() throws SVNException {
        fill();
        
        getEnvironment().copy(getTrunk(), SVNRevision.HEAD, getBranch(), false, false, true, "test branch created");
        getEnvironment().checkout(getBranch(), getBranchWC(), SVNRevision.HEAD, SVNDepth.INFINITY);
        getEnvironment().checkout(getTrunk(), getTrunkWC(), SVNRevision.HEAD, SVNDepth.FILES);
        getEnvironment().update(getTrunkFile("file"), SVNRevision.HEAD, SVNDepth.FILES);
        getEnvironment().update(getTrunkFile("A"), SVNRevision.HEAD, SVNDepth.INFINITY);

        long startRev = getEnvironment().modifyAndCommit(getBranchFile("file"));
        SVNTestFileDescriptor file = new SVNTestFileDescriptor("added_file", "this is added_file");
        file.dump(getBranchWC());
        getEnvironment().add(getBranchFile("added_file"), false, SVNDepth.UNKNOWN, false);
        getEnvironment().add(getBranchFile("C"), true, SVNDepth.INFINITY, false);
        getEnvironment().modifyAndCommit(getBranchFile("A/file"));
        getEnvironment().setProperty(getBranchFile("A/dir"), "prop", SVNPropertyValue.create("value"), SVNDepth.INFINITY);
        getEnvironment().setProperty(getBranchFile("B"), "prop", SVNPropertyValue.create("value"), SVNDepth.INFINITY);
        long endRev = getEnvironment().commit(getBranchWC(), "property is set on A/dir", SVNDepth.INFINITY);

        Collection ranges = new LinkedList();
        ranges.add(new SVNRevisionRange(SVNRevision.create(startRev - 1), SVNRevision.create(endRev)));
        getEnvironment().setEventHandler(SVNTestDebugLog.getEventHandler());
        getEnvironment().merge(getBranch(), getTrunkWC(), ranges, SVNDepth.IMMEDIATES, false, false);
    }
}
