package org.tmatesoft.svn.test;

import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;
import org.tmatesoft.sqljet.core.SqlJetException;
import org.tmatesoft.sqljet.core.SqlJetTransactionMode;
import org.tmatesoft.sqljet.core.table.ISqlJetCursor;
import org.tmatesoft.sqljet.core.table.ISqlJetTable;
import org.tmatesoft.sqljet.core.table.SqlJetDb;
import org.tmatesoft.svn.core.*;
import org.tmatesoft.svn.core.internal.db.SVNSqlJetStatement;
import org.tmatesoft.svn.core.internal.io.dav.DAVUtil;
import org.tmatesoft.svn.core.internal.wc.SVNExternal;
import org.tmatesoft.svn.core.internal.wc17.db.statement.SVNWCDbSchema;
import org.tmatesoft.svn.core.wc.*;
import org.tmatesoft.svn.core.wc2.*;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class UpdateTest {

    @Test
    public void testUpdateWithoutChangesReportsUnlock() throws Exception {
        final TestOptions options = TestOptions.getInstance();

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testUpdateWithoutChangesReportsUnlock", options);
        try {
            final SVNURL url = sandbox.createSvnRepository();

            final CommitBuilder commitBuilder = new CommitBuilder(url);
            commitBuilder.addFile("file");
            commitBuilder.commit();

            final WorkingCopy workingCopy = sandbox.checkoutNewWorkingCopy(url);
            final File workingCopyDirectory = workingCopy.getWorkingCopyDirectory();

            final File file = new File(workingCopyDirectory, "file");
            final SVNURL fileUrl = url.appendPath("file", false);

            final SvnSetLock setLock = svnOperationFactory.createSetLock();
            setLock.setSingleTarget(SvnTarget.fromFile(file));
            setLock.run();

            final SvnUnlock unlock = svnOperationFactory.createUnlock();
            unlock.setSingleTarget(SvnTarget.fromURL(fileUrl));
            unlock.run();

            final EventsHandler eventsHandler = new EventsHandler();
            svnOperationFactory.setEventHandler(eventsHandler);

            final SvnUpdate update = svnOperationFactory.createUpdate();
            update.setSingleTarget(SvnTarget.fromFile(workingCopyDirectory));
            update.run();

            final List<SVNEvent> events = eventsHandler.getEvents();
            Assert.assertEquals(3, events.size());
            final SVNEvent updateStartedEvent = events.get(0);
            final SVNEvent unlockedEvent = events.get(1);
            final SVNEvent updateCompletedEvent = events.get(2);

            Assert.assertEquals(SVNEventAction.UPDATE_STARTED, updateStartedEvent.getAction());
            Assert.assertEquals(SVNEventAction.UPDATE_UPDATE, unlockedEvent.getAction());
            Assert.assertEquals(SVNEventAction.UPDATE_COMPLETED, updateCompletedEvent.getAction());

            Assert.assertEquals(workingCopyDirectory, updateStartedEvent.getFile());
            Assert.assertEquals(file, unlockedEvent.getFile());
            Assert.assertEquals(workingCopyDirectory, updateCompletedEvent.getFile());

            Assert.assertEquals(SVNStatusType.LOCK_UNLOCKED, unlockedEvent.getLockStatus());

        } finally {
            svnOperationFactory.dispose();
            sandbox.dispose();
        }
    }

    @Test
    public void testUpdateOverUnversionedFileProducesConflict() throws Exception {
        final TestOptions options = TestOptions.getInstance();

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testUpdateOverUnversionedFileProducesConflict", options);
        try {
            final SVNURL url = sandbox.createSvnRepository();

            final CommitBuilder commitBuilder = new CommitBuilder(url);
            commitBuilder.addFile("file");
            commitBuilder.commit();

            final WorkingCopy workingCopy = sandbox.checkoutNewWorkingCopy(url);
            final File workingCopyDirectory = workingCopy.getWorkingCopyDirectory();

            final SvnCheckout checkout = svnOperationFactory.createCheckout();
            checkout.setSource(SvnTarget.fromURL(url));
            checkout.setSingleTarget(SvnTarget.fromFile(workingCopyDirectory));
            checkout.setDepth(SVNDepth.EMPTY);
            checkout.run();

            final File file = new File(workingCopyDirectory, "file");
            TestUtil.writeFileContentsString(file, "contents");

            if (TestUtil.isNewWorkingCopyTest()) {
                final SvnUpdate update = svnOperationFactory.createUpdate();
                update.setSingleTarget(SvnTarget.fromFile(file));
                update.run();

                //in new working copy a conflict should be produced
                final SvnGetStatus getStatus = svnOperationFactory.createGetStatus();
                getStatus.setSingleTarget(SvnTarget.fromFile(file));
                final SvnStatus status = getStatus.run();

                Assert.assertTrue(status.isConflicted());
            } else {
                final SvnUpdate update = svnOperationFactory.createUpdate();
                update.setSingleTarget(SvnTarget.fromFile(file));
                try {
                    update.run();
                    //in old working copy obstructed update should cause an exception
                    Assert.fail("An exception should be thrown");
                } catch (SVNException e) {
                    //expected
                    Assert.assertEquals(SVNErrorCode.WC_OBSTRUCTED_UPDATE, e.getErrorMessage().getErrorCode());
                }
            }

        } finally {
            svnOperationFactory.dispose();
            sandbox.dispose();
        }
    }

    @Test
    public void testCorrectDepthIsReportedForDepthImmediates() throws Exception {
        final TestOptions options = TestOptions.getInstance();

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testCorrectDepthIsReportedForDepthImmediates", options);
        try {
            final SVNURL url = sandbox.createSvnRepository();

            final CommitBuilder commitBuilder = new CommitBuilder(url);
            commitBuilder.addFile("directory/file");
            commitBuilder.commit();

            final WorkingCopy workingCopy = sandbox.checkoutNewWorkingCopy(url);
            final File workingCopyDirectory = workingCopy.getWorkingCopyDirectory();
            final File file = workingCopy.getFile("directory/file");
            TestUtil.writeFileContentsString(file, "changed contents");

            final SvnCommit commit = svnOperationFactory.createCommit();
            commit.setSingleTarget(SvnTarget.fromFile(workingCopyDirectory));
            commit.run();

            final SvnUpdate update = svnOperationFactory.createUpdate();
            update.setDepth(SVNDepth.IMMEDIATES);
            update.setSingleTarget(SvnTarget.fromFile(workingCopyDirectory));
            final long[] revision = update.run();

            Assert.assertEquals(2, revision[0]);

        } finally {
            svnOperationFactory.dispose();
            sandbox.dispose();
        }
    }


    @Test
    public void testDavCacheIsCleaned() throws Exception {
        final TestOptions options = TestOptions.getInstance();

        Assume.assumeTrue(TestUtil.isNewWorkingCopyTest());
        Assume.assumeTrue(TestUtil.areAllApacheOptionsSpecified(options));

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testDavCacheIsCleaned", options);
        try {
            final SVNURL url = sandbox.createSvnRepositoryWithDavAccess();

            final SVNExternal external = new SVNExternal("external", url.appendPath("file", false).toString(), SVNRevision.HEAD, SVNRevision.HEAD, false, false, true);

            final CommitBuilder commitBuilder1 = new CommitBuilder(url);
            commitBuilder1.addFile("file");

            commitBuilder1.setDirectoryProperty("", SVNProperty.EXTERNALS, SVNPropertyValue.create(external.toString()));
            commitBuilder1.commit();

            final WorkingCopy workingCopy = sandbox.checkoutNewWorkingCopy(url);
            final File workingCopyDirectory = workingCopy.getWorkingCopyDirectory();

            final CommitBuilder commitBuilder2 = new CommitBuilder(url);
            commitBuilder2.changeFile("file", "contents".getBytes());
            commitBuilder2.commit();

            DAVUtil.setUseDAVWCURL(false);

            final SvnUpdate update = svnOperationFactory.createUpdate();
            update.setSingleTarget(SvnTarget.fromFile(workingCopyDirectory));
            update.run();

            assertDavPropertiesAreCleaned(workingCopy);

        } finally {
            svnOperationFactory.dispose();
            sandbox.dispose();
        }
    }

    private void assertDavPropertiesAreCleaned(WorkingCopy workingCopy) throws SqlJetException, SVNException {
        final SqlJetDb db = SqlJetDb.open(workingCopy.getWCDbFile(), false);
        try {
            final ISqlJetTable table = db.getTable(SVNWCDbSchema.NODES.name());
            db.beginTransaction(SqlJetTransactionMode.READ_ONLY);
            final ISqlJetCursor cursor = table.open();

            for (; !cursor.eof(); cursor.next()) {
                final SVNProperties properties = SVNSqlJetStatement.parseProperties(cursor.getBlobAsArray(SVNWCDbSchema.NODES__Fields.dav_cache.name()));
                final SVNPropertyValue wcUrlPropertyValue = properties == null ? null : properties.getSVNPropertyValue(SVNProperty.WC_URL);
                Assert.assertNull(wcUrlPropertyValue);
            }
            cursor.close();
            db.commit();
        } finally {
            db.close();
        }
    }

    private String getTestName() {
        return "UpdateTest";
    }

    private static class EventsHandler implements ISVNEventHandler {

        private final List<SVNEvent> events;

        private EventsHandler() {
            this.events = new ArrayList<SVNEvent>();
        }

        public List<SVNEvent> getEvents() {
            return events;
        }

        public void handleEvent(SVNEvent event, double progress) throws SVNException {
            events.add(event);
        }

        public void checkCancelled() throws SVNCancelException {
        }
    }
}
