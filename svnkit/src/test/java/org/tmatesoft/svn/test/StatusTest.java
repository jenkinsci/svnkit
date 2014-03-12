package org.tmatesoft.svn.test;

import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNPropertyValue;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.auth.BasicAuthenticationManager;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationManager;
import org.tmatesoft.svn.core.internal.wc.admin.SVNAdminAreaFactory;
import org.tmatesoft.svn.core.internal.wc17.SVNWCContext;
import org.tmatesoft.svn.core.internal.wc17.db.ISVNWCDb;
import org.tmatesoft.svn.core.internal.wc2.SvnWcGeneration;
import org.tmatesoft.svn.core.internal.wc2.compat.SvnCodec;
import org.tmatesoft.svn.core.wc.*;
import org.tmatesoft.svn.core.wc2.*;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

public class StatusTest {

    @Test
    public void testRemoteStatusShowsLockDavAccess() throws Exception {
        final TestOptions options = TestOptions.getInstance();
        Assume.assumeTrue(TestUtil.areAllApacheOptionsSpecified(options));

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testRemoteStatusShowsLockDavAccess", options);
        try {
            final ISVNAuthenticationManager authenticationManager = new BasicAuthenticationManager("user1", "user1");

            final Map<String, String> loginToPassword = new HashMap<String, String>();
            loginToPassword.put("user1", "user1");

            final SVNURL url = sandbox.createSvnRepositoryWithDavAccess(loginToPassword);

            final CommitBuilder commitBuilder1 = new CommitBuilder(url);
            commitBuilder1.setAuthenticationManager(authenticationManager);
            commitBuilder1.addDirectory("directory");
            commitBuilder1.commit();

            final CommitBuilder commitBuilder2 = new CommitBuilder(url);
            commitBuilder2.setAuthenticationManager(authenticationManager);
            commitBuilder2.addFile("directory/file");
            commitBuilder2.commit();

            final SVNURL directoryUrl = url.appendPath("directory", false);
            final SVNURL fileUrl = directoryUrl.appendPath("file", false);

            final File workingCopyDirectory = sandbox.createDirectory("wc");

            svnOperationFactory.setAuthenticationManager(authenticationManager);

            final SvnCheckout checkout = svnOperationFactory.createCheckout();
            checkout.setSource(SvnTarget.fromURL(directoryUrl));
            checkout.setRevision(SVNRevision.create(1));
            checkout.setSingleTarget(SvnTarget.fromFile(workingCopyDirectory));
            checkout.run();

            final SvnSetLock setLock = svnOperationFactory.createSetLock();
            setLock.setSingleTarget(SvnTarget.fromURL(fileUrl));
            setLock.run();


            final SVNWCContext context = new SVNWCContext(svnOperationFactory.getOptions(), svnOperationFactory.getEventHandler());
            try {
                final SvnGetStatus getStatus = svnOperationFactory.createGetStatus();
                getStatus.setRemote(true);
                getStatus.setReportAll(true);
                getStatus.setSingleTarget(SvnTarget.fromFile(workingCopyDirectory));
                getStatus.setReceiver(new ISvnObjectReceiver<SvnStatus>() {
                    public void receive(SvnTarget target, SvnStatus status) throws SVNException {
                        final SVNStatus oldStatus = SvnCodec.status(context, status);

                        if ("file".equals(status.getPath().getName())) {
                            Assert.assertNull(status.getLock());
                            Assert.assertNotNull(status.getRepositoryLock());
                            Assert.assertNull(oldStatus.getLocalLock());
                            Assert.assertNotNull(oldStatus.getRemoteLock());
                        } else {
                            Assert.assertNull(status.getLock());
                            Assert.assertNull(status.getRepositoryLock());
                            Assert.assertNull(oldStatus.getLocalLock());
                            Assert.assertNull(oldStatus.getRemoteLock());
                        }
                    }
                });
                getStatus.run();
            } finally {
                context.close();
            }
        } finally {
            svnOperationFactory.dispose();
            sandbox.dispose();
        }
    }

