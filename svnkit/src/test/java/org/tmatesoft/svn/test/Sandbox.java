package org.tmatesoft.svn.test;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;

public class Sandbox {

    public static Sandbox createWithCleanup(String testName) throws SVNException {
        final TestOptions testOptions = TestOptions.getInstance();

        final Sandbox sandbox = new Sandbox(testName, testOptions);
        sandbox.cleanup();
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
        final WorkingCopy workingCopy = new WorkingCopy(getTestOptions(), createWorkingCopyDirectory());
        workingCopy.checkoutLatestRevision(getRepositoryUrl());
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

    private File createWorkingCopyDirectory() {
        return TestUtil.createDirectory(getTestDirectory(), "wc").getAbsoluteFile();
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
