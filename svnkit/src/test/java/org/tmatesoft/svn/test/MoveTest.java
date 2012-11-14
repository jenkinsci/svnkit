package org.tmatesoft.svn.test;

import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.wc.SVNClientManager;
import org.tmatesoft.svn.core.wc.SVNMoveClient;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNStatusType;
import org.tmatesoft.svn.core.wc2.*;

import java.io.File;
import java.util.Map;

public class MoveTest {

    @Ignore("SVNKIT-295")
    @Test
    public void testMoveFileOutOfVersionControl() throws Exception {
        //SVNKIT-295
        final TestOptions options = TestOptions.getInstance();

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testMoveFileOutOfVersionControl", options);
        try {
            final SVNURL url = sandbox.createSvnRepository();

            final CommitBuilder commitBuilder = new CommitBuilder(url);
            commitBuilder.addFile("file");
            commitBuilder.commit();

            final WorkingCopy workingCopy = sandbox.checkoutNewWorkingCopy(url);
            final File file = workingCopy.getFile("file");

            final File unversionedDirectory = workingCopy.getFile("unversionedDirectory");
            final File targetFile = new File(unversionedDirectory, "file");

            final SVNClientManager clientManager = SVNClientManager.newInstance();
            try {
                final SVNMoveClient moveClient = clientManager.getMoveClient();
                moveClient.doMove(file, targetFile);

                final Map<File, SvnStatus> statuses = TestUtil.getStatuses(svnOperationFactory, workingCopy.getWorkingCopyDirectory());
                Assert.assertEquals(SVNStatusType.STATUS_DELETED, statuses.get(file).getNodeStatus());
                Assert.assertEquals(SVNStatusType.STATUS_UNVERSIONED, statuses.get(unversionedDirectory).getNodeStatus());
                Assert.assertNull(statuses.get(targetFile));
            } finally {
                clientManager.dispose();
            }
        } finally {
            svnOperationFactory.dispose();
            sandbox.dispose();
        }
    }

    @Test
    public void testRenamedDirectoryWithMovedFile() throws Exception {
        final TestOptions options = TestOptions.getInstance();

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testRenamedDirectoryWithMovedFile", options);
        try {
            final SVNURL url = sandbox.createSvnRepository();

            final CommitBuilder commitBuilder = new CommitBuilder(url);
            commitBuilder.addFile("directory/file");
            commitBuilder.commit();

            final WorkingCopy workingCopy = sandbox.checkoutNewWorkingCopy(url);

            final File directory = workingCopy.getFile("directory");
            final File file = new File(directory, "file");

            final File movedDirectory = workingCopy.getFile("movedDirectory");
            final File movedFile = new File(directory, "movedFile");

            final SvnCopy moveFile = svnOperationFactory.createCopy();
            moveFile.addCopySource(SvnCopySource.create(SvnTarget.fromFile(file), SVNRevision.WORKING));
            moveFile.setSingleTarget(SvnTarget.fromFile(movedFile));
            moveFile.setMove(true);
            moveFile.run();

            final SvnCopy moveDirectory = svnOperationFactory.createCopy();
            moveDirectory.addCopySource(SvnCopySource.create(SvnTarget.fromFile(directory), SVNRevision.WORKING));
            moveDirectory.setSingleTarget(SvnTarget.fromFile(movedDirectory));
            moveDirectory.setMove(true);
            moveDirectory.run();

            final Map<File, SvnStatus> statuses = TestUtil.getStatuses(svnOperationFactory, workingCopy.getWorkingCopyDirectory());
            Assert.assertTrue(statuses.get(movedDirectory).isCopied());
            Assert.assertNotNull(statuses.get(movedDirectory).getCopyFromUrl());

            Assert.assertTrue(statuses.get(new File(movedDirectory, "movedFile")).isCopied());
            Assert.assertNotNull(statuses.get(new File(movedDirectory, "movedFile")).getCopyFromUrl());

            Assert.assertTrue(statuses.get(new File(movedDirectory, "file")).isCopied());
            Assert.assertNull(statuses.get(new File(movedDirectory, "file")).getCopyFromUrl());

            Assert.assertEquals(SVNStatusType.STATUS_ADDED, statuses.get(movedDirectory).getNodeStatus());
            Assert.assertEquals(SVNStatusType.STATUS_ADDED, statuses.get(new File(movedDirectory, "movedFile")).getNodeStatus());
            Assert.assertEquals(SVNStatusType.STATUS_DELETED, statuses.get(new File(movedDirectory, "file")).getNodeStatus());
            Assert.assertEquals(SVNStatusType.STATUS_DELETED, statuses.get(file).getNodeStatus());
            Assert.assertEquals(SVNStatusType.STATUS_DELETED, statuses.get(directory).getNodeStatus());

        } finally {
            svnOperationFactory.dispose();
            sandbox.dispose();
        }
    }

    private String getTestName() {
        return "MoveTest";
    }

}
