package org.tmatesoft.svn.core.test;

import org.tmatesoft.svn.cli.SVNCommandLine;
import org.tmatesoft.svn.core.SVNException;

import java.util.Collection;
import java.util.Iterator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PythonTestsGitCommitInfo {

    private static Pattern WORKING_COPY_PATH_PATTERN = Pattern.compile(".*/working_copies/((\\w|-)+)($|\\s|/).*");
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
    private String workingCopyName;

    private PythonTestsGitCommitInfo(GitRepositoryAccess gitRepositoryAccess, GitObjectId commitId) {
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
        this.workingCopyName = detectWorkingCopyDirectory(commitMessage);
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

    public String getWorkingCopyName() {
        return workingCopyName;
    }

    private String canonicalizeCommitMessage(String commitMessage) {
        return commitMessage.replaceAll("/localhost:\\d+/", "/localhost/");
    }

    private String detectWorkingCopyDirectory(String commitMessage) throws SVNException {
        //remove not-yet-supported options
        commitMessage = commitMessage.replace("--ignore-uuid", "");
        commitMessage = commitMessage.replace("--bdb-txn-nosync", "");
        commitMessage = commitMessage.replace("--copy-info", "");

        final SVNCommandLine commandLine = new SVNCommandLine();
        commandLine.init(commitMessage.split(" "));

        final Collection arguments = commandLine.getArguments();
        final Iterator argumentsIterator = arguments.iterator();
        final String commandName = argumentsIterator.hasNext() ? (String) argumentsIterator.next() : null;

        while (argumentsIterator.hasNext()) {
            final String argument = (String) argumentsIterator.next();
            final Matcher matcher = WORKING_COPY_PATH_PATTERN.matcher(argument);
            if (matcher.matches()) {
                return matcher.group(1);
            }
        }

        return null;
    }

    public static void main(String[] args) {
        String s = "jsvn checkout file:///home/dmit10/work/svnkit/svnkit-test/build/sandbox/svn-python-tests/svn-test-work/repositories/commit_tests-26 svn-test-work/working_copies/commit_tests-26/wc1 --config-dir /home/dmit10/work/svnkit/svnkit-test/build/sandbox/svn-python-tests/svn-test-work/local_tmp/config --password rayjandom --no-auth-cache --username jrandom";
        Matcher matcher = COMMIT_MESSAGE_PATTERN.matcher(s);
        System.out.println("matcher = " + matcher.matches());
        int groupCount = matcher.groupCount();
        System.out.println("groupCount = " + groupCount);
        for (int i = 0; i < groupCount; i++) {
            String group = matcher.group(i + 1);
            System.out.println("group = " + group);
        }
    }
}
