package org.tmatesoft.svn.test;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import junit.framework.Assert;
import org.junit.Test;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNStatusType;
import org.tmatesoft.svn.core.wc2.ISvnObjectReceiver;
import org.tmatesoft.svn.core.wc2.SvnCopy;
import org.tmatesoft.svn.core.wc2.SvnCopySource;
import org.tmatesoft.svn.core.wc2.SvnGetStatus;
import org.tmatesoft.svn.core.wc2.SvnOperationFactory;
import org.tmatesoft.svn.core.wc2.SvnScheduleForAddition;
import org.tmatesoft.svn.core.wc2.SvnScheduleForRemoval;
import org.tmatesoft.svn.core.wc2.SvnStatus;
import org.tmatesoft.svn.core.wc2.SvnTarget;

public class VirtualCopyTest {

    @Test
    public void testFileMovedVirtuallyMoved() throws Exception {
        final TestOptions options = TestOptions.getInstance();

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup("testFileMovedVirtuallyMoved", options);
        try {
            final SVNURL url = sandbox.createSvnRepository();

            final String versionedFile = "directory1/versionedFile.txt";
            final String unversionedFile = "directory3/unversionedFile.txt";
            final String fileNotToAdd = "directory3/fileNotToAdd.txt"; //if makeParents for unversionedFile calls "svn add" with infinite depth, this file will be added (that is not expected)

            final CommitBuilder commitBuilder = new CommitBuilder(url);
            commitBuilder.setCommitMessage("Added 2 files into repository.");
            commitBuilder.addFile(versionedFile);
            commitBuilder.commit();

            final WorkingCopy workingCopy = sandbox.checkoutWorkingCopy(url, SVNRevision.HEAD.getNumber());
            final File workingCopyDirectory = workingCopy.getWorkingCopyDirectory();

            final File wcVersionedFile = new File(workingCopyDirectory, versionedFile);
            final File wcUnversionedFile = new File(workingCopyDirectory, unversionedFile);
            final File wcFileNotToAdd = new File(workingCopyDirectory, fileNotToAdd);

            SVNFileUtil.rename(wcVersionedFile, wcUnversionedFile);

            moveVirtual(svnOperationFactory, wcVersionedFile, wcUnversionedFile);

            final Map<File, SvnStatus> statuses = getStatus(svnOperationFactory, workingCopyDirectory);

            assertStatus(SVNStatusType.STATUS_ADDED, wcUnversionedFile, statuses);
            assertStatus(SVNStatusType.STATUS_ADDED, wcUnversionedFile.getParentFile(), statuses);
            assertStatus(SVNStatusType.STATUS_DELETED, wcVersionedFile, statuses);
            assertStatus(SVNStatusType.STATUS_NONE, wcFileNotToAdd, statuses);
            assertStatus(SVNStatusType.STATUS_NORMAL, wcVersionedFile.getParentFile(), statuses);

            Assert.assertEquals(url.appendPath(versionedFile, false), statuses.get(wcUnversionedFile).getCopyFromUrl());
        } finally {
            sandbox.dispose();
            svnOperationFactory.dispose();
        }
    }

    @Test
    public void testFileMovedVirtuallyCopied() throws Exception {
        final TestOptions options = TestOptions.getInstance();

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup("testFileMovedVirtuallyCopied", options);
        try {
            final SVNURL url = sandbox.createSvnRepository();

            final String versionedFile = "directory1/versionedFile.txt";
            final String unversionedFile = "directory3/unversionedFile.txt";
            final String fileNotToAdd = "directory3/fileNotToAdd.txt"; //if makeParents for unversionedFile calls "svn add" with infinite depth, this file will be added (that is not expected)

            final CommitBuilder commitBuilder = new CommitBuilder(url);
            commitBuilder.setCommitMessage("Added 2 files into repository.");
            commitBuilder.addFile(versionedFile);
            commitBuilder.commit();

            final WorkingCopy workingCopy = sandbox.checkoutWorkingCopy(url, SVNRevision.HEAD.getNumber());
            final File workingCopyDirectory = workingCopy.getWorkingCopyDirectory();

            final File wcVersionedFile = new File(workingCopyDirectory, versionedFile);
            final File wcUnversionedFile = new File(workingCopyDirectory, unversionedFile);
            final File wcFileNotToAdd = new File(workingCopyDirectory, fileNotToAdd);

            SVNFileUtil.rename(wcVersionedFile, wcUnversionedFile);

            copyVirtual(svnOperationFactory, wcVersionedFile, wcUnversionedFile);

            final Map<File, SvnStatus> statuses = getStatus(svnOperationFactory, workingCopyDirectory);

            assertStatus(SVNStatusType.STATUS_ADDED, wcUnversionedFile.getParentFile(), statuses);
            assertStatus(SVNStatusType.STATUS_ADDED, wcUnversionedFile, statuses);
            assertStatus(SVNStatusType.STATUS_MISSING, wcVersionedFile, statuses);
            assertStatus(SVNStatusType.STATUS_NONE, wcFileNotToAdd, statuses);
            assertStatus(SVNStatusType.STATUS_NORMAL, wcVersionedFile.getParentFile(), statuses);

            Assert.assertEquals(url.appendPath(versionedFile, false), statuses.get(wcUnversionedFile).getCopyFromUrl());
        } finally {
            sandbox.dispose();
            svnOperationFactory.dispose();
        }
    }

