package org.tmatesoft.svn.test;

import java.io.ByteArrayOutputStream;
import java.io.File;

import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;
import org.tmatesoft.svn.core.SVNCommitInfo;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNPropertyValue;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc2.SvnCopy;
import org.tmatesoft.svn.core.wc2.SvnCopySource;
import org.tmatesoft.svn.core.wc2.SvnDiff;
import org.tmatesoft.svn.core.wc2.SvnOperationFactory;
import org.tmatesoft.svn.core.wc2.SvnTarget;

public class DiffTest {

    @Test
    public void testRemoteDiffTwoFiles() throws Exception {
        final TestOptions options = TestOptions.getInstance();

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testRemoteDiffTwoFiles", options);
        try {
            final SVNURL url = sandbox.createSvnRepository();

            final CommitBuilder commitBuilder = new CommitBuilder(url);
            commitBuilder.addFile("directory/file1", "contents1".getBytes());
            commitBuilder.addFile("directory/file2", "contents2".getBytes());
            final SVNCommitInfo commitInfo = commitBuilder.commit();

            final SVNRevision svnRevision = SVNRevision.create(commitInfo.getNewRevision());

            final SVNURL url1 = url.appendPath("directory/file1", false);
            final SVNURL url2 = url.appendPath("directory/file2", false);

            final String actualDiffOutput = runDiff(svnOperationFactory, url1, svnRevision, url2, svnRevision);
            final String expectedDiffOutput = "Index: file1" + "\n" +
                    "===================================================================" + "\n" +
                    "--- file1\t(.../file1)\t(revision 1)" + "\n" +
                    "+++ file1\t(.../file2)\t(revision 1)" + "\n" +
                    "@@ -1 +0,0 @@" + "\n" +
                    "-contents1\n" +
                    "\\ No newline at end of file" + "\n" +
                    "Index: file1" + "\n" +
                    "===================================================================" + "\n" +
                    "--- file1\t(.../file1)\t(revision 0)" + "\n" +
                    "+++ file1\t(.../file2)\t(revision 1)" + "\n" +
                    "@@ -0,0 +1 @@" + "\n" +
                    "+contents2" + "\n" +
                    "\\ No newline at end of file" + "\n";

            Assert.assertEquals(expectedDiffOutput, actualDiffOutput);

        } finally {
            svnOperationFactory.dispose();
            sandbox.dispose();
        }
    }

    @Test
    public void testRemoteDiffOneFile() throws Exception {
        final TestOptions options = TestOptions.getInstance();

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testRemoteDiffOneFile", options);
        try {
            final SVNURL url = sandbox.createSvnRepository();

            final CommitBuilder commitBuilder1 = new CommitBuilder(url);
            commitBuilder1.addFile("directory/file", "contents1".getBytes());
            final SVNCommitInfo commitInfo1 = commitBuilder1.commit();

            final CommitBuilder commitBuilder2 = new CommitBuilder(url);
            commitBuilder2.changeFile("directory/file", "contents2".getBytes());
            final SVNCommitInfo commitInfo2 = commitBuilder2.commit();

            final SVNRevision startRevision = SVNRevision.create(commitInfo1.getNewRevision());
            final SVNRevision endRevision = SVNRevision.create(commitInfo2.getNewRevision());

            final SVNURL fileUrl = url.appendPath("directory/file", false);

            final String actualDiffOutput = runDiff(svnOperationFactory, fileUrl, startRevision, endRevision);

            final String expectedDiffOutput = "Index: file" + "\n" +
                    "===================================================================" + "\n" +
                    "--- file\t(revision 1)" + "\n" +
                    "+++ file\t(revision 2)" + "\n" +
                    "@@ -1 +1 @@" + "\n" +
                    "-contents1" + "\n" +
                    "\\ No newline at end of file" + "\n" +
                    "+contents2" + "\n" +
                    "\\ No newline at end of file" + "\n";

            Assert.assertEquals(expectedDiffOutput, actualDiffOutput);
        } finally {
            svnOperationFactory.dispose();
            sandbox.dispose();
        }
    }

