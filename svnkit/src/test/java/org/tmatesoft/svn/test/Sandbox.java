package org.tmatesoft.svn.test;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.internal.wc2.SvnWcGeneration;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.wc.SVNClientManager;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.admin.SVNAdminClient;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
    private final List<ApacheProcess> apacheProcesses;
    private final List<SvnserveProcess> svnserveProcesses;
    private final Map<SVNURL, File> urlToRepositoryRoot;

    private Sandbox(String testName, TestOptions testOptions) {
        this.testName = testName;
        this.testOptions = testOptions;
        this.workingCopies = new ArrayList<WorkingCopy>();
        this.apacheProcesses = new ArrayList<ApacheProcess>();
        this.svnserveProcesses = new ArrayList<SvnserveProcess>();
        this.urlToRepositoryRoot = new HashMap<SVNURL, File>();
    }

    public void dispose() {
        for (WorkingCopy workingCopy : workingCopies) {
            workingCopy.dispose();
        }
        for (ApacheProcess apacheProcess : apacheProcesses) {
            ApacheProcess.shutdown(apacheProcess);
        }
        for (SvnserveProcess svnserveProcess : svnserveProcesses) {
            SvnserveProcess.shutdown(svnserveProcess);
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

    public WorkingCopy checkoutNewWorkingCopy(SVNURL url) throws SVNException {
        return checkoutNewWorkingCopy(url, SVNRevision.HEAD.getNumber());
    }

    public WorkingCopy checkoutNewWorkingCopy(SVNURL repositoryUrl, long revision) throws SVNException {
        return checkoutNewWorkingCopy(repositoryUrl, revision, true, TestUtil.getDefaultWcGeneration());
    }

    public WorkingCopy checkoutNewWorkingCopy(SVNURL repositoryUrl, long revision, boolean ignoreExternals, SvnWcGeneration wcGeneration) throws SVNException {
        final WorkingCopy workingCopy = new WorkingCopy(getTestOptions(), createWorkingCopyDirectory());
        workingCopy.setWcGeneration(wcGeneration);
        workingCopy.checkoutRevision(repositoryUrl, revision, ignoreExternals);
        workingCopies.add(workingCopy);
        return workingCopy;
    }

    public SVNURL getFSFSAccessUrl(SVNURL url) {
        if ("file".equals(url.getProtocol())) {
            return url;
        }
        final File repositoryRoot = urlToRepositoryRoot.get(url);
        try {
            return SVNURL.fromFile(repositoryRoot);
        } catch (SVNException e) {
            return null;
        }
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
        createSvnRepository(repositoryDirectory);
        SVNURL url = SVNURL.fromFile(repositoryDirectory);
        urlToRepositoryRoot.put(url, repositoryDirectory);
        return url;
    }

    public SVNURL createSvnRepositoryWithDavAccess() throws SVNException {
        return createSvnRepositoryWithDavAccess(null);
    }

    public SVNURL createSvnRepositoryWithSvnAccess() throws SVNException {
        return createSvnRepositoryWithSvnAccess(null);
    }

    public SVNURL createSvnRepositoryWithDavAccess(Map<String, String> loginToPassword) throws SVNException {
        final File repositoryDirectory = createDirectory("svn.repo");
        createSvnRepository(repositoryDirectory);
        final ApacheProcess apacheProcess = runApacheForSvnRepository(repositoryDirectory, loginToPassword);
        SVNURL url = apacheProcess.getUrl();
        urlToRepositoryRoot.put(url, repositoryDirectory);
        return url;
    }

    public SVNURL createSvnRepositoryWithSvnAccess(Map<String, String> loginToPassword) throws SVNException {
        final File repositoryDirectory = createDirectory("svn.repo");
        createSvnRepository(repositoryDirectory);
        final SvnserveProcess svnserveProcess = runSvnserveForSvnRepository(repositoryDirectory, loginToPassword);
        SVNURL url = svnserveProcess.getUrl();
        urlToRepositoryRoot.put(url, repositoryDirectory);
        return url;
    }

    public File createFailingHook(SVNURL url, String hookName) throws SVNException {
        final String failingHookContents = TestUtil.getFailingHookContents();
        return createHook(url, hookName, failingHookContents);
    }

    public File createHook(SVNURL url, String hookName, String failingHookContents) throws SVNException {
        final File repositoryRoot = urlToRepositoryRoot.get(url);
        final File hookFile = TestUtil.getHookFile(repositoryRoot, hookName);
        TestUtil.writeFileContentsString(hookFile, failingHookContents);
        SVNFileUtil.setExecutable(hookFile, true);
        return hookFile;
    }

    public File writeActiveAuthzContents(SVNURL url, String contents) throws SVNException {
        //maybe url is served by apache?
        final ApacheProcess apacheProcess = findApacheProcess(url);
        if (apacheProcess != null) {

            final File activeAuthzFile = apacheProcess.getAuthzFile();
            if (activeAuthzFile == null) {
                return null;
            }
            TestUtil.writeFileContentsString(activeAuthzFile, contents);
            apacheProcess.reload(); //reload apache configuration
            return activeAuthzFile;
        }

        //maybe url is served by svnserve?
        final SvnserveProcess svnserveProcess = findSvnserveProcess(url);
        if (svnserveProcess != null) {

            final File activeAuthzFile = svnserveProcess.getAuthzFile();
            if (activeAuthzFile == null) {
                return null;
            }

            TestUtil.writeFileContentsString(activeAuthzFile, contents);
            svnserveProcess.reload();
            return activeAuthzFile;
        }

        //authz for FSFS is useless
        return null;
    }

    private ApacheProcess findApacheProcess(SVNURL url) {
        for (ApacheProcess apacheProcess : apacheProcesses) {
            if (apacheProcess.getUrl().equals(url)) {
                return apacheProcess;
            }
        }
        return null;
    }

    private SvnserveProcess findSvnserveProcess(SVNURL url) {
        for (SvnserveProcess svnserveProcess : svnserveProcesses) {
            if (svnserveProcess.getUrl().equals(url)) {
                return svnserveProcess;
            }
        }
        return null;
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

    private ApacheProcess runApacheForSvnRepository(File repositoryDirectory, Map<String, String> loginToPassword) throws SVNException {
        final ApacheProcess apacheProcess = ApacheProcess.run(getTestOptions(), repositoryDirectory, loginToPassword);
        apacheProcesses.add(apacheProcess);
        return apacheProcess;
    }

    private SvnserveProcess runSvnserveForSvnRepository(File repositoryDirectory, Map<String, String> loginToPassword) throws SVNException {
        final SvnserveProcess svnserveProcess = SvnserveProcess.run(getTestOptions(), repositoryDirectory, loginToPassword);
        svnserveProcesses.add(svnserveProcess);
        return svnserveProcess;
    }

    private void createSvnRepository(File repositoryDirectory) throws SVNException {
        final SVNClientManager clientManager = SVNClientManager.newInstance();
        try {
            SVNAdminClient adminClient = clientManager.getAdminClient();
            adminClient.doCreateRepository(repositoryDirectory, null, true, false);

        } finally {
            clientManager.dispose();
        }
    }
}
