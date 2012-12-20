package org.tmatesoft.svn.test;

import org.junit.Assert;
import org.junit.Assume;
import org.junit.Ignore;
import org.junit.Test;
import org.tmatesoft.sqljet.core.SqlJetException;
import org.tmatesoft.sqljet.core.SqlJetTransactionMode;
import org.tmatesoft.sqljet.core.table.ISqlJetCursor;
import org.tmatesoft.sqljet.core.table.ISqlJetTable;
import org.tmatesoft.sqljet.core.table.SqlJetDb;
import org.tmatesoft.svn.core.*;
import org.tmatesoft.svn.core.internal.wc17.db.statement.SVNWCDbSchema;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.io.SVNRepositoryFactory;
import org.tmatesoft.svn.core.wc.ISVNEventHandler;
import org.tmatesoft.svn.core.wc.SVNEvent;
import org.tmatesoft.svn.core.wc.SVNEventAction;
import org.tmatesoft.svn.core.wc2.*;

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

            final SVNRepository svnRepository = SVNRepositoryFactory.create(url.appendPath("directory", false));
            try {
                Assert.assertEquals(SVNNodeKind.NONE, svnRepository.checkPath("file", 2));
            } finally {
                svnRepository.closeSession();
            }


        } finally {
            svnOperationFactory.dispose();
            sandbox.dispose();
        }
    }

    @Test
    public void testRemoteDeleteSeveralTargetsRootUnreadableDavAccess() throws Exception {
        final TestOptions options = TestOptions.getInstance();
        Assume.assumeTrue(TestUtil.areAllApacheOptionsSpecified(options));

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testRemoteDeleteSeveralTargetsRootUnreadableDavAccess", options);
        try {
            final SVNURL url = sandbox.createSvnRepositoryWithDavAccess();

            final CommitBuilder commitBuilder = new CommitBuilder(url);
            commitBuilder.addFile("directory/file1");
            commitBuilder.addFile("directory/file2");
            commitBuilder.commit();

            sandbox.writeActiveAuthzContents(url,
                    "[/]" + "\n" +
                    "* = " + "\n" +
                    "[/directory]" + "\n" +
                    "* = rw" + "\n"
            );

            final SVNURL file1Url = url.appendPath("directory/file1", false);
            final SVNURL file2Url = url.appendPath("directory/file2", false);

            final SvnRemoteDelete remoteDelete = svnOperationFactory.createRemoteDelete();
            remoteDelete.addTarget(SvnTarget.fromURL(file1Url));
            remoteDelete.addTarget(SvnTarget.fromURL(file2Url));
            final SVNCommitInfo commitInfo = remoteDelete.run();

            Assert.assertEquals(2, commitInfo.getNewRevision());

            final SVNRepository svnRepository = SVNRepositoryFactory.create(url.appendPath("directory", false));
            try {
                Assert.assertEquals(SVNNodeKind.NONE, svnRepository.checkPath("file1", 2));
                Assert.assertEquals(SVNNodeKind.NONE, svnRepository.checkPath("file2", 2));
            } finally {
                svnRepository.closeSession();
            }

        } finally {
            svnOperationFactory.dispose();
            sandbox.dispose();
        }
    }

    @Test
    public void testRemovalOfExcludedNodeDoesntAddBaseDeletedEntry() throws Exception {
        final TestOptions options = TestOptions.getInstance();

        Assume.assumeTrue(TestUtil.isNewWorkingCopyTest());

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testRemovalOfExcludedNodeDoesntAddBaseDeletedEntry", options);
        try {
            final SVNURL url = sandbox.createSvnRepository();

            final CommitBuilder commitBuilder = new CommitBuilder(url);
            commitBuilder.addDirectory("directory/subdirectory");
            commitBuilder.commit();

            final WorkingCopy workingCopy = sandbox.checkoutNewWorkingCopy(url);

            final SvnUpdate update = svnOperationFactory.createUpdate();
            update.setDepthIsSticky(true);
            update.setDepth(SVNDepth.EXCLUDE);
            update.setSingleTarget(SvnTarget.fromFile(workingCopy.getFile("directory/subdirectory")));
            update.run();

            final SvnScheduleForRemoval scheduleForRemoval = svnOperationFactory.createScheduleForRemoval();
            scheduleForRemoval.setSingleTarget(SvnTarget.fromFile(workingCopy.getFile("directory")));
            scheduleForRemoval.run();

            isMarkedAsExcludedOnly(workingCopy, "directory/subdirectory");

        } finally {
            svnOperationFactory.dispose();
            sandbox.dispose();
        }
    }

    private void isMarkedAsExcludedOnly(WorkingCopy workingCopy, String path) throws SqlJetException {
        final SqlJetDb db = SqlJetDb.open(workingCopy.getWCDbFile(), false);
        try {
            final ISqlJetTable table = db.getTable(SVNWCDbSchema.NODES.name());
            db.beginTransaction(SqlJetTransactionMode.READ_ONLY);
            final ISqlJetCursor cursor = table.open();

            for (; !cursor.eof(); cursor.next()) {
                final String localRelpath = cursor.getString(SVNWCDbSchema.NODES__Fields.local_relpath.name());

                if (!localRelpath.equals(path)) {
                    continue;
                }

                final String presence = cursor.getString(SVNWCDbSchema.NODES__Fields.presence.name());
                Assert.assertEquals("excluded", presence);
            }
            cursor.close();
            db.commit();
        } finally {
            db.close();
        }
    }

    public String getTestName() {
        return "DeleteTest";
    }
}
