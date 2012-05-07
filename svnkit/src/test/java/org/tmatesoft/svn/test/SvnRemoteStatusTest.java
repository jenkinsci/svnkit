package org.tmatesoft.svn.test;

import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.tmatesoft.svn.core.*;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.internal.wc17.SVNWCContext;
import org.tmatesoft.svn.core.internal.wc2.SvnWcGeneration;
import org.tmatesoft.svn.core.internal.wc2.compat.SvnCodec;
import org.tmatesoft.svn.core.wc.*;
import org.tmatesoft.svn.core.wc2.*;

import java.io.File;
import java.util.*;

public class SvnRemoteStatusTest {

    @Before
    public void setup() {
        Assume.assumeTrue(!TestUtil.isNewWorkingCopyOnly());
    }
    
    @Test
    public void testRemoteUrlIsNotNull() throws SVNException {
        testRemoteUrlPresence(SvnWcGeneration.V16);
        testRemoteUrlPresence(SvnWcGeneration.V17);
    }
    
    @Test
    public void testOldAndNewStatusValues() throws SVNException { 
        Map<String, SVNStatus> newStatuses = collectStatuses(SvnWcGeneration.V17);
        Map<String, SVNStatus> oldStatuses = collectStatuses(SvnWcGeneration.V16);
        
        Assert.assertEquals(newStatuses.size(), oldStatuses.size());
        for (Iterator<String> sts = newStatuses.keySet().iterator(); sts.hasNext();) {
            String path = sts.next();
            SVNStatus newStatus = newStatuses.get(path);
            SVNStatus oldStatus = oldStatuses.get(path);
           
            Assert.assertNotNull(oldStatus);
            compare(newStatus, oldStatus);
        }
    }

    @Test
    public void testRemoteStatusDeletedFile() throws Exception {
        final TestOptions options = TestOptions.getInstance();

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testRemoteStatusDeletedFile", options);
        try {
            final SVNURL url = sandbox.createSvnRepository();

            final CommitBuilder commitBuilder1 = new CommitBuilder(url);
            commitBuilder1.addFile("file");
            commitBuilder1.commit();

            final CommitBuilder commitBuilder2 = new CommitBuilder(url);
            commitBuilder2.delete("file");
            commitBuilder2.commit();

            final WorkingCopy workingCopy = sandbox.checkoutNewWorkingCopy(url, 1);
            final File workingCopyDirectory = workingCopy.getWorkingCopyDirectory();

            final File file = new File(workingCopyDirectory, "file");

            final SvnGetStatus getStatus = svnOperationFactory.createGetStatus();
            getStatus.setSingleTarget(SvnTarget.fromFile(file));
            getStatus.setRemote(true);
            final SvnStatus status = getStatus.run();

            Assert.assertEquals(2, status.getRepositoryChangedRevision());
        } finally {
            svnOperationFactory.dispose();
            sandbox.dispose();
        }
    }

    @Test
    public void testRemoteRevisionIsReported() throws Exception {
        final TestOptions options = TestOptions.getInstance();

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testRemoteRevisionIsReported", options);
        try {
            final SVNURL url = sandbox.createSvnRepository();

            final CommitBuilder commitBuilder = new CommitBuilder(url);
            commitBuilder.addFile("file");
            commitBuilder.commit();

            final WorkingCopy workingCopy = sandbox.checkoutNewWorkingCopy(url, 1);
            final File workingCopyDirectory = workingCopy.getWorkingCopyDirectory();

            final File file = new File(workingCopyDirectory, "file");

            //STATUS_COMPLETED should be called with a correct revision
            svnOperationFactory.setEventHandler(new ISVNEventHandler() {
                public void handleEvent(SVNEvent event, double progress) throws SVNException {
                    if (event.getAction() == SVNEventAction.STATUS_COMPLETED) {
                        Assert.assertEquals(1, event.getRevision());
                    }
                }

                public void checkCancelled() throws SVNCancelException {
                }
            });

            final SvnGetStatus getStatus = svnOperationFactory.createGetStatus();
            getStatus.setSingleTarget(SvnTarget.fromFile(file));
            getStatus.setRemote(true);
            getStatus.setReportAll(false);
            getStatus.run();

        } finally {
            svnOperationFactory.dispose();
            sandbox.dispose();
        }
    }

