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

import java.util.Collection;
import java.util.LinkedList;

import org.tmatesoft.svn.test.wc.SVNWCDescriptor;
import org.tmatesoft.svn.test.wc.SVNTestFileDescriptor;
import org.tmatesoft.svn.test.util.SVNTestDebugLog;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNPropertyValue;
import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNRevisionRange;

/**
 * @author TMate Software Ltd.
 * @version 1.3
 */
public class ElideFileMergedMergeinfoSourceTest extends AbstractExtMergeTest {

    public ISVNTestExtendedMergeCallback getReleaseModeCallback() {
        return null;
    }

    public ISVNTestExtendedMergeCallback getFeatureModeCallback() {
        return null;
    }

    public SVNWCDescriptor getInitialFS() {
        SVNWCDescriptor fs = new SVNWCDescriptor();
        fs.addFile(new SVNTestFileDescriptor("A"));
        fs.addFile(new SVNTestFileDescriptor("A/file", "this is A/file"));
        fs.addFile(new SVNTestFileDescriptor("A/file2", "this is A/file2"));
        return fs;
    }

    public String getDumpFile() {
        return null;
    }

    public void run() throws SVNException {
        fill();
        createWCs();

        getEnvironment().copy(getBranchFile("A/file"), SVNRevision.WORKING, getBranchFile("A/file_renamed"), true, false, true);
        getEnvironment().commit(getBranchWC(), "A/file renamed to A/file_renamed", SVNDepth.INFINITY);

        getEnvironment().addLine(getBranchFile("A/file_renamed"), "new line to A/file_renamed: 1 mod");
        getEnvironment().addLine(getBranchFile("A/file2"), "new line to A/file2: 1 mod");
        long modsRev1 = getEnvironment().commit(getBranchWC(), "mods", SVNDepth.INFINITY);

        getEnvironment().addLine(getBranchFile("A/file_renamed"), "new line to A/file_renamed: 2 mod");
        getEnvironment().addLine(getBranchFile("A/file2"), "new line to A/file2: 2 mod");
        long modsRev2 = getEnvironment().commit(getBranchWC(), "mods", SVNDepth.INFINITY);

        Collection ranges = new LinkedList();
        ranges.add(new SVNRevisionRange(SVNRevision.create(modsRev1 - 1), SVNRevision.create(modsRev2)));
        getEnvironment().merge(getBranch(), getTrunkWC(), ranges, SVNDepth.INFINITY, false, false);
        getEnvironment().merge(getBranch().appendPath("A/file_renamed", false), getTrunkFile("A/file"), ranges, SVNDepth.UNKNOWN, false, false);

        SVNTestDebugLog.log("A/file:");
        getEnvironment().getFileContents(getTrunkFile("A/file"), System.out);
        SVNPropertyValue value = getEnvironment().getProperty(getTrunkFile("A/file"), SVNProperty.MERGE_INFO, SVNRevision.WORKING);
        SVNTestDebugLog.log("\nmerge info = " + value);
        SVNTestDebugLog.log("A/file2:");
        getEnvironment().getFileContents(getTrunkFile("A/file2"), System.out);
        SVNPropertyValue value2 = getEnvironment().getProperty(getTrunkFile("A/file2"), SVNProperty.MERGE_INFO, SVNRevision.WORKING);
        SVNTestDebugLog.log("\nmerge info = " + value2);
    }
}