    @Test
    public void testRemoteStatusOnModifiedFileDepthEmpty() throws Exception {
        final TestOptions options = TestOptions.getInstance();

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testRemoteStatusOnModifiedFileDepthEmpty", options);
        try {
            final SVNURL url = sandbox.createSvnRepository();

            final CommitBuilder commitBuilder = new CommitBuilder(url);
            commitBuilder.addFile("file");
            commitBuilder.commit();

            final WorkingCopy workingCopy = sandbox.checkoutNewWorkingCopy(url);

            final File file = workingCopy.getFile("file");
            TestUtil.writeFileContentsString(file, "modified");

            final SvnGetStatus getStatus = svnOperationFactory.createGetStatus();
            getStatus.setDepth(SVNDepth.EMPTY);
            getStatus.setRemote(true);
            getStatus.setSingleTarget(SvnTarget.fromFile(file));
            final SvnStatus status = getStatus.run();

            Assert.assertNotNull(status);
            Assert.assertEquals(SVNStatusType.STATUS_MODIFIED, status.getTextStatus());
        } finally {
            svnOperationFactory.dispose();
            sandbox.dispose();
        }
    }

    @Test
    public void testWorkingCopyFormatReported() throws Exception {
        final TestOptions options = TestOptions.getInstance();

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testWorkingCopyFormatReported", options);
        try {
            final SVNURL url = sandbox.createSvnRepository();

            final CommitBuilder commitBuilder = new CommitBuilder(url);
            commitBuilder.addFile("remotelyAddedFile");
            commitBuilder.commit();

            final WorkingCopy workingCopy = sandbox.checkoutNewWorkingCopy(url, 0);

            final File unversionedFile = workingCopy.getFile("unversionedFile");
            TestUtil.writeFileContentsString(unversionedFile, "contents");

            final File addedFile = workingCopy.getFile("addedFile");
            TestUtil.writeFileContentsString(addedFile, "contents");
            workingCopy.add(addedFile);

            final int expectedWcFormat = TestUtil.isNewWorkingCopyTest() ? ISVNWCDb.WC_FORMAT_18 : SVNAdminAreaFactory.WC_FORMAT_16;

            final SVNClientManager clientManager = SVNClientManager.newInstance();
            try {
                final SVNStatusClient statusClient = clientManager.getStatusClient();
                statusClient.doStatus(workingCopy.getWorkingCopyDirectory(), SVNRevision.WORKING, SVNDepth.INFINITY, true, true, true, false, new ISVNStatusHandler() {
                    public void handleStatus(SVNStatus status) throws SVNException {
                        Assert.assertEquals(expectedWcFormat, status.getWorkingCopyFormat());
                    }
                }, null);
            } finally {
                clientManager.dispose();
            }

        } finally {
            svnOperationFactory.dispose();
            sandbox.dispose();
        }
    }