    @Test
    public void testSubdirectoryOfLocallyDeletedDirectory() throws Exception {
        final TestOptions options = TestOptions.getInstance();

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testSubdirectoryOfLocallyDeletedDirectory", options);
        try {
            final SVNURL url = sandbox.createSvnRepository();

            final CommitBuilder commitBuilder = new CommitBuilder(url);
            commitBuilder.addFile("directory/subdirectory/file");
            commitBuilder.commit();

            final WorkingCopy workingCopy = sandbox.checkoutNewWorkingCopy(url);
            final File workingCopyDirectory = workingCopy.getWorkingCopyDirectory();

            final File directory = new File(workingCopyDirectory, "directory");
            final File subdirectory = new File(directory, "subdirectory");


            final SvnScheduleForRemoval scheduleForRemoval = svnOperationFactory.createScheduleForRemoval();
            scheduleForRemoval.setSingleTarget(SvnTarget.fromFile(directory));
            scheduleForRemoval.run();

            SVNFileUtil.ensureDirectoryExists(directory);

            final int[] statusTargetsCount = {0};

            final SvnGetStatus getStatus = svnOperationFactory.createGetStatus();
            getStatus.setRemote(true);
            getStatus.setReportAll(false);
            getStatus.setSingleTarget(SvnTarget.fromFile(subdirectory));
            getStatus.setReceiver(new ISvnObjectReceiver<SvnStatus>() {
                public void receive(SvnTarget target, SvnStatus status) throws SVNException {
                    statusTargetsCount[0]++;
                    Assert.assertEquals(SVNStatusType.STATUS_NONE, status.getRepositoryNodeStatus());
                }
            });
            getStatus.run();

            Assert.assertEquals(2, statusTargetsCount[0]);

        } finally {
            svnOperationFactory.dispose();
            sandbox.dispose();
        }
    }

    @Test
    public void testRemoteLockIsReported() throws Exception {
        final TestOptions options = TestOptions.getInstance();

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testRemoteLockIsReported", options);
        try {
            final SVNURL url = sandbox.createSvnRepository();

            final CommitBuilder commitBuilder = new CommitBuilder(url);
            commitBuilder.addFile("file");
            commitBuilder.commit();

            //lock a file remotely
            final SVNURL fileUrl = url.appendPath("file", false);

            final String lockMessage = "lock message";

            final SvnSetLock setLock = svnOperationFactory.createSetLock();
            setLock.setLockMessage(lockMessage);
            setLock.setSingleTarget(SvnTarget.fromURL(fileUrl));
            setLock.run();

            final WorkingCopy workingCopy = sandbox.checkoutNewWorkingCopy(url);
            final File workingCopyDirectory = workingCopy.getWorkingCopyDirectory();

            final File file = new File(workingCopyDirectory, "file");

            final SvnGetStatus getStatus = svnOperationFactory.createGetStatus();
            getStatus.setRemote(true);
            getStatus.setSingleTarget(SvnTarget.fromFile(file));
            final SvnStatus fileStatus = getStatus.run();

            final SVNLock repositoryLock = fileStatus.getRepositoryLock();

            Assert.assertNotNull(repositoryLock);
            Assert.assertEquals("/file", repositoryLock.getPath());
            Assert.assertEquals(lockMessage, repositoryLock.getComment());

        } finally {
            svnOperationFactory.dispose();
            sandbox.dispose();
        }
    }

