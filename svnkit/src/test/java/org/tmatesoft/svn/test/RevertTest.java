package org.tmatesoft.svn.test;

import org.junit.Assert;
import org.junit.Test;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNPropertyValue;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.wc.SVNStatusType;
import org.tmatesoft.svn.core.wc2.SvnOperationFactory;
import org.tmatesoft.svn.core.wc2.SvnRevert;
import org.tmatesoft.svn.core.wc2.SvnStatus;
import org.tmatesoft.svn.core.wc2.SvnTarget;

import java.io.File;
import java.util.Map;

public class RevertTest {

    @Test
    public void testRevertCopyWithoutLocalModifications() throws Exception {
        final TestOptions options = TestOptions.getInstance();

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testRevertCopyWithoutLocalModifications", options);
        try {
            final SVNURL url = sandbox.createSvnRepository();

            final CommitBuilder commitBuilder = new CommitBuilder(url);
            commitBuilder.addFile("sourceFile");
            commitBuilder.commit();

            final WorkingCopy workingCopy = sandbox.checkoutNewWorkingCopy(url);
            final File targetFile = workingCopy.getFile("targetFile");
            workingCopy.copy("sourceFile", "targetFile");

            final SvnRevert revert = svnOperationFactory.createRevert();
            revert.setSingleTarget(SvnTarget.fromFile(targetFile));
            revert.run();

            final Map<File, SvnStatus> statuses = TestUtil.getStatuses(svnOperationFactory, workingCopy.getWorkingCopyDirectory());
            if (TestUtil.isNewWorkingCopyTest()) {
                Assert.assertFalse(targetFile.exists());
                Assert.assertNull(statuses.get(targetFile));
            } else {
                Assert.assertTrue(targetFile.isFile());
                Assert.assertEquals(SVNStatusType.STATUS_UNVERSIONED, statuses.get(targetFile).getNodeStatus());
            }

        } finally {
            svnOperationFactory.dispose();
            sandbox.dispose();
        }
    }

    @Test
    public void testRevertCopyWithLocalModifications() throws Exception {
        final TestOptions options = TestOptions.getInstance();

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testRevertCopyWithLocalModifications", options);
        try {
            final SVNURL url = sandbox.createSvnRepository();

            final CommitBuilder commitBuilder = new CommitBuilder(url);
            commitBuilder.addFile("sourceFile");
            commitBuilder.commit();

            final WorkingCopy workingCopy = sandbox.checkoutNewWorkingCopy(url);
            final File targetFile = workingCopy.getFile("targetFile");
            workingCopy.copy("sourceFile", "targetFile");

            //even local modifications won't prevent the file from deletion

            TestUtil.writeFileContentsString(targetFile, "changed");

            final SvnRevert revert = svnOperationFactory.createRevert();
            revert.setSingleTarget(SvnTarget.fromFile(targetFile));
            revert.run();

            final Map<File, SvnStatus> statuses = TestUtil.getStatuses(svnOperationFactory, workingCopy.getWorkingCopyDirectory());
            if (TestUtil.isNewWorkingCopyTest()) {
                Assert.assertFalse(targetFile.exists());
                Assert.assertNull(statuses.get(targetFile));
            } else {
                Assert.assertTrue(targetFile.isFile());
                Assert.assertEquals(SVNStatusType.STATUS_UNVERSIONED, statuses.get(targetFile).getNodeStatus());
            }

        } finally {
            svnOperationFactory.dispose();
            sandbox.dispose();
        }
    }

    @Test
    public void testRevertCopyWithoutLocalModificationsEvenWithSpecialOption() throws Exception {
        final TestOptions options = TestOptions.getInstance();

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testRevertCopyWithoutLocalModificationsEvenWithSpecialOption", options);
        try {
            final SVNURL url = sandbox.createSvnRepository();

            final CommitBuilder commitBuilder = new CommitBuilder(url);
            commitBuilder.addFile("sourceFile");
            commitBuilder.commit();

            final WorkingCopy workingCopy = sandbox.checkoutNewWorkingCopy(url);
            final File targetFile = workingCopy.getFile("targetFile");
            workingCopy.copy("sourceFile", "targetFile");

            final SvnRevert revert = svnOperationFactory.createRevert();
            revert.setSingleTarget(SvnTarget.fromFile(targetFile));
            revert.setPreserveModifiedCopies(true);
            revert.run();

            final Map<File, SvnStatus> statuses = TestUtil.getStatuses(svnOperationFactory, workingCopy.getWorkingCopyDirectory());
            if (TestUtil.isNewWorkingCopyTest()) {
                Assert.assertFalse(targetFile.exists());
                Assert.assertNull(statuses.get(targetFile));
            } else {
                Assert.assertTrue(targetFile.isFile());
                Assert.assertEquals(SVNStatusType.STATUS_UNVERSIONED, statuses.get(targetFile).getNodeStatus());
            }

        } finally {
            svnOperationFactory.dispose();
            sandbox.dispose();
        }
    }

