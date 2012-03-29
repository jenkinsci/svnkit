package org.tmatesoft.svn.test;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import junit.framework.Assert;
import org.junit.Assume;
import org.junit.Ignore;
import org.junit.Test;
import org.tmatesoft.svn.core.SVNCancelException;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.auth.BasicAuthenticationManager;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.wc.ISVNEventHandler;
import org.tmatesoft.svn.core.wc.SVNEvent;
import org.tmatesoft.svn.core.wc.SVNEventAction;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc2.SvnCheckout;
import org.tmatesoft.svn.core.wc2.SvnOperationFactory;
import org.tmatesoft.svn.core.wc2.SvnSetLock;
import org.tmatesoft.svn.core.wc2.SvnTarget;
import org.tmatesoft.svn.core.wc2.SvnUnlock;

public class DavLockTest {

    @Test
    public void testUnlockWithAnotherUserDoesntResultIntoException() throws Exception {
        final TestOptions options = TestOptions.getInstance();
        Assume.assumeTrue(TestUtil.areAllApacheOptionsSpecified(options));

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
            final LockEventHandler lockEventHandler = createUnlockEventHandler();

            svnOperationFactory.setEventHandler(lockEventHandler);

            final SvnUnlock unlock = svnOperationFactory.createUnlock();
            unlock.setSingleTarget(SvnTarget.fromFile(file));
            unlock.run();

            final List<SVNEvent> events = lockEventHandler.events;
            Assert.assertEquals(1, events.size());
            SVNErrorMessage errorMessage = events.get(0).getErrorMessage();
            final String path = (String) errorMessage.getRelatedObjects()[0];
            Assert.assertEquals(SVNErrorCode.FS_LOCK_OWNER_MISMATCH, errorMessage.getErrorCode());
            Assert.assertEquals("file", path);
        } finally {
            svnOperationFactory.dispose();
            sandbox.dispose();
        }
    }

    @Test
    public void testLockingNonExistingInHeadPathIsNotAllowed() throws Exception {
        final TestOptions options = TestOptions.getInstance();
        Assume.assumeTrue(TestUtil.areAllApacheOptionsSpecified(options));

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testLockingNonExistingInHeadPathIsNotAllowed", options);
        try {
            BasicAuthenticationManager authenticationManager1 = new BasicAuthenticationManager("user1", "password1");

            final Map<String, String> loginToPassword = new HashMap<String, String>();
            loginToPassword.put("user1", "password1");

            final SVNURL url = sandbox.createSvnRepositoryWithDavAccess(loginToPassword);

            final CommitBuilder commitBuilder1 = new CommitBuilder(url);
            commitBuilder1.setAuthenticationManager(authenticationManager1);
            commitBuilder1.addFile("file");
            commitBuilder1.commit();

            final CommitBuilder commitBuilder2 = new CommitBuilder(url);
            commitBuilder2.setAuthenticationManager(authenticationManager1);
            commitBuilder2.delete("file");
            commitBuilder2.commit();

            final File workingCopyDirectory = sandbox.createDirectory("wc");

            //checkout with user1
            svnOperationFactory.setAuthenticationManager(authenticationManager1);

            final SvnCheckout checkout = svnOperationFactory.createCheckout();
            checkout.setRevision(SVNRevision.create(1));
            checkout.setSource(SvnTarget.fromURL(url));
            checkout.setSingleTarget(SvnTarget.fromFile(workingCopyDirectory));
            checkout.run();

            final File file = new File(workingCopyDirectory, "file");

            //create event handler that would check for failed unlock
            final LockEventHandler lockEventHandler = createUnlockEventHandler();

            svnOperationFactory.setEventHandler(lockEventHandler);

            //lock with user1
            final SvnSetLock setLock = svnOperationFactory.createSetLock();
            setLock.setLockMessage("Locked");
            setLock.setSingleTarget(SvnTarget.fromFile(file));
            setLock.run();

            final List<SVNEvent> events = lockEventHandler.events;
            Assert.assertEquals(1, events.size());
            SVNErrorMessage errorMessage = events.get(0).getErrorMessage();
            Assert.assertEquals(SVNErrorCode.FS_OUT_OF_DATE, errorMessage.getErrorCode());

            final Integer httpCode = (Integer) errorMessage.getRelatedObjects()[0];
            final String path = (String) errorMessage.getRelatedObjects()[1];

            Assert.assertEquals(405, httpCode.intValue());
            Assert.assertEquals("Method Not Allowed", path);
        } finally {
            svnOperationFactory.dispose();
            sandbox.dispose();
        }
    }

    @Test
    public void testFailingUnlockHookBlocksUnlocking() throws Exception {
        final TestOptions options = TestOptions.getInstance();
        Assume.assumeTrue(TestUtil.areAllApacheOptionsSpecified(options));

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testFailingUnlockHookBlocksUnlocking", options);
        try {
            BasicAuthenticationManager authenticationManager1 = new BasicAuthenticationManager("user1", "password1");

            final Map<String, String> loginToPassword = new HashMap<String, String>();
            loginToPassword.put("user1", "password1");

            final SVNURL url = sandbox.createSvnRepositoryWithDavAccess(loginToPassword);

            final CommitBuilder commitBuilder = new CommitBuilder(url);
            commitBuilder.setAuthenticationManager(authenticationManager1);
            commitBuilder.addFile("file");
            commitBuilder.commit();

            sandbox.createFailingHook(url, "pre-unlock");

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

            //create event handler that would check for failed unlock
            final LockEventHandler lockEventHandler = createUnlockEventHandler();

            svnOperationFactory.setEventHandler(lockEventHandler);

            final SvnUnlock unlock = svnOperationFactory.createUnlock();
            unlock.setSingleTarget(SvnTarget.fromFile(file));
            try {
                unlock.run();
                Assert.fail("An exception should be thrown");
            } catch (SVNException e) {
                //expected
                e.printStackTrace();
                Assert.assertEquals(SVNErrorCode.RA_DAV_REQUEST_FAILED, e.getErrorMessage().getErrorCode());
            }
        } finally {
            svnOperationFactory.dispose();
            sandbox.dispose();
        }
    }

    @Ignore
    @Test
    public void testIncorrectLockHookBlocksLocking() throws Exception {
        final TestOptions options = TestOptions.getInstance();
        Assume.assumeTrue(TestUtil.areAllApacheOptionsSpecified(options));

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testIncorrectLockHookBlocksLocking", options);
        try {
            BasicAuthenticationManager authenticationManager1 = new BasicAuthenticationManager("user1", "password1");

            final Map<String, String> loginToPassword = new HashMap<String, String>();
            loginToPassword.put("user1", "password1");

            final SVNURL url = sandbox.createSvnRepositoryWithDavAccess(loginToPassword);

            final CommitBuilder commitBuilder = new CommitBuilder(url);
            commitBuilder.setAuthenticationManager(authenticationManager1);
            commitBuilder.addFile("file");
            commitBuilder.commit();

            sandbox.createHook(url, "pre-lock", getIncorrectLockHookContents());

            final File workingCopyDirectory = sandbox.createDirectory("wc");

            //checkout with user1
            svnOperationFactory.setAuthenticationManager(authenticationManager1);

            final SvnCheckout checkout = svnOperationFactory.createCheckout();
            checkout.setSource(SvnTarget.fromURL(url));
            checkout.setSingleTarget(SvnTarget.fromFile(workingCopyDirectory));
            checkout.run();

            final File file = new File(workingCopyDirectory, "file");

            //create event handler that would check for failed unlock
            final LockEventHandler lockEventHandler = createUnlockEventHandler();

            svnOperationFactory.setEventHandler(lockEventHandler);

            //lock with user1
            final SvnSetLock setLock = svnOperationFactory.createSetLock();
            setLock.setLockMessage("Locked");
            setLock.setSingleTarget(SvnTarget.fromFile(file));
            setLock.run();

            //TODO: finish the test
        } finally {
            svnOperationFactory.dispose();
            sandbox.dispose();
        }
    }

    private String getIncorrectLockHookContents() {
        final String token = "token";
        if (SVNFileUtil.isWindows) {
            return "@echo off" + "\r\n" +
                    "echo|set /p=" + token + "\r\n" +
                    "exit 0" + "\r\n";
        } else {
            return "#!/bin/sh" + "\n" +
                    "echo -n " + token + "\n" +
                    "exit 0" + "\n";
        }
    }

    public String getTestName() {
        return "DavLockTest";
    }

    private LockEventHandler createUnlockEventHandler() {
        return new LockEventHandler();
    }

    private static class LockEventHandler implements ISVNEventHandler {

        private final List<SVNEvent> events;

        private LockEventHandler() {
            events = new ArrayList<SVNEvent>();
        }

        public void handleEvent(SVNEvent event, double progress) throws SVNException {
            if (event.getAction() == SVNEventAction.LOCK_FAILED || event.getAction() == SVNEventAction.UNLOCK_FAILED) {
                events.add(event);
            }
        }

        public void checkCancelled() throws SVNCancelException {
        }
    }
}