    @Test
    public void testRemoteStatusRemotelyDeletedWorkingCopy() throws Exception {
        final TestOptions options = TestOptions.getInstance();

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testRemoteStatusRemotelyDeletedWorkingCopy", options);
        try {
            final SVNURL url = sandbox.createSvnRepository();

            final CommitBuilder commitBuilder1 = new CommitBuilder(url);
            commitBuilder1.addFile("directory/file");
            commitBuilder1.commit();

            final CommitBuilder commitBuilder2 = new CommitBuilder(url);
            commitBuilder2.delete("directory");
            commitBuilder2.commit();

            final SVNURL subUrl = url.appendPath("directory", false);

            final WorkingCopy workingCopy = sandbox.checkoutNewWorkingCopy(subUrl, 1);
            final File workingCopyDirectory = workingCopy.getWorkingCopyDirectory();

            svnOperationFactory.setEventHandler(new ISVNEventHandler() {
                public void handleEvent(SVNEvent event, double progress) throws SVNException {
                    if (event.getAction() == SVNEventAction.STATUS_COMPLETED) {
                        Assert.assertEquals(-1, event.getRevision());
                    }
                }

                public void checkCancelled() throws SVNCancelException {
                }
            });

            final SvnGetStatus getShortStatus = svnOperationFactory.createGetStatus();
            getShortStatus.setSingleTarget(SvnTarget.fromFile(workingCopyDirectory));
            getShortStatus.setRemote(true);
            getShortStatus.setReportAll(false);
            getShortStatus.setReceiver(new ISvnObjectReceiver<SvnStatus>() {
                public void receive(SvnTarget target, SvnStatus status) throws SVNException {
                    Assert.fail("No status should be reported for this working copy");
                }
            });
            getShortStatus.run();

            final SvnGetStatus getFullStatus = svnOperationFactory.createGetStatus();
            getFullStatus.setSingleTarget(SvnTarget.fromFile(workingCopyDirectory));
            getFullStatus.setRemote(true);
            getFullStatus.setReportAll(true);
            getFullStatus.setReceiver(new ISvnObjectReceiver<SvnStatus>() {
                public void receive(SvnTarget target, SvnStatus status) throws SVNException {
                    Assert.assertEquals(SVNStatusType.STATUS_DELETED, status.getRepositoryNodeStatus());
                    Assert.assertEquals(SVNStatusType.STATUS_NONE, status.getRepositoryPropertiesStatus());
                    Assert.assertEquals(SVNStatusType.STATUS_NONE, status.getRepositoryTextStatus());
                }
            });
            getFullStatus.run();

        } finally {
            svnOperationFactory.dispose();
            sandbox.dispose();
        }
    }

    @Test
    public void testRemoteStatusForLocallyAddedFile() throws Exception {
        final TestOptions options = TestOptions.getInstance();

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testRemoteStatusForLocallyAddedFile", options);
        try {
            final SVNURL url = sandbox.createSvnRepository();

            final CommitBuilder commitBuilder = new CommitBuilder(url);
            commitBuilder.addDirectory("directory");
            commitBuilder.commit();

            final WorkingCopy workingCopy = sandbox.checkoutNewWorkingCopy(url);

            final File file = workingCopy.getFile("directory/file");
            SVNFileUtil.ensureDirectoryExists(file.getParentFile());
            TestUtil.writeFileContentsString(file, "contents");
            workingCopy.add(file);

            final SvnGetStatus getStatus = svnOperationFactory.createGetStatus();
            getStatus.setSingleTarget(SvnTarget.fromFile(file));
            getStatus.setRemote(true);
            final SvnStatus status = getStatus.run();

            final SVNWCContext context = new SVNWCContext(svnOperationFactory.getOptions(), svnOperationFactory.getEventHandler());
            try {
                final SVNStatus oldStatus = SvnCodec.status(context, status);

                Assert.assertEquals(url.appendPath("directory/file", false), oldStatus.getURL());
                Assert.assertEquals(url, oldStatus.getRepositoryRootURL());
                Assert.assertEquals("directory/file", oldStatus.getRepositoryRelativePath());

                Assert.assertEquals(url, status.getRepositoryRootUrl());
                Assert.assertEquals("directory/file", status.getRepositoryRelativePath());

            } finally {
                context.close();
            }
        } finally {
            svnOperationFactory.dispose();
            sandbox.dispose();
        }
    }

