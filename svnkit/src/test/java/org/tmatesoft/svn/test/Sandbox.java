package org.tmatesoft.svn.test;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.wc.SVNClientManager;
import org.tmatesoft.svn.core.wc.admin.SVNAdminClient;

public class Sandbox {

    public static Sandbox create(String testName, TestOptions testOptions, boolean cleanup) throws SVNException {
        final Sandbox sandbox = new Sandbox(testName, testOptions);
        if (cleanup) {
            sandbox.cleanup();
        }
        return sandbox;
    }

    private final String testName;
    private final TestOptions testOptions;
    private final List<WorkingCopy> workingCopies;

    private File testDirectory;

    private Sandbox(String testName, TestOptions testOptions) {
        this.testName = testName;
        this.testOptions = testOptions;
        this.workingCopies = new ArrayList<WorkingCopy>();
    }

    public void dispose() {
        for (WorkingCopy workingCopy : workingCopies) {
            workingCopy.dispose();
        }
    }

    public WorkingCopy checkoutWorkingCopy() throws SVNException {
        return checkoutWorkingCopy(getRepositoryUrl());
    }

    public WorkingCopy checkoutWorkingCopy(SVNURL repositoryUrl) throws SVNException {
        final WorkingCopy workingCopy = new WorkingCopy(getTestOptions(), getWorkingCopyDirectory());
        workingCopy.checkoutLatestRevision(repositoryUrl);
        workingCopies.add(workingCopy);
        return workingCopy;
    }

    private SVNURL getRepositoryUrl() {
        final SVNURL repositoryUrl = getTestOptions().getRepositoryUrl();

        if (repositoryUrl == null) {
            throw new RuntimeException("Unable to start the test: repository URL is not specified.");
        }

        return repositoryUrl;
    }

    private String getTestName() {
        return testName;
    }

    private File getTestDirectory() {
        if (testDirectory == null) {
            testDirectory = createTestDirectory();
        }
        return testDirectory;
    }

    public SVNURL createSvnRepository() throws SVNException {
        final File repositoryDirectory = createDirectory("svn.repo");

        final SVNClientManager clientManager = SVNClientManager.newInstance();
        try {
            SVNAdminClient adminClient = clientManager.getAdminClient();
            adminClient.doCreateRepository(repositoryDirectory, null, true, false);
            return SVNURL.fromFile(repositoryDirectory);
        } finally {
            clientManager.dispose();
        }
    }

    private File getWorkingCopyDirectory() {
        return new File(getTestDirectory(), "wc");
    }

    private File createDirectory(String suggestedName) {
        return TestUtil.createDirectory(getTestDirectory(), suggestedName).getAbsoluteFile();
    }

    private File createTestDirectory() {
        final File testDirectory = new File(getTempDirectory(), getTestName());
        testDirectory.mkdirs();
        return testDirectory;
    }

    private TestOptions getTestOptions() {
        return testOptions;
    }

    private File getTempDirectory() {
        return getTestOptions().getTempDirectory();
    }

    private void cleanup() throws SVNException {
        SVNFileUtil.deleteAll(getTestDirectory(), null);
    }
}