    @Test
    public void testRemoteStatusOfRemotelyDeletedNode() throws Exception {
        final TestOptions options = TestOptions.getInstance();

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testRemoteStatusOfRemotelyDeletedNode", options);
        try {
            final SVNURL url = sandbox.createSvnRepository();

            final CommitBuilder commitBuilder1 = new CommitBuilder(url);
            commitBuilder1.addFile("file", "theirs".getBytes());
            commitBuilder1.addDirectory("directory");
            commitBuilder1.commit();

            final CommitBuilder commitBuilder2 = new CommitBuilder(url);
            commitBuilder2.delete("file");
            commitBuilder2.delete("directory");
            commitBuilder2.commit();

            final WorkingCopy workingCopy = sandbox.checkoutNewWorkingCopy(url, 0);

            final File file = workingCopy.getFile("file");
            final File directory = workingCopy.getFile("directory");

            final SvnUpdate updateFile = svnOperationFactory.createUpdate();
            updateFile.setRevision(SVNRevision.create(1));
            updateFile.setSingleTarget(SvnTarget.fromFile(file));
            updateFile.run();

            final SvnUpdate updateDirectory = svnOperationFactory.createUpdate();
            updateDirectory.setRevision(SVNRevision.create(1));
            updateDirectory.setSingleTarget(SvnTarget.fromFile(directory));
            updateDirectory.run();

            final SvnStatus[] fileStatus = new SvnStatus[1];
            final SvnStatus[] directoryStatus = new SvnStatus[1];

            final SvnGetStatus getStatus = svnOperationFactory.createGetStatus();
            getStatus.setSingleTarget(SvnTarget.fromFile(workingCopy.getWorkingCopyDirectory()));
            getStatus.setRemote(true);
            getStatus.setReportAll(true);
            getStatus.setReceiver(new ISvnObjectReceiver<SvnStatus>() {
                public void receive(SvnTarget target, SvnStatus status) throws SVNException {
                    if (target.getFile().getName().equals("file")) {
                        fileStatus[0] = status;
                    } else if (target.getFile().getName().equals("directory")) {
                        directoryStatus[0] = status;
                    }
                }
            });
            getStatus.run();

            Assert.assertEquals("file", fileStatus[0].getRepositoryRelativePath());
            Assert.assertEquals("directory", directoryStatus[0].getRepositoryRelativePath());

        } finally {
            svnOperationFactory.dispose();
            sandbox.dispose();
        }
    }

    @Test
    public void testCombinedNodeAndContentsStatus() throws Exception {
        final TestOptions options = TestOptions.getInstance();

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testCombinedNodeAndContentsStatus", options);
        try {
            final SVNURL url = sandbox.createSvnRepository();

            final CommitBuilder commitBuilder1 = new CommitBuilder(url);
            commitBuilder1.addFile("notModifiedFile");
            commitBuilder1.addFile("modifiedFile");
            commitBuilder1.addFile("deletedFile");
            commitBuilder1.commit();

            final CommitBuilder commitBuilder2 = new CommitBuilder(url);
            commitBuilder2.changeFile("modifiedFile", "contents".getBytes());
            commitBuilder2.delete("deletedFile");
            commitBuilder2.addFile("addedFile");
            commitBuilder2.commit();

            final WorkingCopy workingCopy = sandbox.checkoutNewWorkingCopy(url, 1);

            final SVNClientManager clientManager = SVNClientManager.newInstance();
            try {
                final SVNStatusClient statusClient = clientManager.getStatusClient();
                statusClient.doStatus(workingCopy.getWorkingCopyDirectory(),
                        SVNRevision.HEAD,
                        SVNDepth.INFINITY,
                        true,
                        true,
                        false,
                        false,
                        new ISVNStatusHandler() {
                            public void handleStatus(SVNStatus status) throws SVNException {
                                final File file = status.getFile();
                                if (file.getName().endsWith("addedFile")) {
                                    Assert.assertEquals(SVNStatusType.STATUS_ADDED, status.getCombinedRemoteNodeAndContentsStatus());
                                } else if (file.getName().endsWith("deletedFile")) {
                                    Assert.assertEquals(SVNStatusType.STATUS_DELETED, status.getCombinedRemoteNodeAndContentsStatus());
                                } else if (file.getName().endsWith("modifiedFile")) {
                                    Assert.assertEquals(SVNStatusType.STATUS_MODIFIED, status.getCombinedRemoteNodeAndContentsStatus());
                                } else if (file.getName().endsWith("notModifiedFile")) {
                                    Assert.assertEquals(SVNStatusType.STATUS_NONE, status.getCombinedRemoteNodeAndContentsStatus());
                                }
                            }
                        },
                        null);


            } finally {
                clientManager.dispose();
            }


        } finally {
            svnOperationFactory.dispose();
            sandbox.dispose();
        }
    }