    @Test
    public void testDontRevertCopyWithoutLocalTextModificationsSpecialOption() throws Exception {
        final TestOptions options = TestOptions.getInstance();

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testDontRevertCopyWithoutLocalTextModificationsSpecialOption", options);
        try {
            final SVNURL url = sandbox.createSvnRepository();

            final CommitBuilder commitBuilder = new CommitBuilder(url);
            commitBuilder.addFile("sourceFile");
            commitBuilder.commit();

            final WorkingCopy workingCopy = sandbox.checkoutNewWorkingCopy(url);
            final File targetFile = workingCopy.getFile("targetFile");
            workingCopy.copy("sourceFile", "targetFile");

            //even local text modifications won't prevent the file from deletion

            TestUtil.writeFileContentsString(targetFile, "changed");

            final SvnRevert revert = svnOperationFactory.createRevert();
            revert.setSingleTarget(SvnTarget.fromFile(targetFile));
            revert.setPreserveModifiedCopies(true);
            revert.run();

            final Map<File, SvnStatus> statuses = TestUtil.getStatuses(svnOperationFactory, workingCopy.getWorkingCopyDirectory());
            Assert.assertTrue(targetFile.isFile());
            Assert.assertEquals(SVNStatusType.STATUS_UNVERSIONED, statuses.get(targetFile).getNodeStatus());

        } finally {
            svnOperationFactory.dispose();
            sandbox.dispose();
        }
    }

    @Test
    public void testDontRevertCopyWithoutLocalPropertiesModificationsSpecialOption() throws Exception {
        final TestOptions options = TestOptions.getInstance();

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testDontRevertCopyWithoutLocalPropertiesModificationsSpecialOption", options);
        try {
            final SVNURL url = sandbox.createSvnRepository();

            final CommitBuilder commitBuilder = new CommitBuilder(url);
            commitBuilder.addFile("sourceFile");
            commitBuilder.commit();

            final WorkingCopy workingCopy = sandbox.checkoutNewWorkingCopy(url);
            final File targetFile = workingCopy.getFile("targetFile");
            workingCopy.copy("sourceFile", "targetFile");

            //even local properties modifications won't prevent the file from deletion

            workingCopy.setProperty(targetFile, "propertyName", SVNPropertyValue.create("propertyValue"));

            final SvnRevert revert = svnOperationFactory.createRevert();
            revert.setSingleTarget(SvnTarget.fromFile(targetFile));
            revert.setPreserveModifiedCopies(true);
            revert.run();

            final Map<File, SvnStatus> statuses = TestUtil.getStatuses(svnOperationFactory, workingCopy.getWorkingCopyDirectory());
            Assert.assertTrue(targetFile.isFile());
            Assert.assertEquals(SVNStatusType.STATUS_UNVERSIONED, statuses.get(targetFile).getNodeStatus());

        } finally {
            svnOperationFactory.dispose();
            sandbox.dispose();
        }
    }