    @Test
    public void testFileCopiedVirtuallyCopied() throws Exception {
        final TestOptions options = TestOptions.getInstance();

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup("testFileCopiedVirtuallyCopied", options);
        try {
            final SVNURL url = sandbox.createSvnRepository();

            final String versionedFile = "directory1/versionedFile.txt";
            final String unversionedFile = "directory3/unversionedFile.txt";
            final String fileNotToAdd = "directory3/fileNotToAdd.txt"; //if makeParents for unversionedFile calls "svn add" with infinite depth, this file will be added (that is not expected)

            final CommitBuilder commitBuilder = new CommitBuilder(url);
            commitBuilder.setCommitMessage("Added 2 files into repository.");
            commitBuilder.addFile(versionedFile);
            commitBuilder.commit();

            final WorkingCopy workingCopy = sandbox.checkoutWorkingCopy(url, SVNRevision.HEAD.getNumber());
            final File workingCopyDirectory = workingCopy.getWorkingCopyDirectory();

            final File wcVersionedFile = new File(workingCopyDirectory, versionedFile);
            final File wcUnversionedFile = new File(workingCopyDirectory, unversionedFile);
            final File wcFileNotToAdd = new File(workingCopyDirectory, fileNotToAdd);

            SVNFileUtil.copyFile(wcVersionedFile, wcUnversionedFile, false);

            copyVirtual(svnOperationFactory, wcVersionedFile, wcUnversionedFile);

            final Map<File, SvnStatus> statuses = getStatus(svnOperationFactory, workingCopyDirectory);

            assertStatus(SVNStatusType.STATUS_ADDED, wcUnversionedFile.getParentFile(), statuses);
            assertStatus(SVNStatusType.STATUS_NORMAL, wcVersionedFile, statuses);
            assertStatus(SVNStatusType.STATUS_ADDED, wcUnversionedFile, statuses);
            assertStatus(SVNStatusType.STATUS_NONE, wcFileNotToAdd, statuses);
            assertStatus(SVNStatusType.STATUS_NORMAL, wcVersionedFile.getParentFile(), statuses);

            Assert.assertEquals(url.appendPath(versionedFile, false), statuses.get(wcUnversionedFile).getCopyFromUrl());
        } finally {
            sandbox.dispose();
            svnOperationFactory.dispose();
        }
    }

    @Test
    public void testFileCopiedVirtualMoveFailed() throws Exception {
        final TestOptions options = TestOptions.getInstance();

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup("testFileCopiedVirtualMoveFailed", options);
        try {
            final SVNURL url = sandbox.createSvnRepository();

            final String versionedFile = "directory1/versionedFile.txt";
            final String unversionedFile = "directory3/unversionedFile.txt";

            final CommitBuilder commitBuilder = new CommitBuilder(url);
            commitBuilder.setCommitMessage("Added 2 files into repository.");
            commitBuilder.addFile(versionedFile);
            commitBuilder.commit();

            final WorkingCopy workingCopy = sandbox.checkoutWorkingCopy(url, SVNRevision.HEAD.getNumber());
            final File workingCopyDirectory = workingCopy.getWorkingCopyDirectory();

            final File wcVersionedFile = new File(workingCopyDirectory, versionedFile);
            final File wcUnversionedFile = new File(workingCopyDirectory, unversionedFile);

            SVNFileUtil.copyFile(wcVersionedFile, wcUnversionedFile, false);

            Assert.assertTrue(wcUnversionedFile.exists());

            try {
                moveVirtual(svnOperationFactory, wcVersionedFile, wcUnversionedFile);
                Assert.fail("An exception should be thrown");
            } catch (SVNException e) {
                e.printStackTrace();
                Assert.assertEquals(SVNErrorCode.ENTRY_EXISTS, e.getErrorMessage().getErrorCode());
                //expected
            }

            final Map<File, SvnStatus> statuses = getStatus(svnOperationFactory, workingCopyDirectory);
            assertStatus(SVNStatusType.STATUS_NORMAL, wcVersionedFile, statuses);
            assertStatus(SVNStatusType.STATUS_NORMAL, wcVersionedFile.getParentFile(), statuses);
            assertStatus(SVNStatusType.STATUS_NONE, wcUnversionedFile, statuses);
            assertStatus(SVNStatusType.STATUS_UNVERSIONED, wcUnversionedFile.getParentFile(), statuses);
        } finally {
            sandbox.dispose();
            svnOperationFactory.dispose();
        }
    }

    @Test
    public void testFileUnversionedToVersionedVirutalMoveFailed() throws Exception {
        final TestOptions options = TestOptions.getInstance();

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup("testFileUnversionedToVersionedVirutalMoveFailed", options);
        try {
            final SVNURL url = sandbox.createSvnRepository();

            final String versionedFile = "directory1/versionedFile.txt";
            final String unversionedFile = "directory3/unversionedFile.txt";

            final CommitBuilder commitBuilder = new CommitBuilder(url);
            commitBuilder.setCommitMessage("Added 2 files into repository.");
            commitBuilder.addFile(versionedFile);
            commitBuilder.commit();

            final WorkingCopy workingCopy = sandbox.checkoutWorkingCopy(url, SVNRevision.HEAD.getNumber());
            final File workingCopyDirectory = workingCopy.getWorkingCopyDirectory();

            final File wcVersionedFile = new File(workingCopyDirectory, versionedFile);
            final File wcUnversionedFile = new File(workingCopyDirectory, unversionedFile);

            try {
                moveVirtual(svnOperationFactory, wcUnversionedFile, wcVersionedFile);
                Assert.fail("An exception should be thrown");
            } catch (SVNException e) {
                e.printStackTrace();
                Assert.assertEquals(SVNErrorCode.ENTRY_ATTRIBUTE_INVALID, e.getErrorMessage().getErrorCode());
                //expected
            }
            final Map<File, SvnStatus> statuses = getStatus(svnOperationFactory, workingCopyDirectory);
            assertStatus(SVNStatusType.STATUS_NONE, wcUnversionedFile, statuses);
            assertStatus(SVNStatusType.STATUS_NONE, wcUnversionedFile.getParentFile(), statuses);
        } finally {
            sandbox.dispose();
            svnOperationFactory.dispose();
        }
    }

