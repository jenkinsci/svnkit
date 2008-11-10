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

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.Collection;
import java.util.LinkedList;

import org.tmatesoft.svn.core.SVNCancelException;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.io.fs.FSRepositoryFactory;
import org.tmatesoft.svn.core.internal.wc.ISVNExtendedMergeCallback;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.wc.ISVNEventHandler;
import org.tmatesoft.svn.core.wc.SVNClientManager;
import org.tmatesoft.svn.core.wc.SVNDiffClient;
import org.tmatesoft.svn.core.wc.SVNEvent;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNRevisionRange;
import org.tmatesoft.svn.core.wc.SVNUpdateClient;
import org.tmatesoft.svn.core.wc.SVNCopyTask;
import org.tmatesoft.svn.core.wc.SVNEditorAction;
import org.tmatesoft.svn.core.wc.admin.SVNAdminClient;

/**
 * @author TMate Software Ltd.
 * @version 1.2
 * @since 1.2
 */
public class ExtMergeTest implements ISVNExtendedMergeCallback, ISVNEventHandler {

    public static void main(String[] args) {
//	    final File dumpFile = new File("D:\\svnrepos\\merge-ext.dump");
//	    final File sandbox = new File("D:\\smartsvn-merge\\.temp\\sandbox");

        final File dumpFile = new File("/Users/vs/workspace/sandbox/merge-ext/merge-ext.dump");
        final File sandbox = new File("/Users/vs/workspace/sandbox/tmp");
        
        SVNFileUtil.deleteAll(sandbox, true);
        sandbox.mkdirs();
        File wc = new File(sandbox, "wc");
        File repoPath = new File(sandbox, "repo");

        FSRepositoryFactory.setup();
        try {
            SVNURL repo = SVNURL.fromFile(repoPath);
            SVNURL branch = repo.appendPath("case1/branch", false);
            SVNURL trunk = repo.appendPath("case1/trunk", false);

            ExtMergeTest test = new ExtMergeTest(dumpFile, wc, repoPath, trunk, branch);
            test.run();
        } catch (SVNException e) {
            e.printStackTrace(System.err);
        }
    }

    private File myDumpFile;
    private File myWC;
    private File myRepoPath;
    private SVNURL myTrunk;
    private SVNURL myBranch;

