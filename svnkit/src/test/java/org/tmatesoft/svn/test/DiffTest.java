package org.tmatesoft.svn.test;

import org.junit.Assert;
import org.junit.Assume;
import org.junit.Ignore;
import org.junit.Test;
import org.tmatesoft.svn.core.*;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.internal.wc.DefaultSVNOptions;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.internal.wc2.ng.SvnDiffGenerator;
import org.tmatesoft.svn.core.wc.*;
import org.tmatesoft.svn.core.wc2.*;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

@Ignore
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

            final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            final SvnDiffGenerator diffGenerator = new SvnDiffGenerator();
            diffGenerator.setBasePath(new File(""));

            final SvnDiff diff = svnOperationFactory.createDiff();
            diff.setSources(SvnTarget.fromURL(url1, svnRevision), SvnTarget.fromURL(url2, svnRevision));
            diff.setDiffGenerator(diffGenerator);
            diff.setOutput(byteArrayOutputStream);
            diff.run();

            final String actualDiffOutput = new String(byteArrayOutputStream.toByteArray()).replace(System.getProperty("line.separator"), "\n");
            final String expectedDiffOutput = "Index: file1" + "\n" +
                    "===================================================================" + "\n" +
                    "--- file1\t(.../file1)\t(revision 1)" + "\n" +
                    "+++ file1\t(.../file2)\t(revision 1)" + "\n" +
                    "@@ -1 +1 @@" + "\n" +
                    "-contents1\n" +
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

            final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            final SvnDiffGenerator diffGenerator = new SvnDiffGenerator();
            diffGenerator.setBasePath(new File(""));

            final SvnDiff diff = svnOperationFactory.createDiff();
            diff.setSource(SvnTarget.fromURL(fileUrl, startRevision), startRevision, endRevision);
            diff.setOutput(byteArrayOutputStream);
            diff.setDiffGenerator(diffGenerator);
            diff.run();

            final String actualDiffOutput = new String(byteArrayOutputStream.toByteArray()).replace(System.getProperty("line.separator"), "\n");

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
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testLocalToRemoteDiffOneFile", options);
        try {
            final SVNURL url = sandbox.createSvnRepository();

            final CommitBuilder commitBuilder1 = new CommitBuilder(url);
            commitBuilder1.addFile("directory/file", "contents1".getBytes());
            commitBuilder1.addFile("directory/anotherFile", "anotherContents".getBytes());
            commitBuilder1.commit();

            final CommitBuilder commitBuilder2 = new CommitBuilder(url);
            commitBuilder2.changeFile("directory/file", "contents2".getBytes());
            commitBuilder2.commit();

            final WorkingCopy workingCopy = sandbox.checkoutNewWorkingCopy(url, SVNRevision.HEAD.getNumber());
            final File workingCopyDirectory = workingCopy.getWorkingCopyDirectory();

            final File file = new File(workingCopyDirectory, "directory/file");
            final SVNURL anotherFileUrl = url.appendPath("directory/anotherFile", false);

            final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            final SvnDiffGenerator diffGenerator = new SvnDiffGenerator();

            final SvnDiff diff = svnOperationFactory.createDiff();
            diff.setSources(SvnTarget.fromFile(file, SVNRevision.WORKING), SvnTarget.fromURL(anotherFileUrl, SVNRevision.create(1)));
            diff.setOutput(byteArrayOutputStream);
            diff.setDiffGenerator(diffGenerator);
            diff.run();

            final String actualDiffOutput = byteArrayOutputStream.toString();
            final String expectedDiffOutput = "Index: " + file + "\n" +
                    "===================================================================\n" +
                    "--- " + file + "\t(..." + file + ")\t(working copy)\n" +
                    "+++ " + file + "\t(.../" + anotherFileUrl + ")\t(revision 1)\n" +
                    "@@ -1 +1 @@\n" +
                    "-contents2\n" +
                    "\\ No newline at end of file\n" +
                    "+anotherContents\n" +
                    "\\ No newline at end of file\n";

            Assert.assertEquals(expectedDiffOutput, actualDiffOutput);
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
            diff.setSources(SvnTarget.fromFile(workingCopyDirectory, SVNRevision.BASE), SvnTarget.fromFile(workingCopyDirectory, SVNRevision.WORKING));
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
            diff.setSources(SvnTarget.fromFile(workingCopyDirectory, SVNRevision.BASE), SvnTarget.fromFile(workingCopyDirectory, SVNRevision.WORKING));
            diff.setOutput(byteArrayOutputStream);
            diff.setIgnoreAncestry(true);
            diff.run();

            String actualDiffOutput = new String(byteArrayOutputStream.toByteArray());
            final String expectedDiffOutput = "";
            Assert.assertEquals(expectedDiffOutput, actualDiffOutput);

        } finally {
            svnOperationFactory.dispose();
            sandbox.dispose();
        }
    }

    @Test
    public void testPropertiesChangedOnlyHeaderIsPrinted() throws Exception {
        Assume.assumeTrue(TestUtil.isNewWorkingCopyTest());
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
            final String expectedDiffOutput = "Index: " + fileToReplace.getName() + "\n" +
                    "===================================================================\n" +
                    "--- " + fileToReplace.getName() + "\t" + (TestUtil.isNewWorkingCopyTest() ? "(working copy)" : "(revision 1)") + "\n" +
                    "+++ " + fileToReplace.getName() + "\t(working copy)\n" +
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
            diff.setSource(SvnTarget.fromFile(targetFile, SVNRevision.WORKING), SVNRevision.HEAD, SVNRevision.WORKING);
            diff.setOutput(byteArrayOutputStream);
            diff.setShowCopiesAsAdds(false);
            diff.run();

            final String actualDiffOutput =  new String(byteArrayOutputStream.toByteArray());
            final String expectedDiffOutput = "";

            Assert.assertEquals(expectedDiffOutput, actualDiffOutput);

        } finally {
            svnOperationFactory.dispose();
            sandbox.dispose();
        }
    }

    @Test
    public void testOldDiffGeneratorIsCalledOnCorrectPaths() throws Exception {
        final TestOptions options = TestOptions.getInstance();

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testOldDiffGeneratorIsCalledOnCorrectPaths", options);
        try {
            final SVNURL url = sandbox.createSvnRepository();

            final CommitBuilder commitBuilder1 = new CommitBuilder(url);
            commitBuilder1.addFile("directory/file");
            commitBuilder1.commit();

            final CommitBuilder commitBuilder2 = new CommitBuilder(url);
            commitBuilder2.changeFile("directory/file", "newContents".getBytes());
            commitBuilder2.commit();

            final OldGenerator generator = new OldGenerator();

            final SVNClientManager svnClientManager = SVNClientManager.newInstance();
            SVNDiffClient client = new SVNDiffClient(svnClientManager, new DefaultSVNOptions());
            client.setDiffGenerator(generator);
            client.doDiff(url, SVNRevision.create(1), SVNRevision.create(1), SVNRevision.create(2), SVNDepth.INFINITY, true, SVNFileUtil.DUMMY_OUT);

            final List<GeneratorCall> expectedCalls = new ArrayList<GeneratorCall>();
            expectedCalls.add(new GeneratorCall(GeneratorCallKind.DISPLAY_FILE_DIFF, "directory/file"));

            Assert.assertEquals(expectedCalls, generator.calls);
        } finally {
            svnOperationFactory.dispose();
            sandbox.dispose();
        }
    }

    @Test
    public void testGitDiffFormatForCopiedFile() throws Exception {
        Assume.assumeTrue(TestUtil.isNewWorkingCopyTest());
        final TestOptions options = TestOptions.getInstance();

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testGitDiffFormatForCopiedFile", options);
        try {
            final SVNURL url = sandbox.createSvnRepository();

            final CommitBuilder commitBuilder = new CommitBuilder(url);
            commitBuilder.addFile("copySource");
            commitBuilder.commit();

            final WorkingCopy workingCopy = sandbox.checkoutNewWorkingCopy(url, SVNRevision.HEAD.getNumber());
            final File workingCopyDirectory = workingCopy.getWorkingCopyDirectory();

            final File copySourceFile = new File(workingCopyDirectory, "copySource");
            final File copyTargetFile = new File(workingCopyDirectory, "copyTarget");

            final SvnCopy copy = svnOperationFactory.createCopy();
            copy.addCopySource(SvnCopySource.create(SvnTarget.fromFile(copySourceFile), SVNRevision.WORKING));
            copy.setSingleTarget(SvnTarget.fromFile(copyTargetFile, SVNRevision.WORKING));
            copy.run();

            TestUtil.writeFileContentsString(copyTargetFile, "New contents (copy)");

            final File basePath = new File("");

            final SvnDiffGenerator diffGenerator = new SvnDiffGenerator();
            diffGenerator.setBasePath(basePath);

            final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            final SvnDiff diff = svnOperationFactory.createDiff();
            diff.setSources(SvnTarget.fromFile(workingCopyDirectory, SVNRevision.BASE), SvnTarget.fromFile(workingCopyDirectory, SVNRevision.WORKING));
            diff.setUseGitDiffFormat(true);
            diff.setOutput(byteArrayOutputStream);
            diff.setDiffGenerator(diffGenerator);
            diff.run();

            final String actualDiffOutput = byteArrayOutputStream.toString().replace(System.getProperty("line.separator"), "\n");
            final String expectedDiffOutput = "Index: " +
                    getRelativePath(copyTargetFile, basePath) +
                    "\n" +
                    "===================================================================\n" +
                    "diff --git a/copySource b/copyTarget\n" +
                    "copy from copySource\n" +
                    "copy to copyTarget\n" +
                    "--- a/copySource\t(revision 0)\n" +
                    "+++ b/copyTarget\t(working copy)\n" +
                    "@@ -0,0 +1 @@\n" +
                    "+New contents (copy)\n" +
                    "\\ No newline at end of file\n";
            Assert.assertEquals(expectedDiffOutput, actualDiffOutput);
        } finally {
            svnOperationFactory.dispose();
            sandbox.dispose();
        }
    }

    @Test
    public void testGitDiffFormatForMovedFile() throws Exception {
        Assume.assumeTrue(TestUtil.isNewWorkingCopyTest());
        final TestOptions options = TestOptions.getInstance();

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testGitDiffFormatForMovedFile", options);
        try {
            final SVNURL url = sandbox.createSvnRepository();

            final CommitBuilder commitBuilder = new CommitBuilder(url);
            commitBuilder.addFile("moveSource");
            commitBuilder.commit();

            final WorkingCopy workingCopy = sandbox.checkoutNewWorkingCopy(url, SVNRevision.HEAD.getNumber());
            final File workingCopyDirectory = workingCopy.getWorkingCopyDirectory();

            final File moveSourceFile = new File(workingCopyDirectory, "moveSource");
            final File moveTargetFile = new File(workingCopyDirectory, "moveTarget");

            final SvnCopy move = svnOperationFactory.createCopy();
            move.setMove(true);
            move.addCopySource(SvnCopySource.create(SvnTarget.fromFile(moveSourceFile), SVNRevision.WORKING));
            move.setSingleTarget(SvnTarget.fromFile(moveTargetFile, SVNRevision.WORKING));
            move.run();

            TestUtil.writeFileContentsString(moveTargetFile, "New contents (move)");

            final File basePath = new File("");

            final SvnDiffGenerator diffGenerator = new SvnDiffGenerator();
            diffGenerator.setBasePath(basePath);

            final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            final SvnDiff diff = svnOperationFactory.createDiff();
            diff.setSources(SvnTarget.fromFile(workingCopyDirectory, SVNRevision.BASE), SvnTarget.fromFile(workingCopyDirectory, SVNRevision.WORKING));
            diff.setUseGitDiffFormat(true);
            diff.setOutput(byteArrayOutputStream);
            diff.setDiffGenerator(diffGenerator);
            diff.run();

            final String actualDiffOutput = byteArrayOutputStream.toString().replace(System.getProperty("line.separator"), "\n");
            final String expectedDiffOutput = "Index: " +
                    getRelativePath(moveSourceFile, basePath) +
                    "\n" +
                    "===================================================================\n" +
                    "diff --git a/moveSource b/moveSource\n" +
                    "deleted file mode 10644\n" +
                    "Index: " +
                    getRelativePath(moveTargetFile, basePath) +
                    "\n" +
                    "===================================================================\n" +
                    "diff --git a/moveSource b/moveTarget\n" +
                    "copy from moveSource\n" +
                    "copy to moveTarget\n" +
                    "--- a/moveSource\t(revision 0)\n" +
                    "+++ b/moveTarget\t(working copy)\n" +
                    "@@ -0,0 +1 @@\n" +
                    "+New contents (move)\n" +
                    "\\ No newline at end of file\n";
            Assert.assertEquals(expectedDiffOutput, actualDiffOutput);
        } finally {
            svnOperationFactory.dispose();
            sandbox.dispose();
        }
    }

    @Test
    public void testDiffOnNonExistentUrlCalledWithCorrectAnchors() throws Exception {
        final TestOptions options = TestOptions.getInstance();

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testDiffOnNonExistentUrlCalledWithCorrectAnchors", options);
        try {
            final SVNURL url = sandbox.createSvnRepository();

            final CommitBuilder commitBuilder1 = new CommitBuilder(url);
            commitBuilder1.addFile("directory/file");
            commitBuilder1.commit();
            final CommitBuilder commitBuilder2 = new CommitBuilder(url);
            commitBuilder2.changeFile("directory/file", "new contents".getBytes());
            commitBuilder2.commit();
            final CommitBuilder commitBuilder3 = new CommitBuilder(url);
            commitBuilder3.delete("directory");
            commitBuilder3.commit();

            final File basePath = new File("");

            final SvnDiffGenerator diffGenerator = new SvnDiffGenerator();
            diffGenerator.setBasePath(basePath);

            SVNURL urlForDiff = url.appendPath("directory", false);

            final OldGenerator testGenerator = new OldGenerator();

            final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            final SvnDiff diff = svnOperationFactory.createDiff();
            diff.setSources(SvnTarget.fromURL(urlForDiff, SVNRevision.create(3)), SvnTarget.fromURL(urlForDiff, SVNRevision.create(2)));
            diff.setUseGitDiffFormat(false);
            diff.setOutput(byteArrayOutputStream);
            diff.setDiffGenerator(testGenerator);
            diff.run();

            Assert.assertEquals(testGenerator.anchorPath1, url.toString());
            Assert.assertEquals(testGenerator.anchorPath2, url.toString());

            //TODO: compare output
        } finally {
            svnOperationFactory.dispose();
            sandbox.dispose();
        }
    }

    @Test
    public void testAnchorIsRootIfOneRevisionIsZero() throws Exception {
        final TestOptions options = TestOptions.getInstance();

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testAnchorIsRootIfOneRevisionIsZero", options);
        try {
            final SVNURL url = sandbox.createSvnRepository();

            final CommitBuilder commitBuilder1 = new CommitBuilder(url);
            commitBuilder1.addFile("directory/subdirectory/file");
            commitBuilder1.commit();
            final CommitBuilder commitBuilder2 = new CommitBuilder(url);
            commitBuilder2.changeFile("directory/subdirectory/file", "new contents".getBytes());
            commitBuilder2.commit();
            final CommitBuilder commitBuilder3 = new CommitBuilder(url);
            commitBuilder3.delete("directory/subdirectory");
            commitBuilder3.commit();

            final SVNURL urlToDiff = url.appendPath("directory/subdirectory", false);

            OldGenerator generator = new OldGenerator();
            diffFiles(urlToDiff, SVNRevision.create(0), SVNRevision.create(2), generator);

            List<GeneratorCall> calls = generator.calls;
            Assert.assertEquals(3, calls.size());
            Assert.assertEquals(new GeneratorCall(GeneratorCallKind.DISPLAY_ADDED_DIRECTORY, "directory"), calls.get(0));
            Assert.assertEquals(new GeneratorCall(GeneratorCallKind.DISPLAY_ADDED_DIRECTORY, "directory/subdirectory"), calls.get(1));
            Assert.assertEquals(new GeneratorCall(GeneratorCallKind.DISPLAY_FILE_DIFF, "directory/subdirectory/file"), calls.get(2));

            Assert.assertEquals(url.toString(), generator.anchorPath1);
            Assert.assertEquals(url.toString(), generator.anchorPath2);


            generator = new OldGenerator();
            diffFiles(urlToDiff, SVNRevision.create(2), SVNRevision.create(0), generator);

            calls = generator.calls;
            Assert.assertEquals(1, calls.size());
            Assert.assertEquals(new GeneratorCall(GeneratorCallKind.DISPLAY_DELETED_DIRECTORY, "directory"), calls.get(0));

            Assert.assertEquals(url.toString(), generator.anchorPath1);
            Assert.assertEquals(url.toString(), generator.anchorPath2);
        } finally {
            svnOperationFactory.dispose();
            sandbox.dispose();
        }
    }

    @Test
    public void testEolSupportInDiffGenerator() throws Exception {
        final TestOptions options = TestOptions.getInstance();

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testEolSupportInDiffGenerator", options);
        try {
            final SVNURL url = sandbox.createSvnRepository();

            final CommitBuilder commitBuilder = new CommitBuilder(url);
            commitBuilder.addFile("file");
            commitBuilder.commit();

            final WorkingCopy workingCopy = sandbox.checkoutNewWorkingCopy(url);
            final File workingCopyDirectory = workingCopy.getWorkingCopyDirectory();

            final File file = new File(workingCopyDirectory, "file");
            TestUtil.writeFileContentsString(file, "new contents");

            final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            final SvnDiffGenerator diffGenerator = new SvnDiffGenerator();
            diffGenerator.setBasePath(new File(""));
            diffGenerator.setEOL(SVNProperty.EOL_CR_BYTES); //set CR as EOL

            final SvnDiff diff = svnOperationFactory.createDiff();
            diff.setSources(SvnTarget.fromFile(file, SVNRevision.BASE), SvnTarget.fromFile(file, SVNRevision.WORKING));
            diff.setOutput(byteArrayOutputStream);
            diff.setDiffGenerator(diffGenerator);
            diff.run();

            final String diffOutput = byteArrayOutputStream.toString();
            Assert.assertFalse(diffOutput.contains("\n")); //LF is not EOL anymore
        } finally {
            svnOperationFactory.dispose();
            sandbox.dispose();
        }
    }

    @Test
    public void testDiffDeleted() throws Exception {
          final TestOptions options = TestOptions.getInstance();

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testDiffDeleted", options);
        try {
            final SVNURL url = sandbox.createSvnRepository();

            final CommitBuilder commitBuilder1 = new CommitBuilder(url);
            commitBuilder1.addFile("file", "contents".getBytes());
            commitBuilder1.commit();

            final CommitBuilder commitBuilder2 = new CommitBuilder(url);
            commitBuilder2.delete("file");
            commitBuilder2.commit();

            final ByteArrayOutputStream diffNoDeletedOutputStream = new ByteArrayOutputStream();
            final SvnDiffGenerator diffNoDeletedGenerator = new SvnDiffGenerator();
            diffNoDeletedGenerator.setBasePath(new File(""));
            diffNoDeletedGenerator.setDiffDeleted(false);

            final SvnDiff diffNoDeleted = svnOperationFactory.createDiff();
            diffNoDeleted.setSource(SvnTarget.fromURL(url, SVNRevision.create(1)), SVNRevision.create(1), SVNRevision.create(2));
            diffNoDeleted.setOutput(diffNoDeletedOutputStream);
            diffNoDeleted.setDiffGenerator(diffNoDeletedGenerator);
            diffNoDeleted.run();

            final String expectedDiffNoDeletedOutput = "Index: file (deleted)\n" +
                    "===================================================================\n";
            final String actualDiffNoDeletedOutput = diffNoDeletedOutputStream.toString();

            final ByteArrayOutputStream diffDeletedOutputStream = new ByteArrayOutputStream();
            final SvnDiffGenerator diffDeletedGenerator = new SvnDiffGenerator();
            diffDeletedGenerator.setBasePath(new File(""));
            diffDeletedGenerator.setDiffDeleted(true);

            final SvnDiff diffDeleted = svnOperationFactory.createDiff();
            diffDeleted.setSource(SvnTarget.fromURL(url, SVNRevision.create(1)), SVNRevision.create(1), SVNRevision.create(2));
            diffDeleted.setOutput(diffDeletedOutputStream);
            diffDeleted.setDiffGenerator(diffDeletedGenerator);
            diffDeleted.run();

            final String expectedDiffDeletedOutput = "Index: file\n" +
                    "===================================================================\n" +
                    "--- file\t(revision 1)\n" +
                    "+++ file\t(revision 2)\n" +
                    "@@ -1 +0,0 @@\n" +
                    "-contents\n" +
                    "\\ No newline at end of file\n";
            final String actualDiffDeletedOutput = diffDeletedOutputStream.toString();

            Assert.assertEquals(expectedDiffNoDeletedOutput, actualDiffNoDeletedOutput);
            Assert.assertEquals(expectedDiffDeletedOutput, actualDiffDeletedOutput);
        } finally {
            svnOperationFactory.dispose();
            sandbox.dispose();
        }
    }

    @Test
    public void testDiffAdded() throws Exception {
        final TestOptions options = TestOptions.getInstance();

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testDiffDeleted", options);
        try {
            final SVNURL url = sandbox.createSvnRepository();

            final CommitBuilder commitBuilder = new CommitBuilder(url);
            commitBuilder.addFile("file", "contents".getBytes());
            commitBuilder.setFileProperty("file", "propertyName", SVNPropertyValue.create("propertyValue"));
            commitBuilder.commit();

            final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();

            final DefaultSVNDiffGenerator oldDiffGenerator = new DefaultSVNDiffGenerator();
            oldDiffGenerator.setDiffAdded(false);

            final SvnDiff diff = svnOperationFactory.createDiff();
            diff.setSource(SvnTarget.fromURL(url, SVNRevision.create(0)), SVNRevision.create(0), SVNRevision.create(1));
            diff.setDiffGenerator(oldDiffGenerator);
            diff.setOutput(byteArrayOutputStream);
            diff.run();

            final String expectedDiffOutput = "";
            final String actualDiffOutput = byteArrayOutputStream.toString();

            Assert.assertEquals(expectedDiffOutput, actualDiffOutput);
        } finally {
            svnOperationFactory.dispose();
            sandbox.dispose();
        }
    }

    @Test
    public void testDiffBinaryFiles() throws Exception {
        final TestOptions options = TestOptions.getInstance();

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testDiffBinaryFiles", options);
        try {
            final SVNURL url = sandbox.createSvnRepository();

            final CommitBuilder commitBuilder = new CommitBuilder(url);
            commitBuilder.addFile("file", new byte[]{1, 2, 3});
            commitBuilder.setFileProperty("file", SVNProperty.MIME_TYPE, SVNPropertyValue.create("application/octet-stream"));
            commitBuilder.commit();

            final WorkingCopy workingCopy = sandbox.checkoutNewWorkingCopy(url);
            final File workingCopyDirectory = workingCopy.getWorkingCopyDirectory();

            final File file = new File(workingCopyDirectory, "file");
            TestUtil.writeFileContentsString(file, "contents");
            workingCopy.setProperty(file, "custom", SVNPropertyValue.create("custom property value"));

            final SvnDiffGenerator diffGenerator = new SvnDiffGenerator();
            diffGenerator.setBasePath(new File(""));

            final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();

            final SvnDiff diff = svnOperationFactory.createDiff();
            diff.setSource(SvnTarget.fromFile(workingCopyDirectory), SVNRevision.BASE, SVNRevision.WORKING);
            diff.setDiffGenerator(diffGenerator);
            diff.setOutput(byteArrayOutputStream);
            diff.run();

            Assert.fail("TODO: check the output and compare with SVN's version");

        } finally {
            svnOperationFactory.dispose();
            sandbox.dispose();
        }
    }

    private void diffFiles(SVNURL url, final SVNRevision fromVersion, final SVNRevision toVersion, final ISVNDiffGenerator diffGenerator) throws SVNException {
        DefaultSVNOptions options = SVNWCUtil.createDefaultOptions(true);
        final DefaultSVNRepositoryPool pool = new DefaultSVNRepositoryPool(SVNWCUtil.createDefaultAuthenticationManager(), options);
        try {
          final SVNDiffClient diffClient = new SVNDiffClient(pool, options);

          diffClient.setDiffGenerator(diffGenerator);

          diffClient.doDiff(url, fromVersion, url, toVersion, SVNDepth.INFINITY, true, new ByteArrayOutputStream());
        }
        finally {
          pool.dispose();
        }
    }

    private String getRelativePath(File path, File basePath) {
        return SVNPathUtil.getRelativePath(basePath.getAbsolutePath().replace(File.separatorChar, '/'),
                path.getAbsolutePath().replace(File.separatorChar, '/'));
    }

    private String runLocalDiff(SvnOperationFactory svnOperationFactory, File target, File relativeToDirectory) throws SVNException {
        final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();

        final SvnDiff diff = svnOperationFactory.createDiff();
        diff.setSources(SvnTarget.fromFile(target, SVNRevision.BASE), SvnTarget.fromFile(target, SVNRevision.WORKING));
        diff.setOutput(byteArrayOutputStream);
        diff.setRelativeToDirectory(relativeToDirectory);
        diff.setIgnoreAncestry(true);
        diff.run();
        return new String(byteArrayOutputStream.toByteArray()).replace(System.getProperty("line.separator"), "\n");
    }

    private String runDiff(SvnOperationFactory svnOperationFactory, SVNURL fileUrl, SVNRevision startRevision, SVNRevision endRevision) throws SVNException {
        final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();

        final SvnDiff diff = svnOperationFactory.createDiff();
        diff.setSource(SvnTarget.fromURL(fileUrl, startRevision), startRevision, endRevision);
        diff.setOutput(byteArrayOutputStream);
        diff.run();

        return new String(byteArrayOutputStream.toByteArray()).replace(System.getProperty("line.separator"), "\n");
    }

    private String runDiff(SvnOperationFactory svnOperationFactory, SVNURL url1, SVNRevision svnRevision1, SVNURL url2, SVNRevision svnRevision2) throws SVNException {
        final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();

        final SvnDiff diff = svnOperationFactory.createDiff();
        diff.setSources(SvnTarget.fromURL(url1, svnRevision1), SvnTarget.fromURL(url2, svnRevision2));
        diff.setOutput(byteArrayOutputStream);
        diff.run();

        return new String(byteArrayOutputStream.toByteArray()).replace(System.getProperty("line.separator"), "\n");
    }

    private String runDiff(SvnOperationFactory svnOperationFactory, File file, SVNRevision startRevision, SVNRevision endRevision) throws SVNException {
        final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();

        final SvnDiff diff = svnOperationFactory.createDiff();
        diff.setSource(SvnTarget.fromFile(file, startRevision), startRevision, endRevision);
        diff.setOutput(byteArrayOutputStream);
        diff.run();

        return new String(byteArrayOutputStream.toByteArray()).replace(System.getProperty("line.separator"), "\n");
    }

    public String getTestName() {
        return "DiffTest";
    }

    private static enum GeneratorCallKind {
        DISPLAY_PROP_DIFF, DISPLAY_FILE_DIFF, DISPLAY_DELETED_DIRECTORY, DISPLAY_ADDED_DIRECTORY
    }

    private static class GeneratorCall {
        private final GeneratorCallKind callKind;
        private final String path;

        public GeneratorCall(GeneratorCallKind callKind, String path) {
            this.callKind = callKind;
            this.path = path;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            GeneratorCall that = (GeneratorCall) o;

            if (callKind != that.callKind) {
                return false;
            }
            if (!path.equals(that.path)) {
                return false;
            }

            return true;
        }

        @Override
        public int hashCode() {
            int result = callKind.hashCode();
            result = 31 * result + path.hashCode();
            return result;
        }

        @Override
        public String toString() {
            return "GeneratorCall{" +
                    "callKind=" + callKind +
                    ", path='" + path + '\'' +
                    '}';
        }
    }

    private static class OldGenerator implements ISVNDiffGenerator {

        private final List<GeneratorCall> calls;
        private String anchorPath1;
        private String anchorPath2;

        private OldGenerator() {
            calls = new ArrayList<GeneratorCall>();
        }

        public void init(String anchorPath1, String anchorPath2) {
            this.anchorPath1 = anchorPath1;
            this.anchorPath2 = anchorPath2;
        }

        public void setBasePath(File basePath) {
        }

        public void setForcedBinaryDiff(boolean forced) {
        }

        public void setEncoding(String encoding) {
        }

        public String getEncoding() {
            return null;
        }

        public void setEOL(byte[] eol) {
        }

        public byte[] getEOL() {
            return SVNProperty.EOL_LF_BYTES;
        }

        public void setDiffDeleted(boolean isDiffDeleted) {
        }

        public boolean isDiffDeleted() {
            return false;
        }

        public void setDiffAdded(boolean isDiffAdded) {
        }

        public boolean isDiffAdded() {
            return true;
        }

        public void setDiffCopied(boolean isDiffCopied) {
        }

        public boolean isDiffCopied() {
            return false;
        }

        public void setDiffUnversioned(boolean diffUnversioned) {
        }

        public boolean isDiffUnversioned() {
            return false;
        }

        public File createTempDirectory() throws SVNException {
            return SVNFileUtil.createTempDirectory("svnkitdiff");
        }

        public void displayPropDiff(String path, SVNProperties baseProps, SVNProperties diff, OutputStream result) throws SVNException {
            calls.add(new GeneratorCall(GeneratorCallKind.DISPLAY_PROP_DIFF, path));
        }

        public void displayFileDiff(String path, File file1, File file2, String rev1, String rev2, String mimeType1, String mimeType2, OutputStream result) throws SVNException {
            calls.add(new GeneratorCall(GeneratorCallKind.DISPLAY_FILE_DIFF, path));
        }

        public void displayDeletedDirectory(String path, String rev1, String rev2) throws SVNException {
            calls.add(new GeneratorCall(GeneratorCallKind.DISPLAY_DELETED_DIRECTORY, path));
        }

        public void displayAddedDirectory(String path, String rev1, String rev2) throws SVNException {
            calls.add(new GeneratorCall(GeneratorCallKind.DISPLAY_ADDED_DIRECTORY, path));
        }

        public boolean isForcedBinaryDiff() {
            return false;
        }
    }
}
