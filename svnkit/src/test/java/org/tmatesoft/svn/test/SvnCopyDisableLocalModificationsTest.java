package org.tmatesoft.svn.test;

import java.io.File;
import java.util.Map;

import org.junit.Assert;
import org.junit.Test;
import org.tmatesoft.svn.core.SVNCommitInfo;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNStatusType;
import org.tmatesoft.svn.core.wc2.SvnCopy;
import org.tmatesoft.svn.core.wc2.SvnCopySource;
import org.tmatesoft.svn.core.wc2.SvnOperationFactory;
import org.tmatesoft.svn.core.wc2.SvnRemoteCopy;
import org.tmatesoft.svn.core.wc2.SvnStatus;
import org.tmatesoft.svn.core.wc2.SvnTarget;

public class SvnCopyDisableLocalModificationsTest {

    @Test
    public void testCopyFile() throws Exception {
        final TestOptions options = TestOptions.getInstance();

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testCopyFile", options);
        try {
            final SVNURL url = sandbox.createSvnRepository();

            final CommitBuilder commitBuilder = new CommitBuilder(url);
            commitBuilder.addFile("source/file");
            commitBuilder.addDirectory("target");
            commitBuilder.commit();

            final WorkingCopy workingCopy = sandbox.checkoutNewWorkingCopy(url, SVNRevision.HEAD.getNumber());
            final File workingCopyDirectory = workingCopy.getWorkingCopyDirectory();

            final File source = new File(workingCopyDirectory, "source");
            final File target = new File(workingCopyDirectory, "target");
            final File sourceFile = new File(source, "file");
            final File targetFile = new File(target, "file");

            final String sourceFileContents = "local modifications" + "\n";
            TestUtil.writeFileContentsString(sourceFile, sourceFileContents);

            final SvnRemoteCopy remoteCopy = svnOperationFactory.createRemoteCopy();
            remoteCopy.addCopySource(SvnCopySource.create(SvnTarget.fromFile(sourceFile, SVNRevision.WORKING), SVNRevision.WORKING));
            remoteCopy.setSingleTarget(SvnTarget.fromURL(url.appendPath(target.getName(), false).appendPath(targetFile.getName(), false)));
            remoteCopy.setDisableLocalModifications(true);
            final SVNCommitInfo commitInfo = remoteCopy.run();

            workingCopy.revert();
            workingCopy.updateToRevision(commitInfo.getNewRevision());

            final String targetFileContents = TestUtil.readFileContentsString(targetFile);
            Assert.assertEquals("", targetFileContents);//should not be equal to sourceFileContents
        } finally {
            svnOperationFactory.dispose();
            sandbox.dispose();
        }
    }

