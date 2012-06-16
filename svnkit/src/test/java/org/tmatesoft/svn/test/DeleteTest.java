package org.tmatesoft.svn.test;

import org.junit.Assert;
import org.junit.Assume;
import org.junit.Ignore;
import org.junit.Test;
import org.tmatesoft.svn.core.*;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.io.SVNRepositoryFactory;
import org.tmatesoft.svn.core.wc.ISVNEventHandler;
import org.tmatesoft.svn.core.wc.SVNEvent;
import org.tmatesoft.svn.core.wc.SVNEventAction;
import org.tmatesoft.svn.core.wc2.SvnOperationFactory;
import org.tmatesoft.svn.core.wc2.SvnRemoteDelete;
import org.tmatesoft.svn.core.wc2.SvnScheduleForRemoval;
import org.tmatesoft.svn.core.wc2.SvnTarget;

import java.io.File;

public class DeleteTest {

    @Ignore("SVN sends unknown kind in this case")
    @Test
    public void testDeleteSendsUnknownNodeKindInEvent() throws Exception {
        final TestOptions options = TestOptions.getInstance();

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testDeleteSendsUnknownNodeKindInEvent", options);
        try {
            final SVNURL url = sandbox.createSvnRepository();

            final CommitBuilder commitBuilder = new CommitBuilder(url);
            commitBuilder.addFile("directory/file");
            commitBuilder.commit();

            final WorkingCopy workingCopy = sandbox.checkoutNewWorkingCopy(url);
            final File workingCopyDirectory = workingCopy.getWorkingCopyDirectory();

            final File directory = new File(workingCopyDirectory, "directory");

            svnOperationFactory.setEventHandler(new ISVNEventHandler() {
                public void handleEvent(SVNEvent event, double progress) throws SVNException {
                    Assert.assertEquals(SVNEventAction.DELETE, event.getAction());
                    Assert.assertEquals(SVNNodeKind.UNKNOWN, event.getNodeKind());
                }

                public void checkCancelled() throws SVNCancelException {
                }
            });

            final SvnScheduleForRemoval scheduleForRemoval = svnOperationFactory.createScheduleForRemoval();
            scheduleForRemoval.setSingleTarget(SvnTarget.fromFile(directory));
            scheduleForRemoval.run();

        } finally {
            svnOperationFactory.dispose();
            sandbox.dispose();
        }
    }

    @Ignore("SVNKIT-264")
    @Test
    public void testRemoteDeleteRootUnreadableDavAccess() throws Exception {
        final TestOptions options = TestOptions.getInstance();
        Assume.assumeTrue(TestUtil.areAllApacheOptionsSpecified(options));

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testRemoteDeleteRootUnreadableDavAccess", options);
        try {
            final SVNURL url = sandbox.createSvnRepositoryWithDavAccess();

            final CommitBuilder commitBuilder = new CommitBuilder(url);
            commitBuilder.addFile("directory/file");
            commitBuilder.commit();

            sandbox.writeActiveAuthzContents(url,
                    "[/]" + "\n" +
                    "* = " + "\n" +
                    "[/directory]" + "\n" +
                    "* = rw" + "\n"
            );

            final SVNURL fileUrl = url.appendPath("directory/file", false);

            final SvnRemoteDelete remoteDelete = svnOperationFactory.createRemoteDelete();
            remoteDelete.setSingleTarget(SvnTarget.fromURL(fileUrl));
            final SVNCommitInfo commitInfo = remoteDelete.run();

            Assert.assertEquals(2, commitInfo.getNewRevision());

            final SVNRepository svnRepository = SVNRepositoryFactory.create(url);
            try {
                final SVNNodeKind nodeKind = svnRepository.checkPath("directory/file", 2);
                Assert.assertEquals(SVNNodeKind.NONE, nodeKind);
            } finally {
                svnRepository.closeSession();
            }


        } finally {
            svnOperationFactory.dispose();
            sandbox.dispose();
        }
    }

    public String getTestName() {
        return "DeleteTest";
    }
}