    @Test
    public void testRemoteStatusForRemotelyAddedFile() throws Exception {
        final TestOptions options = TestOptions.getInstance();

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testRemoteStatusForRemotelyAddedFile", options);
        try {
            final SVNURL url = sandbox.createSvnRepository();

            final CommitBuilder commitBuilder1 = new CommitBuilder(url);
            commitBuilder1.addDirectory("directory");
            commitBuilder1.commit();

            final CommitBuilder commitBuilder2 = new CommitBuilder(url);
            commitBuilder2.addFile("directory/file");
            commitBuilder2.commit();

            final WorkingCopy workingCopy = sandbox.checkoutNewWorkingCopy(url, 1);

            final File file = workingCopy.getFile("directory/file");

            final SvnGetStatus getStatus = svnOperationFactory.createGetStatus();
            getStatus.setSingleTarget(SvnTarget.fromFile(file));
            getStatus.setRemote(true);
            final SvnStatus status = getStatus.run();

            final SVNWCContext context = new SVNWCContext(svnOperationFactory.getOptions(), svnOperationFactory.getEventHandler());
            try {
                final SVNStatus oldStatus = SvnCodec.status(context, status);

                Assert.assertEquals(null, oldStatus.getURL());
                Assert.assertEquals(url, oldStatus.getRepositoryRootURL());
                Assert.assertEquals("directory/file", oldStatus.getRepositoryRelativePath());

                Assert.assertEquals(url, status.getRepositoryRootUrl());
                Assert.assertEquals("directory/file", status.getRepositoryRelativePath());

            } finally {
                context.close();
            }
        } finally {
            svnOperationFactory.dispose();
            sandbox.dispose();
        }
    }

    @Test
    public void testRemoteStatusForSwitchedFile() throws Exception {
        final TestOptions options = TestOptions.getInstance();

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testRemoteStatusForSwitchedFile", options);
        try {
            final SVNURL url = sandbox.createSvnRepository();

            final CommitBuilder commitBuilder1 = new CommitBuilder(url);
            commitBuilder1.addFile("directory1/file");
            commitBuilder1.commit();

            final CommitBuilder commitBuilder2 = new CommitBuilder(url);
            commitBuilder2.addFile("directory2/file");
            commitBuilder2.commit();

            final WorkingCopy workingCopy = sandbox.checkoutNewWorkingCopy(url, 1);

            final File directory1 = workingCopy.getFile("directory1");
            final File file = workingCopy.getFile("directory1/file");

            SvnSwitch svnSwitch = svnOperationFactory.createSwitch();
            svnSwitch.setIgnoreAncestry(true);
            svnSwitch.setSingleTarget(SvnTarget.fromFile(directory1));
            svnSwitch.setSwitchTarget(SvnTarget.fromURL(url.appendPath("directory2", false)));
            svnSwitch.run();

            final SvnGetStatus getStatus = svnOperationFactory.createGetStatus();
            getStatus.setSingleTarget(SvnTarget.fromFile(file));
            getStatus.setRemote(true);
            final SvnStatus status = getStatus.run();

            final SVNWCContext context = new SVNWCContext(svnOperationFactory.getOptions(), svnOperationFactory.getEventHandler());
            try {
                final SVNStatus oldStatus = SvnCodec.status(context, status);

                Assert.assertEquals(url.appendPath("directory2/file", false), oldStatus.getURL());
                Assert.assertEquals(url, oldStatus.getRepositoryRootURL());
                Assert.assertEquals("directory2/file", oldStatus.getRepositoryRelativePath());

                Assert.assertEquals(url, status.getRepositoryRootUrl());
                Assert.assertEquals("directory2/file", status.getRepositoryRelativePath());

            } finally {
                context.close();
            }
        } finally {
            svnOperationFactory.dispose();
            sandbox.dispose();
        }
    }

    private void compare(SVNStatus newStatus, SVNStatus oldStatus) {
        Assert.assertEquals(oldStatus.getRemoteRevision(), newStatus.getRemoteRevision());
        Assert.assertEquals(oldStatus.getRevision(), newStatus.getRevision());
        Assert.assertEquals(oldStatus.getDepth(), newStatus.getDepth());
        Assert.assertEquals(oldStatus.getCopyFromRevision(), newStatus.getCopyFromRevision());
        Assert.assertEquals(oldStatus.getCopyFromURL(), newStatus.getCopyFromURL());
    }