    @Test
    public void testRevertCopyWithoutLocalModificationsRecursiveRevert() throws Exception {
        final TestOptions options = TestOptions.getInstance();

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testRevertCopyWithoutLocalModificationsRecursiveRevert", options);
        try {
            final SVNURL url = sandbox.createSvnRepository();

            final CommitBuilder commitBuilder = new CommitBuilder(url);
            commitBuilder.addFile("sourceFile");
            commitBuilder.commit();

            final WorkingCopy workingCopy = sandbox.checkoutNewWorkingCopy(url);
            final File targetFile = workingCopy.getFile("targetFile");
            workingCopy.copy("sourceFile", "targetFile");

            final SvnRevert revert = svnOperationFactory.createRevert();
            revert.setSingleTarget(SvnTarget.fromFile(workingCopy.getWorkingCopyDirectory()));
            revert.setDepth(SVNDepth.INFINITY);
            revert.run();

            final Map<File, SvnStatus> statuses = TestUtil.getStatuses(svnOperationFactory, workingCopy.getWorkingCopyDirectory());
            if (TestUtil.isNewWorkingCopyTest()) {
                Assert.assertFalse(targetFile.exists());
                Assert.assertNull(statuses.get(targetFile));
            } else {
                Assert.assertTrue(targetFile.isFile());
                Assert.assertEquals(SVNStatusType.STATUS_UNVERSIONED, statuses.get(targetFile).getNodeStatus());
            }

        } finally {
            svnOperationFactory.dispose();
            sandbox.dispose();
        }
    }

    @Test
    public void testRevertCopyWithLocalModificationsRecursiveRevert() throws Exception {
        final TestOptions options = TestOptions.getInstance();

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testRevertCopyWithLocalModificationsRecursiveRevert", options);
        try {
            final SVNURL url = sandbox.createSvnRepository();

            final CommitBuilder commitBuilder = new CommitBuilder(url);
            commitBuilder.addFile("sourceFile");
            commitBuilder.commit();

            final WorkingCopy workingCopy = sandbox.checkoutNewWorkingCopy(url);
            final File targetFile = workingCopy.getFile("targetFile");
            workingCopy.copy("sourceFile", "targetFile");

            //even local modifications won't prevent the file from deletion

            TestUtil.writeFileContentsString(targetFile, "changed");

            final SvnRevert revert = svnOperationFactory.createRevert();
            revert.setSingleTarget(SvnTarget.fromFile(workingCopy.getWorkingCopyDirectory()));
            revert.setDepth(SVNDepth.INFINITY);
            revert.run();

            final Map<File, SvnStatus> statuses = TestUtil.getStatuses(svnOperationFactory, workingCopy.getWorkingCopyDirectory());
            if (TestUtil.isNewWorkingCopyTest()) {
                Assert.assertFalse(targetFile.exists());
                Assert.assertNull(statuses.get(targetFile));
            } else {
                Assert.assertTrue(targetFile.isFile());
                Assert.assertEquals(SVNStatusType.STATUS_UNVERSIONED, statuses.get(targetFile).getNodeStatus());
            }

        } finally {
            svnOperationFactory.dispose();
            sandbox.dispose();
        }
    }

    @Test
    public void testRevertCopyWithoutLocalModificationsEvenWithSpecialOptionRecursiveRevert() throws Exception {
        final TestOptions options = TestOptions.getInstance();

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testRevertCopyWithoutLocalModificationsEvenWithSpecialOptionRecursiveRevert", options);
        try {
            final SVNURL url = sandbox.createSvnRepository();

            final CommitBuilder commitBuilder = new CommitBuilder(url);
            commitBuilder.addFile("sourceFile");
            commitBuilder.commit();

            final WorkingCopy workingCopy = sandbox.checkoutNewWorkingCopy(url);
            final File targetFile = workingCopy.getFile("targetFile");
            workingCopy.copy("sourceFile", "targetFile");

            final SvnRevert revert = svnOperationFactory.createRevert();
            revert.setSingleTarget(SvnTarget.fromFile(workingCopy.getWorkingCopyDirectory()));
            revert.setDepth(SVNDepth.INFINITY);
            revert.setPreserveModifiedCopies(true);
            revert.run();

            final Map<File, SvnStatus> statuses = TestUtil.getStatuses(svnOperationFactory, workingCopy.getWorkingCopyDirectory());
            if (TestUtil.isNewWorkingCopyTest()) {
                Assert.assertFalse(targetFile.exists());
                Assert.assertNull(statuses.get(targetFile));
            } else {
                Assert.assertTrue(targetFile.isFile());
                Assert.assertEquals(SVNStatusType.STATUS_UNVERSIONED, statuses.get(targetFile).getNodeStatus());
            }

        } finally {
            svnOperationFactory.dispose();
            sandbox.dispose();
        }
    }