    @Test
    public void testFileUnversionedToUnversionedVirutalMoveFailed() throws Exception {
        final TestOptions options = TestOptions.getInstance();

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup("testFileUnversionedToUnversionedVirutalMoveFailed", options);
        try {
            final SVNURL url = sandbox.createSvnRepository();

            final String versionedFile = "directory1/versionedFile.txt";
            final String unversionedFile = "directory3/unversionedFile.txt";
            final String anotherUnversionedFile = "directory4/anotherUnversionedFile.txt";

            final CommitBuilder commitBuilder = new CommitBuilder(url);
            commitBuilder.setCommitMessage("Added 2 files into repository.");
            commitBuilder.addFile(versionedFile);
            commitBuilder.commit();

            final WorkingCopy workingCopy = sandbox.checkoutWorkingCopy(url, SVNRevision.HEAD.getNumber());
            final File workingCopyDirectory = workingCopy.getWorkingCopyDirectory();

            final File wcUnversionedFile = new File(workingCopyDirectory, unversionedFile);
            final File wcAnotherUnversionedFile = new File(workingCopyDirectory, anotherUnversionedFile);

            SVNFileUtil.ensureDirectoryExists(wcAnotherUnversionedFile.getParentFile());

            Assert.assertTrue(wcAnotherUnversionedFile.createNewFile());

            try {
                moveVirtual(svnOperationFactory, wcUnversionedFile, wcAnotherUnversionedFile);
                Assert.fail("An exception should be thrown");
            } catch (SVNException e) {
                e.printStackTrace();
                Assert.assertEquals(SVNErrorCode.ENTRY_NOT_FOUND, e.getErrorMessage().getErrorCode());
                //expected
            }
            final Map<File, SvnStatus> statuses = getStatus(svnOperationFactory, workingCopyDirectory);
            assertStatus(SVNStatusType.STATUS_NONE, wcUnversionedFile, statuses);
            assertStatus(SVNStatusType.STATUS_NONE, wcUnversionedFile.getParentFile(), statuses);
        } finally {
            sandbox.dispose();
            svnOperationFactory.dispose();
        }
    }

    @Test
    public void testCopyingAlreadyCopied() throws Exception {
        final TestOptions options = TestOptions.getInstance();

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup("testCopyingAlreadyCopied", options);
        try {
            final SVNURL url = sandbox.createSvnRepository();

            final String versionedFile = "directory1/versionedFile.txt";
            final String unversionedFile = "directory3/unversionedFile.txt";
            final String anotherUnversionedFile = "directory4/anotherUnversionedFile.txt";

            final CommitBuilder commitBuilder = new CommitBuilder(url);
            commitBuilder.setCommitMessage("Added 2 files into repository.");
            commitBuilder.addFile(versionedFile);
            commitBuilder.commit();

            final WorkingCopy workingCopy = sandbox.checkoutWorkingCopy(url, SVNRevision.HEAD.getNumber());
            final File workingCopyDirectory = workingCopy.getWorkingCopyDirectory();

            final File wcVersionedFile = new File(workingCopyDirectory, versionedFile);
            final File wcUnversionedFile = new File(workingCopyDirectory, unversionedFile);
            final File wcAnotherUnversionedFile = new File(workingCopyDirectory, anotherUnversionedFile);

            SVNFileUtil.ensureDirectoryExists(wcAnotherUnversionedFile.getParentFile());

            copy(svnOperationFactory, wcVersionedFile, wcUnversionedFile);

            SVNFileUtil.copyFile(wcUnversionedFile, wcAnotherUnversionedFile, false);

            copyVirtual(svnOperationFactory, wcUnversionedFile, wcAnotherUnversionedFile);

            final Map<File, SvnStatus> statuses = getStatus(svnOperationFactory, workingCopyDirectory);
            assertStatus(SVNStatusType.STATUS_NORMAL, wcVersionedFile, statuses);
            assertStatus(SVNStatusType.STATUS_NORMAL, wcVersionedFile.getParentFile(), statuses);

            assertStatus(SVNStatusType.STATUS_ADDED, wcUnversionedFile, statuses);
            assertStatus(SVNStatusType.STATUS_ADDED, wcUnversionedFile.getParentFile(), statuses);

            assertStatus(SVNStatusType.STATUS_ADDED, wcAnotherUnversionedFile, statuses);
            assertStatus(SVNStatusType.STATUS_ADDED, wcAnotherUnversionedFile.getParentFile(), statuses);

            Assert.assertEquals(url.appendPath(versionedFile, false), statuses.get(wcUnversionedFile).getCopyFromUrl());
            Assert.assertEquals(url.appendPath(versionedFile, false), statuses.get(wcAnotherUnversionedFile).getCopyFromUrl());
        } finally {
            sandbox.dispose();
            svnOperationFactory.dispose();
        }
    }

    @Test
    public void testMovingAlreadyCopied() throws Exception {
        final TestOptions options = TestOptions.getInstance();

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup("testMovingAlreadyCopied", options);
        try {
            final SVNURL url = sandbox.createSvnRepository();

            final String versionedFile = "directory1/versionedFile.txt";
            final String unversionedFile = "directory3/unversionedFile.txt";
            final String anotherUnversionedFile = "directory4/anotherUnversionedFile.txt";

            final CommitBuilder commitBuilder = new CommitBuilder(url);
            commitBuilder.setCommitMessage("Added 2 files into repository.");
            commitBuilder.addFile(versionedFile);
            commitBuilder.commit();

            final WorkingCopy workingCopy = sandbox.checkoutWorkingCopy(url, SVNRevision.HEAD.getNumber());
            final File workingCopyDirectory = workingCopy.getWorkingCopyDirectory();

            final File wcVersionedFile = new File(workingCopyDirectory, versionedFile);
            final File wcUnversionedFile = new File(workingCopyDirectory, unversionedFile);
            final File wcAnotherUnversionedFile = new File(workingCopyDirectory, anotherUnversionedFile);

            SVNFileUtil.ensureDirectoryExists(wcAnotherUnversionedFile.getParentFile());

            copy(svnOperationFactory, wcVersionedFile, wcUnversionedFile);

            SVNFileUtil.rename(wcUnversionedFile, wcAnotherUnversionedFile);

            moveVirtual(svnOperationFactory, wcUnversionedFile, wcAnotherUnversionedFile);

            final Map<File, SvnStatus> statuses = getStatus(svnOperationFactory, workingCopyDirectory);
            assertStatus(SVNStatusType.STATUS_NORMAL, wcVersionedFile, statuses);
            assertStatus(SVNStatusType.STATUS_NORMAL, wcVersionedFile.getParentFile(), statuses);

            assertStatus(SVNStatusType.STATUS_NONE, wcUnversionedFile, statuses);
            assertStatus(SVNStatusType.STATUS_ADDED, wcUnversionedFile.getParentFile(), statuses);

            assertStatus(SVNStatusType.STATUS_ADDED, wcAnotherUnversionedFile, statuses);
            assertStatus(SVNStatusType.STATUS_ADDED, wcAnotherUnversionedFile.getParentFile(), statuses);

            Assert.assertEquals(url.appendPath(versionedFile, false), statuses.get(wcAnotherUnversionedFile).getCopyFromUrl());
        } finally {
            sandbox.dispose();
            svnOperationFactory.dispose();
        }
    }

