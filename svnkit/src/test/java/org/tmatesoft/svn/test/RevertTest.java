package org.tmatesoft.svn.test;

import org.junit.Assert;
import org.junit.Test;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.wc.SVNStatusType;
import org.tmatesoft.svn.core.wc2.SvnOperationFactory;
import org.tmatesoft.svn.core.wc2.SvnRevert;
import org.tmatesoft.svn.core.wc2.SvnStatus;
import org.tmatesoft.svn.core.wc2.SvnTarget;

import java.io.File;
import java.util.Map;

public class RevertTest {

    @Test
    public void testRevertCopyWithoutLocalModifications() throws Exception {
        final TestOptions options = TestOptions.getInstance();

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testRevertCopyWithoutLocalModifications", options);
        try {
            final SVNURL url = sandbox.createSvnRepository();

            final CommitBuilder commitBuilder = new CommitBuilder(url);
            commitBuilder.addFile("sourceFile");
            commitBuilder.commit();

            final WorkingCopy workingCopy = sandbox.checkoutNewWorkingCopy(url);
            final File targetFile = workingCopy.getFile("targetFile");
            workingCopy.copy("sourceFile", "targetFile");

            final SvnRevert revert = svnOperationFactory.createRevert();
            revert.setSingleTarget(SvnTarget.fromFile(targetFile));
            revert.run();

            final Map<File,SvnStatus> statuses = TestUtil.getStatuses(svnOperationFactory, workingCopy.getWorkingCopyDirectory());
            if (TestUtil.isNewWorkingCopyTest()) {
                Assert.assertFalse(targetFile.exists());
                Assert.assertNull(statuses.get(targetFile));
            } else {
                Assert.assertTrue(targetFile.isFile());
                Assert.assertEquals(SVNStatusType.STATUS_UNVERSIONED, statuses.get(targetFile).getNodeStatus());
            }

        } finally {
            svnOperationFactory.dispose();
            sandbox.dispose();
        }
    }

    @Test
    public void testRevertCopyWithLocalModifications() throws Exception {
        final TestOptions options = TestOptions.getInstance();

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testRevertCopyWithLocalModifications", options);
        try {
            final SVNURL url = sandbox.createSvnRepository();

            final CommitBuilder commitBuilder = new CommitBuilder(url);
            commitBuilder.addFile("sourceFile");
            commitBuilder.commit();

            final WorkingCopy workingCopy = sandbox.checkoutNewWorkingCopy(url);
            final File targetFile = workingCopy.getFile("targetFile");
            workingCopy.copy("sourceFile", "targetFile");

            //even local modifications won't prevent the file from deletion

            TestUtil.writeFileContentsString(targetFile, "changed");

            final SvnRevert revert = svnOperationFactory.createRevert();
            revert.setSingleTarget(SvnTarget.fromFile(targetFile));
            revert.run();

            final Map<File,SvnStatus> statuses = TestUtil.getStatuses(svnOperationFactory, workingCopy.getWorkingCopyDirectory());
            if (TestUtil.isNewWorkingCopyTest()) {
                Assert.assertFalse(targetFile.exists());
                Assert.assertNull(statuses.get(targetFile));
            } else {
                Assert.assertTrue(targetFile.isFile());
                Assert.assertEquals(SVNStatusType.STATUS_UNVERSIONED, statuses.get(targetFile).getNodeStatus());
            }

        } finally {
            svnOperationFactory.dispose();
            sandbox.dispose();
        }
    }

    private String getTestName() {
        return "RevertTest";
    }
}
