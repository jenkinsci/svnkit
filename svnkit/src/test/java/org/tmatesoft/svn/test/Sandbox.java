package org.tmatesoft.svn.test;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.internal.wc2.SvnWcGeneration;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.wc.SVNClientManager;
import org.tmatesoft.svn.core.wc.admin.SVNAdminClient;
import org.tmatesoft.svn.core.wc2.SvnOperationFactory;

public class Sandbox {

    public static Sandbox createWithoutCleanup(String testName, TestOptions testOptions) throws SVNException {
        return create(testName, testOptions, false);
    }

    public static Sandbox createWithCleanup(String testName, TestOptions testOptions) throws SVNException {
        return create(testName, testOptions, true);
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
        return checkoutWorkingCopyOrUpdateTo(SVNRepository.INVALID_REVISION);
    }

    public WorkingCopy checkoutWorkingCopyOrUpdateTo(long revision) throws SVNException {
        if (getWorkingCopyDirectory().exists()) {
            final WorkingCopy workingCopy = openExistingWorkingCopy();
            workingCopy.updateToRevision(revision);
            return workingCopy;
        } else {
            return checkoutOrUpdateExistingWorkingCopy(getRepositoryUrl(), revision);
        }
    }

    private WorkingCopy openExistingWorkingCopy() {
        final WorkingCopy workingCopy = new WorkingCopy(getTestOptions(), getWorkingCopyDirectory());
        workingCopy.setRepositoryUrl(getRepositoryUrl());
        workingCopies.add(workingCopy);
        return workingCopy;
    }

    public WorkingCopy checkoutOrUpdateExistingWorkingCopy(SVNURL repositoryUrl, long revision) throws SVNException {
        return checkoutOrUpdateExistingWorkingCopy(repositoryUrl, revision, SvnWcGeneration.V17);
    }
    
    public WorkingCopy checkoutOrUpdateExistingWorkingCopy(SVNURL repositoryUrl, long revision, SvnWcGeneration wcGeneration) throws SVNException {
        final WorkingCopy workingCopy = new WorkingCopy(getTestOptions(), getWorkingCopyDirectory());
        workingCopy.setWcGeneration(wcGeneration);
        workingCopy.checkoutRevision(repositoryUrl, revision);
        workingCopies.add(workingCopy);
        return workingCopy;
    }

    public WorkingCopy checkoutNewWorkingCopy(SVNURL repositoryUrl, long revision) throws SVNException {
        return checkoutNewWorkingCopy(repositoryUrl, revision, getDefaultWcGeneration());
    }

    private SvnWcGeneration getDefaultWcGeneration() {
        return new SvnOperationFactory().getPrimaryWcGeneration();
    }
    
    public WorkingCopy checkoutNewWorkingCopy(SVNURL repositoryUrl, long revision, SvnWcGeneration wcGeneration) throws SVNException {
        final WorkingCopy workingCopy = new WorkingCopy(getTestOptions(), createWorkingCopyDirectory());
        workingCopy.setWcGeneration(wcGeneration);
        workingCopy.checkoutRevision(repositoryUrl, revision);
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

    private File createWorkingCopyDirectory() {
        return createDirectory("wc");
    }

    public File createDirectory(String suggestedName) {
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

    private static Sandbox create(String testName, TestOptions testOptions, boolean cleanup) throws SVNException {
        final Sandbox sandbox = new Sandbox(testName, testOptions);
        if (cleanup) {
            sandbox.cleanup();
        }
        return sandbox;
    }
}