    @Test
    public void testCopyingAlreadyMoved() throws Exception {
        final TestOptions options = TestOptions.getInstance();

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup("testCopyingAlreadyMoved", options);
        try {
            final SVNURL url = sandbox.createSvnRepository();

            final String versionedFile = "directory1/versionedFile.txt";
            final String unversionedFile = "directory3/unversionedFile.txt";
            final String anotherUnversionedFile = "directory4/anotherUnversionedFile.txt";

            final CommitBuilder commitBuilder = new CommitBuilder(url);
            commitBuilder.setCommitMessage("Added 2 files into repository.");
            commitBuilder.addFile(versionedFile);
            commitBuilder.commit();

            final WorkingCopy workingCopy = sandbox.checkoutWorkingCopy(url, SVNRevision.HEAD.getNumber());
            final File workingCopyDirectory = workingCopy.getWorkingCopyDirectory();

            final File wcVersionedFile = new File(workingCopyDirectory, versionedFile);
            final File wcUnversionedFile = new File(workingCopyDirectory, unversionedFile);
            final File wcAnotherUnversionedFile = new File(workingCopyDirectory, anotherUnversionedFile);

            SVNFileUtil.ensureDirectoryExists(wcAnotherUnversionedFile.getParentFile());

            move(svnOperationFactory, wcVersionedFile, wcUnversionedFile);

            SVNFileUtil.copyFile(wcUnversionedFile, wcAnotherUnversionedFile, false);

            copyVirtual(svnOperationFactory, wcUnversionedFile, wcAnotherUnversionedFile);

            final Map<File, SvnStatus> statuses = getStatus(svnOperationFactory, workingCopyDirectory);
            assertStatus(SVNStatusType.STATUS_DELETED, wcVersionedFile, statuses);
            assertStatus(SVNStatusType.STATUS_NORMAL, wcVersionedFile.getParentFile(), statuses);

            assertStatus(SVNStatusType.STATUS_ADDED, wcUnversionedFile, statuses);
            assertStatus(SVNStatusType.STATUS_ADDED, wcUnversionedFile.getParentFile(), statuses);

            assertStatus(SVNStatusType.STATUS_ADDED, wcAnotherUnversionedFile, statuses);
            assertStatus(SVNStatusType.STATUS_ADDED, wcAnotherUnversionedFile.getParentFile(), statuses);

            Assert.assertEquals(url.appendPath(versionedFile, false), statuses.get(wcUnversionedFile).getCopyFromUrl());
            Assert.assertEquals(url.appendPath(versionedFile, false), statuses.get(wcAnotherUnversionedFile).getCopyFromUrl());
        } finally {
            sandbox.dispose();
            svnOperationFactory.dispose();
        }
    }

    @Test
    public void testMovingAlreadyMoved() throws Exception {
        final TestOptions options = TestOptions.getInstance();

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup("testMovingAlreadyMoved", options);
        try {
            final SVNURL url = sandbox.createSvnRepository();

            final String versionedFile = "directory1/versionedFile.txt";
            final String unversionedFile = "directory3/unversionedFile.txt";
            final String anotherUnversionedFile = "directory4/anotherUnversionedFile.txt";

            final CommitBuilder commitBuilder = new CommitBuilder(url);
            commitBuilder.setCommitMessage("Added 2 files into repository.");
            commitBuilder.addFile(versionedFile);
            commitBuilder.commit();

            final WorkingCopy workingCopy = sandbox.checkoutWorkingCopy(url, SVNRevision.HEAD.getNumber());
            final File workingCopyDirectory = workingCopy.getWorkingCopyDirectory();

            final File wcVersionedFile = new File(workingCopyDirectory, versionedFile);
            final File wcUnversionedFile = new File(workingCopyDirectory, unversionedFile);
            final File wcAnotherUnversionedFile = new File(workingCopyDirectory, anotherUnversionedFile);

            SVNFileUtil.ensureDirectoryExists(wcAnotherUnversionedFile.getParentFile());

            move(svnOperationFactory, wcVersionedFile, wcUnversionedFile);

            SVNFileUtil.rename(wcUnversionedFile, wcAnotherUnversionedFile);

            moveVirtual(svnOperationFactory, wcUnversionedFile, wcAnotherUnversionedFile);

            final Map<File, SvnStatus> statuses = getStatus(svnOperationFactory, workingCopyDirectory);
            assertStatus(SVNStatusType.STATUS_DELETED, wcVersionedFile, statuses);
            assertStatus(SVNStatusType.STATUS_NORMAL, wcVersionedFile.getParentFile(), statuses);

            assertStatus(SVNStatusType.STATUS_NONE, wcUnversionedFile, statuses);
            assertStatus(SVNStatusType.STATUS_ADDED, wcUnversionedFile.getParentFile(), statuses);

            assertStatus(SVNStatusType.STATUS_ADDED, wcAnotherUnversionedFile, statuses);
            assertStatus(SVNStatusType.STATUS_ADDED, wcAnotherUnversionedFile.getParentFile(), statuses);

            Assert.assertEquals(url.appendPath(versionedFile, false), statuses.get(wcAnotherUnversionedFile).getCopyFromUrl());
        } finally {
            sandbox.dispose();
            svnOperationFactory.dispose();
        }
    }

