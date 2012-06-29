package org.tmatesoft.svn.test;

import org.junit.Assert;
import org.junit.Test;
import org.tmatesoft.svn.core.*;
import org.tmatesoft.svn.core.wc.ISVNEventHandler;
import org.tmatesoft.svn.core.wc.SVNEvent;
import org.tmatesoft.svn.core.wc.SVNEventAction;
import org.tmatesoft.svn.core.wc.SVNStatusType;
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
