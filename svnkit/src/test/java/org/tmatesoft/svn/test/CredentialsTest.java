package org.tmatesoft.svn.test;

import junit.framework.Assert;
import org.junit.Assume;
import org.junit.Test;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.auth.*;
import org.tmatesoft.svn.core.internal.wc.DefaultSVNAuthenticationManager;
import org.tmatesoft.svn.core.internal.wc.SVNConfigFile;
import org.tmatesoft.svn.core.wc2.SvnGetInfo;
import org.tmatesoft.svn.core.wc2.SvnInfo;
import org.tmatesoft.svn.core.wc2.SvnOperationFactory;
import org.tmatesoft.svn.core.wc2.SvnTarget;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

public class CredentialsTest {

    @Test
    public void testPasswordIsNotRemovedIfStorePasswordsOptionIsFalse() throws Exception {
        final TestOptions testOptions = TestOptions.getInstance();

        Assume.assumeTrue(TestUtil.areAllApacheOptionsSpecified(testOptions));

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testPasswordIsNotRemovedIfStorePasswordsOptionIsFalse", testOptions);
        try {
            final BasicAuthenticationManager authenticationManager = new BasicAuthenticationManager("username", "password");

            final Map<String, String> loginToPassword = new HashMap<String, String>();
            loginToPassword.put("username", "password");

            final SVNURL url = sandbox.createSvnRepositoryWithDavAccess(loginToPassword);

            final CommitBuilder commitBuilder = new CommitBuilder(url);
            commitBuilder.setAuthenticationManager(authenticationManager);
            commitBuilder.addFile("file");
            commitBuilder.commit();

            final File configDirectory = sandbox.createDirectory("config.directory");

            new SVNConfigFile(new File(configDirectory, "config")).setPropertyValue("auth", "password-stores", "", true);

            final File serversFile = new File(configDirectory, "servers");

            final DefaultSVNAuthenticationManager defaultAuthenticationManager = createAuthenticationManager(configDirectory, true, "username", "password");

            new SVNConfigFile(serversFile).setPropertyValue("global", "store-passwords", "yes", true);
            new SVNConfigFile(serversFile).setPropertyValue("global", "store-plaintext-passwords", "yes", true);
            runInfo(svnOperationFactory, url, createAuthenticationManager(configDirectory, true, "username", "password"));

            new SVNConfigFile(serversFile).setPropertyValue("global", "store-passwords", "no", true);
            new SVNConfigFile(serversFile).setPropertyValue("global", "store-plaintext-passwords", "no", true);
            runInfo(svnOperationFactory, url, defaultAuthenticationManager);
            runInfo(svnOperationFactory, url, defaultAuthenticationManager); //because of the bug, password is removed here

            final SvnInfo svnInfo = runInfo(svnOperationFactory, url, new DefaultSVNAuthenticationManager(configDirectory, false, null, null));
            Assert.assertEquals(url, svnInfo.getUrl());
            Assert.assertEquals(1, svnInfo.getRevision());

        } finally {
            svnOperationFactory.dispose();
            sandbox.dispose();
        }
    }

    private SvnInfo runInfo(SvnOperationFactory svnOperationFactory, SVNURL url, ISVNAuthenticationManager authenticationManager) throws SVNException {
        svnOperationFactory =  new SvnOperationFactory();
        try {
            svnOperationFactory.setAuthenticationManager(authenticationManager);

            final SvnGetInfo getInfo = svnOperationFactory.createGetInfo();
            getInfo.setSingleTarget(SvnTarget.fromURL(url));
            return getInfo.run();
        } finally {
            svnOperationFactory.dispose();
        }
    }

    private DefaultSVNAuthenticationManager createAuthenticationManager(File configDirectory, final boolean storageAllowed, final String username, final String password) {
        final DefaultSVNAuthenticationManager defaultAuthenticationManager = new DefaultSVNAuthenticationManager(configDirectory, storageAllowed, null, null);
        defaultAuthenticationManager.setAuthenticationProvider(new ISVNAuthenticationProvider() {
            public SVNAuthentication requestClientAuthentication(String kind, SVNURL url, String realm, SVNErrorMessage errorMessage, SVNAuthentication previousAuth, boolean authMayBeStored) {
                if (errorMessage != null && errorMessage.getErrorCode() == SVNErrorCode.RA_NOT_AUTHORIZED) {
                    return null;
                }
                return new SVNPasswordAuthentication(username, password, storageAllowed, url, false);
            }

            public int acceptServerAuthentication(SVNURL url, String realm, Object certificate, boolean resultMayBeStored) {
                return ACCEPTED;
            }
        });
        return defaultAuthenticationManager;
    }

    private String getTestName() {
        return "CredentialsTest";
    }
}
