package org.tmatesoft.svn.test;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import org.junit.Assume;
import org.junit.Test;
import org.tmatesoft.svn.core.SVNCancelException;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.auth.BasicAuthenticationManager;
import org.tmatesoft.svn.core.wc.ISVNEventHandler;
import org.tmatesoft.svn.core.wc.SVNEvent;
import org.tmatesoft.svn.core.wc2.SvnCheckout;
import org.tmatesoft.svn.core.wc2.SvnOperationFactory;
import org.tmatesoft.svn.core.wc2.SvnSetLock;
import org.tmatesoft.svn.core.wc2.SvnTarget;
import org.tmatesoft.svn.core.wc2.SvnUnlock;

public class DavLockTest {

    @Test
    public void testUnlockWithAnotherUserDoesntResultIntoException() throws Exception {
        final TestOptions options = TestOptions.getInstance();
        Assume.assumeTrue(areAllApacheOptionsSpecified(options));

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testUnlockWithAnotherUserDoesntResultIntoException", options);
        try {
            BasicAuthenticationManager authenticationManager1 = new BasicAuthenticationManager("user1", "password1");
            BasicAuthenticationManager authenticationManager2 = new BasicAuthenticationManager("user2", "password2");

            final Map<String, String> loginToPassword = new HashMap<String, String>();
            loginToPassword.put("user1", "password1");
            loginToPassword.put("user2", "password2");

            final SVNURL url = sandbox.createSvnRepositoryWithDavAccess(loginToPassword);

            final CommitBuilder commitBuilder = new CommitBuilder(url);
            commitBuilder.setAuthenticationManager(authenticationManager1);
            commitBuilder.addFile("file");
            commitBuilder.commit();

            final File workingCopyDirectory = sandbox.createDirectory("wc");

            //checkout with user1
            svnOperationFactory.setAuthenticationManager(authenticationManager1);

            final SvnCheckout checkout = svnOperationFactory.createCheckout();
            checkout.setSource(SvnTarget.fromURL(url));
            checkout.setSingleTarget(SvnTarget.fromFile(workingCopyDirectory));
            checkout.run();

            final File file = new File(workingCopyDirectory, "file");

            //lock with user1
            final SvnSetLock setLock = svnOperationFactory.createSetLock();
            setLock.setLockMessage("Locked");
            setLock.setSingleTarget(SvnTarget.fromFile(file));
            setLock.run();

            //unlock with user2
            svnOperationFactory.setAuthenticationManager(authenticationManager2);

            //create event handler that would check for failed unlock
            svnOperationFactory.setEventHandler(createEventHandler());

            final SvnUnlock unlock = svnOperationFactory.createUnlock();
            unlock.setSingleTarget(SvnTarget.fromFile(file));
            unlock.run();

            //no exception should be thrown
            //TODO: check event handler for correct event received

        } finally {
            svnOperationFactory.dispose();
            sandbox.dispose();
        }
    }

    public String getTestName() {
        return "DavLockTest";
    }

    private static boolean areAllApacheOptionsSpecified(TestOptions testOptions) {
        return testOptions.getApacheRoot() != null && testOptions.getApacheCtlCommand() != null;
    }

    private ISVNEventHandler createEventHandler() {
        return new ISVNEventHandler() {
            public void handleEvent(SVNEvent event, double progress) throws SVNException {
            }

            public void checkCancelled() throws SVNCancelException {
            }
        };
    }
}
