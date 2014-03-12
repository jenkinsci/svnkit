package org.tmatesoft.svn.test;

import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;
import org.tmatesoft.sqljet.core.SqlJetException;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.SVNPropertyValue;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.internal.wc.SVNExternal;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.internal.wc17.db.statement.SVNWCDbSchema;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNStatusType;
import org.tmatesoft.svn.core.wc2.*;
import org.tmatesoft.svn.core.wc2.hooks.ISvnExternalsHandler;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ExternalsTest {

    @Test
    public void testFileExternalDeletedTogetherWithPropertyOwner() throws Exception {
        final TestOptions options = TestOptions.getInstance();

        Assume.assumeTrue(TestUtil.isNewWorkingCopyTest());

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testFileExternalDeletedTogetherWithPropertyOwner", options);
        try {
            final SVNURL url = sandbox.createSvnRepository();

            final SVNExternal external = new SVNExternal("external", url.appendPath("file", false).toString(), SVNRevision.HEAD, SVNRevision.HEAD, false, false, true);

            final CommitBuilder commitBuilder = new CommitBuilder(url);
            commitBuilder.addFile("file");
            commitBuilder.addDirectory("directory");
            commitBuilder.setDirectoryProperty("directory", SVNProperty.EXTERNALS, SVNPropertyValue.create(external.toString()));
            commitBuilder.commit();

            final File workingCopyDirectory = sandbox.createDirectory("wc");
            final File directory = new File(workingCopyDirectory, "directory");

            final SvnCheckout checkout = svnOperationFactory.createCheckout();
            checkout.setSource(SvnTarget.fromURL(url));
            checkout.setSingleTarget(SvnTarget.fromFile(workingCopyDirectory));
            checkout.setIgnoreExternals(false);
            checkout.run();

            final SvnScheduleForRemoval scheduleForRemoval = svnOperationFactory.createScheduleForRemoval();
            scheduleForRemoval.setSingleTarget(SvnTarget.fromFile(directory));
            scheduleForRemoval.run();

            final SvnCommit commit = svnOperationFactory.createCommit();
            commit.setSingleTarget(SvnTarget.fromFile(workingCopyDirectory));
            commit.run();

            final SvnUpdate update = svnOperationFactory.createUpdate();
            update.setSingleTarget(SvnTarget.fromFile(workingCopyDirectory));
            update.run();

            final WorkingCopy workingCopy = new WorkingCopy(options, workingCopyDirectory);
            try {
                assertTableIsEmpty(workingCopy, SVNWCDbSchema.EXTERNALS.name());
            } finally {
                workingCopy.dispose();
            }

        } finally {
            svnOperationFactory.dispose();
            sandbox.dispose();
        }
    }

    @Test
    public void testExternalTargetChangeDoesntProduceConflictingInformation() throws Exception {
        final TestOptions options = TestOptions.getInstance();

        Assume.assumeTrue(TestUtil.isNewWorkingCopyTest());

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testExternalTargetChangeDoesntProduceConflictingInformation", options);
        try {
            final SVNURL url = sandbox.createSvnRepository();

            final SVNExternal external = new SVNExternal("external", url.appendPath("file", false).toString(), SVNRevision.HEAD, SVNRevision.HEAD, false, false, true);
            final SVNExternal anotherExternal = new SVNExternal("external", url.appendPath("anotherFile", false).toString(), SVNRevision.HEAD, SVNRevision.HEAD, false, false, true);

            final CommitBuilder commitBuilder = new CommitBuilder(url);
            commitBuilder.addFile("file");
            commitBuilder.addFile("anotherFile");
            commitBuilder.setFileProperty("file", "custom property", SVNPropertyValue.create("custom value"));
            commitBuilder.setDirectoryProperty("", SVNProperty.EXTERNALS, SVNPropertyValue.create(external.toString()));
            commitBuilder.commit();

            final File workingCopyDirectory = sandbox.createDirectory("wc");

            final SvnCheckout checkout = svnOperationFactory.createCheckout();
            checkout.setSource(SvnTarget.fromURL(url));
            checkout.setSingleTarget(SvnTarget.fromFile(workingCopyDirectory));
            checkout.setIgnoreExternals(false);
            checkout.run();

            final SvnSetProperty setProperty = svnOperationFactory.createSetProperty();
            setProperty.setSingleTarget(SvnTarget.fromFile(workingCopyDirectory));
            setProperty.setPropertyName(SVNProperty.EXTERNALS);
            setProperty.setPropertyValue(SVNPropertyValue.create(anotherExternal.toString()));
            setProperty.run();

            final SvnUpdate update = svnOperationFactory.createUpdate();
            update.setSingleTarget(SvnTarget.fromFile(workingCopyDirectory));
            update.run();

            final WorkingCopy workingCopy = new WorkingCopy(options, workingCopyDirectory);
            try {
                Assert.assertEquals(1, TestUtil.getTableSize(workingCopy, SVNWCDbSchema.ACTUAL_NODE.name()));
            } finally {
                workingCopy.dispose();
            }

        } finally {
            svnOperationFactory.dispose();
            sandbox.dispose();
        }
    }

    @Test
    public void testStatusOnFileExternalReportsIt() throws Exception {
        final TestOptions options = TestOptions.getInstance();

        Assume.assumeTrue(TestUtil.isNewWorkingCopyTest());

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testStatusOnFileExternalReportsIt", options);
        try {
            final SVNURL url = sandbox.createSvnRepository();

            final SVNExternal external = new SVNExternal("external", url.appendPath("file", false).toString(), SVNRevision.HEAD, SVNRevision.HEAD, false, false, true);

            final CommitBuilder commitBuilder = new CommitBuilder(url);
            commitBuilder.addFile("file");
            commitBuilder.setDirectoryProperty("", SVNProperty.EXTERNALS, SVNPropertyValue.create(external.toString()));
            commitBuilder.commit();

            final File workingCopyDirectory = sandbox.createDirectory("wc");

            final SvnCheckout checkout = svnOperationFactory.createCheckout();
            checkout.setSource(SvnTarget.fromURL(url));
            checkout.setSingleTarget(SvnTarget.fromFile(workingCopyDirectory));
            checkout.setIgnoreExternals(false);
            checkout.run();

            final File file = new File(workingCopyDirectory, "external");

            final SvnGetStatus getStatus = svnOperationFactory.createGetStatus();
            getStatus.setSingleTarget(SvnTarget.fromFile(file));
            final SvnStatus status = getStatus.run();

            Assert.assertTrue(status.isFileExternal());

        } finally {
            svnOperationFactory.dispose();
            sandbox.dispose();
        }
    }

    @Test
    public void testRemoteCopyCallExternalsHandler() throws Exception {
        final TestOptions options = TestOptions.getInstance();

        Assume.assumeTrue(TestUtil.isNewWorkingCopyTest());

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testRemoteCopyCallExternalsHandler", options);
        try {
            final SVNURL url = sandbox.createSvnRepository();

            final SVNExternal external = new SVNExternal("external", url.appendPath("trunk/directory", false).toString(), SVNRevision.HEAD, SVNRevision.HEAD, false, false, true);

            final CommitBuilder commitBuilder = new CommitBuilder(url);
            commitBuilder.addFile("trunk/directory/file");
            commitBuilder.setDirectoryProperty("trunk", SVNProperty.EXTERNALS, SVNPropertyValue.create(external.toString()));
            commitBuilder.commit();

            final File workingCopyDirectory = sandbox.createDirectory("wc");

            final SvnCheckout checkout = svnOperationFactory.createCheckout();
            checkout.setSource(SvnTarget.fromURL(url.appendPath("trunk", false)));
            checkout.setSingleTarget(SvnTarget.fromFile(workingCopyDirectory));
            checkout.setIgnoreExternals(false);
            checkout.run();

            ExternalsHandler externalsHandler = new ExternalsHandler();

            final SvnRemoteCopy remoteCopy = svnOperationFactory.createRemoteCopy();
            remoteCopy.setDisableLocalModifications(true);
            remoteCopy.setExternalsHandler(externalsHandler);
            remoteCopy.setSingleTarget(SvnTarget.fromURL(url.appendPath("branches", false)));
            remoteCopy.setMove(false);
            remoteCopy.setFailWhenDstExists(true);
            remoteCopy.setMakeParents(true);
            remoteCopy.setCommitMessage("");
            remoteCopy.addCopySource(SvnCopySource.create(SvnTarget.fromFile(workingCopyDirectory), SVNRevision.WORKING));
            remoteCopy.run();

            Assert.assertEquals(1, externalsHandler.externals.size());
            Assert.assertEquals(new File(workingCopyDirectory, "external"), externalsHandler.externals.keySet().iterator().next());

        } finally {
            svnOperationFactory.dispose();
            sandbox.dispose();
        }
    }

    @Test
    public void testExternalObstructionIsReportedOnce() throws Exception {
        final TestOptions options = TestOptions.getInstance();

        Assume.assumeTrue(TestUtil.isNewWorkingCopyTest());

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testExternalObstructionIsReportedOnce", options);
        try {
            final SVNURL url = sandbox.createSvnRepository();

            final SVNExternal external = new SVNExternal("external", url.appendPath("file", false).toString(), SVNRevision.HEAD, SVNRevision.HEAD, false, false, true);

            final CommitBuilder commitBuilder = new CommitBuilder(url);
            commitBuilder.addFile("file");
            commitBuilder.setDirectoryProperty("", SVNProperty.EXTERNALS, SVNPropertyValue.create(external.toString()));
            commitBuilder.commit();

            final File workingCopyDirectory = sandbox.createDirectory("wc");

            final SvnCheckout checkout = svnOperationFactory.createCheckout();
            checkout.setSource(SvnTarget.fromURL(url));
            checkout.setSingleTarget(SvnTarget.fromFile(workingCopyDirectory));
            checkout.setIgnoreExternals(false);
            checkout.run();

            final File externalFile = new File(workingCopyDirectory, "external");
            SVNFileUtil.deleteAll(externalFile, null);
            SVNFileUtil.ensureDirectoryExists(externalFile);

            final List<SvnStatus> statuses = new ArrayList<SvnStatus>();

            final SvnGetStatus getStatus = svnOperationFactory.createGetStatus();
            getStatus.setSingleTarget(SvnTarget.fromFile(workingCopyDirectory));
            getStatus.setReceiver(new ISvnObjectReceiver<SvnStatus>() {
                public void receive(SvnTarget target, SvnStatus status) throws SVNException {
                    final String path = target.getPathOrUrlString();
                    if (path.endsWith("external")) {
                        statuses.add(status);
                    }
                }
            });
            getStatus.run();

            Assert.assertEquals(1, statuses.size());
            Assert.assertEquals(SVNStatusType.STATUS_OBSTRUCTED, statuses.get(0).getNodeStatus());
            Assert.assertEquals(SVNStatusType.STATUS_NORMAL, statuses.get(0).getTextStatus());
            Assert.assertEquals(SVNStatusType.STATUS_NONE, statuses.get(0).getPropertiesStatus());

        } finally {
            svnOperationFactory.dispose();
            sandbox.dispose();
        }
    }

    @Test
    public void testExternalIsReportedIfPropertyIsRemoved() throws Exception {
        //SVNKIT-460
        final TestOptions options = TestOptions.getInstance();

        Assume.assumeTrue(TestUtil.isNewWorkingCopyTest());

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testExternalIsReportedIfPropertyIsRemoved", options);
        try {
            final SVNURL url = sandbox.createSvnRepository();

            final SVNExternal external = new SVNExternal("external", url.appendPath("directory", false).toString(), SVNRevision.HEAD, SVNRevision.HEAD, false, false, true);

            final CommitBuilder commitBuilder = new CommitBuilder(url);
            commitBuilder.addFile("directory/file");
            commitBuilder.setDirectoryProperty("", SVNProperty.EXTERNALS, SVNPropertyValue.create(external.toString()));
            commitBuilder.commit();

            final File workingCopyDirectory = sandbox.createDirectory("wc");

            final SvnCheckout checkout = svnOperationFactory.createCheckout();
            checkout.setSource(SvnTarget.fromURL(url));
            checkout.setSingleTarget(SvnTarget.fromFile(workingCopyDirectory));
            checkout.setIgnoreExternals(false);
            checkout.run();

            final SvnSetProperty setProperty = svnOperationFactory.createSetProperty();
            setProperty.setSingleTarget(SvnTarget.fromFile(workingCopyDirectory));
            setProperty.setPropertyName(SVNProperty.EXTERNALS);
            setProperty.setPropertyValue(null);
            setProperty.run();

            final List<SvnStatus> statuses = new ArrayList<SvnStatus>();

            final SvnGetStatus getStatus = svnOperationFactory.createGetStatus();
            getStatus.setSingleTarget(SvnTarget.fromFile(workingCopyDirectory));
            getStatus.setReportAll(true);
            getStatus.setReceiver(new ISvnObjectReceiver<SvnStatus>() {
                public void receive(SvnTarget target, SvnStatus status) throws SVNException {
                    statuses.add(status);
                }
            });
            getStatus.run();

            Assert.assertEquals(6, statuses.size());
            Assert.assertEquals(new File(workingCopyDirectory, "external"), statuses.get(4).getPath());
            Assert.assertEquals(new File(workingCopyDirectory, "external/file"), statuses.get(5).getPath());

        } finally {
            svnOperationFactory.dispose();
            sandbox.dispose();
        }
    }

    @Test
    public void testFileExternalsNotMarkedAsDeletedOnMoving() throws Exception {
        final TestOptions options = TestOptions.getInstance();

        Assume.assumeTrue(TestUtil.isNewWorkingCopyTest());

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testFileExternalsNotMarkedAsDeletedOnMoving", options);
        try {
            final SVNURL url = sandbox.createSvnRepository();

            final SVNExternal external = new SVNExternal("directory/external", url.appendPath("directory/file", false).toString(), SVNRevision.HEAD, SVNRevision.HEAD, false, false, true);

            final CommitBuilder commitBuilder = new CommitBuilder(url);
            commitBuilder.addFile("directory/file");
            commitBuilder.setDirectoryProperty("", SVNProperty.EXTERNALS, SVNPropertyValue.create(external.toString()));
            commitBuilder.commit();

            final File workingCopyDirectory = sandbox.createDirectory("wc");

            final SvnCheckout checkout = svnOperationFactory.createCheckout();
            checkout.setSource(SvnTarget.fromURL(url));
            checkout.setSingleTarget(SvnTarget.fromFile(workingCopyDirectory));
            checkout.setIgnoreExternals(false);
            checkout.run();

            final File externalDirectory = new File(workingCopyDirectory, "directory");
            final File renamedDirectory = new File(workingCopyDirectory, "renamed");

            final File externalFile = new File(externalDirectory, "external");

            final SvnCopy copy = svnOperationFactory.createCopy();
            copy.addCopySource(SvnCopySource.create(SvnTarget.fromFile(externalDirectory), SVNRevision.WORKING));
            copy.setSingleTarget(SvnTarget.fromFile(renamedDirectory));
            copy.setMove(true);
            copy.run();

            final Map<File, SvnStatus> statuses = TestUtil.getStatuses(svnOperationFactory, workingCopyDirectory);
            Assert.assertFalse(statuses.containsKey(externalFile));

        } finally {
            svnOperationFactory.dispose();
            sandbox.dispose();
        }
    }

    private void assertTableIsEmpty(WorkingCopy workingCopy, String tableName) throws SqlJetException {
        Assert.assertEquals(0, TestUtil.getTableSize(workingCopy, tableName));
    }

    private String getTestName() {
        return "ExternalsTest";
    }

    private static class ExternalsHandler implements ISvnExternalsHandler {
        private final Map<File, SVNURL> externals;

        private ExternalsHandler() {
            this.externals = new HashMap<File, SVNURL>();
        }

        public SVNRevision[] handleExternal(File externalPath, SVNURL externalURL, SVNRevision externalRevision, SVNRevision externalPegRevision, String externalsDefinition, SVNRevision externalsWorkingRevision) {
            this.externals.put(externalPath, externalURL);
            return new SVNRevision[] {externalRevision, externalPegRevision};
        }
    }
}
