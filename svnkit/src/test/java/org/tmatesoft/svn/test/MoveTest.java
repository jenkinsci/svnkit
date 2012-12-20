package org.tmatesoft.svn.test;

import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.wc.SVNClientManager;
import org.tmatesoft.svn.core.wc.SVNMoveClient;
import org.tmatesoft.svn.core.wc.SVNStatusType;
import org.tmatesoft.svn.core.wc2.SvnOperationFactory;
import org.tmatesoft.svn.core.wc2.SvnStatus;

import java.io.File;
import java.util.Map;

@Ignore("SVNKIT-295")
public class MoveTest {

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

    private String getTestName() {
        return "MoveTest";
    }

}
