package org.tmatesoft.svn.test;

import java.io.File;

import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNStatusType;
import org.tmatesoft.svn.core.wc2.SvnGetStatus;
import org.tmatesoft.svn.core.wc2.SvnMarkReplaced;
import org.tmatesoft.svn.core.wc2.SvnOperationFactory;
import org.tmatesoft.svn.core.wc2.SvnStatus;
import org.tmatesoft.svn.core.wc2.SvnTarget;

public class MarkReplacedTest {

    @Test
    public void testMarkDirectoryReplaced() throws Exception {
        final TestOptions options = TestOptions.getInstance();

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testMarkDirectoryReplaced", options);
        try {
            final SVNURL url = sandbox.createSvnRepository();

            final CommitBuilder commitBuilder = new CommitBuilder(url);
            commitBuilder.addFile("directory/file");
            commitBuilder.commit();

            final WorkingCopy workingCopy = sandbox.checkoutNewWorkingCopy(url, SVNRevision.HEAD.getNumber());
            final File workingCopyDirectory = workingCopy.getWorkingCopyDirectory();

            final File directory = new File(workingCopyDirectory, "directory");
            final File file = new File(directory, "file");

            markReplaced(svnOperationFactory, directory);
            Assert.assertEquals(SVNStatusType.STATUS_REPLACED, getStatus(svnOperationFactory, directory));
            assertEqualsAny(SVNStatusType.STATUS_REPLACED, SVNStatusType.STATUS_ADDED, getStatus(svnOperationFactory, file));

            //now markReplaced should simply do nothing, but shouldn't fail
            markReplaced(svnOperationFactory, directory);
            Assert.assertEquals(SVNStatusType.STATUS_REPLACED, getStatus(svnOperationFactory, directory));
            assertEqualsAny(SVNStatusType.STATUS_REPLACED, SVNStatusType.STATUS_ADDED, getStatus(svnOperationFactory, file));
        } finally {
            sandbox.dispose();
            svnOperationFactory.dispose();
        }
    }

    @Test
    public void testMarkFileReplaced() throws Exception {
        final TestOptions options = TestOptions.getInstance();

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testMarkFileReplaced", options);
        try {
            final SVNURL url = sandbox.createSvnRepository();

            final CommitBuilder commitBuilder = new CommitBuilder(url);
            commitBuilder.addFile("directory/file");
            commitBuilder.commit();

            final WorkingCopy workingCopy = sandbox.checkoutNewWorkingCopy(url, SVNRevision.HEAD.getNumber());
            final File workingCopyDirectory = workingCopy.getWorkingCopyDirectory();

            final File directory = new File(workingCopyDirectory, "directory");
            final File file = new File(directory, "file");

            markReplaced(svnOperationFactory, file);
            Assert.assertEquals(SVNStatusType.STATUS_REPLACED, getStatus(svnOperationFactory, file));
            Assert.assertEquals(SVNStatusType.STATUS_NORMAL, getStatus(svnOperationFactory, directory));

            //now markReplaced should simply do nothing, but shouldn't fail
            markReplaced(svnOperationFactory, file);
            Assert.assertEquals(SVNStatusType.STATUS_REPLACED, getStatus(svnOperationFactory, file));
            Assert.assertEquals(SVNStatusType.STATUS_NORMAL, getStatus(svnOperationFactory, directory));
        } finally {
            sandbox.dispose();
            svnOperationFactory.dispose();
        }
    }

    @Test
    public void testMarkRootReplacedFails() throws Exception {
        Assume.assumeTrue(TestUtil.isNewWorkingCopyTest());
        final TestOptions options = TestOptions.getInstance();

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testMarkRootReplacedFails", options);
        try {
            final SVNURL url = sandbox.createSvnRepository();

            final CommitBuilder commitBuilder = new CommitBuilder(url);
            commitBuilder.addFile("directory/file");
            commitBuilder.commit();

            final WorkingCopy workingCopy = sandbox.checkoutNewWorkingCopy(url, SVNRevision.HEAD.getNumber());
            final File workingCopyDirectory = workingCopy.getWorkingCopyDirectory();

            final File directory = new File(workingCopyDirectory, "directory");
            final File file = new File(directory, "file");

            try {
                markReplaced(svnOperationFactory, workingCopyDirectory);
                Assert.fail("An exception should be thrown");
            } catch (SVNException e) {
                Assert.assertEquals(SVNErrorCode.WC_PATH_UNEXPECTED_STATUS, e.getErrorMessage().getErrorCode());
                //expected
            }

            Assert.assertEquals(SVNStatusType.STATUS_NORMAL, getStatus(svnOperationFactory, workingCopyDirectory));
            Assert.assertEquals(SVNStatusType.STATUS_NORMAL, getStatus(svnOperationFactory, directory));
            Assert.assertEquals(SVNStatusType.STATUS_NORMAL, getStatus(svnOperationFactory, file));
        } finally {
            sandbox.dispose();
            svnOperationFactory.dispose();
        }
    }

    private void assertEqualsAny(SVNStatusType expected1, SVNStatusType expected2, SVNStatusType actual) {
        Assert.assertTrue(expected1 == actual || expected2 == actual);
    }

    private SVNStatusType getStatus(SvnOperationFactory svnOperationFactory, File file) throws SVNException {
        final SvnGetStatus getStatus = svnOperationFactory.createGetStatus();
        getStatus.setSingleTarget(SvnTarget.fromFile(file));
        getStatus.setRemote(false);
        getStatus.setReportAll(true);
        getStatus.setReportExternals(false);
        getStatus.setReportIgnored(true);
        final SvnStatus status = getStatus.run();

        Assert.assertNotNull(status);
        return status.getNodeStatus();
    }

    private void markReplaced(SvnOperationFactory svnOperationFactory, File file) throws SVNException {
        final SvnMarkReplaced markReplaced = svnOperationFactory.createMarkReplaced();
        markReplaced.setSingleTarget(SvnTarget.fromFile(file));
        markReplaced.run();
    }

    private String getTestName() {
        return "MarkReplacedTest";
    }
}