    private void testRemoteUrlPresence(SvnWcGeneration wcGeneration) throws SVNException {
        final TestOptions options = TestOptions.getInstance();

        final Sandbox sandbox = Sandbox.createWithCleanup(getClass().getSimpleName() + "." + wcGeneration, options);
        final SVNURL url = sandbox.createSvnRepository();

        final CommitBuilder commitBuilder = new CommitBuilder(url);
        commitBuilder.addDirectory("trunk");
        commitBuilder.addFile("trunk/remotelyChanged.txt");
        commitBuilder.addDirectory("trunk/remotelyChanged");
        commitBuilder.addFile("trunk/remotelyDeleted.txt");
        commitBuilder.addDirectory("trunk/remotelyDeleted");
        commitBuilder.addFile("trunk/remotelyReplaced.txt");
        commitBuilder.addDirectory("trunk/remotelyReplaced");
        commitBuilder.commit();

        // move
        WorkingCopy wc = sandbox.checkoutNewWorkingCopy(url, -1, true, wcGeneration);
        final SvnOperationFactory svnOperationFactory = wc.getOperationFactory();

        // make changes
        final CommitBuilder remoteChange = new CommitBuilder(url);
        remoteChange.addFile("trunk/remotelyAdded.txt");
        remoteChange.addDirectory("trunk/remotelyAdded");
        remoteChange.delete("trunk/remotelyDeleted");
        remoteChange.delete("trunk/remotelyDeleted.txt");

        remoteChange.delete("trunk/remotelyReplaced");
        remoteChange.delete("trunk/remotelyReplaced.txt");

        remoteChange.addFile("trunk/remotelyReplaced.txt");
        remoteChange.addDirectory("trunk/remotelyReplaced");

        remoteChange.changeFile("trunk/remotelyChanged.txt", "change".getBytes());
        remoteChange.commit();

        SvnGetStatus st = svnOperationFactory.createGetStatus();
        st.setSingleTarget(SvnTarget.fromFile(wc.getWorkingCopyDirectory()));
        st.setRemote(true);
        Collection<SvnStatus> statuses = new ArrayList<SvnStatus>();
        st.run(statuses);

        for (SvnStatus status : statuses) {
            String remotePath = status.getRepositoryRelativePath();
            SVNURL repositoryRoot = status.getRepositoryRootUrl();

            Assert.assertNotNull(repositoryRoot);
            Assert.assertNotNull(remotePath);
        }
    }

    private Map<String, SVNStatus> collectStatuses(SvnWcGeneration wcGeneration) throws SVNException {
        final TestOptions options = TestOptions.getInstance();

        final Sandbox sandbox = Sandbox.createWithCleanup(getClass().getSimpleName() + "." + wcGeneration, options);
        final SVNURL url = sandbox.createSvnRepository();

        final CommitBuilder commitBuilder = new CommitBuilder(url);
        commitBuilder.addDirectory("trunk");
        commitBuilder.addFile("trunk/remotelyChanged.txt");
        commitBuilder.addDirectory("trunk/remotelyChanged");
        commitBuilder.addFile("trunk/remotelyDeleted.txt");
        commitBuilder.addDirectory("trunk/remotelyDeleted");
        commitBuilder.addFile("trunk/remotelyReplaced.txt");
        commitBuilder.addDirectory("trunk/remotelyReplaced");
        commitBuilder.commit();

        // move
        WorkingCopy wc = sandbox.checkoutNewWorkingCopy(url, -1, true, wcGeneration);

        // make changes
        final CommitBuilder remoteChange = new CommitBuilder(url);
        remoteChange.addFile("trunk/remotelyAdded.txt");
        remoteChange.addDirectory("trunk/remotelyAdded");
        remoteChange.delete("trunk/remotelyDeleted");
        remoteChange.delete("trunk/remotelyDeleted.txt");

        remoteChange.delete("trunk/remotelyReplaced");
        remoteChange.delete("trunk/remotelyReplaced.txt");

        remoteChange.addFile("trunk/remotelyReplaced.txt");
        remoteChange.addDirectory("trunk/remotelyReplaced");

        remoteChange.changeFile("trunk/remotelyChanged.txt", "change".getBytes());
        remoteChange.commit();

        final Map<String, SVNStatus> result = new HashMap<String, SVNStatus>();
        SVNStatusClient stClient = SVNClientManager.newInstance().getStatusClient();
        stClient.doStatus(wc.getWorkingCopyDirectory(), SVNRevision.WORKING, SVNDepth.INFINITY, true, true, true, false, new ISVNStatusHandler() {
            public void handleStatus(SVNStatus status) throws SVNException {
                result.put(status.getRepositoryRelativePath(), status);
            }
        }, null);
        return result;
    }

    private String getTestName() {
        return "SvnRemoteStatusTest";
    }
}