    @Test
    public void testCopyingWronglyCopied() throws Exception {
        final TestOptions options = TestOptions.getInstance();

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup("testCopyingWronglyCopied", options);
        try {
            final SVNURL url = sandbox.createSvnRepository();

            final String versionedFile = "directory1/versionedFile.txt";
            final String unversionedFile = "directory3/unversionedFile.txt";
            final String anotherUnversionedFile = "directory4/anotherUnversionedFile.txt";

            final CommitBuilder commitBuilder = new CommitBuilder(url);
            commitBuilder.setCommitMessage("Added 2 files into repository.");
            commitBuilder.addFile(versionedFile);
            commitBuilder.commit();

            final WorkingCopy workingCopy = sandbox.checkoutWorkingCopy(url, SVNRevision.HEAD.getNumber());
            final File workingCopyDirectory = workingCopy.getWorkingCopyDirectory();

            final File wcVersionedFile = new File(workingCopyDirectory, versionedFile);
            final File wcUnversionedFile = new File(workingCopyDirectory, unversionedFile);
            final File wcAnotherUnversionedFile = new File(workingCopyDirectory, anotherUnversionedFile);

            SVNFileUtil.ensureDirectoryExists(wcAnotherUnversionedFile.getParentFile());

            wronglyCopy(svnOperationFactory, wcVersionedFile, wcUnversionedFile);

            SVNFileUtil.copyFile(wcUnversionedFile, wcAnotherUnversionedFile, false);

            copyVirtual(svnOperationFactory, wcUnversionedFile, wcAnotherUnversionedFile);

            final Map<File, SvnStatus> statuses = getStatus(svnOperationFactory, workingCopyDirectory);
            assertStatus(SVNStatusType.STATUS_NORMAL, wcVersionedFile, statuses);
            assertStatus(SVNStatusType.STATUS_NORMAL, wcVersionedFile.getParentFile(), statuses);

            assertStatus(SVNStatusType.STATUS_ADDED, wcUnversionedFile, statuses);
            assertStatus(SVNStatusType.STATUS_ADDED, wcUnversionedFile.getParentFile(), statuses);

            assertStatus(SVNStatusType.STATUS_ADDED, wcAnotherUnversionedFile, statuses);
            assertStatus(SVNStatusType.STATUS_ADDED, wcAnotherUnversionedFile.getParentFile(), statuses);

            Assert.assertNull(statuses.get(wcUnversionedFile).getCopyFromUrl());
            Assert.assertNull(statuses.get(wcAnotherUnversionedFile).getCopyFromUrl());
        } finally {
            sandbox.dispose();
            svnOperationFactory.dispose();
        }
    }

    @Test
    public void testMovingWronglyCopied() throws Exception {
        final TestOptions options = TestOptions.getInstance();

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup("testMovingWronglyCopied", options);
        try {
            final SVNURL url = sandbox.createSvnRepository();

            final String versionedFile = "directory1/versionedFile.txt";
            final String unversionedFile = "directory3/unversionedFile.txt";
            final String anotherUnversionedFile = "directory4/anotherUnversionedFile.txt";

            final CommitBuilder commitBuilder = new CommitBuilder(url);
            commitBuilder.setCommitMessage("Added 2 files into repository.");
            commitBuilder.addFile(versionedFile);
            commitBuilder.commit();

            final WorkingCopy workingCopy = sandbox.checkoutWorkingCopy(url, SVNRevision.HEAD.getNumber());
            final File workingCopyDirectory = workingCopy.getWorkingCopyDirectory();

            final File wcVersionedFile = new File(workingCopyDirectory, versionedFile);
            final File wcUnversionedFile = new File(workingCopyDirectory, unversionedFile);
            final File wcAnotherUnversionedFile = new File(workingCopyDirectory, anotherUnversionedFile);

            SVNFileUtil.ensureDirectoryExists(wcAnotherUnversionedFile.getParentFile());

            wronglyCopy(svnOperationFactory, wcVersionedFile, wcUnversionedFile);

            SVNFileUtil.rename(wcUnversionedFile, wcAnotherUnversionedFile);

            moveVirtual(svnOperationFactory, wcUnversionedFile, wcAnotherUnversionedFile);

            final Map<File, SvnStatus> statuses = getStatus(svnOperationFactory, workingCopyDirectory);
            assertStatus(SVNStatusType.STATUS_NORMAL, wcVersionedFile, statuses);
            assertStatus(SVNStatusType.STATUS_NORMAL, wcVersionedFile.getParentFile(), statuses);

            assertStatus(SVNStatusType.STATUS_NONE, wcUnversionedFile, statuses);
            assertStatus(SVNStatusType.STATUS_ADDED, wcUnversionedFile.getParentFile(), statuses);

            assertStatus(SVNStatusType.STATUS_ADDED, wcAnotherUnversionedFile, statuses);
            assertStatus(SVNStatusType.STATUS_ADDED, wcAnotherUnversionedFile.getParentFile(), statuses);

            Assert.assertNull(statuses.get(wcAnotherUnversionedFile).getCopyFromUrl());
        } finally {
            sandbox.dispose();
            svnOperationFactory.dispose();
        }
    }

