package org.tmatesoft.svn.core.test;

import org.tmatesoft.svn.cli.SVNCommandLine;
import org.tmatesoft.svn.cli.svn.SVN;
import org.tmatesoft.svn.core.SVNException;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PythonTestsGitCommitInfo {

    static {
        registerOptionsWorkaround();
    }

    private static Pattern WORKING_COPY_PATH_PATTERN = Pattern.compile(".*/working_copies/((\\w|-|\\.)+)($|\\s|/).*");
    private static Pattern COMMIT_MESSAGE_PATTERN = Pattern.compile("(\\w+)( -m '.*')? (\\w+)( -m '.*')? .*/(\\w+)_tests-(\\d+).*");

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
            this.subcommand = matcher.group(3);
            this.testCase = matcher.group(5);
            try {
                this.testNumber = Integer.parseInt(matcher.group(6));
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
        commitMessage = commitMessage.replace("--force-uuid", "");

        try {
        final SVNCommandLine commandLine = new SVNCommandLine();
        commandLine.init(splitIntoArguments(commitMessage));

        final Collection arguments = commandLine.getArguments();
        final Iterator argumentsIterator = arguments.iterator();
        final String commandName = argumentsIterator.hasNext() ? (String) argumentsIterator.next() : null;

        while (argumentsIterator.hasNext()) {
            final String argument = (String) argumentsIterator.next();
            final Matcher matcher = WORKING_COPY_PATH_PATTERN.matcher(argument);
            if (matcher.matches()) {
//                System.out.println(commitId + " Working copy pattern matching " + argument);
                return matcher.group(1);
            } else {
//                System.out.println(commitId + " Doesn't match " + argument + "(" + commitMessage + ")");
            }
        }    }
        catch (Throwable th) {
            System.out.println("Bad commmit message " + commitMessage);
            throw new RuntimeException(th);
        }

        return null;
    }

    private static String[] splitIntoArguments(String commitMessage) {
        final List<String> arguments = new ArrayList<String>();

        StringBuilder argumentBuilder = new StringBuilder();
        Character currentQuoteCharacter = null;
        for (int i = 0; i < commitMessage.length(); i++) {
            final char c = commitMessage.charAt(i);

            switch (c) {
                case '\"':
                case '\'':
                    if (currentQuoteCharacter == null) {
                        currentQuoteCharacter = c;
                    } else if (currentQuoteCharacter == c) {
                        currentQuoteCharacter = null;
                    } else {
                        argumentBuilder.append(c);
                    }
                    break;
                case ' ':
                case '\t':
                    if (currentQuoteCharacter == null) {
                        if (argumentBuilder.length() > 0) {
                            arguments.add(argumentBuilder.toString());
                            argumentBuilder = new StringBuilder();
                        }
                    } else {
                        argumentBuilder.append(c);
                    }
                    break;
                default:
                    argumentBuilder.append(c);
                    break;
            }
        }

        if (argumentBuilder.length() > 0) {
            arguments.add(argumentBuilder.toString());
        }

        return arguments.toArray(new String[]{});
    }


    private static void registerOptionsWorkaround() {
        //TODO: in perfect world registerOptions() method should be static
        new SVN(){
            @Override
            protected void registerOptions() {
                super.registerOptions();
            }
        }.registerOptions();
    }
}
