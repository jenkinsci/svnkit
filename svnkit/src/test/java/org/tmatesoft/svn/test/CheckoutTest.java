package org.tmatesoft.svn.test;

import org.junit.Assert;
import org.junit.Test;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.wc17.db.ISVNWCDb;
import org.tmatesoft.svn.core.wc.SVNEvent;
import org.tmatesoft.svn.core.wc.SVNEventAction;
import org.tmatesoft.svn.core.wc.SVNStatusType;
import org.tmatesoft.svn.core.wc2.*;

import java.io.File;

public class CheckoutTest {
    @Test
    public void testCheckoutWC17() throws Exception {
        final TestOptions options = TestOptions.getInstance();

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testCheckoutWC17", options);
        try {
            final SVNURL url = sandbox.createSvnRepository();

            final CommitBuilder commitBuilder = new CommitBuilder(url);
            commitBuilder.addFile("file");
            commitBuilder.commit();

            final File workingCopyDirectory = sandbox.createDirectory("wc");

            final int expectedWorkingCopyFormat = ISVNWCDb.WC_FORMAT_17;

            final SvnCheckout checkout = svnOperationFactory.createCheckout();
            checkout.setSource(SvnTarget.fromURL(url));
            checkout.setSingleTarget(SvnTarget.fromFile(workingCopyDirectory));
            checkout.setTargetWorkingCopyFormat(expectedWorkingCopyFormat);
            checkout.run();

            final SvnGetStatus getStatus = svnOperationFactory.createGetStatus();
            getStatus.setDepth(SVNDepth.EMPTY);
            getStatus.setReportAll(true);
            getStatus.setSingleTarget(SvnTarget.fromFile(workingCopyDirectory));
            final SvnStatus status = getStatus.run();

            final int actualWorkingCopyFormat = status.getWorkingCopyFormat();
            Assert.assertEquals(expectedWorkingCopyFormat, actualWorkingCopyFormat);

        } finally {
            svnOperationFactory.dispose();
            sandbox.dispose();
        }
    }

    private String getTestName() {
        return getClass().getSimpleName();
    }
}