    @Test
    public void testDontRevertCopyWithoutLocalTextModificationsSpecialOptionRecursiveRevert() throws Exception {
        final TestOptions options = TestOptions.getInstance();

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testDontRevertCopyWithoutLocalTextModificationsSpecialOptionRecursiveRevert", options);
        try {
            final SVNURL url = sandbox.createSvnRepository();

            final CommitBuilder commitBuilder = new CommitBuilder(url);
            commitBuilder.addFile("sourceFile");
            commitBuilder.commit();

            final WorkingCopy workingCopy = sandbox.checkoutNewWorkingCopy(url);
            final File targetFile = workingCopy.getFile("targetFile");
            workingCopy.copy("sourceFile", "targetFile");

            //even local text modifications won't prevent the file from deletion

            TestUtil.writeFileContentsString(targetFile, "changed");

            final SvnRevert revert = svnOperationFactory.createRevert();
            revert.setSingleTarget(SvnTarget.fromFile(workingCopy.getWorkingCopyDirectory()));
            revert.setDepth(SVNDepth.INFINITY);
            revert.setPreserveModifiedCopies(true);
            revert.run();

            final Map<File, SvnStatus> statuses = TestUtil.getStatuses(svnOperationFactory, workingCopy.getWorkingCopyDirectory());
            Assert.assertTrue(targetFile.isFile());
            Assert.assertEquals(SVNStatusType.STATUS_UNVERSIONED, statuses.get(targetFile).getNodeStatus());

        } finally {
            svnOperationFactory.dispose();
            sandbox.dispose();
        }
    }

    @Test
    public void testDontRevertCopyWithoutLocalPropertiesModificationsSpecialOptionRecursiveRevert() throws Exception {
        final TestOptions options = TestOptions.getInstance();

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testDontRevertCopyWithoutLocalPropertiesModificationsSpecialOptionRecursiveRevert", options);
        try {
            final SVNURL url = sandbox.createSvnRepository();

            final CommitBuilder commitBuilder = new CommitBuilder(url);
            commitBuilder.addFile("sourceFile");
            commitBuilder.commit();

            final WorkingCopy workingCopy = sandbox.checkoutNewWorkingCopy(url);
            final File targetFile = workingCopy.getFile("targetFile");
            workingCopy.copy("sourceFile", "targetFile");

            //even local properties modifications won't prevent the file from deletion

            workingCopy.setProperty(targetFile, "propertyName", SVNPropertyValue.create("propertyValue"));

            final SvnRevert revert = svnOperationFactory.createRevert();
            revert.setSingleTarget(SvnTarget.fromFile(workingCopy.getWorkingCopyDirectory()));
            revert.setDepth(SVNDepth.INFINITY);
            revert.setPreserveModifiedCopies(true);
            revert.run();

            final Map<File, SvnStatus> statuses = TestUtil.getStatuses(svnOperationFactory, workingCopy.getWorkingCopyDirectory());
            Assert.assertTrue(targetFile.isFile());
            Assert.assertEquals(SVNStatusType.STATUS_UNVERSIONED, statuses.get(targetFile).getNodeStatus());

        } finally {
            svnOperationFactory.dispose();
            sandbox.dispose();
        }
    }

    @Test
    public void testUnmodifiedFileIsUntouchedPreserveModifiedOption() throws Exception {
        final TestOptions options = TestOptions.getInstance();

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testUnmodifiedFileIsUntouchedPreserveModifiedOption", options);
        try {
            final SVNURL url = sandbox.createSvnRepository();

            final CommitBuilder commitBuilder = new CommitBuilder(url);
            commitBuilder.addFile("file");
            commitBuilder.commit();

            final WorkingCopy workingCopy = sandbox.checkoutNewWorkingCopy(url);
            final File file = workingCopy.getFile("file");

            final SvnRevert revert = svnOperationFactory.createRevert();
            revert.setSingleTarget(SvnTarget.fromFile(file));
            revert.setDepth(SVNDepth.INFINITY);
            revert.setPreserveModifiedCopies(true);
            revert.run();

            final Map<File, SvnStatus> statuses = TestUtil.getStatuses(svnOperationFactory, workingCopy.getWorkingCopyDirectory());
            Assert.assertTrue(file.isFile());
            Assert.assertEquals(SVNStatusType.STATUS_NORMAL, statuses.get(file).getNodeStatus());

        } finally {
            svnOperationFactory.dispose();
            sandbox.dispose();
        }
    }