    @Test
    public void testCopyingWronglyMoved() throws Exception {
        final TestOptions options = TestOptions.getInstance();

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup("testCopyingWronglyMoved", options);
        try {
            final SVNURL url = sandbox.createSvnRepository();

            final String versionedFile = "directory1/versionedFile.txt";
            final String unversionedFile = "directory3/unversionedFile.txt";
            final String anotherUnversionedFile = "directory4/anotherUnversionedFile.txt";

            final CommitBuilder commitBuilder = new CommitBuilder(url);
            commitBuilder.setCommitMessage("Added 2 files into repository.");
            commitBuilder.addFile(versionedFile);
            commitBuilder.commit();

            final WorkingCopy workingCopy = sandbox.checkoutWorkingCopy(url, SVNRevision.HEAD.getNumber());
            final File workingCopyDirectory = workingCopy.getWorkingCopyDirectory();

            final File wcVersionedFile = new File(workingCopyDirectory, versionedFile);
            final File wcUnversionedFile = new File(workingCopyDirectory, unversionedFile);
            final File wcAnotherUnversionedFile = new File(workingCopyDirectory, anotherUnversionedFile);

            SVNFileUtil.ensureDirectoryExists(wcAnotherUnversionedFile.getParentFile());

            wronglyMove(svnOperationFactory, wcVersionedFile, wcUnversionedFile);

            SVNFileUtil.copyFile(wcUnversionedFile, wcAnotherUnversionedFile, false);

            copyVirtual(svnOperationFactory, wcUnversionedFile, wcAnotherUnversionedFile);

            final Map<File, SvnStatus> statuses = getStatus(svnOperationFactory, workingCopyDirectory);
            assertStatus(SVNStatusType.STATUS_DELETED, wcVersionedFile, statuses);
            assertStatus(SVNStatusType.STATUS_NORMAL, wcVersionedFile.getParentFile(), statuses);

            assertStatus(SVNStatusType.STATUS_ADDED, wcUnversionedFile, statuses);
            assertStatus(SVNStatusType.STATUS_ADDED, wcUnversionedFile.getParentFile(), statuses);

            assertStatus(SVNStatusType.STATUS_ADDED, wcAnotherUnversionedFile, statuses);
            assertStatus(SVNStatusType.STATUS_ADDED, wcAnotherUnversionedFile.getParentFile(), statuses);

            Assert.assertNull(statuses.get(wcUnversionedFile).getCopyFromUrl());
            Assert.assertNull(statuses.get(wcAnotherUnversionedFile).getCopyFromUrl());
        } finally {
            sandbox.dispose();
            svnOperationFactory.dispose();
        }
    }

    @Test
    public void testMovingWronglyMoved() throws Exception {
        final TestOptions options = TestOptions.getInstance();

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup("testMovingWronglyMoved", options);
        try {
            final SVNURL url = sandbox.createSvnRepository();

            final String versionedFile = "directory1/versionedFile.txt";
            final String unversionedFile = "directory3/unversionedFile.txt";
            final String anotherUnversionedFile = "directory4/anotherUnversionedFile.txt";

            final CommitBuilder commitBuilder = new CommitBuilder(url);
            commitBuilder.setCommitMessage("Added 2 files into repository.");
            commitBuilder.addFile(versionedFile);
            commitBuilder.commit();

            final WorkingCopy workingCopy = sandbox.checkoutWorkingCopy(url, SVNRevision.HEAD.getNumber());
            final File workingCopyDirectory = workingCopy.getWorkingCopyDirectory();

            final File wcVersionedFile = new File(workingCopyDirectory, versionedFile);
            final File wcUnversionedFile = new File(workingCopyDirectory, unversionedFile);
            final File wcAnotherUnversionedFile = new File(workingCopyDirectory, anotherUnversionedFile);

            SVNFileUtil.ensureDirectoryExists(wcAnotherUnversionedFile.getParentFile());

            wronglyMove(svnOperationFactory, wcVersionedFile, wcUnversionedFile);

            SVNFileUtil.rename(wcUnversionedFile, wcAnotherUnversionedFile);

            moveVirtual(svnOperationFactory, wcUnversionedFile, wcAnotherUnversionedFile);

            final Map<File, SvnStatus> statuses = getStatus(svnOperationFactory, workingCopyDirectory);
            assertStatus(SVNStatusType.STATUS_DELETED, wcVersionedFile, statuses);
            assertStatus(SVNStatusType.STATUS_NORMAL, wcVersionedFile.getParentFile(), statuses);

            assertStatus(SVNStatusType.STATUS_NONE, wcUnversionedFile, statuses);
            assertStatus(SVNStatusType.STATUS_ADDED, wcUnversionedFile.getParentFile(), statuses);

            assertStatus(SVNStatusType.STATUS_ADDED, wcAnotherUnversionedFile, statuses);
            assertStatus(SVNStatusType.STATUS_ADDED, wcAnotherUnversionedFile.getParentFile(), statuses);

            Assert.assertNull(statuses.get(wcAnotherUnversionedFile).getCopyFromUrl());
        } finally {
            sandbox.dispose();
            svnOperationFactory.dispose();
        }
    }

    @Test
    public void testDirectoryCopiedFileVirtuallyCopied() throws Exception {
        final TestOptions options = TestOptions.getInstance();

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup("testDirectoryCopiedFileVirtuallyCopied", options);
        try {
            final SVNURL url = sandbox.createSvnRepository();

            final String versionedFile = "directory1/versionedFile.txt";
            final String targetFile = "directory3/versionedFile.txt";
            final String anotherTargetFile = "directory4/anotherTargetFile.txt";

            final CommitBuilder commitBuilder = new CommitBuilder(url);
            commitBuilder.setCommitMessage("Added 2 files into repository.");
            commitBuilder.addFile(versionedFile);
            commitBuilder.commit();

            final WorkingCopy workingCopy = sandbox.checkoutWorkingCopy(url, SVNRevision.HEAD.getNumber());
            final File workingCopyDirectory = workingCopy.getWorkingCopyDirectory();

            final File wcVersionedFile = new File(workingCopyDirectory, versionedFile);
            final File wcTargetFile = new File(workingCopyDirectory, targetFile);
            final File wcAnotherTargetFile = new File(workingCopyDirectory, anotherTargetFile);

            SVNFileUtil.ensureDirectoryExists(wcAnotherTargetFile.getParentFile());

            copy(svnOperationFactory, wcVersionedFile.getParentFile(), wcTargetFile.getParentFile());

            SVNFileUtil.copyFile(wcTargetFile, wcAnotherTargetFile, false);

            copyVirtual(svnOperationFactory, wcTargetFile, wcAnotherTargetFile);

            final Map<File, SvnStatus> statuses = getStatus(svnOperationFactory, workingCopyDirectory);
            assertStatus(SVNStatusType.STATUS_NORMAL, wcVersionedFile, statuses);
            assertStatus(SVNStatusType.STATUS_NORMAL, wcVersionedFile.getParentFile(), statuses);

            assertStatus(SVNStatusType.STATUS_NORMAL, wcTargetFile, statuses);
            assertStatus(SVNStatusType.STATUS_ADDED, wcTargetFile.getParentFile(), statuses);

            assertStatus(SVNStatusType.STATUS_ADDED, wcAnotherTargetFile, statuses);
            assertStatus(SVNStatusType.STATUS_ADDED, wcAnotherTargetFile.getParentFile(), statuses);

            Assert.assertEquals(url.appendPath(versionedFile, false), statuses.get(wcAnotherTargetFile).getCopyFromUrl());
        } finally {
            sandbox.dispose();
            svnOperationFactory.dispose();
        }
    }

