package org.tmatesoft.svn.test;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.Assert;
import org.junit.Assume;
import org.junit.Ignore;
import org.junit.Test;
import org.tmatesoft.sqljet.core.SqlJetException;
import org.tmatesoft.sqljet.core.SqlJetTransactionMode;
import org.tmatesoft.sqljet.core.table.ISqlJetCursor;
import org.tmatesoft.sqljet.core.table.ISqlJetTable;
import org.tmatesoft.sqljet.core.table.SqlJetDb;
import org.tmatesoft.svn.core.SVNCancelException;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNProperties;
import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.SVNPropertyValue;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.db.SVNSqlJetStatement;
import org.tmatesoft.svn.core.internal.io.dav.DAVUtil;
import org.tmatesoft.svn.core.internal.wc.DefaultSVNOptions;
import org.tmatesoft.svn.core.internal.wc.SVNExternal;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.internal.wc17.SVNWCContext;
import org.tmatesoft.svn.core.internal.wc17.db.ISVNWCDb;
import org.tmatesoft.svn.core.internal.wc17.db.statement.SVNWCDbSchema;
import org.tmatesoft.svn.core.internal.wc2.compat.SvnCodec;
import org.tmatesoft.svn.core.internal.wc2.ng.SvnNgDowngrade;
import org.tmatesoft.svn.core.wc.ISVNConflictHandler;
import org.tmatesoft.svn.core.wc.ISVNEventHandler;
import org.tmatesoft.svn.core.wc.SVNConflictAction;
import org.tmatesoft.svn.core.wc.SVNConflictChoice;
import org.tmatesoft.svn.core.wc.SVNConflictDescription;
import org.tmatesoft.svn.core.wc.SVNConflictReason;
import org.tmatesoft.svn.core.wc.SVNConflictResult;
import org.tmatesoft.svn.core.wc.SVNEvent;
import org.tmatesoft.svn.core.wc.SVNEventAction;
import org.tmatesoft.svn.core.wc.SVNOperation;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNStatus;
import org.tmatesoft.svn.core.wc.SVNStatusType;
import org.tmatesoft.svn.core.wc.SVNTreeConflictDescription;
import org.tmatesoft.svn.core.wc2.SvnCheckout;
import org.tmatesoft.svn.core.wc2.SvnCommit;
import org.tmatesoft.svn.core.wc2.SvnCopy;
import org.tmatesoft.svn.core.wc2.SvnCopySource;
import org.tmatesoft.svn.core.wc2.SvnGetInfo;
import org.tmatesoft.svn.core.wc2.SvnGetProperties;
import org.tmatesoft.svn.core.wc2.SvnGetStatus;
import org.tmatesoft.svn.core.wc2.SvnInfo;
import org.tmatesoft.svn.core.wc2.SvnOperationFactory;
import org.tmatesoft.svn.core.wc2.SvnRevert;
import org.tmatesoft.svn.core.wc2.SvnScheduleForAddition;
import org.tmatesoft.svn.core.wc2.SvnScheduleForRemoval;
import org.tmatesoft.svn.core.wc2.SvnSetLock;
import org.tmatesoft.svn.core.wc2.SvnSetProperty;
import org.tmatesoft.svn.core.wc2.SvnStatus;
import org.tmatesoft.svn.core.wc2.SvnTarget;
import org.tmatesoft.svn.core.wc2.SvnUnlock;
import org.tmatesoft.svn.core.wc2.SvnUpdate;

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
            Assert.assertEquals(SVNEventAction.UPDATE_BROKEN_LOCK, unlockedEvent.getAction());
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

    @Test
    public void testUpdateAlwaysUpdatesFileTimestamp() throws Exception {
        //SVNKIT-322: file timestamp is updated to some time in past
        // if update rolls back a file to some contents that is already present in .svn/pristine
        final TestOptions options = TestOptions.getInstance();

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testUpdateAlwaysUpdatesFileTimestamp", options);
        try {
            final SVNURL url = sandbox.createSvnRepository();

            final CommitBuilder commitBuilder1 = new CommitBuilder(url);
            commitBuilder1.addFile("file", "original contents".getBytes());
            commitBuilder1.commit();

            final CommitBuilder commitBuilder2 = new CommitBuilder(url);
            commitBuilder2.changeFile("file", "changes contents".getBytes());
            commitBuilder2.commit();

            final CommitBuilder commitBuilder3 = new CommitBuilder(url);
            commitBuilder3.changeFile("file", "original contents".getBytes());
            commitBuilder3.commit();

            final WorkingCopy workingCopy = sandbox.checkoutNewWorkingCopy(url);
            final File file = workingCopy.getFile("file");

            final SvnGetInfo getInfoBeforeUpdates = svnOperationFactory.createGetInfo();
            getInfoBeforeUpdates.setSingleTarget(SvnTarget.fromFile(file));
            final SvnInfo infoBeforeUpdates = getInfoBeforeUpdates.run();

            Thread.sleep(1000); // make expected file timestamp different from the actual

            final SvnUpdate update1 = svnOperationFactory.createUpdate();
            update1.setSingleTarget(SvnTarget.fromFile(file));
            update1.setRevision(SVNRevision.create(2));
            update1.run();

            final SvnGetInfo getInfoBetweenUpdates = svnOperationFactory.createGetInfo();
            getInfoBetweenUpdates.setSingleTarget(SvnTarget.fromFile(file));
            final SvnInfo infoBetweenUpdates = getInfoBetweenUpdates.run();

            Thread.sleep(1000); // make expected file timestamp different from the actual

            final SvnUpdate update2 = svnOperationFactory.createUpdate();
            update2.setSingleTarget(SvnTarget.fromFile(file));
            update2.setRevision(SVNRevision.create(1));
            update2.run();

            final SvnGetInfo getInfoAfterUpdates = svnOperationFactory.createGetInfo();
            getInfoAfterUpdates.setSingleTarget(SvnTarget.fromFile(file));
            final SvnInfo infoAfterUpdates = getInfoAfterUpdates.run();

            final long timestampBeforeUpdates = infoBeforeUpdates.getWcInfo().getRecordedTime();
            final long timestampBetweenUpdates = infoBetweenUpdates.getWcInfo().getRecordedTime();
            final long timestampAfterUpdates = infoAfterUpdates.getWcInfo().getRecordedTime();

            // timestamps should always increase, otherwise this confuses build tools that rely on timestamps
            Assert.assertTrue(timestampBeforeUpdates < timestampBetweenUpdates);
            Assert.assertTrue(timestampBetweenUpdates < timestampAfterUpdates);

        } finally {
            svnOperationFactory.dispose();
            sandbox.dispose();
        }
    }

    @Test
    public void testUpdateBinaryFile() throws Exception {
        final TestOptions options = TestOptions.getInstance();

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testUpdateBinaryFile", options);
        try {
            final SVNURL url = sandbox.createSvnRepository();

            final CommitBuilder commitBuilder1 = new CommitBuilder(url);
            commitBuilder1.addFile("file", new byte[]{0, 1, 2, 3});
            commitBuilder1.setFileProperty("file", SVNProperty.MIME_TYPE, SVNPropertyValue.create("application/octet-stream"));
            commitBuilder1.commit();

            final CommitBuilder commitBuilder2 = new CommitBuilder(url);
            commitBuilder2.changeFile("file", new byte[]{0, 1, 2, 3, 4});
            commitBuilder2.commit();

            final WorkingCopy workingCopy = sandbox.checkoutNewWorkingCopy(url, 1);
            final File file = workingCopy.getFile("file");
            TestUtil.writeFileContentsString(file, "changed");

            final SvnUpdate update = svnOperationFactory.createUpdate();
            update.setSingleTarget(SvnTarget.fromFile(workingCopy.getWorkingCopyDirectory()));
            update.run();

            final Map<File,SvnStatus> statuses = TestUtil.getStatuses(svnOperationFactory, workingCopy.getWorkingCopyDirectory());
            Assert.assertEquals(SVNStatusType.STATUS_CONFLICTED, statuses.get(file).getNodeStatus());
            Assert.assertEquals(SVNStatusType.STATUS_NORMAL, statuses.get(file).getPropertiesStatus());
            Assert.assertEquals(SVNStatusType.STATUS_CONFLICTED, statuses.get(file).getTextStatus());

        } finally {
            svnOperationFactory.dispose();
            sandbox.dispose();
        }
    }

    @Test
    public void testUpdateTextFileResultsInTextConflict() throws Exception {
        final TestOptions options = TestOptions.getInstance();

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testUpdateTextFileResultsInTextConflict", options);
        try {
            final SVNURL url = sandbox.createSvnRepository();

            final CommitBuilder commitBuilder1 = new CommitBuilder(url);
            commitBuilder1.addFile("file", "content".getBytes());
            commitBuilder1.setFileProperty("file", SVNProperty.MIME_TYPE, SVNPropertyValue.create("text/plain"));
            commitBuilder1.commit();

            final CommitBuilder commitBuilder2 = new CommitBuilder(url);
            commitBuilder2.changeFile("file", "changed content".getBytes());
            commitBuilder2.commit();

            final WorkingCopy workingCopy = sandbox.checkoutNewWorkingCopy(url, 1);
            final File file = workingCopy.getFile("file");
            TestUtil.writeFileContentsString(file, "changed in another way");

            final SvnUpdate update = svnOperationFactory.createUpdate();
            update.setSingleTarget(SvnTarget.fromFile(workingCopy.getWorkingCopyDirectory()));
            update.run();

            final Map<File,SvnStatus> statuses = TestUtil.getStatuses(svnOperationFactory, workingCopy.getWorkingCopyDirectory());
            Assert.assertEquals(SVNStatusType.STATUS_CONFLICTED, statuses.get(file).getNodeStatus());

        } finally {
            svnOperationFactory.dispose();
            sandbox.dispose();
        }
    }

    @Test
    public void testUpdateTextFileResultsInPropertiesConflict() throws Exception {
        final TestOptions options = TestOptions.getInstance();

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testUpdateTextFileResultsInPropertiesConflict", options);
        try {
            final SVNURL url = sandbox.createSvnRepository();

            final CommitBuilder commitBuilder1 = new CommitBuilder(url);
            commitBuilder1.addFile("file", "content".getBytes());
            commitBuilder1.setFileProperty("file", "propertyName", SVNPropertyValue.create("propertyValue"));
            commitBuilder1.commit();

            final CommitBuilder commitBuilder2 = new CommitBuilder(url);
            commitBuilder2.setFileProperty("file", "propertyName", SVNPropertyValue.create("changedValue"));
            commitBuilder2.commit();

            final WorkingCopy workingCopy = sandbox.checkoutNewWorkingCopy(url, 1);
            final File file = workingCopy.getFile("file");
            workingCopy.setProperty(file, "propertyName", SVNPropertyValue.create("changedInAnotherWay"));

            final SvnUpdate update = svnOperationFactory.createUpdate();
            update.setSingleTarget(SvnTarget.fromFile(workingCopy.getWorkingCopyDirectory()));
            update.run();

            final Map<File,SvnStatus> statuses = TestUtil.getStatuses(svnOperationFactory, workingCopy.getWorkingCopyDirectory());
            Assert.assertEquals(SVNStatusType.STATUS_CONFLICTED, statuses.get(file).getNodeStatus());
            Assert.assertEquals(SVNStatusType.STATUS_CONFLICTED, statuses.get(file).getPropertiesStatus());
            Assert.assertEquals(SVNStatusType.STATUS_NORMAL, statuses.get(file).getTextStatus());

        } finally {
            svnOperationFactory.dispose();
            sandbox.dispose();
        }
    }

    @Test
    public void testUpdateTextFileResultsInTextAndPropertiesConflict() throws Exception {
        final TestOptions options = TestOptions.getInstance();

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testUpdateTextFileResultsInTextAndPropertiesConflict", options);
        try {
            final SVNURL url = sandbox.createSvnRepository();

            final CommitBuilder commitBuilder1 = new CommitBuilder(url);
            commitBuilder1.addFile("file", "content".getBytes());
            commitBuilder1.setFileProperty("file", "propertyName", SVNPropertyValue.create("propertyValue"));
            commitBuilder1.commit();

            final CommitBuilder commitBuilder2 = new CommitBuilder(url);
            commitBuilder2.changeFile("file", "changed content".getBytes());
            commitBuilder2.setFileProperty("file", "propertyName", SVNPropertyValue.create("changedValue"));
            commitBuilder2.commit();

            final WorkingCopy workingCopy = sandbox.checkoutNewWorkingCopy(url, 1);
            final File file = workingCopy.getFile("file");
            TestUtil.writeFileContentsString(file, "changed in another way content");
            workingCopy.setProperty(file, "propertyName", SVNPropertyValue.create("changedInAnotherWay"));

            final EventsHandler events = new EventsHandler();
            svnOperationFactory.setEventHandler(events);

            final SvnUpdate update = svnOperationFactory.createUpdate();
            update.setSingleTarget(SvnTarget.fromFile(workingCopy.getWorkingCopyDirectory()));
            update.run();

            final Map<File,SvnStatus> statuses = TestUtil.getStatuses(svnOperationFactory, workingCopy.getWorkingCopyDirectory());
            Assert.assertEquals(SVNStatusType.STATUS_CONFLICTED, statuses.get(file).getNodeStatus());
            Assert.assertEquals(SVNStatusType.STATUS_CONFLICTED, statuses.get(file).getTextStatus());
            Assert.assertEquals(SVNStatusType.STATUS_CONFLICTED, statuses.get(file).getPropertiesStatus());

            for (SVNEvent event : events.events) {
                if (file.equals(event.getFile())) {
                    Assert.assertEquals(SVNStatusType.CONFLICTED, event.getContentsStatus());
                    Assert.assertEquals(SVNStatusType.CONFLICTED, event.getPropertiesStatus());
                }
            }
        } finally {
            svnOperationFactory.dispose();
            sandbox.dispose();
        }
    }

    @Test
    public void testTreeConflict() throws Exception {
        final TestOptions options = TestOptions.getInstance();

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testTreeConflict", options);
        try {
            final SVNURL url = sandbox.createSvnRepository();

            final CommitBuilder commitBuilder1 = new CommitBuilder(url);
            commitBuilder1.addFile("file", "content".getBytes());
            commitBuilder1.setFileProperty("file", SVNProperty.MIME_TYPE, SVNPropertyValue.create("text/plain"));
            commitBuilder1.commit();

            final CommitBuilder commitBuilder2 = new CommitBuilder(url);
            commitBuilder2.changeFile("file", "changed content".getBytes());
            commitBuilder2.commit();

            final WorkingCopy workingCopy = sandbox.checkoutNewWorkingCopy(url, 1);
            final File file = workingCopy.getFile("file");
            workingCopy.delete(file);

            final SvnUpdate update = svnOperationFactory.createUpdate();
            update.setSingleTarget(SvnTarget.fromFile(workingCopy.getWorkingCopyDirectory()));
            update.run();

            final Map<File,SvnStatus> statuses = TestUtil.getStatuses(svnOperationFactory, workingCopy.getWorkingCopyDirectory());
            Assert.assertEquals(SVNStatusType.STATUS_DELETED, statuses.get(file).getNodeStatus());
            Assert.assertTrue(statuses.get(file).isConflicted());
        } finally {
            svnOperationFactory.dispose();
            sandbox.dispose();
        }
    }

    @Test
    public void testLocalModificationIncomingDeletionTreeConflict() throws Exception {
        final TestOptions options = TestOptions.getInstance();

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testLocalModificationIncomingDeletionTreeConflict", options);
        try {
            final SVNURL url = sandbox.createSvnRepository();

            final CommitBuilder commitBuilder1 = new CommitBuilder(url);
            commitBuilder1.addFile("file");
            commitBuilder1.addFile("directory/file");
            commitBuilder1.commit();

            final CommitBuilder commitBuilder2 = new CommitBuilder(url);
            commitBuilder2.delete("file");
            commitBuilder2.delete("directory");
            commitBuilder2.commit();

            final WorkingCopy workingCopy = sandbox.checkoutNewWorkingCopy(url, 1);
            final File file = workingCopy.getFile("file");
            final File directory = workingCopy.getFile("directory");
            final File anotherFile = workingCopy.getFile("directory/file");

            TestUtil.writeFileContentsString(file, "changed");
            TestUtil.writeFileContentsString(anotherFile, "changed");

            final SvnUpdate update = svnOperationFactory.createUpdate();
            update.setSingleTarget(SvnTarget.fromFile(workingCopy.getWorkingCopyDirectory()));
            update.run();

            final Map<File, SvnStatus> statuses = TestUtil.getStatuses(svnOperationFactory, workingCopy.getWorkingCopyDirectory());
            Assert.assertTrue(statuses.get(file).isCopied());
            Assert.assertTrue(statuses.get(directory).isCopied());
            Assert.assertTrue(statuses.get(anotherFile).isCopied());
            Assert.assertTrue(statuses.get(file).isConflicted());
            Assert.assertTrue(statuses.get(directory).isConflicted());
            Assert.assertFalse(statuses.get(anotherFile).isConflicted());
            Assert.assertEquals(SVNStatusType.STATUS_ADDED, statuses.get(file).getNodeStatus());
            Assert.assertEquals(SVNStatusType.STATUS_ADDED, statuses.get(directory).getNodeStatus());
            Assert.assertEquals(SVNStatusType.STATUS_MODIFIED, statuses.get(anotherFile).getNodeStatus());

        } finally {
            svnOperationFactory.dispose();
            sandbox.dispose();
        }
    }

    @Test
    public void testUpdateToZeroRevsionDeletesFilesInWorkingCopy() throws Exception {
        final TestOptions options = TestOptions.getInstance();

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testUpdateToZeroRevsionDeletesFilesInWorkingCopy", options);
        try {
            final SVNURL url = sandbox.createSvnRepository();

            final WorkingCopy workingCopy = sandbox.checkoutNewWorkingCopy(url);
            final File workingCopyDirectory = workingCopy.getWorkingCopyDirectory();
            final File file = workingCopy.getFile("directory/subdirectory/file");
            SVNFileUtil.ensureDirectoryExists(file.getParentFile());
            TestUtil.writeFileContentsString(file, "");

            workingCopy.add(file);
            workingCopy.commit("");

            final EventsHandler eventHandler = new EventsHandler();
            svnOperationFactory.setEventHandler(eventHandler);

            final SvnUpdate update = svnOperationFactory.createUpdate();
            update.setRevision(SVNRevision.create(0));
            update.setSingleTarget(SvnTarget.fromFile(workingCopyDirectory));
            update.run();

            final Map<File, SvnStatus> statuses = TestUtil.getStatuses(svnOperationFactory, workingCopyDirectory);
            Assert.assertEquals(1, statuses.size());
            Assert.assertEquals(0, statuses.get(workingCopyDirectory).getRevision());
            Assert.assertEquals(SVNStatusType.STATUS_NORMAL, statuses.get(workingCopyDirectory).getNodeStatus());
            Assert.assertFalse(workingCopy.getFile("directory").exists());

        } finally {
            svnOperationFactory.dispose();
            sandbox.dispose();
        }
    }

    @Test
    public void testMergeDirectoryProperties() throws Exception {
        final TestOptions options = TestOptions.getInstance();

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testMergeDirectoryProperties", options);
        try {
            final SVNURL url = sandbox.createSvnRepository();

            final CommitBuilder commitBuilder1 = new CommitBuilder(url);
            commitBuilder1.addDirectory("directory");
            commitBuilder1.commit();

            final CommitBuilder commitBuilder2 = new CommitBuilder(url);
            commitBuilder2.setDirectoryProperty("directory", "remoteProperty", SVNPropertyValue.create("remotePropertyValue"));
            commitBuilder2.commit();

            final WorkingCopy workingCopy = sandbox.checkoutNewWorkingCopy(url, 1);
            final File workingCopyDirectory = workingCopy.getWorkingCopyDirectory();
            final File directory = workingCopy.getFile("directory");
            workingCopy.setProperty(directory, "localProperty", SVNPropertyValue.create("localPropertyValue"));

            final SvnUpdate update = svnOperationFactory.createUpdate();
            update.setSingleTarget(SvnTarget.fromFile(workingCopyDirectory));
            update.run();

            final SvnGetProperties getProperties = svnOperationFactory.createGetProperties();
            getProperties.setSingleTarget(SvnTarget.fromFile(directory));
            SVNProperties properties = getProperties.run();

            Assert.assertEquals(2, properties.size());
            Assert.assertEquals("remotePropertyValue", SVNPropertyValue.getPropertyAsString(properties.getSVNPropertyValue("remoteProperty")));
            Assert.assertEquals("localPropertyValue", SVNPropertyValue.getPropertyAsString(properties.getSVNPropertyValue("localProperty")));


        } finally {
            svnOperationFactory.dispose();
            sandbox.dispose();
        }
    }

    @Test
    public void testDirectoryPropertiesConflict() throws Exception {
        final TestOptions options = TestOptions.getInstance();

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testDirectoryPropertiesConflict", options);
        try {
            final SVNURL url = sandbox.createSvnRepository();

            final CommitBuilder commitBuilder1 = new CommitBuilder(url);
            commitBuilder1.addDirectory("directory");
            commitBuilder1.commit();

            final CommitBuilder commitBuilder2 = new CommitBuilder(url);
            commitBuilder2.setDirectoryProperty("directory", "property", SVNPropertyValue.create("remotePropertyValue"));
            commitBuilder2.commit();

            final WorkingCopy workingCopy = sandbox.checkoutNewWorkingCopy(url, 1);
            final File workingCopyDirectory = workingCopy.getWorkingCopyDirectory();
            final File directory = workingCopy.getFile("directory");
            workingCopy.setProperty(directory, "property", SVNPropertyValue.create("localPropertyValue"));

            final SvnUpdate update = svnOperationFactory.createUpdate();
            update.setSingleTarget(SvnTarget.fromFile(workingCopyDirectory));
            update.run();

            final SvnGetProperties getProperties = svnOperationFactory.createGetProperties();
            getProperties.setSingleTarget(SvnTarget.fromFile(directory));
            SVNProperties properties = getProperties.run();

            Assert.assertEquals(1, properties.size());
            Assert.assertEquals("localPropertyValue", SVNPropertyValue.getPropertyAsString(properties.getSVNPropertyValue("property")));

            final Map<File, SvnStatus> statuses = TestUtil.getStatuses(svnOperationFactory, workingCopyDirectory);
            Assert.assertEquals(SVNStatusType.STATUS_CONFLICTED, statuses.get(directory).getNodeStatus());

        } finally {
            svnOperationFactory.dispose();
            sandbox.dispose();
        }
    }

    @Test
    public void testMergeFileProperties() throws Exception {
        final TestOptions options = TestOptions.getInstance();

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testMergeFileProperties", options);
        try {
            final SVNURL url = sandbox.createSvnRepository();

            final CommitBuilder commitBuilder1 = new CommitBuilder(url);
            commitBuilder1.addFile("file");
            commitBuilder1.commit();

            final CommitBuilder commitBuilder2 = new CommitBuilder(url);
            commitBuilder2.setFileProperty("file", "remoteProperty", SVNPropertyValue.create("remotePropertyValue"));
            commitBuilder2.commit();

            final WorkingCopy workingCopy = sandbox.checkoutNewWorkingCopy(url, 1);
            final File workingCopyDirectory = workingCopy.getWorkingCopyDirectory();
            final File file = workingCopy.getFile("file");
            workingCopy.setProperty(file, "localProperty", SVNPropertyValue.create("localPropertyValue"));

            final SvnUpdate update = svnOperationFactory.createUpdate();
            update.setSingleTarget(SvnTarget.fromFile(workingCopyDirectory));
            update.run();

            final SvnGetProperties getProperties = svnOperationFactory.createGetProperties();
            getProperties.setSingleTarget(SvnTarget.fromFile(file));
            SVNProperties properties = getProperties.run();

            Assert.assertEquals(2, properties.size());
            Assert.assertEquals("remotePropertyValue", SVNPropertyValue.getPropertyAsString(properties.getSVNPropertyValue("remoteProperty")));
            Assert.assertEquals("localPropertyValue", SVNPropertyValue.getPropertyAsString(properties.getSVNPropertyValue("localProperty")));

        } finally {
            svnOperationFactory.dispose();
            sandbox.dispose();
        }
    }

    @Test
    public void testUpdateDeletedMissingDirectory() throws Exception {
        final TestOptions options = TestOptions.getInstance();

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testUpdateDeletedMissingDirectory", options);
        try {
            final SVNURL url = sandbox.createSvnRepository();

            final CommitBuilder commitBuilder1 = new CommitBuilder(url);
            commitBuilder1.addDirectory("directory/subdirectory");
            commitBuilder1.commit();

            final CommitBuilder commitBuilder2 = new CommitBuilder(url);
            commitBuilder2.delete("directory");
            commitBuilder2.commit();

            final WorkingCopy workingCopy = sandbox.checkoutNewWorkingCopy(url, 1);
            final File workingCopyDirectory = workingCopy.getWorkingCopyDirectory();
            final File directory = workingCopy.getFile("directory");

            SVNFileUtil.deleteAll(directory, true);

            final SvnUpdate update = svnOperationFactory.createUpdate();
            update.setSingleTarget(SvnTarget.fromFile(directory));
            update.run();

            final SvnUpdate update1 = svnOperationFactory.createUpdate();
            update1.setRevision(SVNRevision.create(1));
            update1.setSingleTarget(SvnTarget.fromFile(workingCopyDirectory));
            update1.run();

            final SvnUpdate update2 = svnOperationFactory.createUpdate();
            update2.setSingleTarget(SvnTarget.fromFile(workingCopyDirectory));
            update2.run();

            final SvnGetInfo getInfo = svnOperationFactory.createGetInfo();
            getInfo.setSingleTarget(SvnTarget.fromFile(workingCopyDirectory));
            final SvnInfo info = getInfo.run();

            Assert.assertEquals(2, info.getRevision());

        } finally {
            svnOperationFactory.dispose();
            sandbox.dispose();
        }
    }

    @Test
    public void testUnversionedFileObstruction() throws Exception {
        final TestOptions options = TestOptions.getInstance();

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testUnversionedFileObstruction", options);
        try {
            final SVNURL url = sandbox.createSvnRepository();

            final CommitBuilder commitBuilder = new CommitBuilder(url);
            commitBuilder.addDirectory("obstruction");
            commitBuilder.commit();

            final WorkingCopy workingCopy = sandbox.checkoutNewWorkingCopy(url, 0);
            final File workingCopyDirectory = workingCopy.getWorkingCopyDirectory();
            final File obstruction = workingCopy.getFile("obstruction");
            SVNFileUtil.createFile(obstruction, "contents", "UTF-8");

            final SvnUpdate update = svnOperationFactory.createUpdate();
            update.setSingleTarget(SvnTarget.fromFile(workingCopyDirectory));
            update.run();

            final Map<File, SvnStatus> statuses = TestUtil.getStatuses(svnOperationFactory, workingCopyDirectory);

            Assert.assertEquals(SVNStatusType.STATUS_DELETED, statuses.get(obstruction).getNodeStatus());

            SVNFileUtil.deleteFile(obstruction);

            final SvnRevert revert = svnOperationFactory.createRevert();
            revert.setSingleTarget(SvnTarget.fromFile(obstruction));
            revert.run();

            final SvnUpdate update1 = svnOperationFactory.createUpdate();
            update1.setSingleTarget(SvnTarget.fromFile(workingCopyDirectory));
            update1.run();

            final Map<File, SvnStatus> statuses1 = TestUtil.getStatuses(svnOperationFactory, workingCopyDirectory);
            Assert.assertEquals(SVNStatusType.STATUS_NORMAL, statuses1.get(obstruction).getNodeStatus());

        } finally {
            svnOperationFactory.dispose();
            sandbox.dispose();
        }
    }

    @Ignore("Incomplete")
    @Test
    public void testConflictsWhileEolStyleChange() throws Exception {
        final TestOptions options = TestOptions.getInstance();

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testConflictsWhileEolStyleChange", options);
        try {
            final SVNURL url = sandbox.createSvnRepository();

            final CommitBuilder commitBuilder = new CommitBuilder(url);
            commitBuilder.addFile("file", ("remote" + "\r\n").getBytes());
            commitBuilder.commit();

            final CommitBuilder commitBuilder2 = new CommitBuilder(url);
            commitBuilder2.setFileProperty("file", SVNProperty.EOL_STYLE, SVNPropertyValue.create(SVNProperty.EOL_STYLE_CRLF));
            commitBuilder2.commit();

            final CommitBuilder commitBuilder3 = new CommitBuilder(url);
            commitBuilder3.changeFile("file", ("remote" + "\r").getBytes());
            commitBuilder3.setFileProperty("file", SVNProperty.EOL_STYLE, SVNPropertyValue.create(SVNProperty.EOL_STYLE_CR));
            commitBuilder3.commit();

            final CommitBuilder commitBuilder4 = new CommitBuilder(url);
            commitBuilder4.setFileProperty("file", SVNProperty.EOL_STYLE, null);
            commitBuilder4.commit();

            final WorkingCopy workingCopy = sandbox.checkoutNewWorkingCopy(url, 1);
            final File workingCopyDirectory = workingCopy.getWorkingCopyDirectory();
            final File file = workingCopy.getFile("file");

            TestUtil.writeFileContentsString(file, "changed" + "\n");

            final SvnUpdate update2 = svnOperationFactory.createUpdate();
            update2.setRevision(SVNRevision.create(2));
            update2.setSingleTarget(SvnTarget.fromFile(workingCopyDirectory));
            update2.run();

            final Map<File, SvnStatus> statuses2 = TestUtil.getStatuses(svnOperationFactory, workingCopyDirectory);
            Assert.assertEquals(SVNStatusType.STATUS_CONFLICTED, statuses2.get(file).getNodeStatus());

            TestUtil.writeFileContentsString(file, "changed" + "\r");

            final SvnUpdate update3 = svnOperationFactory.createUpdate();
            update3.setRevision(SVNRevision.create(3));
            update3.setSingleTarget(SvnTarget.fromFile(workingCopyDirectory));
            update3.run();

            TestUtil.writeFileContentsString(file, "changed" + "\r");

            final SvnUpdate update4 = svnOperationFactory.createUpdate();
            update4.setRevision(SVNRevision.create(4));
            update4.setSingleTarget(SvnTarget.fromFile(workingCopyDirectory));
            update4.run();

            //TODO: compare statuses after each update with native SVN and complete

        } finally {
            svnOperationFactory.dispose();
            sandbox.dispose();
        }
    }

    @Test
    public void testUpdateUponAddedFileShouldPreserveProperties() throws Exception {
        final TestOptions options = TestOptions.getInstance();

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testUpdateUponAddedFileShouldPreserveProperties", options);
        try {
            final SVNURL url = sandbox.createSvnRepository();

            final CommitBuilder commitBuilder = new CommitBuilder(url);
            commitBuilder.addFile("file", "original".getBytes());
            commitBuilder.setFileProperty("file", "propertyName", SVNPropertyValue.create("propertyValue"));
            commitBuilder.commit();

            final WorkingCopy workingCopy = sandbox.checkoutNewWorkingCopy(url, 0);
            final File workingCopyDirectory = workingCopy.getWorkingCopyDirectory();
            final File file = workingCopy.getFile("file");

            TestUtil.writeFileContentsString(file, "changed");

            final SvnScheduleForAddition scheduleForAddition = svnOperationFactory.createScheduleForAddition();
            scheduleForAddition.setSingleTarget(SvnTarget.fromFile(file));
            scheduleForAddition.run();

            final SvnSetProperty setProperty = svnOperationFactory.createSetProperty();
            setProperty.setSingleTarget(SvnTarget.fromFile(file));
            setProperty.setPropertyName("propertyName");
            setProperty.setPropertyValue(SVNPropertyValue.create("propertyValue"));
            setProperty.run();

            final SvnUpdate update = svnOperationFactory.createUpdate();
            update.setSingleTarget(SvnTarget.fromFile(workingCopyDirectory));
            update.run();

            final SvnGetProperties getProperties = svnOperationFactory.createGetProperties();
            getProperties.setSingleTarget(SvnTarget.fromFile(file));
            final SVNProperties properties = getProperties.run();

            Assert.assertEquals("propertyValue", SVNPropertyValue.getPropertyAsString(properties.getSVNPropertyValue("propertyName")));

            final Map<File, SvnStatus> statuses = TestUtil.getStatuses(svnOperationFactory, workingCopyDirectory);
            Assert.assertEquals(SVNStatusType.STATUS_CONFLICTED, statuses.get(file).getNodeStatus());
            Assert.assertEquals(SVNStatusType.STATUS_NORMAL, statuses.get(file).getPropertiesStatus());
            Assert.assertEquals(SVNStatusType.STATUS_CONFLICTED, statuses.get(file).getTextStatus());

        } finally {
            svnOperationFactory.dispose();
            sandbox.dispose();
        }
    }

    @Test
    public void testApplyRemoteCopyUponLocalCopy() throws Exception {
        final TestOptions options = TestOptions.getInstance();

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testApplyRemoteCopyUponLocalCopy", options);
        try {
            final SVNURL url = sandbox.createSvnRepository();

            final CommitBuilder commitBuilder1 = new CommitBuilder(url);
            commitBuilder1.addFile("directory/file");
            commitBuilder1.addFile("anotherDirectory/anotherFile");
            commitBuilder1.commit();

            final CommitBuilder commitBuilder2 = new CommitBuilder(url);
            commitBuilder2.addDirectoryByCopying("copied", "directory");
            commitBuilder2.commit();

            final WorkingCopy workingCopy = sandbox.checkoutNewWorkingCopy(url, 1);
            final File workingCopyDirectory = workingCopy.getWorkingCopyDirectory();
            final File anotherDirectory = workingCopy.getFile("anotherDirectory");
            final File copied = workingCopy.getFile("copied");
            final File copiedFile = workingCopy.getFile("copied/file");
            final File copiedAnotherFile = workingCopy.getFile("copied/anotherFile");

            final SvnCopy copy = svnOperationFactory.createCopy();
            copy.addCopySource(SvnCopySource.create(SvnTarget.fromFile(anotherDirectory), SVNRevision.WORKING));
            copy.setSingleTarget(SvnTarget.fromFile(copied));
            copy.run();

            final SvnUpdate update = svnOperationFactory.createUpdate();
            update.setSingleTarget(SvnTarget.fromFile(workingCopyDirectory));
            update.run();

            final Map<File, SvnStatus> statuses = TestUtil.getStatuses(svnOperationFactory, workingCopyDirectory);
            Assert.assertEquals(SVNStatusType.STATUS_DELETED, statuses.get(copiedFile).getNodeStatus());
            Assert.assertEquals(SVNStatusType.STATUS_REPLACED, statuses.get(copied).getNodeStatus());
            Assert.assertTrue(statuses.get(copied).isCopied());
            Assert.assertTrue(statuses.get(copied).isConflicted());
            Assert.assertTrue(statuses.get(copiedAnotherFile).isCopied());

        } finally {
            svnOperationFactory.dispose();
            sandbox.dispose();
        }
    }

    @Test
    public void testRemovePropertyOnTextConflictedFile() throws Exception {
        final TestOptions options = TestOptions.getInstance();

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testRemovePropertyOnTextConflictedFile", options);
        try {
            final SVNURL url = sandbox.createSvnRepository();

            final CommitBuilder commitBuilder1 = new CommitBuilder(url);
            commitBuilder1.addFile("file");
            commitBuilder1.commit();

            final CommitBuilder commitBuilder2 = new CommitBuilder(url);
            commitBuilder2.changeFile("file", "content".getBytes());
            commitBuilder2.setFileProperty("file", "propertyName", SVNPropertyValue.create("propertyValue"));
            commitBuilder2.commit();

            final WorkingCopy workingCopy = sandbox.checkoutNewWorkingCopy(url);
            final File file = workingCopy.getFile("file");
            TestUtil.writeFileContentsString(file, "changed");

            final SvnUpdate update = svnOperationFactory.createUpdate();
            update.setRevision(SVNRevision.create(1));
            update.setSingleTarget(SvnTarget.fromFile(workingCopy.getWorkingCopyDirectory()));
            update.run();

            final SvnGetProperties getProperties = svnOperationFactory.createGetProperties();
            getProperties.setSingleTarget(SvnTarget.fromFile(file));
            SVNProperties properties = getProperties.run();

            final Map<File, SvnStatus> statuses = TestUtil.getStatuses(svnOperationFactory, workingCopy.getWorkingCopyDirectory());
            Assert.assertTrue(statuses.get(file).isConflicted());
            Assert.assertTrue(properties == null || properties.size() == 0);

        } finally {
            svnOperationFactory.dispose();
            sandbox.dispose();
        }
    }

    @Test
    public void testResolveTextConflictWhileUpdate() throws Exception {
        final TestOptions options = TestOptions.getInstance();

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testResolveTextConflictWhileUpdate", options);
        try {
            final SVNURL url = sandbox.createSvnRepository();

            final CommitBuilder commitBuilder1 = new CommitBuilder(url);
            commitBuilder1.addFile("file");
            commitBuilder1.commit();

            final CommitBuilder commitBuilder2 = new CommitBuilder(url);
            commitBuilder2.changeFile("file", "content".getBytes());
            commitBuilder2.commit();

            final WorkingCopy workingCopy = sandbox.checkoutNewWorkingCopy(url, 1);
            final File file = workingCopy.getFile("file");
            TestUtil.writeFileContentsString(file, "changed");

            final DefaultSVNOptions svnOptions = new DefaultSVNOptions();
            svnOptions.setConflictHandler(new ISVNConflictHandler() {
                public SVNConflictResult handleConflict(SVNConflictDescription conflictDescription) throws SVNException {
                    if (conflictDescription.getPath().getName().equals("file")) {
                        return new SVNConflictResult(SVNConflictChoice.BASE, null);
                    }
                    return null;
                }
            });
            svnOperationFactory.setOptions(svnOptions);

            final EventsHandler eventHandler = new EventsHandler();
            svnOperationFactory.setEventHandler(eventHandler);

            final SvnUpdate update = svnOperationFactory.createUpdate();
            update.setSingleTarget(SvnTarget.fromFile(file));
            update.run();

            SVNEvent event = eventHandler.findEvent(SVNEventAction.RESOLVED);
            Assert.assertNotNull(event);

            Map<File, SvnStatus> statuses = TestUtil.getStatuses(svnOperationFactory, workingCopy.getWorkingCopyDirectory());
            Assert.assertFalse(statuses.get(file).isConflicted());
            Assert.assertEquals(SVNStatusType.STATUS_MODIFIED, statuses.get(file).getNodeStatus());

        } finally {
            svnOperationFactory.dispose();
            sandbox.dispose();
        }
    }

    @Test
    public void testTreeConflictRemoteEditLocalDelete() throws Exception {
        final TestOptions options = TestOptions.getInstance();

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testTreeConflictRemoteEditLocalDelete", options);
        try {
            final SVNURL url = sandbox.createSvnRepository();

            final CommitBuilder commitBuilder1 = new CommitBuilder(url);
            commitBuilder1.addDirectory("directory");
            commitBuilder1.commit();

            final CommitBuilder commitBuilder2 = new CommitBuilder(url);
            commitBuilder2.setDirectoryProperty("directory", "propertyName", SVNPropertyValue.create("propertyValue"));
            commitBuilder2.commit();

            final WorkingCopy workingCopy = sandbox.checkoutNewWorkingCopy(url, 1);
            final File workingCopyDirectory = workingCopy.getWorkingCopyDirectory();
            final File directory = workingCopy.getFile("directory");

            final SvnScheduleForRemoval scheduleForRemoval = svnOperationFactory.createScheduleForRemoval();
            scheduleForRemoval.setSingleTarget(SvnTarget.fromFile(directory));
            scheduleForRemoval.run();

            final SvnUpdate update = svnOperationFactory.createUpdate();
            update.setSingleTarget(SvnTarget.fromFile(workingCopyDirectory));
            update.run();

            final Map<File, SvnStatus> statuses = TestUtil.getStatuses(svnOperationFactory, workingCopyDirectory);
            final SVNWCContext context = new SVNWCContext(svnOperationFactory.getOptions(), svnOperationFactory.getEventHandler());
            try {
                final SVNStatus status = SvnCodec.status(context, statuses.get(directory));
                final SVNTreeConflictDescription treeConflict = status.getTreeConflict();

                Assert.assertEquals(SVNOperation.UPDATE, treeConflict.getOperation());
                Assert.assertEquals(SVNConflictAction.EDIT, treeConflict.getConflictAction());
                Assert.assertEquals(SVNConflictReason.DELETED, treeConflict.getConflictReason());
            } finally {
                context.close();
            }
        } finally {
            svnOperationFactory.dispose();
            sandbox.dispose();
        }
    }

    @Test
    public void testDeletionOntoMovedDirectory() throws Exception {
        final TestOptions options = TestOptions.getInstance();

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testDeletionOntoMovedDirectory", options);
        try {
            final SVNURL url = sandbox.createSvnRepository();

            final CommitBuilder commitBuilder1 = new CommitBuilder(url);
            commitBuilder1.addFile("directory/file");
            commitBuilder1.commit();

            final CommitBuilder commitBuilder2 = new CommitBuilder(url);
            commitBuilder2.delete("directory/file");
            commitBuilder2.commit();

            final WorkingCopy workingCopy = sandbox.checkoutNewWorkingCopy(url, 1);
            final File directory = workingCopy.getFile("directory");
            final File movedDirectory = workingCopy.getFile("movedDirectory");
            final File file = workingCopy.getFile("movedDirectory/file");
            final File workingCopyDirectory = workingCopy.getWorkingCopyDirectory();

            final SvnCopy copy = svnOperationFactory.createCopy();
            copy.addCopySource(SvnCopySource.create(SvnTarget.fromFile(directory), SVNRevision.WORKING));
            copy.setSingleTarget(SvnTarget.fromFile(movedDirectory));
            copy.setMove(true);
            copy.run();

            final SvnUpdate update = svnOperationFactory.createUpdate();
            update.setSingleTarget(SvnTarget.fromFile(workingCopyDirectory));
            update.run();

            final Map<File, SvnStatus> statuses = TestUtil.getStatuses(svnOperationFactory, workingCopyDirectory);
            Assert.assertEquals(SVNStatusType.STATUS_ADDED, statuses.get(movedDirectory).getNodeStatus());
            Assert.assertTrue(statuses.get(movedDirectory).isCopied());
            Assert.assertEquals(url.appendPath("directory", false), statuses.get(movedDirectory).getCopyFromUrl());

            Assert.assertEquals(SVNStatusType.STATUS_NORMAL, statuses.get(file).getNodeStatus());
            Assert.assertTrue(statuses.get(file).isCopied());
            Assert.assertEquals(url.appendPath("directory/file", false), statuses.get(file).getCopyFromUrl());

            Assert.assertEquals(SVNStatusType.STATUS_DELETED, statuses.get(directory).getNodeStatus());
            Assert.assertFalse(statuses.get(directory).isCopied());
        } finally {
            svnOperationFactory.dispose();
            sandbox.dispose();
        }
    }

    @Test
    public void testWrongNodeKindObstructionOnDeleteCausesTreeConflict() throws Exception {
        final TestOptions options = TestOptions.getInstance();

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testWrongNodeKindObstructionOnDeleteCausesTreeConflict", options);
        try {
            final SVNURL url = sandbox.createSvnRepository();

            final CommitBuilder commitBuilder1 = new CommitBuilder(url);
            commitBuilder1.addFile("directory/subdirectory/file");
            commitBuilder1.addFile("anotherDirectory/subdirectory/file");
            commitBuilder1.commit();

            final CommitBuilder commitBuilder2 = new CommitBuilder(url);
            commitBuilder2.delete("directory/subdirectory");
            commitBuilder2.commit();

            final CommitBuilder commitBuilder3 = new CommitBuilder(url);
            commitBuilder3.addDirectoryByCopying("directory/subdirectory", "anotherDirectory/subdirectory");
            commitBuilder3.commit();

            final WorkingCopy workingCopy = sandbox.checkoutNewWorkingCopy(url);
            final File workingCopyDirectory = workingCopy.getWorkingCopyDirectory();
            final File subdirectory = workingCopy.getFile("directory/subdirectory");
            final File file = workingCopy.getFile("directory/subdirectory/file");
            SVNFileUtil.deleteAll(subdirectory, null);
            TestUtil.writeFileContentsString(subdirectory, "Obstruction");

            final SvnUpdate update = svnOperationFactory.createUpdate();
            update.setRevision(SVNRevision.create(2));
            update.setSingleTarget(SvnTarget.fromFile(workingCopyDirectory));
            update.run();

            final Map<File, SvnStatus> statuses = TestUtil.getStatuses(svnOperationFactory, workingCopyDirectory);
            Assert.assertTrue(statuses.get(subdirectory).isConflicted());
            Assert.assertEquals(SVNStatusType.STATUS_OBSTRUCTED, statuses.get(subdirectory).getNodeStatus());
            Assert.assertEquals(SVNStatusType.STATUS_MISSING, statuses.get(file).getNodeStatus());

        } finally {
            svnOperationFactory.dispose();
            sandbox.dispose();
        }
    }

    @Test
    public void testCheckoutWC18InsideWC17() throws Exception {
        final TestOptions options = TestOptions.getInstance();

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testCheckoutWC18InsideWC17", options);
        try {
            final SVNURL url = sandbox.createSvnRepository();

            final WorkingCopy workingCopy = sandbox.checkoutNewWorkingCopy(url);
            final File workingCopyDirectory = workingCopy.getWorkingCopyDirectory();


            final SVNWCContext context = new SVNWCContext(svnOperationFactory.getOptions(), svnOperationFactory.getEventHandler());
            try {
                final SvnNgDowngrade svnNgDowngrade = new SvnNgDowngrade();
                svnNgDowngrade.downgrade(context, workingCopyDirectory);
            } finally {
                context.close();
            }

            final File directory = workingCopy.getFile("directory/subdirectory/subsubdirectory");
            SVNFileUtil.ensureDirectoryExists(directory);

            final SvnCheckout checkout = svnOperationFactory.createCheckout();
            checkout.setSource(SvnTarget.fromURL(url));
            checkout.setSingleTarget(SvnTarget.fromFile(directory));
            checkout.run();

            final SvnGetStatus getStatus17 = svnOperationFactory.createGetStatus();
            getStatus17.setSingleTarget(SvnTarget.fromFile(workingCopyDirectory));
            getStatus17.setReportAll(true);
            getStatus17.setDepth(SVNDepth.EMPTY);
            final SvnStatus status17 = getStatus17.run();

            final SvnGetStatus getStatus18 = svnOperationFactory.createGetStatus();
            getStatus18.setSingleTarget(SvnTarget.fromFile(directory));
            getStatus18.setReportAll(true);
            getStatus18.setDepth(SVNDepth.EMPTY);
            final SvnStatus status18 = getStatus18.run();

            Assert.assertEquals(ISVNWCDb.WC_FORMAT_17, status17.getWorkingCopyFormat());
            Assert.assertEquals(ISVNWCDb.WC_FORMAT_18, status18.getWorkingCopyFormat());

        } finally {
            svnOperationFactory.dispose();
            sandbox.dispose();
        }
    }

    @Test
    public void testSetImmediatesDepthOnEmptyDepthDirectory() throws Exception {
        final TestOptions options = TestOptions.getInstance();

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testSetImmediatesDepthOnEmptyDepthDirectory", options);
        try {
            final SVNURL url = sandbox.createSvnRepository();

            final CommitBuilder commitBuilder = new CommitBuilder(url);
            commitBuilder.addFile("directory/subdirectory/file");
            commitBuilder.addDirectory("directory/subdirectory/directory");
            commitBuilder.commit();

            final WorkingCopy workingCopy = sandbox.checkoutNewWorkingCopy(url);
            final File parentDirectory = workingCopy.getFile("directory");
            final File subdirectory = workingCopy.getFile("directory/subdirectory");
            final File directory = workingCopy.getFile("directory/subdirectory/directory");
            final File file = workingCopy.getFile("directory/subdirectory/file");

            final SvnUpdate update1 = svnOperationFactory.createUpdate();
            update1.setSingleTarget(SvnTarget.fromFile(subdirectory));
            update1.setDepthIsSticky(true);
            update1.setDepth(SVNDepth.EMPTY);
            update1.run();

            final EventsHandler eventHandler = new EventsHandler();
            svnOperationFactory.setEventHandler(eventHandler);

            final SvnUpdate update2 = svnOperationFactory.createUpdate();
            update2.setSingleTarget(SvnTarget.fromFile(subdirectory));
            update2.setDepthIsSticky(true);
            update2.setDepth(SVNDepth.IMMEDIATES);
            update2.run();

            final List<SVNEvent> events = eventHandler.getEvents();
            Assert.assertEquals(subdirectory, events.get(0).getFile());
            Assert.assertEquals(SVNEventAction.UPDATE_STARTED, events.get(0).getAction());
            Assert.assertEquals(file, events.get(1).getFile());
            Assert.assertEquals(SVNEventAction.UPDATE_ADD, events.get(1).getAction());
            Assert.assertEquals(directory, events.get(2).getFile());
            Assert.assertEquals(SVNEventAction.UPDATE_ADD, events.get(2).getAction());
            Assert.assertEquals(subdirectory, events.get(3).getFile());
            Assert.assertEquals(SVNEventAction.UPDATE_UPDATE, events.get(3).getAction());
            Assert.assertEquals(parentDirectory, events.get(4).getFile());
            Assert.assertEquals(SVNEventAction.UPDATE_UPDATE, events.get(4).getAction());
            Assert.assertEquals(subdirectory, events.get(5).getFile());
            Assert.assertEquals(SVNEventAction.UPDATE_COMPLETED, events.get(5).getAction());
        } finally {
            svnOperationFactory.dispose();
            sandbox.dispose();
        }
    }

    @Test
    public void testTextConflictWC17() throws Exception {
        final TestOptions options = TestOptions.getInstance();

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testTextConflictWC17", options);
        try {
            final SVNURL url = sandbox.createSvnRepository();

            final CommitBuilder commitBuilder1 = new CommitBuilder(url);
            commitBuilder1.addFile("file", "base".getBytes());
            commitBuilder1.commit();

            final CommitBuilder commitBuilder2 = new CommitBuilder(url);
            commitBuilder2.changeFile("file", "theirs".getBytes());
            commitBuilder2.commit();

            final File workingCopyDirectory = sandbox.createDirectory("wc");

            final SvnCheckout checkout = svnOperationFactory.createCheckout();
            checkout.setRevision(SVNRevision.create(1));
            checkout.setTargetWorkingCopyFormat(ISVNWCDb.WC_FORMAT_17);
            checkout.setSource(SvnTarget.fromURL(url));
            checkout.setSingleTarget(SvnTarget.fromFile(workingCopyDirectory));
            checkout.run();

            final File file = new File(workingCopyDirectory, "file");
            TestUtil.writeFileContentsString(file, "ours");

            final SvnUpdate update = svnOperationFactory.createUpdate();
            update.setSingleTarget(SvnTarget.fromFile(workingCopyDirectory));
            update.run();

            final Map<File, SvnStatus> statuses = TestUtil.getStatuses(svnOperationFactory, workingCopyDirectory);
            Assert.assertEquals(SVNStatusType.STATUS_CONFLICTED, statuses.get(file).getNodeStatus());
            Assert.assertEquals(SVNStatusType.STATUS_CONFLICTED, statuses.get(file).getTextStatus());
            Assert.assertEquals(SVNStatusType.STATUS_NONE, statuses.get(file).getPropertiesStatus());

        } finally {
            svnOperationFactory.dispose();
            sandbox.dispose();
        }
    }


    @Test
    public void testPropertyConflictWC17() throws Exception {
        final TestOptions options = TestOptions.getInstance();

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testPropertyConflictWC17", options);
        try {
            final SVNURL url = sandbox.createSvnRepository();

            final CommitBuilder commitBuilder1 = new CommitBuilder(url);
            commitBuilder1.addFile("file");
            commitBuilder1.setFileProperty("file", "propertyName", SVNPropertyValue.create("base"));
            commitBuilder1.commit();

            final CommitBuilder commitBuilder2 = new CommitBuilder(url);
            commitBuilder2.setFileProperty("file", "propertyName", SVNPropertyValue.create("theirs"));
            commitBuilder2.commit();

            final File workingCopyDirectory = sandbox.createDirectory("wc");

            final SvnCheckout checkout = svnOperationFactory.createCheckout();
            checkout.setRevision(SVNRevision.create(1));
            checkout.setTargetWorkingCopyFormat(ISVNWCDb.WC_FORMAT_17);
            checkout.setSource(SvnTarget.fromURL(url));
            checkout.setSingleTarget(SvnTarget.fromFile(workingCopyDirectory));
            checkout.run();

            final File file = new File(workingCopyDirectory, "file");

            final SvnSetProperty setProperty = svnOperationFactory.createSetProperty();
            setProperty.setPropertyName("propertyName");
            setProperty.setPropertyValue(SVNPropertyValue.create("ours"));
            setProperty.setSingleTarget(SvnTarget.fromFile(file));
            setProperty.run();

            final SvnUpdate update = svnOperationFactory.createUpdate();
            update.setSingleTarget(SvnTarget.fromFile(workingCopyDirectory));
            update.run();

            final Map<File, SvnStatus> statuses = TestUtil.getStatuses(svnOperationFactory, workingCopyDirectory);
            Assert.assertEquals(SVNStatusType.STATUS_CONFLICTED, statuses.get(file).getNodeStatus());
            Assert.assertEquals(SVNStatusType.STATUS_NORMAL, statuses.get(file).getTextStatus());
            Assert.assertEquals(SVNStatusType.STATUS_CONFLICTED, statuses.get(file).getPropertiesStatus());

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

        public SVNEvent findEvent(SVNEventAction eventAction) {
            for (SVNEvent event : events) {
                if (event.getAction() == eventAction) {
                    return event;
                }
            }
            return null;
        }
    }
}
