package org.tmatesoft.svn.test;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.tmatesoft.svn.core.SVNCommitInfo;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNPropertyValue;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.wc.SVNFileListUtil;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.internal.wc17.db.ISVNWCDb;
import org.tmatesoft.svn.core.internal.wc2.SvnWcGeneration;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc2.SvnCheckout;
import org.tmatesoft.svn.core.wc2.SvnCommit;
import org.tmatesoft.svn.core.wc2.SvnCopy;
import org.tmatesoft.svn.core.wc2.SvnCopySource;
import org.tmatesoft.svn.core.wc2.SvnGetStatus;
import org.tmatesoft.svn.core.wc2.SvnOperationFactory;
import org.tmatesoft.svn.core.wc2.SvnRevert;
import org.tmatesoft.svn.core.wc2.SvnScheduleForAddition;
import org.tmatesoft.svn.core.wc2.SvnScheduleForRemoval;
import org.tmatesoft.svn.core.wc2.SvnSetProperty;
import org.tmatesoft.svn.core.wc2.SvnStatus;
import org.tmatesoft.svn.core.wc2.SvnTarget;
import org.tmatesoft.svn.core.wc2.SvnUpdate;

public class WorkingCopy {

    private final TestOptions testOptions;
    private final File workingCopyDirectory;

    private SvnOperationFactory clientManager;
    private long currentRevision;

    private PrintWriter logger;
    private SVNURL repositoryUrl;
    private SvnWcGeneration wcGeneration;

    public WorkingCopy(TestOptions testOptions, File workingCopyDirectory) {
        this.testOptions = testOptions;
        this.workingCopyDirectory = workingCopyDirectory;
        this.currentRevision = -1;
    }
    
    public void setWcGeneration(SvnWcGeneration generation) {
        this.wcGeneration = generation;
        if (clientManager != null) {
            clientManager.setPrimaryWcGeneration(wcGeneration);
        }
    }

    public void setRepositoryUrl(SVNURL repositoryUrl) {
        this.repositoryUrl = repositoryUrl;
    }
    
    public File deleteFile(String relativePath) throws SVNException {
        SVNFileUtil.deleteAll(getFile(relativePath), true);
        return getFile(relativePath);
    }

    public File changeFileContents(String relativePath, String contents) throws SVNException {
        TestUtil.writeFileContentsString(getFile(relativePath), contents);
        return getFile(relativePath);
    }

    public File getFile(String relativePath) {
        return new File(getWorkingCopyDirectory(), relativePath);
    }

    public long checkoutLatestRevision(SVNURL repositoryUrl) throws SVNException {
        return checkoutRevision(repositoryUrl, SVNRepository.INVALID_REVISION);
    }
    public long checkoutRevision(SVNURL repositoryUrl, long revision) throws SVNException {
        return checkoutRevision(repositoryUrl, revision, true);
    }

    public long checkoutRevision(SVNURL repositoryUrl, long revision, boolean ignoreExternals) throws SVNException {
        disposeLogger();
        SVNFileUtil.deleteFile(getLogFile());

        beforeOperation();

        log("Checking out " + repositoryUrl);

        final SvnCheckout checkout = getOperationFactory().createCheckout();
        checkout.setIgnoreExternals(ignoreExternals);

        this.repositoryUrl = repositoryUrl;

        final boolean isWcExists = getWorkingCopyDirectory().isDirectory();
        try {
            checkout.setSingleTarget(SvnTarget.fromFile(getWorkingCopyDirectory()));
            checkout.setSource(SvnTarget.fromURL(repositoryUrl, revision >= 0 ? SVNRevision.create(revision) : SVNRevision.HEAD));
            checkout.setRevision(revision >= 0 ? SVNRevision.create(revision) : SVNRevision.HEAD);
            checkout.setDepth(SVNDepth.INFINITY);
            checkout.setAllowUnversionedObstructions(true);
            currentRevision = checkout.run();
        } catch (Throwable th) {
            if (isWcExists) {
                SVNFileUtil.deleteAll(getWorkingCopyDirectory(), true);
                checkout.run();
            } else {
                wrapThrowable(th);
            }
                
        }

        log("Checked out " + repositoryUrl);
        checkNativeStatusShowsNoChanges();
        afterOperation();

        return currentRevision;
    }