    public ExtMergeTest(File dumpFile, File wc, File repoPath, SVNURL trunk, SVNURL branch) {
        myBranch = branch;
        myDumpFile = dumpFile;
        myRepoPath = repoPath;
        myTrunk = trunk;
        myWC = wc;
    }

// ------------------------------------------------------------------------
//r11 | (no author) | 2008-09-19 13:05:52 +0200 (??, 19 ??? 2008) | 1 line
//Changed paths:
//   M /case2/branch/dir2/file3
//
//Modified dir2\file3
//------------------------------------------------------------------------
//r10 | (no author) | 2008-09-19 13:05:51 +0200 (??, 19 ??? 2008) | 1 line
//Changed paths:
//   D /case2/branch/dir2/file2
//   A /case2/branch/dir2/file3 (from /case2/branch/dir2/file2:9)
//
//Moved dir2\file2 to dir2\file3
//------------------------------------------------------------------------
//r9 | (no author) | 2008-09-19 13:05:49 +0200 (??, 19 ??? 2008) | 1 line
//Changed paths:
//   M /case2/branch/dir2/file2
//
//Modified dir2\file2
//------------------------------------------------------------------------
//r8 | (no author) | 2008-09-19 13:05:48 +0200 (??, 19 ??? 2008) | 1 line
//Changed paths:
//   D /case2/branch/dir2/file1
//   A /case2/branch/dir2/file2 (from /case2/branch/dir2/file1:7)
//
//Moved dir/file1 to dir/file2
//------------------------------------------------------------------------
//r7 | (no author) | 2008-09-19 13:05:48 +0200 (??, 19 ??? 2008) | 1 line
//Changed paths:
//   D /case2/branch/dir1
//   A /case2/branch/dir2 (from /case2/branch/dir1:6)
//
//Moved dir1 to dir2
//------------------------------------------------------------------------
//r6 | (no author) | 2008-09-19 13:05:47 +0200 (??, 19 ??? 2008) | 1 line
//Changed paths:
//   A /case2/branch (from /case2/trunk:5)
//
//Created branch1
//------------------------------------------------------------------------
//r5 | (no author) | 2008-09-19 13:05:47 +0200 (??, 19 ??? 2008) | 1 line
//Changed paths:
//   A /case2
//   A /case2/trunk
//   A /case2/trunk/dir1
//   A /case2/trunk/dir1/file1



//    Initial import
//    ------------------------------------------------------------------------
//    r4 | (no author) | 2008-09-19 13:05:46 +0200 (??, 19 ??? 2008) | 1 line
//    Changed paths:
//       M /case1/branch/file2
//
//    Modified file2
//    ------------------------------------------------------------------------
//    r3 | (no author) | 2008-09-19 13:05:45 +0200 (??, 19 ??? 2008) | 1 line
//    Changed paths:
//       D /case1/branch/file1
//       A /case1/branch/file2 (from /case1/branch/file1:2)
//
//    Moved file1 to file2
//    ------------------------------------------------------------------------
//    r2 | (no author) | 2008-09-19 13:05:44 +0200 (??, 19 ??? 2008) | 1 line
//    Changed paths:
//       A /case1/branch (from /case1/trunk:1)
//
//    Created branch1
//    ------------------------------------------------------------------------
//    r1 | (no author) | 2008-09-19 13:05:44 +0200 (??, 19 ??? 2008) | 1 line
//    Changed paths:
//       A /case1
//       A /case1/trunk
//       A /case1/trunk/file1
//
//    Initial import
//    ------------------------------------------------------------------------
    
    public void run() throws SVNException {
        SVNClientManager manager = SVNClientManager.newInstance();
        SVNAdminClient admin = manager.getAdminClient();
        SVNUpdateClient upClient = manager.getUpdateClient();
        SVNDiffClient diffClient = manager.getDiffClient();
        diffClient.setExtendedMergeCallback(this);
        diffClient.setEventHandler(this);

        admin.doCreateRepository(myRepoPath, null, false, false);

        InputStream dumpStream = null;
        try {
            dumpStream = new BufferedInputStream(new FileInputStream(myDumpFile));
        } catch (FileNotFoundException e) {
            e.printStackTrace(System.err);
            System.exit(0);
        }
        admin.doLoad(myRepoPath, dumpStream);

        upClient.doCheckout(myTrunk, myWC, SVNRevision.UNDEFINED, SVNRevision.create(2), SVNDepth.INFINITY, false);

        Collection ranges = new LinkedList();
        ranges.add(new SVNRevisionRange(SVNRevision.create(3), SVNRevision.create(4)));
        upClient.doUpdate(myWC, SVNRevision.HEAD, SVNDepth.INFINITY, false, false);
        diffClient.doMerge(myBranch, SVNRevision.UNDEFINED, ranges, myWC, SVNDepth.INFINITY, true, false, false, false);
    }

    public SVNCopyTask getTargetCopySource(SVNURL sourceUrl, long sourceRevision, SVNURL targetUrl, long targetRevision) {
        return null;
    }

    public SVNURL[] getTrueMergeTargets(SVNURL sourceUrl, long sourceRevision, SVNURL targetUrl, long targetRevision, SVNEditorAction action) {
        return null;
    }

    public SVNURL transformLocation(SVNURL sourceUrl, long sourceRevision, long targetRevision) throws SVNException {
        return null;
    }

    public void handleEvent(SVNEvent event, double progress) throws SVNException {
        System.out.println("event: path = " + event.getFile() + "; action = " + event.getAction());
    }

    public void checkCancelled() throws SVNCancelException {
    }
}
