package org.tmatesoft.svn.core.test;

import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.util.SVNStreamGobbler;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.util.SVNLogType;

import java.io.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class GitRepositoryAccess {

    private final File workingTree;
    private final String gitCommand;

    public GitRepositoryAccess(File workingTree, String gitCommand) {
        this.workingTree = workingTree;
        this.gitCommand = gitCommand;
    }

    public void deleteDotGitDirectory() throws SVNException {
        SVNFileUtil.deleteAll(getDotGitDirectory(), true, null);
    }

    public File getWorkingTree() {
        return workingTree;
    }

    public File getDotGitDirectory() {
        return new File(workingTree, ".git");
    }

    public void init() throws SVNException {
        runGit("init", ".");
    }

    public void performImport(String commitMessage) throws SVNException {
        runGit("add", ".");
        runGit("commit", "-m", commitMessage);
    }

    public String getCommitMessage(GitObjectId commitId) throws SVNException {
        final String output = runGit("cat-file", "commit", commitId.asString());
        final String[] lines = output.split("\n");
        final StringBuilder commitMessageBuilder = new StringBuilder();

        boolean emptyLineFound = false;
        for (String line : lines) {
            line = line.trim();
            if (line.length() == 0) {
                emptyLineFound = true;
                continue;
            }
            if (!emptyLineFound) {
                continue;
            }
            //this line belongs to a commit message
            commitMessageBuilder.append(line).append('\n');
        }
        return commitMessageBuilder.toString().trim();
    }

    public GitObjectId getBlobId(GitObjectId treeish, String relativePath) throws SVNException {
        final String output = runGit("ls-tree", "-r", treeish.asString(), relativePath).trim();
        if (output.length() == 0) {
            return null;
        }
        return new GitObjectId(output.split(" ")[2].split("\t")[0].trim());
    }

    public void copyBlobToFile(GitObjectId blobId, File outputFile) throws SVNException {
        runGitRedirectOutput(outputFile, "cat-file", "blob", blobId.asString());
    }

    public GitObjectId getHeadId() throws SVNException {
        final String output = runGit("rev-parse", "HEAD");
        return new GitObjectId(output.trim());
    }

    public List<GitObjectId> getCommitsByFirstParent(GitObjectId fromCommitId) throws SVNException {
        final String output = runGit("rev-list", "--first-parent", fromCommitId.asString());
        return parseCommitsList(output);
    }

    public List<GitObjectId> getCommitsByFirstParentUntil(GitObjectId fromCommitId, GitObjectId toCommitIdExcluding) throws SVNException {
        final String output = runGit("rev-list", "--first-parent", fromCommitId.asString(), "^" + toCommitIdExcluding);
        return parseCommitsList(output);
    }

    private String runGit(String... args) throws SVNException {
        List<String> command = new ArrayList<String>(1 + (args == null ? 0 : args.length));
        command.add(gitCommand);
        if (args != null) {
            Collections.addAll(command, args);
        }

        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.directory(workingTree);

        SVNStreamGobbler inputGobbler = null;
        SVNStreamGobbler errorGobbler = null;
        try {
            Process process = processBuilder.start();
            inputGobbler = new SVNStreamGobbler(process.getInputStream());
            errorGobbler = new SVNStreamGobbler(process.getErrorStream());
            inputGobbler.start();
            errorGobbler.start();

            process.waitFor();
            inputGobbler.waitFor();
            errorGobbler.waitFor();

        } catch (IOException e) {
            SVNErrorMessage errorMessage = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, e);
            SVNErrorManager.error(errorMessage, SVNLogType.WC);
        } catch (InterruptedException e) {
            SVNErrorMessage errorMessage = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, e);
            SVNErrorManager.error(errorMessage, SVNLogType.WC);
        } finally {
            if (inputGobbler != null) {
                inputGobbler.close();
            }
            if (errorGobbler != null) {
                errorGobbler.close();
            }
        }

        return inputGobbler.getResult();
    }

    private void runGitRedirectOutput(File outputFile, String... args) throws SVNException {
        List<String> command = new ArrayList<String>(1 + (args == null ? 0 : args.length));
        command.add(gitCommand);
        if (args != null) {
            Collections.addAll(command, args);
        }

        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.directory(workingTree);

        SVNBinaryStreamGobbler inputGobbler = null;
        try {
            Process process = processBuilder.start();
            inputGobbler = new SVNBinaryStreamGobbler(process.getInputStream(), new BufferedOutputStream(new FileOutputStream(outputFile)));
            inputGobbler.start();

            process.waitFor();
            inputGobbler.waitFor();

        } catch (IOException e) {
            SVNErrorMessage errorMessage = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, e);
            SVNErrorManager.error(errorMessage, SVNLogType.WC);
        } catch (InterruptedException e) {
            SVNErrorMessage errorMessage = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, e);
            SVNErrorManager.error(errorMessage, SVNLogType.WC);
        } finally {
            if (inputGobbler != null) {
                inputGobbler.close();
            }
        }
    }

    private List<GitObjectId> parseCommitsList(String output) {
        final String[] lines = output.split("\n");
        final List<GitObjectId> commits = new ArrayList<GitObjectId>(lines.length);
        for (String line : lines) {
            line = line.trim();
            if (line.length() == 0) {
                continue;
            }
            commits.add(new GitObjectId(line));
        }
        return commits;
    }

    private static class SVNBinaryStreamGobbler extends Thread {

        private IOException error;
        private boolean myIsEOF;
        private boolean myIsClosed;

        private final InputStream inputStream;
        private final OutputStream outputStream;

        public SVNBinaryStreamGobbler(InputStream inputStream, OutputStream outputStream) {
            this.inputStream = inputStream;
            this.outputStream = outputStream;
        }

        public void run() {
            byte[] buffer = new byte[1024];
            synchronized (outputStream) {
                while (true) {
                    try {
                        int r = inputStream.read(buffer);
                        if (r < 0) {
                            break;
                        }
                        if (r > 0) {
                            outputStream.write(buffer, 0, r);
                        }
                    } catch (IOException e) {
                        if (!myIsClosed) {
                            error = e;
                        }
                        break;
                    }
                }
                myIsEOF = true;
                outputStream.notifyAll();
            }
        }

        public void waitFor() {
            synchronized (outputStream) {
                while (!myIsEOF) {
                    try {
                        outputStream.wait();
                    } catch (InterruptedException e) {
                    }
                }
            }
        }

        public void close() {
            synchronized (outputStream) {
                myIsEOF = true;
                outputStream.notifyAll();
                myIsClosed = true;
                SVNFileUtil.closeFile(inputStream);
                SVNFileUtil.closeFile(outputStream);
            }
        }

        public IOException getError() {
            return error;
        }
    }

}
