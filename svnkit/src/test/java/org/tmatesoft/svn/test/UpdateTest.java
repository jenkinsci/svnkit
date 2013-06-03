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
import org.tmatesoft.svn.core.internal.db.SVNSqlJetStatement;
import org.tmatesoft.svn.core.internal.io.dav.DAVUtil;
import org.tmatesoft.svn.core.internal.wc.SVNExternal;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.internal.wc17.db.statement.SVNWCDbSchema;
import org.tmatesoft.svn.core.wc.*;
import org.tmatesoft.svn.core.wc2.*;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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
