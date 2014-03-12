package org.tmatesoft.svn.test;

import org.junit.Assert;
import org.junit.Test;
import org.tmatesoft.svn.core.*;
import org.tmatesoft.svn.core.wc.SVNWCUtil;
import org.tmatesoft.svn.core.wc2.*;

import java.io.File;

public class ConfigTest {

    @Test
    public void testConfigAuthDirectoriesCreated() throws Exception {
        final TestOptions options = TestOptions.getInstance();

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testConfigAuthDirectoriesCreated", options);
        try {
            final SVNURL url = sandbox.createSvnRepository();

            final File workingCopyDirectory = sandbox.createDirectory("wc");
            final File confgDirectory = sandbox.createDirectory("confg");

            svnOperationFactory.setOptions(SVNWCUtil.createDefaultOptions(confgDirectory, false));
            final SvnCheckout checkout = svnOperationFactory.createCheckout();
            checkout.setSource(SvnTarget.fromURL(url));
            checkout.setSingleTarget(SvnTarget.fromFile(workingCopyDirectory));
            checkout.run();

            Assert.assertTrue(confgDirectory.isDirectory());
            Assert.assertTrue(new File(confgDirectory, "auth").isDirectory());
            Assert.assertTrue(new File(confgDirectory, "auth/svn.simple").isDirectory());
            Assert.assertTrue(new File(confgDirectory, "auth/svn.ssl.client-passphrase").isDirectory());
            Assert.assertTrue(new File(confgDirectory, "auth/svn.username").isDirectory());
            Assert.assertTrue(new File(confgDirectory, "auth/svn.ssl.server").isDirectory());
            Assert.assertTrue(new File(confgDirectory, "README.txt").isFile());
            Assert.assertTrue(new File(confgDirectory, "servers").isFile());
            Assert.assertTrue(new File(confgDirectory, "config").isFile());

        } finally {
            svnOperationFactory.dispose();
            sandbox.dispose();
        }
    }

    private String getTestName() {
        return getClass().getSimpleName();
    }
}
