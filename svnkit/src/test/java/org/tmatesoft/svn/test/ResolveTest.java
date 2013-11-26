package org.tmatesoft.svn.test;

import org.junit.Assert;
import org.junit.Test;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.wc.*;
import org.tmatesoft.svn.core.wc2.*;

import java.io.File;
import java.util.Map;

public class ResolveTest {
    @Test
    public void testResolveMovedFileDeletionLeavesNoUnversionedFile() throws Exception {
        final TestOptions options = TestOptions.getInstance();

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testResolveMovedFileDeletionLeavesNoUnversionedFile", options);
        try {
            final SVNURL url = sandbox.createSvnRepository();

            final CommitBuilder commitBuilder1 = new CommitBuilder(url);
            commitBuilder1.addFile("directory/subdirectory/file");
            commitBuilder1.commit();

            final CommitBuilder commitBuilder2 = new CommitBuilder(url);
            commitBuilder2.delete("directory/subdirectory/file");
            commitBuilder2.commit();

            final WorkingCopy workingCopy = sandbox.checkoutNewWorkingCopy(url, 1);
            final File directory = workingCopy.getFile("directory");
            final File subdirectory = workingCopy.getFile("directory/subdirectory");
            final File renamedSubdirectory = workingCopy.getFile("directory/renamedSubdirectory");
            final File workingCopyDirectory = workingCopy.getWorkingCopyDirectory();

            final SvnCopy copy = svnOperationFactory.createCopy();
            copy.addCopySource(SvnCopySource.create(SvnTarget.fromFile(subdirectory), SVNRevision.WORKING));
            copy.setSingleTarget(SvnTarget.fromFile(renamedSubdirectory));
            copy.setMove(true);
            copy.run();

            final SvnUpdate update = svnOperationFactory.createUpdate();
            update.setSingleTarget(SvnTarget.fromFile(workingCopyDirectory));
            update.run();

            final SvnResolve resolve = svnOperationFactory.createResolve();
            resolve.setConflictChoice(SVNConflictChoice.MINE_CONFLICT);
            resolve.setSingleTarget(SvnTarget.fromFile(subdirectory));
            resolve.run();

            final Map<File, SvnStatus> statuses = TestUtil.getStatuses(svnOperationFactory, workingCopyDirectory);
            Assert.assertEquals(4, statuses.size());
            Assert.assertEquals(SVNStatusType.STATUS_DELETED, statuses.get(subdirectory).getNodeStatus());
            Assert.assertEquals(SVNStatusType.STATUS_ADDED, statuses.get(renamedSubdirectory).getNodeStatus());
            Assert.assertTrue(statuses.get(renamedSubdirectory).isCopied());
            Assert.assertEquals(SVNStatusType.STATUS_NORMAL, statuses.get(directory).getNodeStatus());
            Assert.assertEquals(SVNStatusType.STATUS_NORMAL, statuses.get(workingCopyDirectory).getNodeStatus());

        } finally {
            svnOperationFactory.dispose();
            sandbox.dispose();
        }
    }

    private String getTestName() {
        return getClass().getSimpleName();
    }
}