    @Test
    public void testDirectoryMovedFileVirtuallyCopied() throws Exception {
        final TestOptions options = TestOptions.getInstance();

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup("testDirectoryMovedFileVirtuallyCopied", options);
        try {
            final SVNURL url = sandbox.createSvnRepository();

            final String versionedFile = "directory1/versionedFile.txt";
            final String targetFile = "directory3/versionedFile.txt";
            final String anotherTargetFile = "directory4/anotherTargetFile.txt";

            final CommitBuilder commitBuilder = new CommitBuilder(url);
            commitBuilder.setCommitMessage("Added 2 files into repository.");
            commitBuilder.addFile(versionedFile);
            commitBuilder.commit();

            final WorkingCopy workingCopy = sandbox.checkoutWorkingCopy(url, SVNRevision.HEAD.getNumber());
            final File workingCopyDirectory = workingCopy.getWorkingCopyDirectory();

            final File wcVersionedFile = new File(workingCopyDirectory, versionedFile);
            final File wcTargetFile = new File(workingCopyDirectory, targetFile);
            final File wcAnotherTargetFile = new File(workingCopyDirectory, anotherTargetFile);

            SVNFileUtil.ensureDirectoryExists(wcAnotherTargetFile.getParentFile());

            move(svnOperationFactory, wcVersionedFile.getParentFile(), wcTargetFile.getParentFile());

            SVNFileUtil.copyFile(wcTargetFile, wcAnotherTargetFile, false);

            copyVirtual(svnOperationFactory, wcTargetFile, wcAnotherTargetFile);

            final Map<File, SvnStatus> statuses = getStatus(svnOperationFactory, workingCopyDirectory);
            assertStatus(SVNStatusType.STATUS_DELETED, wcVersionedFile, statuses);
            assertStatus(SVNStatusType.STATUS_DELETED, wcVersionedFile.getParentFile(), statuses);

            assertStatus(SVNStatusType.STATUS_NORMAL, wcTargetFile, statuses);
            assertStatus(SVNStatusType.STATUS_ADDED, wcTargetFile.getParentFile(), statuses);

            assertStatus(SVNStatusType.STATUS_ADDED, wcAnotherTargetFile, statuses);
            assertStatus(SVNStatusType.STATUS_ADDED, wcAnotherTargetFile.getParentFile(), statuses);

            Assert.assertEquals(url.appendPath(versionedFile, false), statuses.get(wcAnotherTargetFile).getCopyFromUrl());
        } finally {
            sandbox.dispose();
            svnOperationFactory.dispose();
        }
    }

    @Test
    public void testDirectoryCopiedFileVirtuallyMoved() throws Exception {
        final TestOptions options = TestOptions.getInstance();

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup("testDirectoryCopiedFileVirtuallyMoved", options);
        try {
            final SVNURL url = sandbox.createSvnRepository();

            final String versionedFile = "directory1/versionedFile.txt";
            final String targetFile = "directory3/versionedFile.txt";
            final String anotherTargetFile = "directory4/anotherTargetFile.txt";

            final CommitBuilder commitBuilder = new CommitBuilder(url);
            commitBuilder.setCommitMessage("Added 2 files into repository.");
            commitBuilder.addFile(versionedFile);
            commitBuilder.commit();

            final WorkingCopy workingCopy = sandbox.checkoutWorkingCopy(url, SVNRevision.HEAD.getNumber());
            final File workingCopyDirectory = workingCopy.getWorkingCopyDirectory();

            final File wcVersionedFile = new File(workingCopyDirectory, versionedFile);
            final File wcTargetFile = new File(workingCopyDirectory, targetFile);
            final File wcAnotherTargetFile = new File(workingCopyDirectory, anotherTargetFile);

            SVNFileUtil.ensureDirectoryExists(wcAnotherTargetFile.getParentFile());

            copy(svnOperationFactory, wcVersionedFile.getParentFile(), wcTargetFile.getParentFile());

            SVNFileUtil.rename(wcTargetFile, wcAnotherTargetFile);

            moveVirtual(svnOperationFactory, wcTargetFile, wcAnotherTargetFile);

            final Map<File, SvnStatus> statuses = getStatus(svnOperationFactory, workingCopyDirectory);
            assertStatus(SVNStatusType.STATUS_NORMAL, wcVersionedFile, statuses);
            assertStatus(SVNStatusType.STATUS_NORMAL, wcVersionedFile.getParentFile(), statuses);

            assertStatus(SVNStatusType.STATUS_DELETED, wcTargetFile, statuses);
            assertStatus(SVNStatusType.STATUS_ADDED, wcTargetFile.getParentFile(), statuses);

            assertStatus(SVNStatusType.STATUS_ADDED, wcAnotherTargetFile, statuses);
            assertStatus(SVNStatusType.STATUS_ADDED, wcAnotherTargetFile.getParentFile(), statuses);

            Assert.assertEquals(url.appendPath(versionedFile, false), statuses.get(wcAnotherTargetFile).getCopyFromUrl());
        } finally {
            sandbox.dispose();
            svnOperationFactory.dispose();
        }
    }

