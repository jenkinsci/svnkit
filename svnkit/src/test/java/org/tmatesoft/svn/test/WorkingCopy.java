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
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.wc.SVNClientManager;
import org.tmatesoft.svn.core.wc.SVNCommitClient;
import org.tmatesoft.svn.core.wc.SVNCopyClient;
import org.tmatesoft.svn.core.wc.SVNCopySource;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNUpdateClient;
import org.tmatesoft.svn.core.wc.SVNWCClient;

public class WorkingCopy {

    private final TestOptions testOptions;
    private final File workingCopyDirectory;

    private SVNClientManager clientManager;
    private long currentRevision;

    private PrintWriter logger;
    private SVNURL repositoryUrl;

    public WorkingCopy(String testName, File workingCopyDirectory) {
        this(TestOptions.getInstance(), workingCopyDirectory);
    }

    public WorkingCopy(TestOptions testOptions, File workingCopyDirectory) {
        this.testOptions = testOptions;
        this.workingCopyDirectory = workingCopyDirectory;
        this.currentRevision = -1;
    }

    public void setRepositoryUrl(SVNURL repositoryUrl) {
        this.repositoryUrl = repositoryUrl;
    }
    
    public File deleteFile(String relativePath) {
        getFile(relativePath).delete();
        return getFile(relativePath);
    }

    public File changeFileContents(String relativePath, String contents) throws IOException {
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
        disposeLogger();
        SVNFileUtil.deleteFile(getLogFile());

        beforeOperation();

        log("Checking out " + repositoryUrl);

        final SVNUpdateClient updateClient = getClientManager().getUpdateClient();
        updateClient.setIgnoreExternals(true);

        this.repositoryUrl = repositoryUrl;

        final boolean isWcExists = getWorkingCopyDirectory().isDirectory();
        try {
            currentRevision = updateClient.doCheckout(repositoryUrl,
                    getWorkingCopyDirectory(),
                    SVNRevision.create(revision),
                    SVNRevision.create(revision),
                    SVNDepth.INFINITY,
                    true);
        } catch (Throwable th) {
            if (isWcExists) {
                SVNFileUtil.deleteAll(getWorkingCopyDirectory(), true);

                currentRevision = updateClient.doCheckout(repositoryUrl,
                        getWorkingCopyDirectory(),
                        SVNRevision.create(revision),
                        SVNRevision.create(revision),
                        SVNDepth.INFINITY,
                        true);
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

        final SVNUpdateClient updateClient = getClientManager().getUpdateClient();
        updateClient.setIgnoreExternals(true);

        try {
            currentRevision = updateClient.doUpdate(getWorkingCopyDirectory(),
                    SVNRevision.create(revision),
                    SVNDepth.INFINITY,
                    true,
                    true);
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

        final SVNCopyClient copyClient = getClientManager().getCopyClient();

        try {
            copyClient.doCopy(new SVNCopySource[]{
                    new SVNCopySource(SVNRevision.WORKING, SVNRevision.WORKING, directory)},
                    anotherDirectory,
                    false, false, false);
        } catch (Throwable th) {
            wrapThrowable(th);
        }

        log("Copied " + directory + " as a child of " + anotherDirectory);

        afterOperation();
    }

    public long commit(String commitMessage) throws SVNException {
        beforeOperation();

        log("Committing ");

        final SVNCommitClient commitClient = getClientManager().getCommitClient();

        SVNCommitInfo commitInfo = null;
        try {
            commitInfo = commitClient.doCommit(new File[]{getWorkingCopyDirectory()},
                    false,
                    commitMessage,
                    null, null,
                    false, true,
                    SVNDepth.INFINITY);
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

        final SVNWCClient wcClient = getClientManager().getWCClient();

        try {
            wcClient.doAdd(file, false, false, false, SVNDepth.INFINITY, true, true, true);
        } catch (Throwable th) {
            wrapThrowable(th);
        }

        log("Added " + file);

        afterOperation();
    }

    public void revert() throws SVNException {
        beforeOperation();

        log("Reverting working copy");

        final SVNWCClient wcClient = getClientManager().getWCClient();

        try {
            wcClient.doRevert(new File[]{getWorkingCopyDirectory()}, SVNDepth.INFINITY, null);
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

        final SVNWCClient wcClient = getClientManager().getWCClient();

        try {
            wcClient.doSetProperty(file, propertyName, propertyValue, true, SVNDepth.INFINITY, null, null);
        } catch (Throwable th) {
            wrapThrowable(th);
        }

        log("Set property " + propertyName + " on " + file);

        afterOperation();
    }

    public void delete(File file) throws SVNException {
        beforeOperation();

        log("Deleting " + file);

        final SVNWCClient wcClient = getClientManager().getWCClient();

        try {
            wcClient.doDelete(file, true, false);
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

    private void backupWcDbFile() throws SVNException {
        final File wcDbFile = getWCDbFile();
        if (!wcDbFile.exists()) {
            return;
        }

        final File wcDbBackupFile = new File(getWCDbFile().getAbsolutePath() + ".backup");
        SVNFileUtil.copy(wcDbFile, wcDbBackupFile, false, false);

        log("Backed up wc.db");
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
            wrapThrowable(e);
        } catch (InterruptedException e) {
            wrapThrowable(e);
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

    private SVNClientManager getClientManager() {
        if (clientManager == null) {
            clientManager = SVNClientManager.newInstance();
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

        backupWcDbFile();
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
}
