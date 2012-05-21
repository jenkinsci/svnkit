package org.tmatesoft.svn.core.test;

import org.tmatesoft.svn.core.SVNException;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PythonTestsGitCommitInfo {

    private static Pattern COMMIT_MESSAGE_PATTERN = Pattern.compile("(\\w+) (\\w+) .*/(\\w+)_tests-(\\d+).*");

    public static PythonTestsGitCommitInfo loadFromCommit(GitRepositoryAccess gitRepositoryAccess, GitObjectId commitId) throws SVNException {
        PythonTestsGitCommitInfo commitInfo = new PythonTestsGitCommitInfo(gitRepositoryAccess, commitId);
        commitInfo.load();
        return commitInfo;
    }

    private final GitRepositoryAccess gitRepositoryAccess;

    private final GitObjectId commitId;

    private String commitMessage;
    private String canonicalizedCommitMessage;
    private String command;
    private String subcommand;
    private String testCase;
    private int testNumber;

    public PythonTestsGitCommitInfo(GitRepositoryAccess gitRepositoryAccess, GitObjectId commitId) {
        this.gitRepositoryAccess = gitRepositoryAccess;
        this.commitId = commitId;
    }

    private void load() throws SVNException {
        this.commitMessage = gitRepositoryAccess.getCommitMessage(commitId);
        this.canonicalizedCommitMessage = canonicalizeCommitMessage(commitMessage);
        final Matcher matcher = COMMIT_MESSAGE_PATTERN.matcher(commitMessage);
        if (matcher.matches()) {
            this.command = matcher.group(1);
            this.subcommand = matcher.group(2);
            this.testCase = matcher.group(3);
            try {
                this.testNumber = Integer.parseInt(matcher.group(4));
            } catch (NumberFormatException e) {
                this.testNumber = -1;
            }
        } else {
            this.command = null;
            this.subcommand = null;
            this.testCase = null;
            this.testNumber = -1;
        }
    }

    public GitObjectId getCommitId() {
        return commitId;
    }

    public String getCommitMessage() {
        return commitMessage;
    }

    public String getCanonicalizedCommitMessage() {
        return canonicalizedCommitMessage;
    }

    public String getCommand() {
        return command;
    }

    public String getSubcommand() {
        return subcommand;
    }

    public String getTestCase() {
        return testCase;
    }

    public int getTestNumber() {
        return testNumber;
    }

    private String canonicalizeCommitMessage(String commitMessage) {
        return commitMessage.replaceAll("/localhost:\\d+/", "/localhost/");
    }
}