    @Test
    public void testRemoteStatusTextNotModifiedWhenPropertiesModified() throws Exception {
        //SVNKIT-437
        final TestOptions options = TestOptions.getInstance();

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testRemoteStatusTextNotModifiedWhenPropertiesModified", options);

        try {
            final SVNURL url = sandbox.createSvnRepository();

            // Add file
            final CommitBuilder commitBuilder1 = new CommitBuilder(url);
            commitBuilder1.addFile("file");
            commitBuilder1.commit();

            // Check out a working copy (wc1)
            final WorkingCopy workingCopy1 = sandbox.checkoutNewWorkingCopy(url);
            final File file1 = workingCopy1.getFile("file");

            // Check out another working copy (wc2)
            final WorkingCopy workingCopy2 = sandbox.checkoutNewWorkingCopy(url, 1, true, SvnWcGeneration.V17, sandbox.createDirectory("wc2"));
            final File file2 = workingCopy2.getFile("file");

            // Set property and commit (from wc2) using the old API (what we use in our app & tests)
            // Somehow, this seems to commit something different?!
            SVNClientManager scm = SVNClientManager.newInstance();
            scm.getWCClient().doSetProperty(file2, "p", SVNPropertyValue.create("v"), true, SVNDepth.EMPTY, null, null);
            scm.getCommitClient().doCommit(new File[] { file2 }, false, "", null, null, false, true, SVNDepth.EMPTY);

            final SvnGetStatus getStatus = svnOperationFactory.createGetStatus();
            getStatus.setSingleTarget(SvnTarget.fromFile(file1));
            getStatus.setRemote(true);
            final SvnStatus status = getStatus.run();

            Assert.assertEquals(SVNStatusType.STATUS_NORMAL, status.getNodeStatus());
            Assert.assertEquals(SVNStatusType.STATUS_NORMAL, status.getTextStatus());
            Assert.assertEquals(SVNStatusType.STATUS_NONE, status.getPropertiesStatus());

            Assert.assertEquals(SVNStatusType.STATUS_MODIFIED, status.getRepositoryNodeStatus());
            Assert.assertEquals(SVNStatusType.STATUS_NONE, status.getRepositoryTextStatus());
            Assert.assertEquals(SVNStatusType.STATUS_MODIFIED, status.getRepositoryPropertiesStatus());
        } finally {
            svnOperationFactory.dispose();
            sandbox.dispose();
        }
    }

    @Test
    public void testConflictGeneratedFilesAreReported() throws Exception {
        final TestOptions options = TestOptions.getInstance();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testConflictGeneratedFilesAreReported", options);

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        try {
            final SVNURL url = sandbox.createSvnRepository();

            // Create file
            CommitBuilder commitBuilder = new CommitBuilder(url);
            commitBuilder.addFile("conflict.txt");
            commitBuilder.commit();

            // WC 1 - will have the conflict
            WorkingCopy wc1 = sandbox.checkoutNewWorkingCopy(url);
            File file1 = wc1.getFile("conflict.txt");
            TestUtil.writeFileContentsString(file1, "wc1");

            // WC 2 - used to generate the conflict
            WorkingCopy wc2 = sandbox.checkoutNewWorkingCopy(url);
            File file2 = wc2.getFile("conflict.txt");
            TestUtil.writeFileContentsString(file2, "wc2");
            wc2.commit("test");

            // Get conflict
            try {
                wc1.updateToRevision(-1);
            } catch (Throwable t) {
                // Throws a runtime exception, didn't study it, but the conflict is generated
            }

            // We use old API
            SVNClientManager scm = SVNClientManager.newInstance();
            SVNStatus status = scm.getStatusClient().doStatus(file1, false);
            Assert.assertEquals(SVNStatusType.STATUS_CONFLICTED, status.getNodeStatus());
            Assert.assertEquals(SVNStatusType.STATUS_CONFLICTED, status.getContentsStatus());

            Assert.assertEquals(wc1.getFile("conflict.txt.r2"), status.getConflictNewFile());
            Assert.assertEquals(wc1.getFile("conflict.txt.r1"), status.getConflictOldFile());
            Assert.assertEquals(wc1.getFile("conflict.txt.mine"), status.getConflictWrkFile());

        } finally {
            sandbox.dispose();
            svnOperationFactory.dispose();
        }
    }

    private String getTestName() {
        return "StatusTest";
    }
}