    @Test
    public void testLocalDiffOneFile() throws Exception {
        final TestOptions options = TestOptions.getInstance();

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testLocalDiffOneFile", options);
        try {
            final SVNURL url = sandbox.createSvnRepository();

            final CommitBuilder commitBuilder1 = new CommitBuilder(url);
            commitBuilder1.addFile("directory/file", "contents1".getBytes());
            final SVNCommitInfo commitInfo1 = commitBuilder1.commit();

            final CommitBuilder commitBuilder2 = new CommitBuilder(url);
            commitBuilder2.changeFile("directory/file", "contents2".getBytes());
            final SVNCommitInfo commitInfo2 = commitBuilder2.commit();

            final SVNRevision svnRevision1 = SVNRevision.create(commitInfo1.getNewRevision());
            final SVNRevision svnRevision2 = SVNRevision.create(commitInfo2.getNewRevision());

            final WorkingCopy workingCopy = sandbox.checkoutNewWorkingCopy(url, SVNRevision.HEAD.getNumber());
            final File workingCopyDirectory = workingCopy.getWorkingCopyDirectory();

            final File file = new File(workingCopyDirectory, "directory/file");

            final String actualDiffOutput = runDiff(svnOperationFactory, file, svnRevision1, svnRevision2);

            final String expectedDiffOutput = "Index: " + file.getPath() + "\n" +
                    "===================================================================\n" +
                    "--- " + file.getPath() + "\t(revision " + svnRevision1.getNumber() + ")\n" +
                    "+++ " + file.getPath() + "\t(revision " + svnRevision2.getNumber() + ")\n" +
                    "@@ -1 +1 @@\n" +
                    "-contents1\n" +
                    "\\ No newline at end of file\n" +
                    "+contents2\n" +
                    "\\ No newline at end of file\n";

            Assert.assertEquals(expectedDiffOutput, actualDiffOutput);
        } finally {
            svnOperationFactory.dispose();
            sandbox.dispose();
        }
    }

    @Test
    public void testLocalToRemoteDiffOneFile() throws Exception {
        final TestOptions options = TestOptions.getInstance();

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testLocalDiffOneFile", options);
        try {
            final SVNURL url = sandbox.createSvnRepository();

            final CommitBuilder commitBuilder1 = new CommitBuilder(url);
            commitBuilder1.addFile("directory/file", "contents1".getBytes());
            commitBuilder1.addFile("directory/anotherFile", "anotherContents".getBytes());
            final SVNCommitInfo commitInfo1 = commitBuilder1.commit();

            final CommitBuilder commitBuilder2 = new CommitBuilder(url);
            commitBuilder2.changeFile("directory/file", "contents2".getBytes());
            final SVNCommitInfo commitInfo2 = commitBuilder2.commit();

            final SVNRevision svnRevision1 = SVNRevision.create(commitInfo1.getNewRevision());
            final SVNRevision svnRevision2 = SVNRevision.create(commitInfo2.getNewRevision());

            final WorkingCopy workingCopy = sandbox.checkoutNewWorkingCopy(url, SVNRevision.HEAD.getNumber());
            final File workingCopyDirectory = workingCopy.getWorkingCopyDirectory();

            final File file = new File(workingCopyDirectory, "directory/file");

            final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();

            final SvnDiff diff = svnOperationFactory.createDiff();
            diff.setTargets(SvnTarget.fromFile(file, SVNRevision.WORKING), SvnTarget.fromURL(url.appendPath("directory/anotherFile", false), SVNRevision.create(1)));
            diff.setOutput(byteArrayOutputStream);
            diff.run();

            final String diffOutput = new String(byteArrayOutputStream.toByteArray());

            //TODO finish the test
        } finally {
            svnOperationFactory.dispose();
            sandbox.dispose();
        }
    }

    @Test
    public void testDiffAddedFile() throws Exception {
        final TestOptions options = TestOptions.getInstance();

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testDiffAddedFile", options);
        try {
            final SVNURL url = sandbox.createSvnRepository();

            final WorkingCopy workingCopy = sandbox.checkoutNewWorkingCopy(url, SVNRevision.HEAD.getNumber());
            final File workingCopyDirectory = workingCopy.getWorkingCopyDirectory();

            final File fileToAdd = new File(workingCopyDirectory, "fileToAdd");
            //noinspection ResultOfMethodCallIgnored
            fileToAdd.createNewFile();

            workingCopy.add(fileToAdd);

            final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            final SvnDiff diff = svnOperationFactory.createDiff();
            diff.setTargets(SvnTarget.fromFile(workingCopyDirectory, SVNRevision.BASE), SvnTarget.fromFile(workingCopyDirectory, SVNRevision.WORKING));
            diff.setOutput(byteArrayOutputStream);
            diff.run();

            String actualDiffOutput = new String(byteArrayOutputStream.toByteArray());
            final String expectedDiffOutput = "Index: " + fileToAdd.getPath() + "\n" +
                             "===================================================================\n";
            Assert.assertEquals(expectedDiffOutput, actualDiffOutput);

        } finally {
            svnOperationFactory.dispose();
            sandbox.dispose();
        }
    }

