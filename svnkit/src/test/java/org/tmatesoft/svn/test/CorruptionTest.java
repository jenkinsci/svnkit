package org.tmatesoft.svn.test;

import java.io.File;

import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.wc17.SVNWCContext;
import org.tmatesoft.svn.core.internal.wc17.db.ISVNWCDb;
import org.tmatesoft.svn.core.wc2.SvnChecksum;
import org.tmatesoft.svn.core.wc2.SvnOperationFactory;
import org.tmatesoft.svn.core.wc2.SvnTarget;
import org.tmatesoft.svn.core.wc2.SvnUpdate;

public class CorruptionTest {

    @Test
    public void testDavUpdateFileWithCorruptedPristine() throws Exception {
        final TestOptions options = TestOptions.getInstance();
        Assume.assumeTrue(TestUtil.areAllApacheOptionsSpecified(options));
        Assume.assumeTrue(TestUtil.isNewWorkingCopyTest());

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testDavUpdateFileWithCorruptedPristine", options);
        try {
            final SVNURL url = sandbox.createSvnRepositoryWithDavAccess();

            final String originalContentsString = "original contents";
            final String newContentsString = "new contents";

            final SvnChecksum originalContentsSha1 = TestUtil.calculateSha1(originalContentsString.getBytes());

            final CommitBuilder commitBuilder1 = new CommitBuilder(url);
            commitBuilder1.addFile("file", originalContentsString.getBytes());
            commitBuilder1.commit();

            final CommitBuilder commitBuilder2 = new CommitBuilder(url);
            commitBuilder2.changeFile("file", newContentsString.getBytes());
            commitBuilder2.commit();

            final WorkingCopy workingCopy = sandbox.checkoutNewWorkingCopy(url, 1);
            final File workingCopyDirectory = workingCopy.getWorkingCopyDirectory();
            final File file = new File(workingCopyDirectory, "file");

            final File pristinePath = getPristinePath(svnOperationFactory, originalContentsSha1, file);

            corruptContents(pristinePath, "corrupted contents");

            final SvnUpdate update = svnOperationFactory.createUpdate();
            update.setSingleTarget(SvnTarget.fromFile(file));
            try {
                update.run();
                Assert.fail("An exception should be thrown");
            } catch (SVNException e) {
                //expected
                e.printStackTrace();
                Assert.assertEquals(SVNErrorCode.WC_CORRUPT_TEXT_BASE, e.getErrorMessage().getErrorCode());
                Assert.assertTrue(e.getErrorMessage().getMessage().contains("Checksum mismatch"));
            }
        } finally {
            svnOperationFactory.dispose();
            sandbox.dispose();
        }
    }

    private File getPristinePath(SvnOperationFactory svnOperationFactory, SvnChecksum originalContentsSha1, File file) throws SVNException {
        final SVNWCContext context = new SVNWCContext(svnOperationFactory.getOptions(), svnOperationFactory.getEventHandler());
        try {
            final ISVNWCDb db = context.getDb();
            return db.getPristinePath(file, originalContentsSha1);
        } finally {
            context.close();
        }
    }

    private void corruptContents(File pristinePath, String corruptedContents) throws SVNException {
        TestUtil.writeFileContentsString(pristinePath, corruptedContents);
    }

    private String getTestName() {
        return "CorruptionTest";
    }
}
