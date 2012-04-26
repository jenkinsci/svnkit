package org.tmatesoft.svn.test;

import java.io.File;

import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc2.SvnOperationFactory;
import org.tmatesoft.svn.core.wc2.SvnRevert;
import org.tmatesoft.svn.core.wc2.SvnTarget;

public class SvnRevertMissingDirectoriesTest {

    @Test
    public void testRevertMissingDirectories() throws Exception {
        Assume.assumeTrue(TestUtil.isNewWorkingCopyTest());
        final TestOptions options = TestOptions.getInstance();

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testRevertMissingDirectories", options);
        try {
            final SVNURL url = sandbox.createSvnRepository();

            final CommitBuilder commitBuilder = new CommitBuilder(url);
            commitBuilder.addFile("directory/file");
            commitBuilder.commit();

            final WorkingCopy workingCopy = sandbox.checkoutNewWorkingCopy(url, SVNRevision.HEAD.getNumber());
            final File workingCopyDirectory = workingCopy.getWorkingCopyDirectory();

            final File directory = new File(workingCopyDirectory, "directory");
            final File file = new File(directory, "file");
            SVNFileUtil.deleteAll(directory, true);

            final SvnRevert revert = svnOperationFactory.createRevert();
            revert.setDepth(SVNDepth.INFINITY);
            revert.setSingleTarget(SvnTarget.fromFile(directory));
//            revert.setRevertMissingDirectories(true); for 1.7 the value is ignored, missing directories are always reverted
            revert.run();

            Assert.assertTrue(file.isFile());
        } finally {
            sandbox.dispose();
            svnOperationFactory.dispose();
        }
    }

    private String getTestName() {
        return "SvnRevertMissingDirectoriesTest";
    }
}
