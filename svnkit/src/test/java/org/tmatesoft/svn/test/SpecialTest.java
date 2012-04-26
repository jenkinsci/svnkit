package org.tmatesoft.svn.test;

import java.io.File;
import java.util.Map;

import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.tmatesoft.svn.core.SVNCommitInfo;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.SVNPropertyValue;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.internal.wc16.SVNUpdateClient16;
import org.tmatesoft.svn.core.wc.SVNClientManager;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNStatusType;
import org.tmatesoft.svn.core.wc2.SvnCheckout;
import org.tmatesoft.svn.core.wc2.SvnGetInfo;
import org.tmatesoft.svn.core.wc2.SvnInfo;
import org.tmatesoft.svn.core.wc2.SvnOperationFactory;
import org.tmatesoft.svn.core.wc2.SvnScheduleForAddition;
import org.tmatesoft.svn.core.wc2.SvnStatus;
import org.tmatesoft.svn.core.wc2.SvnTarget;
import org.tmatesoft.svn.core.wc2.SvnUpdate;

public class SpecialTest {

    @Before
    public void setup() {
        Assume.assumeTrue(SVNFileUtil.symlinksSupported());
    }

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

    @Test
    public void testUnversionedSymlinkWithinOldWorkingCopyPointsToNewWorkingCopy() throws Exception {
        Assume.assumeTrue(!TestUtil.isNewWorkingCopyOnly());
        Assume.assumeTrue(TestUtil.isNewWorkingCopyTest());

        final TestOptions options = TestOptions.getInstance();

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testUnversionedSymlinkWithinOldWorkingCopyPointsToNewWorkingCopy", options);
        try {
            final SVNURL url = sandbox.createSvnRepository();

            final File oldWorkingCopy = checkoutOldWorkingCopy(sandbox.createDirectory("wc.old"), url);
            final File newWorkingCopy = checkoutNewWorkingCopy(new File(oldWorkingCopy, "wc.new"), url, svnOperationFactory);
            final File symlink = new File(oldWorkingCopy, "symlink");
            SVNFileUtil.createSymlink(symlink, newWorkingCopy.getAbsolutePath());

            final SvnGetInfo getInfo = svnOperationFactory.createGetInfo();
            getInfo.setSingleTarget(SvnTarget.fromFile(symlink));
            final SvnInfo info = getInfo.run();

            Assert.assertEquals(symlink, info.getWcInfo().getWcRoot());
        } finally {
            svnOperationFactory.dispose();
            sandbox.dispose();
        }
    }

    @Test
    public void testUnversionedSymlinkWithinOldWorkingCopyPointsToNewWorkingCopy17Only() throws Exception {
        Assume.assumeTrue(TestUtil.isNewWorkingCopyOnly());
        Assume.assumeTrue(TestUtil.isNewWorkingCopyTest());

        final TestOptions options = TestOptions.getInstance();

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testUnversionedSymlinkWithinOldWorkingCopyPointsToNewWorkingCopy17Only", options);
        try {
            final SVNURL url = sandbox.createSvnRepository();

            final File oldWorkingCopy = checkoutOldWorkingCopy(sandbox.createDirectory("wc.old"), url);
            final File newWorkingCopy = checkoutNewWorkingCopy(new File(oldWorkingCopy, "wc.new"), url, svnOperationFactory);
            final File symlink = new File(oldWorkingCopy, "symlink");
            SVNFileUtil.createSymlink(symlink, newWorkingCopy.getAbsolutePath());

            final SvnGetInfo getInfo = svnOperationFactory.createGetInfo();
            getInfo.setSingleTarget(SvnTarget.fromFile(symlink));
            final SvnInfo info = getInfo.run();

            Assert.assertEquals(symlink, info.getWcInfo().getWcRoot());
        } finally {
            svnOperationFactory.dispose();
            sandbox.dispose();
        }
    }

    private File checkoutNewWorkingCopy(File directory, SVNURL url, SvnOperationFactory svnOperationFactory) throws SVNException {
        final SvnCheckout checkout = svnOperationFactory.createCheckout();
        checkout.setSource(SvnTarget.fromURL(url));
        checkout.setSingleTarget(SvnTarget.fromFile(directory));
        checkout.run();
        return directory;
    }

    private File checkoutOldWorkingCopy(File directory, SVNURL url) throws SVNException {
        final SVNClientManager clientManager = SVNClientManager.newInstance();
        try {
            final SVNUpdateClient16 updateClient16 = new SVNUpdateClient16(clientManager, clientManager.getOptions());
            updateClient16.doCheckout(url, directory, SVNRevision.HEAD, SVNRevision.HEAD, SVNDepth.INFINITY, true);
            return directory;
        } finally {
            clientManager.dispose();
        }
    }

    private String getTestName() {
        return "SpecialTest";
    }
}
