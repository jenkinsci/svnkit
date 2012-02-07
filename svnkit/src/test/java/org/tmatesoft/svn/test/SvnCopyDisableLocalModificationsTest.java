package org.tmatesoft.svn.test;

import java.io.File;

import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;
import org.tmatesoft.svn.core.SVNCommitInfo;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc2.SvnCopySource;
import org.tmatesoft.svn.core.wc2.SvnOperationFactory;
import org.tmatesoft.svn.core.wc2.SvnRemoteCopy;
import org.tmatesoft.svn.core.wc2.SvnTarget;

public class SvnCopyDisableLocalModificationsTest {

    @Test
    public void testCopy() throws Exception {
        Assume.assumeTrue(!TestUtil.isNewWorkingCopyTest());//temporarily ignored for new working copy

        final TestOptions options = TestOptions.getInstance();

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testCopy", options);
        try {
            final SVNURL url = sandbox.createSvnRepository();

            final CommitBuilder commitBuilder = new CommitBuilder(url);
            commitBuilder.addFile("source/file");
            commitBuilder.addDirectory("target");
            commitBuilder.commit();

            final WorkingCopy workingCopy = sandbox.checkoutNewWorkingCopy(url, SVNRevision.HEAD.getNumber());
            final File workingCopyDirectory = workingCopy.getWorkingCopyDirectory();

            final File source = new File(workingCopyDirectory, "source");
            final File target = new File(workingCopyDirectory, "target");
            final File sourceFile = new File(source, "file");
            final File targetFile = new File(target, "file");

            final String sourceFileContents = "local modifications";
            TestUtil.writeFileContentsString(sourceFile, sourceFileContents);

            final SvnRemoteCopy remoteCopy = svnOperationFactory.createRemoteCopy();
            remoteCopy.addCopySource(SvnCopySource.create(SvnTarget.fromFile(sourceFile, SVNRevision.WORKING), SVNRevision.WORKING));
            remoteCopy.setSingleTarget(SvnTarget.fromURL(url.appendPath(target.getName(), false)));
            remoteCopy.setFailWhenDstExists(false);//i.e. as child
            remoteCopy.setDisableLocalModifications(true);
            final SVNCommitInfo commitInfo = remoteCopy.run();

            workingCopy.updateToRevision(commitInfo.getNewRevision());

            final String targetFileContents = TestUtil.readFileContentsString(targetFile);
            Assert.assertEquals("", targetFileContents);//should not be equal to sourceFileContents
        } finally {
            svnOperationFactory.dispose();
            sandbox.dispose();
        }
    }

    private String getTestName() {
        return "SvnCopyDisableLocalModifications";
    }
}
