package org.tmatesoft.svn.test;

import org.junit.Assume;
import org.junit.Ignore;
import org.junit.Test;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.auth.BasicAuthenticationManager;
import org.tmatesoft.svn.core.wc2.SvnCheckout;
import org.tmatesoft.svn.core.wc2.SvnCommit;
import org.tmatesoft.svn.core.wc2.SvnOperationFactory;
import org.tmatesoft.svn.core.wc2.SvnTarget;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

@Ignore("Incomplete")
public class AuthTest {
    @Test
    public void testDavCommitFailsBecauseOfPathBasedAuth() throws Exception {
        final TestOptions options = TestOptions.getInstance();
        Assume.assumeTrue(TestUtil.areAllApacheOptionsSpecified(options));

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testDavCommitFailsBecauseOfPathBasedAuth", options);
        try {
            BasicAuthenticationManager authenticationManager1 = new BasicAuthenticationManager("user1", "password1");

            final Map<String, String> loginToPassword = new HashMap<String, String>();
            loginToPassword.put("user1", "password1"); //only user1 is allowed

            final SVNURL url = sandbox.createSvnRepositoryWithDavAccess(loginToPassword);

            final CommitBuilder commitBuilder = new CommitBuilder(url);
            commitBuilder.setAuthenticationManager(authenticationManager1);
            commitBuilder.addFile("directory/file");
            commitBuilder.commit();

            final File workingCopyDirectory = sandbox.createDirectory("wc");

            //checkout with user1
            svnOperationFactory.setAuthenticationManager(authenticationManager1);

            final SvnCheckout checkout = svnOperationFactory.createCheckout();
            checkout.setSource(SvnTarget.fromURL(url));
            checkout.setSingleTarget(SvnTarget.fromFile(workingCopyDirectory));
            checkout.run();

            final File file = new File(workingCopyDirectory, "directory/file");
            TestUtil.writeFileContentsString(file, "new contents");

            final String readOnly =
                    "[/]" + "\n" +
                            "*=rw" + "\n" +
                            "[/directory]" + "\n" +
                            "*=r" + "\n";

            sandbox.writeActiveAuthzContents(url, readOnly);

            final SvnCommit commit = svnOperationFactory.createCommit();
            commit.setSingleTarget(SvnTarget.fromFile(workingCopyDirectory));
            commit.run();

        } finally {
            svnOperationFactory.dispose();
            sandbox.dispose();
        }
    }

    @Test
    public void test() throws Exception {
        final TestOptions options = TestOptions.getInstance();
        Assume.assumeTrue(TestUtil.areAllApacheOptionsSpecified(options));

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".test", options);
        try {
            BasicAuthenticationManager authenticationManager1 = new BasicAuthenticationManager("user1", "password1");

            final Map<String, String> loginToPassword = new HashMap<String, String>();
            loginToPassword.put("user1", "password1"); //only user1 is allowed

            final SVNURL url = sandbox.createSvnRepositoryWithDavAccess(loginToPassword);

            final CommitBuilder commitBuilder = new CommitBuilder(url);
            commitBuilder.setAuthenticationManager(authenticationManager1);
            commitBuilder.addFile("directory/file");
            commitBuilder.commit();

            final File workingCopyDirectory = sandbox.createDirectory("wc");

            //checkout with user1
            svnOperationFactory.setAuthenticationManager(authenticationManager1);

            final SvnCheckout checkout = svnOperationFactory.createCheckout();
            checkout.setSource(SvnTarget.fromURL(url));
            checkout.setSingleTarget(SvnTarget.fromFile(workingCopyDirectory));
            checkout.run();

            final File directory = new File(workingCopyDirectory, "directory");
            final File file = new File(directory, "file");
            TestUtil.writeFileContentsString(file, "new contents");

//            final SvnScheduleForAddition scheduleForAddition = svnOperationFactory.createScheduleForAddition();
//            scheduleForAddition.setSingleTarget(SvnTarget.fromFile(file));
//            scheduleForAddition.run();
//
//            final SvnSetProperty setProperty = svnOperationFactory.createSetProperty();
//            setProperty.setPropertyName("test");
//            setProperty.setPropertyValue(SVNPropertyValue.create("value"));
//            setProperty.setSingleTarget(SvnTarget.fromFile(directory));
//            setProperty.run();

            final String readOnly =
                    "[/]" + "\n" +
                            "*=rw" + "\n" +
                            "[/directory]" + "\n" +
                            "*=r" + "\n";

            sandbox.writeActiveAuthzContents(url, readOnly);

            final SvnCommit commit = svnOperationFactory.createCommit();
            commit.setSingleTarget(SvnTarget.fromFile(workingCopyDirectory));
            commit.run();

        } finally {
            svnOperationFactory.dispose();
            sandbox.dispose();
        }
    }

    private String getTestName() {
        return "AuthTest";
    }
}
