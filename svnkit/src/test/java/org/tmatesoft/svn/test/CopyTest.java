package org.tmatesoft.svn.test;

import java.io.File;
import java.io.IOException;
import java.util.Map;

import junit.framework.Assert;
import org.junit.Test;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNStatusType;
import org.tmatesoft.svn.core.wc2.SvnCopy;
import org.tmatesoft.svn.core.wc2.SvnCopySource;
import org.tmatesoft.svn.core.wc2.SvnOperationFactory;
import org.tmatesoft.svn.core.wc2.SvnStatus;
import org.tmatesoft.svn.core.wc2.SvnTarget;

public class CopyTest {

    @Test
    public void testMoveBasePegRevision() throws Exception {
        testCopyBasePegRevision(true, "testMoveBasePegRevision");
    }

    @Test
    public void testCopyBasePegRevision() throws Exception {
        testCopyBasePegRevision(false, "testCopyBasePegRevision");
    }

    private void testCopyBasePegRevision(boolean move, String testName) throws SVNException, IOException {
        final TestOptions options = TestOptions.getInstance();

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + "." +
                testName, options);
        try {
            final SVNURL url = sandbox.createSvnRepository();

            final CommitBuilder commitBuilder = new CommitBuilder(url);
            commitBuilder.addFile("sourceFile", "original contents".getBytes());
            commitBuilder.commit();

            final WorkingCopy workingCopy = sandbox.checkoutNewWorkingCopy(url);
            final File workingCopyDirectory = workingCopy.getWorkingCopyDirectory();
            final File sourceFile = new File(workingCopyDirectory, "sourceFile");
            final File targetFile = new File(workingCopyDirectory, "targetFile");

            final String expectedNewContents = move ? "new contents" : "original contents";
            TestUtil.writeFileContentsString(sourceFile, "new contents");

            final SvnCopy copy = svnOperationFactory.createCopy();
            copy.addCopySource(SvnCopySource.create(SvnTarget.fromFile(sourceFile, SVNRevision.BASE), SVNRevision.UNDEFINED));
            copy.setSingleTarget(SvnTarget.fromFile(targetFile));
            copy.setMove(move);
            copy.run();

            Assert.assertTrue(targetFile.isFile());

            final String actualNewContents = TestUtil.readFileContentsString(targetFile);
            Assert.assertEquals(expectedNewContents, actualNewContents);

            final Map<File,SvnStatus> statuses = TestUtil.getStatuses(svnOperationFactory, workingCopyDirectory);
            Assert.assertEquals(move ? SVNStatusType.STATUS_DELETED : SVNStatusType.STATUS_MODIFIED, statuses.get(sourceFile).getNodeStatus());
            Assert.assertEquals(SVNStatusType.STATUS_ADDED, statuses.get(targetFile).getNodeStatus());
            Assert.assertEquals(url.appendPath(sourceFile.getName(), false), statuses.get(targetFile).getCopyFromUrl());

        } finally {
            svnOperationFactory.dispose();
            sandbox.dispose();
        }
    }

    private String getTestName() {
        return "MoveTest";
    }
}