    @Test
    public void testCopyDirectory() throws Exception {
        final TestOptions options = TestOptions.getInstance();

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testCopyDirectory", options);
        try {
            final SVNURL url = sandbox.createSvnRepository();

            final CommitBuilder commitBuilder = new CommitBuilder(url);
            commitBuilder.addFile("source/directory/deletedFile");
            commitBuilder.addFile("source/directory/modifiedFile");
            commitBuilder.addFile("source/directory/sourceFile");
            commitBuilder.addDirectory("target");
            commitBuilder.commit();

            final WorkingCopy workingCopy = sandbox.checkoutNewWorkingCopy(url, SVNRevision.HEAD.getNumber());
            final File workingCopyDirectory = workingCopy.getWorkingCopyDirectory();

            final File source = new File(workingCopyDirectory, "source");
            final File target = new File(workingCopyDirectory, "target");
            final File sourceDirectory = new File(source, "directory");
            final File targetDirectory = new File(target, "directory");
            final File sourceModifiedFile = new File(sourceDirectory, "modifiedFile");
            final File targetModifiedFile = new File(targetDirectory, "modifiedFile");
            final File sourceAddedFile = new File(sourceDirectory, "addedFile");
            final File targetAddedFile = new File(targetDirectory, "addedFile");
            final File sourceDeletedFile = new File(sourceDirectory, "deletedFile");
            final File targetDeletedFile = new File(targetDirectory, "deletedFile");
            final File sourceSourceFile = new File(sourceDirectory, "sourceFile");
            final File targetSourceFile = new File(targetDirectory, "sourceFile");
            final File sourceCopiedFile = new File(sourceDirectory, "copiedFile");
            final File targetCopiedFile = new File(targetDirectory, "copiedFile");

            final String sourceFileContents = "local modifications" + "\n";
            TestUtil.writeFileContentsString(sourceModifiedFile, sourceFileContents);

            copy(svnOperationFactory, sourceSourceFile, sourceCopiedFile);

            //noinspection ResultOfMethodCallIgnored
            sourceAddedFile.createNewFile();
            workingCopy.add(sourceAddedFile);

            workingCopy.delete(sourceDeletedFile);

            final SvnRemoteCopy remoteCopy = svnOperationFactory.createRemoteCopy();
            remoteCopy.addCopySource(SvnCopySource.create(SvnTarget.fromFile(sourceDirectory, SVNRevision.WORKING), SVNRevision.WORKING));
            remoteCopy.setSingleTarget(SvnTarget.fromURL(url.appendPath(target.getName(), false).appendPath(targetDirectory.getName(), false)));
            remoteCopy.setDisableLocalModifications(true);
            final SVNCommitInfo commitInfo = remoteCopy.run();

            workingCopy.revert();
            workingCopy.updateToRevision(commitInfo.getNewRevision());

            final String targetModifiedFileContents = TestUtil.readFileContentsString(targetModifiedFile);
            Assert.assertEquals("", targetModifiedFileContents);//should not be equal to sourceFileContents

            final Map<File,SvnStatus> statuses = TestUtil.getStatuses(svnOperationFactory, workingCopyDirectory);
            Assert.assertEquals(null, statuses.get(targetAddedFile));
            Assert.assertEquals(null, statuses.get(targetCopiedFile));
            Assert.assertEquals(SVNStatusType.STATUS_NORMAL, statuses.get(targetModifiedFile).getNodeStatus());
            Assert.assertEquals(SVNStatusType.STATUS_NORMAL, statuses.get(targetDeletedFile).getNodeStatus());
            Assert.assertEquals(SVNStatusType.STATUS_NORMAL, statuses.get(targetSourceFile).getNodeStatus());
        } finally {
            svnOperationFactory.dispose();
            sandbox.dispose();
        }
    }

    @Test
    public void testCopyEmptyDirectoryNoLocalModifications() throws Exception {
        final TestOptions options = TestOptions.getInstance();

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testCopyEmptyDirectoryNoLocalModifications", options);
        try {
            final SVNURL url = sandbox.createSvnRepository();

            final CommitBuilder commitBuilder = new CommitBuilder(url);
            commitBuilder.addDirectory("source/directory");
            commitBuilder.addDirectory("target");
            commitBuilder.commit();

            final WorkingCopy workingCopy = sandbox.checkoutNewWorkingCopy(url, SVNRevision.HEAD.getNumber());
            final File workingCopyDirectory = workingCopy.getWorkingCopyDirectory();

            final File source = new File(workingCopyDirectory, "source");
            final File target = new File(workingCopyDirectory, "target");
            final File sourceDirectory = new File(source, "directory");
            final File targetDirectory = new File(target, "directory");

            final SvnRemoteCopy remoteCopy = svnOperationFactory.createRemoteCopy();
            remoteCopy.addCopySource(SvnCopySource.create(SvnTarget.fromFile(sourceDirectory, SVNRevision.WORKING), SVNRevision.WORKING));
            remoteCopy.setSingleTarget(SvnTarget.fromURL(url.appendPath(target.getName(), false).appendPath(targetDirectory.getName(), false)));
            remoteCopy.setDisableLocalModifications(true);
            remoteCopy.setMove(false);
            remoteCopy.setFailWhenDstExists(true);
            remoteCopy.setMakeParents(true);

            final SVNCommitInfo commitInfo = remoteCopy.run();
            Assert.assertEquals(2, commitInfo.getNewRevision());

        } finally {
            svnOperationFactory.dispose();
            sandbox.dispose();
        }
    }

    private void copy(SvnOperationFactory svnOperationFactory, File sourceSourceFile, File sourceCopiedFile) throws SVNException {
        final SvnCopy copy = svnOperationFactory.createCopy();
        copy.addCopySource(SvnCopySource.create(SvnTarget.fromFile(sourceSourceFile), SVNRevision.WORKING));
        copy.setSingleTarget(SvnTarget.fromFile(sourceCopiedFile));
        copy.run();
    }

    private String getTestName() {
        return "SvnCopyDisableLocalModifications";
    }
}