    @Test
    public void testModifiedFileIsRevertedPreserveModifiedOption() throws Exception {
        final TestOptions options = TestOptions.getInstance();

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testModifiedFileIsRevertedPreserveModifiedOption", options);
        try {
            final SVNURL url = sandbox.createSvnRepository();

            final CommitBuilder commitBuilder = new CommitBuilder(url);
            commitBuilder.addFile("file");
            commitBuilder.commit();

            final WorkingCopy workingCopy = sandbox.checkoutNewWorkingCopy(url);
            final File file = workingCopy.getFile("file");
            TestUtil.writeFileContentsString(file, "contents");

            final SvnRevert revert = svnOperationFactory.createRevert();
            revert.setSingleTarget(SvnTarget.fromFile(file));
            revert.setDepth(SVNDepth.INFINITY);
            revert.setPreserveModifiedCopies(true);
            revert.run();

            final Map<File, SvnStatus> statuses = TestUtil.getStatuses(svnOperationFactory, workingCopy.getWorkingCopyDirectory());
            Assert.assertTrue(file.isFile());
            Assert.assertEquals(SVNStatusType.STATUS_NORMAL, statuses.get(file).getNodeStatus());

        } finally {
            svnOperationFactory.dispose();
            sandbox.dispose();
        }
    }


    @Test
    public void testUnmodifiedFileIsUntouchedPreserveModifiedOptionRecursiveRevert() throws Exception {
        final TestOptions options = TestOptions.getInstance();

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testUnmodifiedFileIsUntouchedPreserveModifiedOptionRecursiveRevert", options);
        try {
            final SVNURL url = sandbox.createSvnRepository();

            final CommitBuilder commitBuilder = new CommitBuilder(url);
            commitBuilder.addFile("file");
            commitBuilder.commit();

            final WorkingCopy workingCopy = sandbox.checkoutNewWorkingCopy(url);
            final File file = workingCopy.getFile("file");

            final SvnRevert revert = svnOperationFactory.createRevert();
            revert.setSingleTarget(SvnTarget.fromFile(workingCopy.getWorkingCopyDirectory()));
            revert.setDepth(SVNDepth.INFINITY);
            revert.setPreserveModifiedCopies(true);
            revert.run();

            final Map<File, SvnStatus> statuses = TestUtil.getStatuses(svnOperationFactory, workingCopy.getWorkingCopyDirectory());
            Assert.assertTrue(file.isFile());
            Assert.assertEquals(SVNStatusType.STATUS_NORMAL, statuses.get(file).getNodeStatus());

        } finally {
            svnOperationFactory.dispose();
            sandbox.dispose();
        }
    }


    @Test
    public void testModifiedFileIsRevertedPreserveModifiedOptionRecursiveRevert() throws Exception {
        final TestOptions options = TestOptions.getInstance();

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testModifiedFileIsRevertedPreserveModifiedOptionRecursiveRevert", options);
        try {
            final SVNURL url = sandbox.createSvnRepository();

            final CommitBuilder commitBuilder = new CommitBuilder(url);
            commitBuilder.addFile("file");
            commitBuilder.commit();

            final WorkingCopy workingCopy = sandbox.checkoutNewWorkingCopy(url);
            final File file = workingCopy.getFile("file");
            TestUtil.writeFileContentsString(file, "contents");

            final SvnRevert revert = svnOperationFactory.createRevert();
            revert.setSingleTarget(SvnTarget.fromFile(workingCopy.getWorkingCopyDirectory()));
            revert.setDepth(SVNDepth.INFINITY);
            revert.setPreserveModifiedCopies(true);
            revert.run();

            final Map<File, SvnStatus> statuses = TestUtil.getStatuses(svnOperationFactory, workingCopy.getWorkingCopyDirectory());
            Assert.assertTrue(file.isFile());
            Assert.assertEquals(SVNStatusType.STATUS_NORMAL, statuses.get(file).getNodeStatus());

        } finally {
            svnOperationFactory.dispose();
            sandbox.dispose();
        }
    }

    private String getTestName() {
        return "RevertTest";
    }
}