    @Test
    public void testDiffReplacedFile() throws Exception {
        final TestOptions options = TestOptions.getInstance();

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testDiffReplacedFile", options);
        try {
            final SVNURL url = sandbox.createSvnRepository();

            final CommitBuilder commitBuilder = new CommitBuilder(url);
            commitBuilder.addFile("fileToReplace");
            commitBuilder.commit();

            final WorkingCopy workingCopy = sandbox.checkoutNewWorkingCopy(url, SVNRevision.HEAD.getNumber());
            final File workingCopyDirectory = workingCopy.getWorkingCopyDirectory();

            final File fileToReplace = new File(workingCopyDirectory, "fileToReplace");
            workingCopy.delete(fileToReplace);
            //noinspection ResultOfMethodCallIgnored
            fileToReplace.createNewFile();
            workingCopy.add(fileToReplace);

            final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            final SvnDiff diff = svnOperationFactory.createDiff();
            diff.setTargets(SvnTarget.fromFile(workingCopyDirectory, SVNRevision.BASE), SvnTarget.fromFile(workingCopyDirectory, SVNRevision.WORKING));
            diff.setOutput(byteArrayOutputStream);
            diff.run();

            String actualDiffOutput = new String(byteArrayOutputStream.toByteArray());
            final String expectedDiffOutput = "Index: " + fileToReplace.getPath() + "\n" +
                             "===================================================================\n";
            Assert.assertEquals(expectedDiffOutput, actualDiffOutput);

        } finally {
            svnOperationFactory.dispose();
            sandbox.dispose();
        }
    }

    @Ignore("Temporarily ignored")
    @Test
    public void testPropertiesChangedOnlyHeaderIsPrinted() throws Exception {
        final TestOptions options = TestOptions.getInstance();

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testPropertiesChangedOnlyHeaderIsPrinted", options);
        try {
            final SVNURL url = sandbox.createSvnRepository();

            final CommitBuilder commitBuilder = new CommitBuilder(url);
            commitBuilder.addFile("file");
            commitBuilder.addDirectory("directory");
            commitBuilder.commit();

            final WorkingCopy workingCopy = sandbox.checkoutNewWorkingCopy(url, SVNRevision.HEAD.getNumber());
            final File workingCopyDirectory = workingCopy.getWorkingCopyDirectory();

            final File file = new File(workingCopyDirectory, "file");
            final File directory = new File(workingCopyDirectory, "directory");

            workingCopy.setProperty(file, "fileProperty", SVNPropertyValue.create("filePropertyValue"));
            workingCopy.setProperty(directory, "directoryProperty", SVNPropertyValue.create("directoryPropertyValue"));

            final String fileDiffHeader = "Index: file\n" +
                    "===================================================================\n" +
                    "--- file\t(revision 1)\n" +
                    "+++ file\t(working copy)\n";
            final String directoryDiffHeader = "Index: directory\n" +
                    "===================================================================\n" +
                    "--- directory\t(revision 1)\n" +
                    "+++ directory\t(working copy)\n";

            final String actualFileDiffOutput = runLocalDiff(svnOperationFactory, file, workingCopyDirectory);
            final String actualDirectoryDiffOutput = runLocalDiff(svnOperationFactory, directory, workingCopyDirectory);

            Assert.assertTrue(actualFileDiffOutput.startsWith(fileDiffHeader));
            Assert.assertTrue(actualDirectoryDiffOutput.startsWith(directoryDiffHeader));
        } finally {
            svnOperationFactory.dispose();
            sandbox.dispose();
        }
    }

    @Test
    public void testDiffLocalReplacedFile() throws Exception {
        final TestOptions options = TestOptions.getInstance();

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testDiffLocalReplacedFile", options);
        try {
            final SVNURL url = sandbox.createSvnRepository();

            final CommitBuilder commitBuilder = new CommitBuilder(url);
            commitBuilder.addFile("fileToReplace");
            commitBuilder.commit();

            final WorkingCopy workingCopy = sandbox.checkoutNewWorkingCopy(url, SVNRevision.HEAD.getNumber());
            final File workingCopyDirectory = workingCopy.getWorkingCopyDirectory();

            final File fileToReplace = new File(workingCopyDirectory, "fileToReplace");
            workingCopy.delete(fileToReplace);
            //noinspection ResultOfMethodCallIgnored
            fileToReplace.createNewFile();
            TestUtil.writeFileContentsString(fileToReplace, "newContents");
            workingCopy.add(fileToReplace);

            final String actualDiffOutput = runLocalDiff(svnOperationFactory, fileToReplace, workingCopyDirectory);
            final String expectedDiffOutput = "Index: " + fileToReplace.getPath() + "\n" +
                    "===================================================================\n" +
                    "--- " + fileToReplace.getPath() + "\t(working copy)\n" +
                    "+++ " + fileToReplace.getPath() + "\t(working copy)\n" +
                    "@@ -0,0 +1 @@\n" +
                    "+newContents\n" +
                    "\\ No newline at end of file\n";
            Assert.assertEquals(expectedDiffOutput, actualDiffOutput);
        } finally {
            svnOperationFactory.dispose();
            sandbox.dispose();
        }
    }

