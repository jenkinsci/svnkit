package org.tmatesoft.svn.test;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import junit.framework.Assert;
import org.junit.Test;
import org.tmatesoft.svn.core.SVNDepth;
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
import org.tmatesoft.svn.core.wc2.SvnStatus;
import org.tmatesoft.svn.core.wc2.SvnTarget;

public class VirtualCopyTest {

    @Test
    public void testFileMovedVirtuallyMoved() throws Exception {
        final TestOptions options = TestOptions.getInstance();

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.create("testFileMoved", options, true);
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

    private void assertStatus(SVNStatusType statusType, File file, Map<File, SvnStatus> statuses) {
        if (statusType == SVNStatusType.STATUS_NORMAL && statuses.get(file) == null) {
            return;
        }
        if (statusType == SVNStatusType.STATUS_NONE && statuses.get(file) == null) {
            return;
        }
        Assert.assertEquals(statusType, statuses.get(file).getNodeStatus());
    }

    @Test
    public void testFileMovedVirtuallyCopied() throws Exception {
        final TestOptions options = TestOptions.getInstance();

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.create("testFileCopied", options, true);
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
        final Sandbox sandbox = Sandbox.create("testFileCopied", options, true);
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
        final Sandbox sandbox = Sandbox.create("testFileCopied", options, true);
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
        final Sandbox sandbox = Sandbox.create("testFileCopied", options, true);
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
        final Sandbox sandbox = Sandbox.create("testFileCopied", options, true);
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
        copyVirtual(svnOperationFactory, fromFile, toFile, false);
    }

    private void moveVirtual(SvnOperationFactory svnOperationFactory, File fromFile, File toFile) throws SVNException {
        copyVirtual(svnOperationFactory, fromFile, toFile, true);
    }

    private void copyVirtual(SvnOperationFactory svnOperationFactory, File fromFile, File toFile, boolean move) throws SVNException {
        final SvnCopy copy = svnOperationFactory.createCopy();
        copy.addCopySource(SvnCopySource.create(SvnTarget.fromFile(fromFile), SVNRevision.WORKING));
        copy.setSingleTarget(SvnTarget.fromFile(toFile));
        copy.setMove(move);
        copy.setFailWhenDstExists(true);
        copy.setIgnoreExternals(true);
        copy.setMakeParents(true);
        copy.setVirtual(true);
        copy.run();
    }
}
