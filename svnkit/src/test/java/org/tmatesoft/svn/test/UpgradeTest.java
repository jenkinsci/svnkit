package org.tmatesoft.svn.test;

import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.internal.wc17.SVNWCUtils;
import org.tmatesoft.svn.core.internal.wc2.SvnWcGeneration;
import org.tmatesoft.svn.core.wc.SVNStatusType;
import org.tmatesoft.svn.core.wc2.*;

import java.io.File;
import java.util.Map;

public class UpgradeTest {

    @Test
    public void testUpgradeTempDirectoryIsNotEmpty() throws Exception {
        final TestOptions options = TestOptions.getInstance();

        Assume.assumeTrue(!TestUtil.isNewWorkingCopyOnly());
        Assume.assumeTrue(TestUtil.isNewWorkingCopyTest());

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testUpgradeTempDirectoryIsNotEmpty", options);
        try {
            final SVNURL url = sandbox.createSvnRepository();

            final CommitBuilder commitBuilder = new CommitBuilder(url);
            commitBuilder.addFile("directory/file1");
            commitBuilder.addFile("directory/file2");
            commitBuilder.commit();

            final File workingCopyDirectory = sandbox.createDirectory("wc");

            //checkout working copy in old format
            checkout(svnOperationFactory, url, workingCopyDirectory, SvnWcGeneration.V16);

            final File tmpDirectory = SVNWCUtils.admChild(workingCopyDirectory, "tmp");
            final File wcngDirectory = SVNFileUtil.createFilePath(tmpDirectory, "wcng");

            //not let's create some contents in .svn/tmp/wcng directory (for instace from the previous upgrade attempt if JVM crashed);
            // every contents there shouldn't be considered and can be removed
            checkout(svnOperationFactory, url, wcngDirectory, SvnWcGeneration.V17);

            final SvnUpgrade upgrade = svnOperationFactory.createUpgrade();
            upgrade.setSingleTarget(SvnTarget.fromFile(workingCopyDirectory));
            upgrade.run();

            //let's check that generation is ok
            Assert.assertEquals(SvnWcGeneration.V17, SvnOperationFactory.detectWcGeneration(workingCopyDirectory, false));

            final Map<File,SvnStatus> statuses = TestUtil.getStatuses(svnOperationFactory, workingCopyDirectory);
            Assert.assertEquals(SVNStatusType.STATUS_NORMAL, statuses.get(workingCopyDirectory).getNodeStatus());
            Assert.assertEquals(SVNStatusType.STATUS_NORMAL, statuses.get(SVNFileUtil.createFilePath(workingCopyDirectory, "directory")).getNodeStatus());
            Assert.assertEquals(SVNStatusType.STATUS_NORMAL, statuses.get(SVNFileUtil.createFilePath(workingCopyDirectory, "directory/file1")).getNodeStatus());
            Assert.assertEquals(SVNStatusType.STATUS_NORMAL, statuses.get(SVNFileUtil.createFilePath(workingCopyDirectory, "directory/file2")).getNodeStatus());

        } finally {
            svnOperationFactory.dispose();
            sandbox.dispose();
        }
    }

    private void checkout(SvnOperationFactory svnOperationFactory, SVNURL url, File wcngDirectory, SvnWcGeneration primaryWcGeneration) throws SVNException {
        svnOperationFactory.setPrimaryWcGeneration(primaryWcGeneration);

        final SvnCheckout checkout = svnOperationFactory.createCheckout();
        checkout.setSource(SvnTarget.fromURL(url));
        checkout.setSingleTarget(SvnTarget.fromFile(wcngDirectory));
        checkout.run();
    }

    private String getTestName() {
        return "UpgradeTest";
    }
}
