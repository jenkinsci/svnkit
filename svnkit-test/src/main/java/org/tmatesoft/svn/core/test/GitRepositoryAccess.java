package org.tmatesoft.svn.core.test;

import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.util.SVNStreamGobbler;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.util.SVNLogType;

import java.io.File;
import java.io.IOException;
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

    public GitObjectId getHeadId() throws SVNException {
        String output = runGit("rev-parse", "HEAD");
        return new GitObjectId(output);
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
}