    @Test
    public void testDiffLocalCopiedFile() throws Exception {
        final TestOptions options = TestOptions.getInstance();

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testDiffLocalCopiedFile", options);
        try {
            final SVNURL url = sandbox.createSvnRepository();

            final CommitBuilder commitBuilder1 = new CommitBuilder(url);
            commitBuilder1.addFile("sourceFile");
            commitBuilder1.commit();

            final WorkingCopy workingCopy = sandbox.checkoutNewWorkingCopy(url, 1);
            final File workingCopyDirectory = workingCopy.getWorkingCopyDirectory();

            final File targetFile = new File(workingCopyDirectory, "targetFile");
            final File sourceFile = new File(workingCopyDirectory, "sourceFile");

            final SvnCopy copy = svnOperationFactory.createCopy();
            copy.addCopySource(SvnCopySource.create(SvnTarget.fromFile(sourceFile), SVNRevision.WORKING));
            copy.setSingleTarget(SvnTarget.fromFile(targetFile));
            copy.setFailWhenDstExists(true);
            copy.setMove(true);
            copy.run();

            final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();

            final SvnDiff diff = svnOperationFactory.createDiff();
            diff.setTarget(SvnTarget.fromFile(targetFile, SVNRevision.WORKING), SVNRevision.HEAD, SVNRevision.WORKING);
            diff.setOutput(byteArrayOutputStream);
            diff.run();

            final String actualDiffOutput =  new String(byteArrayOutputStream.toByteArray());
            final String expectedDiffOutput = "";

            Assert.assertEquals(expectedDiffOutput, actualDiffOutput);

        } finally {
            svnOperationFactory.dispose();
            sandbox.dispose();
        }
    }

    private String runLocalDiff(SvnOperationFactory svnOperationFactory, File target, File relativeToDirectory) throws SVNException {
        final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();

        final SvnDiff diff = svnOperationFactory.createDiff();
        diff.setTargets(SvnTarget.fromFile(target, SVNRevision.BASE), SvnTarget.fromFile(target, SVNRevision.WORKING));
        diff.setOutput(byteArrayOutputStream);
        diff.setRelativeToDirectory(relativeToDirectory);
        diff.run();
        return new String(byteArrayOutputStream.toByteArray());
    }

    private String runDiff(SvnOperationFactory svnOperationFactory, SVNURL fileUrl, SVNRevision startRevision, SVNRevision endRevision) throws SVNException {
        final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();

        final SvnDiff diff = svnOperationFactory.createDiff();
        diff.setTarget(SvnTarget.fromURL(fileUrl, startRevision), startRevision, endRevision);
        diff.setOutput(byteArrayOutputStream);
        diff.run();

        return new String(byteArrayOutputStream.toByteArray());
    }

    private String runDiff(SvnOperationFactory svnOperationFactory, SVNURL url1, SVNRevision svnRevision1, SVNURL url2, SVNRevision svnRevision2) throws SVNException {
        final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();

        final SvnDiff diff = svnOperationFactory.createDiff();
        diff.setTargets(SvnTarget.fromURL(url1, svnRevision1), SvnTarget.fromURL(url2, svnRevision2));
        diff.setOutput(byteArrayOutputStream);
        diff.run();

        return new String(byteArrayOutputStream.toByteArray());
    }
    private String runDiff(SvnOperationFactory svnOperationFactory, File file, SVNRevision startRevision, SVNRevision endRevision) throws SVNException {
        final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();

        final SvnDiff diff = svnOperationFactory.createDiff();
        diff.setTarget(SvnTarget.fromFile(file, startRevision), startRevision, endRevision);
        diff.setOutput(byteArrayOutputStream);
        diff.run();

        return new String(byteArrayOutputStream.toByteArray());
    }

    public String getTestName() {
        return "DiffTest";
    }
}
