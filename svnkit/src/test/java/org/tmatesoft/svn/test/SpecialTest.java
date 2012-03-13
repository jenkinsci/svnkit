package org.tmatesoft.svn.test;

import java.io.File;
import java.util.Map;

import org.junit.Assert;
import org.junit.Test;
import org.tmatesoft.svn.core.SVNCommitInfo;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.SVNPropertyValue;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNStatusType;
import org.tmatesoft.svn.core.wc2.SvnGetInfo;
import org.tmatesoft.svn.core.wc2.SvnOperationFactory;
import org.tmatesoft.svn.core.wc2.SvnScheduleForAddition;
import org.tmatesoft.svn.core.wc2.SvnStatus;
import org.tmatesoft.svn.core.wc2.SvnTarget;
import org.tmatesoft.svn.core.wc2.SvnUpdate;

public class SpecialTest {
    @Test
    public void testExternalsAsSymlinksTargets() throws Exception {
        final TestOptions options = TestOptions.getInstance();

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testExternalsAsSymlinksTargets", options);
        try {
            final SVNURL url = sandbox.createSvnRepository();

            final CommitBuilder commitBuilder1 = new CommitBuilder(url);
            commitBuilder1.addFile("directory/file");
            final SVNCommitInfo commitInfo1 = commitBuilder1.commit();

            final CommitBuilder commitBuilder2 = new CommitBuilder(url);
            commitBuilder2.setDirectoryProperty("", SVNProperty.EXTERNALS, SVNPropertyValue.create("^/directory externalsTarget"));
            final SVNCommitInfo commitInfo2 = commitBuilder2.commit();

            final WorkingCopy workingCopy = sandbox.checkoutNewWorkingCopy(url, commitInfo1.getNewRevision());

            final File workingCopyDirectory = workingCopy.getWorkingCopyDirectory();
            final File externalsSymlink = new File(workingCopyDirectory, "externalsTarget");
            final File symlink = new File(workingCopyDirectory, "symlink");

            SVNFileUtil.createSymlink(symlink, externalsSymlink.getPath());

            final SvnUpdate update = svnOperationFactory.createUpdate();
            update.setSingleTarget(SvnTarget.fromFile(workingCopyDirectory));
            update.setRevision(SVNRevision.create(commitInfo2.getNewRevision()));
            update.setDepth(SVNDepth.INFINITY);
            update.setIgnoreExternals(false);
            update.run();

            final SvnScheduleForAddition scheduleForAddition = svnOperationFactory.createScheduleForAddition();
            scheduleForAddition.setSingleTarget(SvnTarget.fromFile(symlink));
            scheduleForAddition.run();

            final Map<File,SvnStatus> statuses = TestUtil.getStatuses(svnOperationFactory, workingCopyDirectory);
            Assert.assertEquals(SVNStatusType.STATUS_ADDED, statuses.get(symlink).getNodeStatus());
        } finally {
            svnOperationFactory.dispose();
            sandbox.dispose();
        }
    }
    @Test
    public void testSymlinkPointsToWorkingCopy() throws Exception {
        final TestOptions options = TestOptions.getInstance();

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testSymlinkPointsToWorkingCopy", options);
        try {
            final SVNURL url = sandbox.createSvnRepository();

            final WorkingCopy workingCopy = sandbox.checkoutNewWorkingCopy(url, SVNRevision.HEAD.getNumber());
            final File workingCopyDirectory = workingCopy.getWorkingCopyDirectory();

            final File symlink = new File(sandbox.createDirectory("directoryFroSymlink"), "symlink");

            SVNFileUtil.createSymlink(symlink, workingCopyDirectory.getAbsolutePath());

            final SvnGetInfo getInfo = svnOperationFactory.createGetInfo();
            getInfo.setSingleTarget(SvnTarget.fromFile(symlink));
            getInfo.run();
        } finally {
            svnOperationFactory.dispose();
            sandbox.dispose();
        }
    }

    private String getTestName() {
        return "SpecialTest";
    }
}