    @Test
    public void testDirectoryMovedFileVirtuallyMoved() throws Exception {
        final TestOptions options = TestOptions.getInstance();

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup("testDirectoryMovedFileVirtuallyMoved", options);
        try {
            final SVNURL url = sandbox.createSvnRepository();

            final String versionedFile = "directory1/versionedFile.txt";
            final String targetFile = "directory3/versionedFile.txt";
            final String anotherTargetFile = "directory4/anotherTargetFile.txt";

            final CommitBuilder commitBuilder = new CommitBuilder(url);
            commitBuilder.setCommitMessage("Added 2 files into repository.");
            commitBuilder.addFile(versionedFile);
            commitBuilder.commit();

            final WorkingCopy workingCopy = sandbox.checkoutWorkingCopy(url, SVNRevision.HEAD.getNumber());
            final File workingCopyDirectory = workingCopy.getWorkingCopyDirectory();

            final File wcVersionedFile = new File(workingCopyDirectory, versionedFile);
            final File wcTargetFile = new File(workingCopyDirectory, targetFile);
            final File wcAnotherTargetFile = new File(workingCopyDirectory, anotherTargetFile);

            SVNFileUtil.ensureDirectoryExists(wcAnotherTargetFile.getParentFile());

            move(svnOperationFactory, wcVersionedFile.getParentFile(), wcTargetFile.getParentFile());

            SVNFileUtil.rename(wcTargetFile, wcAnotherTargetFile);

            moveVirtual(svnOperationFactory, wcTargetFile, wcAnotherTargetFile);

            final Map<File, SvnStatus> statuses = getStatus(svnOperationFactory, workingCopyDirectory);
            assertStatus(SVNStatusType.STATUS_DELETED, wcVersionedFile, statuses);
            assertStatus(SVNStatusType.STATUS_DELETED, wcVersionedFile.getParentFile(), statuses);

            assertStatus(SVNStatusType.STATUS_DELETED, wcTargetFile, statuses);
            assertStatus(SVNStatusType.STATUS_ADDED, wcTargetFile.getParentFile(), statuses);

            assertStatus(SVNStatusType.STATUS_ADDED, wcAnotherTargetFile, statuses);
            assertStatus(SVNStatusType.STATUS_ADDED, wcAnotherTargetFile.getParentFile(), statuses);

            Assert.assertEquals(url.appendPath(versionedFile, false), statuses.get(wcAnotherTargetFile).getCopyFromUrl());
        } finally {
            sandbox.dispose();
            svnOperationFactory.dispose();
        }
    }

    private void assertStatus(SVNStatusType statusType, File file, Map<File, SvnStatus> statuses) {
        if (statusType == SVNStatusType.STATUS_NORMAL && statuses.get(file) == null) {
            return;
        }
        if (statusType == SVNStatusType.STATUS_NONE && statuses.get(file) == null) {
            return;
        }
        Assert.assertEquals(statusType, statuses.get(file).getNodeStatus());
    }

    private Map<File, SvnStatus> getStatus(SvnOperationFactory svnOperationFactory, File workingCopyDirectory) throws SVNException {
        final Map<File, SvnStatus> pathToStatus = new HashMap<File, SvnStatus>();
        final SvnGetStatus status = svnOperationFactory.createGetStatus();
        status.setDepth(SVNDepth.INFINITY);
        status.setRemote(false);
        status.setReportAll(true);
        status.setReportIgnored(true);
        status.setReportExternals(false);
        status.setApplicalbeChangelists(null);
        status.setReceiver(new ISvnObjectReceiver<SvnStatus>() {
            public void receive(SvnTarget target, SvnStatus status) throws SVNException {
                pathToStatus.put(status.getPath(), status);
            }
        });

        status.addTarget(SvnTarget.fromFile(workingCopyDirectory));
        status.run();
        return pathToStatus;
    }

    private void copyVirtual(SvnOperationFactory svnOperationFactory, File fromFile, File toFile) throws SVNException {
        copy(svnOperationFactory, fromFile, toFile, false, true);
    }

    private void moveVirtual(SvnOperationFactory svnOperationFactory, File fromFile, File toFile) throws SVNException {
        copy(svnOperationFactory, fromFile, toFile, true, true);
    }

    private void move(SvnOperationFactory svnOperationFactory, File fromFile, File toFile) throws SVNException {
        copy(svnOperationFactory, fromFile, toFile, true, false);
    }

    private void copy(SvnOperationFactory svnOperationFactory, File fromFile, File toFile) throws SVNException {
        copy(svnOperationFactory, fromFile, toFile, false, false);
    }

    private void copy(SvnOperationFactory svnOperationFactory, File fromFile, File toFile, boolean move, boolean virtual) throws SVNException {
        final SvnCopy copy = svnOperationFactory.createCopy();
        copy.addCopySource(SvnCopySource.create(SvnTarget.fromFile(fromFile), SVNRevision.WORKING));
        copy.setSingleTarget(SvnTarget.fromFile(toFile));
        copy.setFailWhenDstExists(true);//fail when dst exists = !copy as child
        copy.setIgnoreExternals(true);
        copy.setMakeParents(true);
        copy.setVirtual(virtual);
        copy.setMove(move);
        copy.run();
    }

    private void wronglyMove(SvnOperationFactory svnOperationFactory, File fromFile, File toFile) throws SVNException {
        SVNFileUtil.copyFile(fromFile, toFile, false);
        delete(svnOperationFactory, fromFile);
        add(svnOperationFactory, toFile);
    }

    private void wronglyCopy(SvnOperationFactory svnOperationFactory, File fromFile, File toFile) throws SVNException {
        SVNFileUtil.copyFile(fromFile, toFile, false);

        add(svnOperationFactory, toFile);
    }

    private void delete(SvnOperationFactory svnOperationFactory, File file) throws SVNException {
        final SvnScheduleForRemoval scheduleForRemoval = svnOperationFactory.createScheduleForRemoval();
        scheduleForRemoval.setSingleTarget(SvnTarget.fromFile(file));
        scheduleForRemoval.setDeleteFiles(true);
        scheduleForRemoval.setForce(false);
        scheduleForRemoval.setDryRun(false);
        scheduleForRemoval.run();
    }

    private void add(SvnOperationFactory svnOperationFactory, File file) throws SVNException {
        final SvnScheduleForAddition scheduleForAddition = svnOperationFactory.createScheduleForAddition();
        scheduleForAddition.setSingleTarget(SvnTarget.fromFile(file));
        scheduleForAddition.setAddParents(true);
        scheduleForAddition.setMkDir(false);
        scheduleForAddition.setApplyAutoProperties(false);
        scheduleForAddition.setForce(false);
        scheduleForAddition.setIncludeIgnored(true);
        scheduleForAddition.run();
    }
}