    public void updateToRevision(long revision) throws SVNException {
        beforeOperation();

        log("Updating to revision " + revision);
        SvnUpdate up = getOperationFactory().createUpdate();
        up.setIgnoreExternals(true);
        up.setSingleTarget(SvnTarget.fromFile(getWorkingCopyDirectory()));
        up.setRevision(revision >= 0 ? SVNRevision.create(revision) : SVNRevision.HEAD);
        up.setDepth(SVNDepth.INFINITY);
        up.setAllowUnversionedObstructions(true);
        try {
            currentRevision = up.run()[0];
        } catch (Throwable th) {
            wrapThrowable(th);
        }

        log("Updated to revision " + currentRevision);
        checkNativeStatusShowsNoChanges();
        afterOperation();
    }

    public File findAnyDirectory() {
        final File workingCopyDirectory = getWorkingCopyDirectory();
        final File[] files = SVNFileListUtil.listFiles(workingCopyDirectory);
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory() && !file.getName().equals(SVNFileUtil.getAdminDirectoryName())) {
                    return file;
                }
            }
        }

        throwException("The repository contains no directories, run the tests with another repository");
        return null;
    }

    public File findAnotherDirectory(File directory) {
        final File workingCopyDirectory = getWorkingCopyDirectory();
        final File[] files = SVNFileListUtil.listFiles(workingCopyDirectory);
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory() && !file.getName().equals(SVNFileUtil.getAdminDirectoryName()) && !file.getAbsolutePath().equals(directory.getAbsolutePath())) {
                    return file;
                }
            }
        }

        throwException("The repository root should contain at least two directories, please run the tests with another repository");
        return null;
    }

    public void copyAsChild(File directory, File anotherDirectory) throws SVNException {
        beforeOperation();

        log("Copying " + directory + " as a child of " + anotherDirectory);

        final SvnCopy copyClient = getOperationFactory().createCopy();
        copyClient.addCopySource(SvnCopySource.create(SvnTarget.fromFile(directory), SVNRevision.WORKING));
        copyClient.setSingleTarget(SvnTarget.fromFile(anotherDirectory));
        copyClient.setFailWhenDstExists(false);
        try {
            copyClient.run();
        } catch (Throwable th) {
            wrapThrowable(th);
        }

        log("Copied " + directory + " as a child of " + anotherDirectory);

        afterOperation();
    }

    public long commit(String commitMessage) throws SVNException {
        beforeOperation();

        log("Committing ");

        final SvnCommit commitClient = getOperationFactory().createCommit();
        commitClient.setSingleTarget(SvnTarget.fromFile(getWorkingCopyDirectory()));
        commitClient.setCommitMessage(commitMessage);
        commitClient.setDepth(SVNDepth.INFINITY);
        commitClient.setForce(true);
        
        SVNCommitInfo commitInfo = null;
        try {
            commitInfo = commitClient.run();
        } catch (Throwable th) {
            wrapThrowable(th);
        }

        log("Committed revision " + (commitInfo == null ? "none" : commitInfo.getNewRevision()));
        checkNativeStatusShowsNoChanges();
        afterOperation();

        return commitInfo == null ? -1 : commitInfo.getNewRevision();
    }

    public void add(File file) throws SVNException {
        beforeOperation();

        log("Adding " + file);

        SvnScheduleForAddition add = getOperationFactory().createScheduleForAddition();
        add.setSingleTarget(SvnTarget.fromFile(file));
        add.setDepth(SVNDepth.INFINITY);
        add.setIncludeIgnored(true);
        add.setAddParents(true);
        
        try {
            add.run();
        } catch (Throwable th) {
            wrapThrowable(th);
        }

        log("Added " + file);

        afterOperation();
    }

    public void revert() throws SVNException {
        beforeOperation();

        log("Reverting working copy");

        SvnRevert revert = getOperationFactory().createRevert();
        revert.setSingleTarget(SvnTarget.fromFile(getWorkingCopyDirectory()));
        revert.setDepth(SVNDepth.INFINITY);
        try {
            revert.run();
        } catch (Throwable th) {
            wrapThrowable(th);
        }

        log("Reverted working copy");
        checkNativeStatusShowsNoChanges();
        afterOperation();
    }

    public List<File> getChildren() {
        final List<File> childrenList = new ArrayList<File>();

        final File[] children = SVNFileListUtil.listFiles(getWorkingCopyDirectory());
        if (children != null) {
            for (File child : children) {
                if (!child.getName().equals(SVNFileUtil.getAdminDirectoryName())) {
                    childrenList.add(child);
                }
            }
        }
        return childrenList;
    }

    public void setProperty(File file, String propertyName, SVNPropertyValue propertyValue) throws SVNException {
        beforeOperation();

        log("Setting property " + propertyName + " on " + file);

        SvnSetProperty ps = getOperationFactory().createSetProperty();
        ps.setSingleTarget(SvnTarget.fromFile(file));
        ps.setPropertyName(propertyName);
        ps.setPropertyValue(propertyValue);
        ps.setDepth(SVNDepth.EMPTY);
        ps.setForce(true);

        try {
            ps.run();
        } catch (Throwable th) {
            wrapThrowable(th);
        }

        log("Set property " + propertyName + " on " + file);

        afterOperation();
    }

    public void delete(File file) throws SVNException {
        beforeOperation();

        log("Deleting " + file);

        SvnScheduleForRemoval rm = getOperationFactory().createScheduleForRemoval();
        rm.setSingleTarget(SvnTarget.fromFile(file));
        rm.setForce(true);
        try {
            rm.run();
        } catch (Throwable th) {
            wrapThrowable(th);
        }

        log("Deleted " + file);

        afterOperation();
    }

    public long getCurrentRevision() {
        return currentRevision;
    }

    public SVNURL getRepositoryUrl() {
        return repositoryUrl;
    }

    private void checkWorkingCopyConsistency() {
        final File wcDbFile = getWCDbFile();
        if (!wcDbFile.exists()) {
            log("No wc.db, maybe running on the old SVN working copy format, integrity check skipped");
            return;
        }
        final String wcDbPath = wcDbFile.getAbsolutePath().replace('/', File.separatorChar);

        final ProcessBuilder processBuilder = new ProcessBuilder();
        processBuilder.command(getSqlite3Command(), wcDbPath, "pragma integrity_check;");

        BufferedReader bufferedReader = null;
        try {
            final Process process = processBuilder.start();
            if (process != null) {
                bufferedReader = new BufferedReader(new InputStreamReader(process.getInputStream()));

                final String line = bufferedReader.readLine();
                if (line == null || !"ok".equals(line.trim())) {
                    final String notConsistentMessage = "SVN working copy database is not consistent.";

                    throwException(notConsistentMessage);
                }

                process.waitFor();
            }
        } catch (IOException e) {
            log("failed to start sqlite3");
        } catch (InterruptedException e) {
            log("failed to start sqlite3");
        } finally {
            SVNFileUtil.closeFile(bufferedReader);
        }
    }

    public File getWCDbFile() {
        return new File(getAdminDirectory(), ISVNWCDb.SDB_FILE).getAbsoluteFile();
    }

    private File getAdminDirectory() {
        return new File(getWorkingCopyDirectory(), SVNFileUtil.getAdminDirectoryName()).getAbsoluteFile();
    }

    public void dispose() {
        if (clientManager != null) {
            clientManager.dispose();
            clientManager = null;
        }
        disposeLogger();
    }

    private void disposeLogger() {
        SVNFileUtil.closeFile(logger);
        logger = null;
    }

    public SvnOperationFactory getOperationFactory() {
        if (clientManager == null) {
            clientManager = new SvnOperationFactory();
            clientManager.setPrimaryWcGeneration(wcGeneration);
        }
        return clientManager;
    }

    private TestOptions getTestOptions() {
        return testOptions;
    }

    public File getWorkingCopyDirectory() {
        return workingCopyDirectory.getAbsoluteFile();
    }

    private String getSqlite3Command() {
        return getTestOptions().getSqlite3Command();
    }

    public String getSvnCommand() {
        return getTestOptions().getSvnCommand();
    }

    public PrintWriter getLogger() {
        if (logger == null) {

            final File adminDirectory = getAdminDirectory();
            if (!adminDirectory.exists()) {
                //not checked out yet
                return null;
            }

            final File testLogFile = getLogFile();

            FileWriter fileWriter = null;
            try {
                fileWriter = new FileWriter(testLogFile, true);
            } catch (IOException e) {
                SVNFileUtil.closeFile(fileWriter);
                throw new RuntimeException(e);
            }

            logger = new PrintWriter(fileWriter);
        }
        return logger;
    }

    private File getLogFile() {
        return new File(getAdminDirectory(), "test.log");
    }

    private void log(String message) {
        final PrintWriter logger = getLogger();
        if (logger != null) {
            logger.println("[" + new Date() + "]" + message);
            logger.flush();
        }
    }

    private void beforeOperation() throws SVNException {
        log("");

//        backupWcDbFile();
    }

    private void afterOperation() {
        checkWorkingCopyConsistency();
        log("Checked for consistency");
        
        disposeLogger();
    }

    private void checkNativeStatusShowsNoChanges() {
        log("Checking for local changes with native svn");

        final ProcessBuilder processBuilder = new ProcessBuilder();
        processBuilder.command(getSvnCommand(), "status", "--ignore-externals", "-q");
        processBuilder.directory(getWorkingCopyDirectory());

        BufferedReader bufferedReader = null;
        try {
            final Process process = processBuilder.start();
            if (process != null) {
                bufferedReader = new BufferedReader(new InputStreamReader(process.getInputStream()));

                final List<String> lines = new ArrayList<String>();
                while (true) {
                    String line = bufferedReader.readLine();
                    if (line == null) {
                        break;
                    }
                    if (line.endsWith("\n")) {
                        line = line.substring(0, line.length() - 1);
                    }
                    if (line.endsWith("\r")) {
                        line = line.substring(0, line.length() - 1);
                    }
                    if (line.length() > 0) {
                        lines.add(line);
                    }
                }

                if (lines.size() > 0) {
                    final StringBuilder stringBuilder = new StringBuilder("SVN status showed:").append('\n');
                    for (String line : lines) {
                        stringBuilder.append(line).append('\n');
                    }
                    log(stringBuilder.toString());
                    log("");
                }

                final String localChangesMessage = "SVN status shows local changes for the working copy.";
                for (String line : lines) {
                    if (line.charAt(0) != ' ' && line.charAt(0) != 'M') {
                        throwException(localChangesMessage);
                    } else if (line.charAt(1) != ' ') {
                        throwException(localChangesMessage);
                    }
                }

                process.waitFor();
            }

        } catch (IOException e) {
            wrapThrowable(e);
        } catch (InterruptedException e) {
            wrapThrowable(e);
        } finally {
            SVNFileUtil.closeFile(bufferedReader);
        }

        log("No local changes");
    }

    private void throwException(String message) {
        final RuntimeException runtimeException = new RuntimeException(message);
        runtimeException.printStackTrace(getLogger());
        throw runtimeException;
    }

    private void wrapThrowable(Throwable th) {
        th.printStackTrace(getLogger());
        throw new RuntimeException(th);
    }

    public SvnStatus getStatus(String path) throws SVNException {
        SvnGetStatus st = new SvnOperationFactory().createGetStatus();
        st.setDepth(SVNDepth.EMPTY);
        st.setSingleTarget(SvnTarget.fromFile(getFile(path)));
        return st.run();
    }

    public void copy(String srcPath, String dstPath) throws SVNException {
        SvnCopy cp = new SvnOperationFactory().createCopy();
        cp.addCopySource(SvnCopySource.create(SvnTarget.fromFile(getFile(srcPath)), SVNRevision.WORKING));
        cp.setSingleTarget(SvnTarget.fromFile(getFile(dstPath)));
        cp.run();
    }
}
