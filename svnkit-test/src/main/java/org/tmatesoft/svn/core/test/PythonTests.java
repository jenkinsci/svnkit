/*
 * ====================================================================
 * Copyright (c) 2004-2012 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */

package org.tmatesoft.svn.core.test;

import com.martiansoftware.nailgun.NGServer;
import org.tmatesoft.sqljet.core.SqlJetException;
import org.tmatesoft.sqljet.core.SqlJetTransactionMode;
import org.tmatesoft.sqljet.core.internal.memory.SqlJetMemoryPointer;
import org.tmatesoft.sqljet.core.schema.ISqlJetColumnDef;
import org.tmatesoft.sqljet.core.schema.ISqlJetSchema;
import org.tmatesoft.sqljet.core.schema.ISqlJetTableDef;
import org.tmatesoft.sqljet.core.table.ISqlJetCursor;
import org.tmatesoft.sqljet.core.table.ISqlJetTable;
import org.tmatesoft.sqljet.core.table.SqlJetDb;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNProperties;
import org.tmatesoft.svn.core.internal.db.SVNSqlJetDb;
import org.tmatesoft.svn.core.internal.db.SVNSqlJetStatement;
import org.tmatesoft.svn.core.internal.util.DefaultSVNDebugFormatter;
import org.tmatesoft.svn.core.internal.util.SVNDate;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.internal.wc17.SVNWCUtils;
import org.tmatesoft.svn.core.internal.wc17.db.statement.SVNWCDbSchema;
import org.tmatesoft.svn.util.SVNLogType;

import java.io.*;
import java.net.ServerSocket;
import java.util.*;
import java.util.logging.FileHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * @version 1.3
 * @author  TMate Software Ltd.
 */
public class PythonTests {

    public static final int DEFAULT_DAEMON_PORT_NUMBER = 1729;

    private static File ourPropertiesFile;

    private static Process ourSVNServer;
    private static AbstractTestLogger[] ourLoggers;
    private static NGServer ourDaemon;
    private static Properties ourProperties;
    private static String ourTestType;
    private static int daemonPortNumber = -1;
    private static String currentTestCase = null;
    private static int currentTestNumber = -1;
    private static String currentTestErrorMessage = null;

    /**
     * true if tests are run in regular modes;
     * in wc.db checking mode tests are run twice: for JSVN and SVN; logging is disabled for this case and only wc.db mismatches are logged
     */
    private static boolean regularLoggingEnabled = true;

    private static Set<String> commandsNotToCheckWorkingCopyAfter;
    static {
        commandsNotToCheckWorkingCopyAfter = new HashSet<String>();
        commandsNotToCheckWorkingCopyAfter.add("status");
        commandsNotToCheckWorkingCopyAfter.add("st");
        commandsNotToCheckWorkingCopyAfter.add("info");
        commandsNotToCheckWorkingCopyAfter.add("checkout");
        commandsNotToCheckWorkingCopyAfter.add("co");
        commandsNotToCheckWorkingCopyAfter.add("propget");
        commandsNotToCheckWorkingCopyAfter.add("pg");
        commandsNotToCheckWorkingCopyAfter.add("proplist");
    }

    public static void main(String[] args) {
		String fileName = args[0];
		String libPath = args[1];
		if (libPath == null) {
		    libPath = "";
		}
		ourPropertiesFile = new File(fileName);

		Properties properties = null;
		String defaultTestSuite = null;
		try {
			properties = loadProperties(ourPropertiesFile);
			defaultTestSuite = loadDefaultTestSuite();
		} catch (IOException e) {
			e.printStackTrace();
			System.exit(1);
		}
		File testResultsDirectory = new File(properties.getProperty("python.tests.results", "build/logs"));
		boolean logAll = Boolean.TRUE.toString().equalsIgnoreCase(properties.getProperty("logAll", "false").trim());
        ourLoggers = new AbstractTestLogger[] {new ConsoleLogger(), new JUnitTestLogger(testResultsDirectory, logAll)};
		
		ourProperties = properties;
        Logger logger = setupLogging();
        
        for (int i = 0; i < ourLoggers.length; i++) {
            try{
                ourLoggers[i].startTests(properties);
            }catch(IOException ioe){
                ioe.printStackTrace();
                System.exit(1);
            }
        }

        if (Boolean.TRUE.toString().equals(properties.getProperty("daemon"))) {
            try {
                libPath = startCommandDaemon(properties);
            } catch (IOException e) {
                e.printStackTrace();
                System.exit(1);
            }
        }

        boolean wcdbCheckMode = Boolean.TRUE.toString().equals(properties.getProperty("python.check.wc.db"));
        regularLoggingEnabled = !wcdbCheckMode;

        String pythonTestsRoot = properties.getProperty("python.tests", "python/cmdline");
		properties.setProperty("repository.root", new File(pythonTestsRoot).getAbsolutePath());
        String absTestsRootLocation = new File(pythonTestsRoot).getAbsolutePath().replace(File.separatorChar, '/');
        if(!absTestsRootLocation.startsWith("/")){
            absTestsRootLocation = "/" + absTestsRootLocation; 
        }

        try {
            final File currentDirectory = new File("").getAbsoluteFile();

            final File gitRepositoryDirectory = new File("svn-python-tests/svn-test-work");
            final GitRepositoryAccess gitRepositoryAccess = new GitRepositoryAccess(gitRepositoryDirectory, "git");

            gitRepositoryAccess.deleteDotGitDirectory();

            if (wcdbCheckMode) {
                System.out.println("Running all tests with JSVN, then with SVN, please wait (there will be no output for a long time)...");
            }

            runPythonTestsForAllProtocols(libPath, properties, defaultTestSuite, logger, absTestsRootLocation);

            if (wcdbCheckMode) {
                assert gitRepositoryAccess.getDotGitDirectory().isDirectory();

                changeCurrentDirectory(currentDirectory);

                final File workingCopiesDirectory = new File(gitRepositoryDirectory, "working_copies");

                final GitObjectId headIdAfterJSVN = gitRepositoryAccess.getHeadId();
//                System.out.println("headIdAfterJSVN = " + headIdAfterJSVN);
                final List<GitObjectId> commitsAfterJSVN = gitRepositoryAccess.getCommitsByFirstParent(headIdAfterJSVN);

                final String patternMatchingNoCommand = "^$";
                properties.put("python.tests.pattern", patternMatchingNoCommand);

                try {
                    generateScripts(properties);
                } catch (IOException e) {
                    e.printStackTrace();
                    return;
                }

                runPythonTestsForAllProtocols(libPath, properties, defaultTestSuite, logger, absTestsRootLocation);
                final GitObjectId headIdAfterSVN = gitRepositoryAccess.getHeadId();
//                System.out.println("headIdAfterSVN = " + headIdAfterSVN);
                final List<GitObjectId> commitsAfterSVN = gitRepositoryAccess.getCommitsByFirstParentUntil(headIdAfterSVN, headIdAfterJSVN);

                Collections.reverse(commitsAfterJSVN);
                Collections.reverse(commitsAfterSVN);

                checkWorkingCopiesByGitSnapshots(gitRepositoryAccess, workingCopiesDirectory, commitsAfterJSVN, commitsAfterSVN);

                maybeEndTest();
                maybeEndTestCase();
            }
        } catch (SVNException e) {
            e.printStackTrace();
        }

        for (int i = 0; i < ourLoggers.length; i++) {
            ourLoggers[i].endTests(properties);
        }
        if (ourDaemon != null) {
            ourDaemon.shutdown(false);
        }
	}

    private static List<PythonTestsGitCommitInfo> loadCommitsInfo(GitRepositoryAccess gitRepositoryAccess, List<GitObjectId> commits) throws SVNException {
        ArrayList<PythonTestsGitCommitInfo> commitInfo = new ArrayList<PythonTestsGitCommitInfo>(commits.size());
        for (GitObjectId commit : commits) {
            commitInfo.add(PythonTestsGitCommitInfo.loadFromCommit(gitRepositoryAccess, commit));
        }
        return commitInfo;
    }

    private static void checkWorkingCopiesByGitSnapshots(GitRepositoryAccess gitRepositoryAccess, File workingCopiesDirectory, List<GitObjectId> commitsAfterJSVN, List<GitObjectId> commitsAfterSVN) throws SVNException {
        final List<PythonTestsGitCommitInfo> commitsInfoAfterJSVN = loadCommitsInfo(gitRepositoryAccess, commitsAfterJSVN);
        final List<PythonTestsGitCommitInfo> commitsInfoAfterSVN = loadCommitsInfo(gitRepositoryAccess, commitsAfterSVN);

        checkWorkingCopiesByGitSnapshots(workingCopiesDirectory, gitRepositoryAccess, commitsInfoAfterJSVN, commitsInfoAfterSVN);
    }

    private static void checkWorkingCopiesByGitSnapshots(File workingCopiesDirectory, GitRepositoryAccess gitRepositoryAccess, List<PythonTestsGitCommitInfo> commitsInfoAfterJSVN, List<PythonTestsGitCommitInfo> commitsInfoAfterSVN) throws SVNException {
        int j = 0;
        for (int i = 0; i < commitsInfoAfterJSVN.size(); i++) {
            final PythonTestsGitCommitInfo commitInfoAfterJSVN = commitsInfoAfterJSVN.get(i);

            int jOriginal = j;
            boolean found = false;
            while (j < commitsInfoAfterSVN.size()){
                found = commitsInfoAfterSVN.get(j).getCanonicalizedCommitMessage().equals(commitInfoAfterJSVN.getCanonicalizedCommitMessage());
                if (found) {
                    break;
                }
                j++;
            }

            if (found) {
                final PythonTestsGitCommitInfo commitInfoAfterSVN = commitsInfoAfterSVN.get(j);
                processMatchedGitCommits(workingCopiesDirectory, gitRepositoryAccess, commitInfoAfterJSVN, commitInfoAfterSVN);
                j++;
            } else {
                j = jOriginal + 1;
                System.out.println("Can't find pair for commit " + commitInfoAfterJSVN.getCommitId());
            }
        }
    }

    private static void processMatchedGitCommits(File workingCopiesDirectory, GitRepositoryAccess gitRepositoryAccess, PythonTestsGitCommitInfo commitInfoAfterJSVN, PythonTestsGitCommitInfo commitInfoAfterSVN) throws SVNException {
        boolean canDetermineWorkingCopy = commitInfoAfterJSVN.getWorkingCopyName() != null;
        if (!canDetermineWorkingCopy) {
//            System.out.println("jsvn commit=" + commitInfoAfterJSVN.getCommitId() + "; svn commit=" + commitInfoAfterSVN.getCommitId() + "; can't detect working copy");
        }

        final boolean checkWorkingCopy = canDetermineWorkingCopy && !commandsNotToCheckWorkingCopyAfter.contains(commitInfoAfterJSVN.getSubcommand());
        if (!checkWorkingCopy) {
//            System.out.println("jsvn commit=" + commitInfoAfterJSVN.getCommitId() + "; svn commit=" + commitInfoAfterSVN.getCommitId() + "; working copy shouldn't be checked");
            return;
        }
//        System.out.println(commitInfoAfterJSVN.getCommitMessage());
//        System.out.println("command = " + commitInfoAfterJSVN.getCommand());
//        System.out.println("subcommand = " + commitInfoAfterJSVN.getSubcommand());
//        System.out.println("testcase = " + commitInfoAfterJSVN.getTestCase());
//        System.out.println("testnumber = " + commitInfoAfterJSVN.getTestNumber());
//        System.out.println("working copy name = " + commitInfoAfterJSVN.getWorkingCopyName());
//        System.out.println("jsvn commit id = " + commitInfoAfterJSVN.getCommitId());
//        System.out.println("svn commit id = " + commitInfoAfterSVN.getCommitId());

        String newTestCase = commitInfoAfterJSVN.getTestCase();
        int newTestNumber = commitInfoAfterJSVN.getTestNumber();

        if (newTestCase == null) {
            newTestCase = currentTestCase;
        }

        if (newTestNumber == -1) {
            newTestNumber = currentTestNumber;
        }

        boolean shouldChangeTestCase = !areEqual(currentTestCase, newTestCase);
        boolean shouldChangeTestNumber = (currentTestNumber != newTestNumber) || shouldChangeTestCase;

//        System.out.println("currentTestCase = " + currentTestCase);
//        System.out.println("currentTestNumber = " + currentTestNumber);
//        System.out.println("newTestCase = " + newTestCase);
//        System.out.println("newTestNumber = " + newTestNumber);
//        System.out.println("shouldChangeTestCase = " + shouldChangeTestCase);
//        System.out.println("shouldChangeTestNumber = " + shouldChangeTestNumber);

        if (shouldChangeTestNumber) {
            maybeEndTest();
        }

        if (shouldChangeTestCase) {
            maybeEndTestCase();
        }

        if (shouldChangeTestCase) {
            maybeStartTestCase(newTestCase);
        }

        if (shouldChangeTestNumber) {
            maybeStartTest(newTestNumber);
        }

        final File workingCopyDirectory = new File(workingCopiesDirectory, commitInfoAfterJSVN.getWorkingCopyName());
        final File wcDbFile = new File(workingCopyDirectory, SVNFileUtil.getAdminDirectoryName() +"/wc.db");
        final File workingTree = gitRepositoryAccess.getWorkingTree();

        final String relativeWCDbPath = SVNPathUtil.getRelativePath(workingTree.getAbsolutePath().replace(File.separatorChar, '/'),
                wcDbFile.getAbsolutePath().replace(File.separatorChar, '/'));

        final GitObjectId wcDbBlobAfterJSVN = gitRepositoryAccess.getBlobId(commitInfoAfterJSVN.getCommitId(), relativeWCDbPath);
        final GitObjectId wcDbBlobAfterSVN = gitRepositoryAccess.getBlobId(commitInfoAfterSVN.getCommitId(), relativeWCDbPath);

        if (wcDbBlobAfterJSVN == null && wcDbBlobAfterSVN == null) {
//            System.out.println("jsvn commit=" + commitInfoAfterJSVN.getCommitId() + "; svn commit=" + commitInfoAfterSVN.getCommitId() + "; both don't have wc.db");
            return;
        }

        if (wcDbBlobAfterJSVN == null || wcDbBlobAfterSVN == null) {
            currentTestErrorMessage = "jsvn commit=" + commitInfoAfterJSVN.getCommitId() + "; svn commit=" + commitInfoAfterSVN.getCommitId() + "; one commit has " + relativeWCDbPath + " another one has not";
            System.out.println("ERROR: " + currentTestErrorMessage);
            return;
        }

        final File wcDbAfterJSVN = SVNFileUtil.createTempFile("svnkit.tests.wc.db.after.jsvn", "");
        final File wcDbAfterSVN = SVNFileUtil.createTempFile("svnkit.tests.wc.db.after.svn", "");

        try {

            gitRepositoryAccess.copyBlobToFile(wcDbBlobAfterJSVN, wcDbAfterJSVN);
            gitRepositoryAccess.copyBlobToFile(wcDbBlobAfterSVN, wcDbAfterSVN);

            compareWCDbContents(commitInfoAfterJSVN, commitInfoAfterSVN, wcDbAfterJSVN, wcDbAfterSVN);
        } finally {
            try {
                SVNFileUtil.deleteFile(wcDbAfterJSVN);
            } catch (SVNException ignore) {
            }
            try {
                SVNFileUtil.deleteFile(wcDbAfterSVN);
            } catch (SVNException ignore) {
            }
        }
    }

    private static boolean areEqual(Object o1, Object o2) {
        return o1 == null ? o2 == null : o1.equals(o2);
    }

    private static void maybeStartTest(int testNumber) {
        if (testNumber == -1) {
            return;
        }
        currentTestErrorMessage = null;
        currentTestNumber = testNumber;
    }

    private static void maybeEndTest() {
        if (currentTestNumber == -1) {
            return;
        }

        TestResult testResult = new TestResult(currentTestCase, String.valueOf(currentTestNumber), currentTestErrorMessage == null);
        if (currentTestErrorMessage != null) {
            testResult.setOutput(new StringBuffer(currentTestErrorMessage));
        }

        for (AbstractTestLogger ourLogger : ourLoggers) {
            ourLogger.handleTest(testResult);
        }
        currentTestNumber = -1;
        currentTestErrorMessage = null;
    }

    private static void maybeStartTestCase(String testCase) {
        if (testCase == null) {
            return;
        }
        for (AbstractTestLogger ourLogger : ourLoggers) {
            ourLogger.startSuite(testCase);
        }
        currentTestCase = testCase;
    }

    private static void maybeEndTestCase() {
        if (currentTestCase == null) {
            return;
        }
        for (AbstractTestLogger ourLogger : ourLoggers) {
            ourLogger.endSuite(currentTestCase);
        }
        currentTestCase = null;
    }

    private static void compareWCDbContents(PythonTestsGitCommitInfo commitInfoAfterJSVN, PythonTestsGitCommitInfo commitInfoAfterSVN, File wcDbAfterJSVN, File wcDbAfterSVN) throws SVNException {
        final SVNSqlJetDb svnSqlJetDbAfterJSVN = SVNSqlJetDb.open(wcDbAfterJSVN, SVNSqlJetDb.Mode.ReadOnly);
        final SVNSqlJetDb svnSqlJetDbAfterSVN = SVNSqlJetDb.open(wcDbAfterSVN, SVNSqlJetDb.Mode.ReadOnly);
        try {
            final SqlJetDb dbAfterJSVN = svnSqlJetDbAfterJSVN.getDb();
            final SqlJetDb dbAfterSVN = svnSqlJetDbAfterSVN.getDb();

            final ISqlJetSchema schemaAfterJSVN = dbAfterJSVN.getSchema();
            final ISqlJetSchema schemaAfterSVN = dbAfterSVN.getSchema();

            final SortedSet<String> tableNamesAfterJSVN = new TreeSet<String>(schemaAfterJSVN.getTableNames());
            final SortedSet<String> tableNamesAfterSVN = new TreeSet<String>(schemaAfterSVN.getTableNames());

            if (!tableNamesAfterJSVN.equals(tableNamesAfterSVN)) {
                currentTestErrorMessage = "jsvn commit=" + commitInfoAfterJSVN.getCommitId() + "; svn commit=" + commitInfoAfterSVN.getCommitId() + "; tables set differ";
                System.out.println("ERROR: " + currentTestErrorMessage);
                return;
            }

            for (String tableName : tableNamesAfterJSVN) {
                if (tableName.startsWith("sqlite_")) {
                    //skip special table
                    continue;
                }

                final ISqlJetTable tableAfterJSVN = dbAfterJSVN.getTable(tableName);
                final ISqlJetTable tableAfterSVN = dbAfterSVN.getTable(tableName);

                final ISqlJetTableDef definition = tableAfterJSVN.getDefinition();
                final List<ISqlJetColumnDef> columnDefinitions = definition.getColumns();


                final List<Object[]> rowsAfterJSVN = loadRows(dbAfterJSVN, tableAfterJSVN, tableName);
                final List<Object[]> rowsAfterSVN = loadRows(dbAfterSVN, tableAfterSVN, tableName);

                if (rowsAfterJSVN.size() != rowsAfterSVN.size()) {
                    currentTestErrorMessage = "jsvn commit=" + commitInfoAfterJSVN.getCommitId() + "; svn commit=" + commitInfoAfterSVN.getCommitId() + "; table " + tableName + " rows count differ";
                    System.out.println("ERROR: " + currentTestErrorMessage);
                    return;
                }

                int rowAfterJSVN = 0;
                for (Object[] valuesAfterJSVN : rowsAfterJSVN) {
                    boolean found = false;
                    for (Object[] valuesAfterSVN : rowsAfterSVN) {
                        if (areSqlJetArraysEqual(columnDefinitions, valuesAfterJSVN, valuesAfterSVN)) {
                            found = true;
                            break;
                        }
                    }

                    if (!found) {
                        currentTestErrorMessage = "jsvn commit=" + commitInfoAfterJSVN.getCommitId() + "; svn commit=" + commitInfoAfterSVN.getCommitId() + "; table " + tableName + " row " + rowAfterJSVN + " corresponds no row of reference table";
                        System.out.println("ERROR: " + currentTestErrorMessage);
                        return;
                    }

                    rowAfterJSVN++;
                }
            }

        } catch (SqlJetException e) {
            SVNErrorMessage errorMessage = SVNErrorMessage.create(SVNErrorCode.WC_DB_ERROR);
            SVNErrorManager.error(errorMessage, e, SVNLogType.WC);
        } finally {
            svnSqlJetDbAfterJSVN.close();
            svnSqlJetDbAfterSVN.close();
        }
    }

    private static boolean areSqlJetArraysEqual(List<ISqlJetColumnDef> columnDefinitions, Object[] valuesAfterJSVN, Object[] valuesAfterSVN) {
        if (valuesAfterJSVN.length != valuesAfterSVN.length) {
            return false;
        }
        int length = valuesAfterJSVN.length;
        for (int i = 0; i < length; i++) {
            if (!areSqlJetValuesEqual(columnDefinitions.get(i), valuesAfterJSVN[i], valuesAfterSVN[i])) {
                System.out.println(columnDefinitions.get(i).getName() + ":" + valuesAfterJSVN[i] + "!=" + valuesAfterSVN[i]);
                return false;
            }
        }
        return true;
    }

    private static List<Object[]> loadRows(SqlJetDb db, ISqlJetTable table, String tableName) throws SqlJetException {
        db.beginTransaction(SqlJetTransactionMode.READ_ONLY);
        final ISqlJetCursor cursor = table.open();
        final List<Object[]> rows = new ArrayList<Object[]>();

        System.out.println("===========" + tableName + "==============");
        int row = 0;
        while (!cursor.eof()) {
            final Object[] values = cursor.getRowValues();
            final boolean zeroRefCountInPristine = SVNWCDbSchema.PRISTINE.name().equals(tableName) && (cursor.getInteger("refcount") == 0);
            if (!zeroRefCountInPristine) {
                rows.add(values);
            }

            cursor.next();

            System.out.print(row);
            System.out.print(":  ");
            for (Object value : values) {
                System.out.print(value);
                System.out.print(';');
            }

            System.out.println();
            row++;
        }
        System.out.println("=========================");
        cursor.close();
        return rows;
    }

    private static boolean areSqlJetValuesEqual(ISqlJetColumnDef columnDefinition, Object value1, Object value2) {
        String columnDefinitionName = columnDefinition.getName();
        if (columnDefinitionName.endsWith("_date") || columnDefinitionName.endsWith("_time")) {
            value1 = value1 == null ? 0L : value1;
            value2 = value2 == null ? 0L : value2;
            //TODO: is it a bug?
        }

        if (value1 == null) {
            return value2 == null;
        }

        if (value2 == null) {
            return false;
        }

        if (!value1.getClass().equals(value2.getClass())) {
            return false;
        }

        if (value1 instanceof SqlJetMemoryPointer) {
            SqlJetMemoryPointer memoryPointer1 = (SqlJetMemoryPointer) value1;
            SqlJetMemoryPointer memoryPointer2 = (SqlJetMemoryPointer) value2;

            boolean areEqual = memoryPointer1.compareTo(memoryPointer2) == 0;
            if (!areEqual && columnDefinitionName.equals("properties")) {
                int count1 = memoryPointer1.getBuffer().getSize() - memoryPointer1.getPointer();
                int count2 = memoryPointer2.getBuffer().getSize() - memoryPointer2.getPointer();

                byte[] buffer1 = new byte[count1];
                byte[] buffer2 = new byte[count2];
                memoryPointer1.getBytes(buffer1);
                memoryPointer2.getBytes(buffer2);

                try {
                    final SVNProperties properties1 = SVNSqlJetStatement.parseProperties(buffer1);
                    final SVNProperties properties2 = SVNSqlJetStatement.parseProperties(buffer2);

                    if (properties1 == null) {
                        return properties2 == null;
                    }

                    if (properties2 == null) {
                        return false;
                    }

                    Set propertiesNames = new HashSet();
                    propertiesNames.addAll(properties1.asMap().keySet());
                    propertiesNames.addAll(properties2.asMap().keySet());

                    for (Object name : propertiesNames) {
                        String propertyName = (String) name;
                        byte[] binaryValue1 = properties1.getBinaryValue(propertyName);
                        byte[] binaryValue2 = properties2.getBinaryValue(propertyName);

                        if (!Arrays.equals(binaryValue1, binaryValue2)) {
                            System.out.println("binaryValue1.length = " + binaryValue1.length);
                            System.out.println("binaryValue2.length = " + binaryValue2.length);
                            for (int i = 0; i < Math.min(binaryValue1.length, binaryValue2.length); i++) {
                                System.out.println(binaryValue1[i]);
                                System.out.println(binaryValue2[i]);
                            }
                            return false;
                        }
                    }

                    return true;

                } catch (SVNException e) {
                    e.printStackTrace();
                    return false;
                }
            }
            return areEqual;
        }

        if (columnDefinitionName.endsWith("_date") || columnDefinitionName.endsWith("_time")) {
            SVNDate date1 = SVNWCUtils.readDate((Long) value1);
            SVNDate date2 = SVNWCUtils.readDate((Long) value2);

            long time1 = date1.getTime();
            long time2 = date2.getTime();

            return Math.abs(time1 - time2) < 100000000000L;
        }

        if (columnDefinitionName.equals("uuid")) {
            return true;
        }

        if (columnDefinitionName.equals("lock_token")) {
            return true;
        }

        return value1.equals(value2);
    }

    private static void changeCurrentDirectory(File currentDirectory) {
        System.setProperty("user.dir", currentDirectory.getAbsolutePath());
    }

    public static int getDaemonPortNumber() {
        if (daemonPortNumber == -1) {
            daemonPortNumber = findUnoccupiedPort(DEFAULT_DAEMON_PORT_NUMBER);
        }
        return daemonPortNumber;
    }

    private static void runPythonTestsForAllProtocols(String libPath, Properties properties, String defaultTestSuite, Logger logger, String absTestsRootLocation) {
        String url = "file://" + absTestsRootLocation;
        if (Boolean.TRUE.toString().equals(properties.getProperty("python.file"))) {
            boolean started = false;
            try {
                for (int i = 0; i < ourLoggers.length; i++) {
                    ourLoggers[i].startServer("file", url);
                }
                started = true;
                runPythonTests(properties, defaultTestSuite, "fsfs", url, libPath, logger);
            } catch (Throwable th) {
                th.printStackTrace();
            } finally {
                if (started) {
                    for (int i = 0; i < ourLoggers.length; i++) {
                        ourLoggers[i].endServer("file", url);
                    }
                }
            }
        }

        url = "svn://localhost";
        if (Boolean.TRUE.toString().equals(properties.getProperty("python.svn"))) {
            boolean started = false;
			try {
				int port = startSVNServe(properties);
                url += ":" + port;
                for (int i = 0; i < ourLoggers.length; i++) {
                    ourLoggers[i].startServer("svnserve", url);
                }
                started = true;
				runPythonTests(properties, defaultTestSuite, "svn", url, libPath, logger);
			} catch (Throwable th) {
				th.printStackTrace();
			} finally {
				stopSVNServe();
                if (started) {
                    for (int i = 0; i < ourLoggers.length; i++) {
                        ourLoggers[i].endServer("svnserve", url);
                    }
                }
			}
		}

        if (Boolean.TRUE.toString().equals(properties.getProperty("python.http"))) {
            String apacheEnabled = properties.getProperty("apache", "true");
            if (Boolean.TRUE.toString().equals(apacheEnabled.trim())) {
                properties.setProperty("apache.conf", "apache/python.template.conf");
                boolean started = false;
                int port = -1;
                try {
                    port = startApache(properties, logger);
                    url = "http://localhost:" + port;
                    for (int i = 0; i < ourLoggers.length; i++) {
                        ourLoggers[i].startServer("apache", url);
                    }
                    started = true;
                    runPythonTests(properties, defaultTestSuite, "dav", url, libPath, logger);
                } catch (Throwable th) {
                    th.printStackTrace();
                } finally {
                    try {
                        stopApache(properties, port, logger);
                        if (started) {
                            for (int i = 0; i < ourLoggers.length; i++) {
                                ourLoggers[i].endServer("apache", url);
                            }
                        }
                    } catch (Throwable th) {
                        th.printStackTrace();
                    }
                }
            }

            //now check the servlet flag
            String servletContainer = properties.getProperty("servlet.container", "false");
            if (Boolean.TRUE.toString().equals(servletContainer.trim())) {
                boolean started = false;
                int port = -1;
                try {
                    port = startTomcat(properties, logger);
                    url = "http://localhost:" + port + "/svnkit";
                    for (int i = 0; i < ourLoggers.length; i++) {
                        ourLoggers[i].startServer("tomcat", url);
                    }
                    //wait a little until tomcat
                    Thread.sleep(1000);
                    started = true;
                    runPythonTests(properties, defaultTestSuite, "dav", url, libPath, logger);
                } catch (Throwable th) {
                    th.printStackTrace();
                } finally {
                    try {
                        stopTomcat(properties, logger);
                        if (started) {
                            for (int i = 0; i < ourLoggers.length; i++) {
                                ourLoggers[i].endServer("tomcat", url);
                            }
                        }
                    } catch (Throwable th) {
                        th.printStackTrace();
                    }
                }
            }
        }
    }

    private static void setTestType(String type) {
        ourTestType = type;
    }
    
    public static String getTestType() {
        return ourTestType;
    }
    
    public static File getLogsDirectory() {
        String path = ourProperties.getProperty("python.tests.logDir", "build/logs");
        return new File(path);
    }
    
    public static boolean needsSleepForTimestamp(String testName) {
        String sleepyTestsPattern = ourProperties.getProperty("python.tests.sleepy");
        if (sleepyTestsPattern != null) {
            return Pattern.matches(sleepyTestsPattern, testName);
        }
        return false;
    }
    
    public static boolean isLoggingEnabled() {
        return Boolean.TRUE.toString().equalsIgnoreCase(ourProperties.getProperty("python.tests.logging", "false"));
    }

    private static Logger setupLogging() {
        return setupLogger("python", Level.INFO);
    }

    private static Logger setupLogger(String name, Level level) {
        Logger python = Logger.getLogger(name);
        python.setUseParentHandlers(false);
        python.setLevel(level);
        return python;
    }
    
    private static Handler createLogHandler(File logDirectory, String logName) throws IOException {
        String logFilePattern = logDirectory.getAbsolutePath().replace(File.separatorChar, '/') + "/" + logName + ".log";
        FileHandler fileHandler = new FileHandler(logFilePattern, 0, 1, false);
        fileHandler.setLevel(Level.INFO);
        fileHandler.setFormatter(new DefaultSVNDebugFormatter());
        return fileHandler;
    }

	private static void runPythonTests(Properties properties, String defaultTestSuite, String type, String url, String libPath, Logger pythonLogger) throws IOException {
		String pythonLauncher = properties.getProperty("python.launcher");
		String testSuite = properties.getProperty("python.tests.suite", defaultTestSuite);
		String options = properties.getProperty("python.tests.options", "");
        String testsLocation = properties.getProperty("python.tests", "python/cmdline");
        String listOption = properties.getProperty("python.tests.listOption", "list");
		String fsfsConfig = properties.getProperty("fsfs.config");
		setTestType(type);
		File logsDirectory = getLogsDirectory();
        logsDirectory.mkdirs();

		for (StringTokenizer tests = new StringTokenizer(testSuite, ","); tests.hasMoreTokens();) {
			final String testFileString = tests.nextToken();
			List tokens = tokenizeTestFileString(testFileString);

            String suiteName = (String) tokens.get(0);
            if (regularLoggingEnabled) {
                for (int i = 0; i < ourLoggers.length; i++) {
                    ourLoggers[i].startSuite(getTestType() + "." + suiteName);
                }
            }

			final String testFile = suiteName + "_tests.py";
			tokens = tokens.subList(1, tokens.size());
			
            Handler logHandler = null;
			if (isLoggingEnabled()) {
                logHandler = createLogHandler(logsDirectory, type + "_" + suiteName + "_python");
			    pythonLogger.addHandler(logHandler);
			}
			long startTime = System.currentTimeMillis();
			try {
    			if (tokens.isEmpty() || (tokens.size() == 1 && "ALL".equals(tokens.get(0)))) {
                    processTestCase(pythonLauncher, testsLocation, testFile, options, null, url, libPath, fsfsConfig, pythonLogger);
    			} else {
    	            final List availabledTestCases = getAvailableTestCases(pythonLauncher, testsLocation, testFile, listOption, pythonLogger);
    	            final List testCases = !tokens.isEmpty() ? combineTestCases(tokens, availabledTestCases) : availabledTestCases;
    	            processTestCase(pythonLauncher, testsLocation, testFile, options, testCases, url, libPath, fsfsConfig, pythonLogger);
    			}
			} finally {
			    if (logHandler != null) {
			        logHandler.close();
			        pythonLogger.removeHandler(logHandler);
			    }
			}
            if (regularLoggingEnabled) {
                for (int i = 0; i < ourLoggers.length; i++) {
                    ourLoggers[i].endSuite(getTestType() + "." + suiteName);
                }
            }
		}
	}

	private static void processTestCase(String pythonLauncher, String testsLocation, String testFile, String options, List testCases, 
	        String url, String libPath, String fsfsConfigPath, Logger pythonLogger) {
	    Collection commandsList = new ArrayList();
        commandsList.add(pythonLauncher);
        commandsList.add(testFile);
        commandsList.add("--v");
        commandsList.add("--cleanup");
        commandsList.add("--use-jsvn");        
        commandsList.add("--bin=" + libPath);        
        commandsList.add("--url=" + url);
        if (fsfsConfigPath != null) {
            commandsList.add("--config-file=" + new File(fsfsConfigPath).getAbsolutePath());
        }
        
        if (options != null && !"".equals(options.trim())) {
            commandsList.add(options);
        }
        if (testCases != null) {
            for (Iterator cases = testCases.iterator(); cases.hasNext();) {
                Integer testCase = (Integer) cases.next();
                commandsList.add(String.valueOf(testCase));
            }
        }
        String[] commands = (String[]) commandsList.toArray(new String[commandsList.size()]);

		try {
			Process process = Runtime.getRuntime().exec(commands, null, new File(testsLocation));
			ReaderThread inReader = new ReaderThread(process.getInputStream(), null, pythonLogger);
			inReader.start();
			ReaderThread errReader = new ReaderThread(process.getErrorStream(), null, pythonLogger);
			errReader.start();
			try {
                process.waitFor();
            } catch (InterruptedException e) {
			} finally {
			    inReader.close();
			    errReader.close();
			    process.destroy();
			}
		} catch (Throwable th) {
		    th.printStackTrace();
			pythonLogger.log(Level.SEVERE, "", th);
		}
	}

	private static List tokenizeTestFileString(String testFileString) {
		final StringTokenizer tokenizer = new StringTokenizer(testFileString, " ", false);
		final List tokens = new ArrayList();
		while (tokenizer.hasMoreTokens()) {
			tokens.add(tokenizer.nextToken());
			continue;
		}

		return tokens;
	}

	private static List combineTestCases(List tokens, List availableTestCases) {
		final List combinedTestCases = new ArrayList();
		if (availableTestCases.isEmpty()) {
		    return combinedTestCases;
		}
		Integer endInt = (Integer) availableTestCases.get(availableTestCases.size() - 1);
		Integer startInt = (Integer) availableTestCases.get(0);
		boolean isAllSpecified = false;
		for (Iterator it = tokens.iterator(); it.hasNext();) {
			final String token = (String)it.next();
			if (token.equalsIgnoreCase("all")) {
				isAllSpecified = true;
			    combinedTestCases.addAll(availableTestCases);
				continue;
			}

            if (token.indexOf("-") > 0 || (token.indexOf("-") == 0 && !isAllSpecified)) {
                // parse range
                String startNumber = token.substring(0, token.indexOf("-"));
                String endNumber = token.substring(token.indexOf("-") + 1);
                try {
                    int start = startInt.intValue();
                    int end = endInt.intValue();
                    if (!"".equals(startNumber)) {
                        start = Integer.parseInt(startNumber);
                    }
                    
                    if (!"".equals(endNumber)) {
                        end = Integer.parseInt(endNumber);
                    }
                    
                    if (start > end) {
                        int i = start;
                        start = end;
                        end = i;
                    }
                    for(int i = start; i <= end; i++) {
                        if (availableTestCases.contains(new Integer(i))) {
                            combinedTestCases.add(new Integer(i));
                        }
                    }
                } catch (NumberFormatException nfe) {
                }
                continue;
            }
			final Integer testCase;
			try {
				testCase = new Integer(token);
			} catch (NumberFormatException ex) {
				System.err.println("ERROR: " + ex.getMessage());
				ex.printStackTrace(System.err);
				continue;
			}

			if (testCase.intValue() < 0) {
				combinedTestCases.remove(new Integer(-testCase.intValue()));
			} else if (availableTestCases.contains(testCase)) {
                combinedTestCases.add(testCase);
			}
		}

		return combinedTestCases;
	}

	private static List getAvailableTestCases(String pythonLauncher, String testsLocation, String testFile, String listOption, Logger pythonLogger) throws IOException {
		final String[] commands = new String[]{pythonLauncher, testFile, listOption};
		final ByteArrayOutputStream os = new ByteArrayOutputStream();
		try {
			Process process = Runtime.getRuntime().exec(commands, null, new File(testsLocation));
            ReaderThread readerThread = new ReaderThread(process.getInputStream(), new PrintStream(os), pythonLogger);
            readerThread.start();
			ReaderThread errReader = new ReaderThread(process.getErrorStream(), null, pythonLogger);
			errReader.start();
			try {
				process.waitFor();
                readerThread.join(5000);                
			}
			catch (InterruptedException e) {
			} finally {
			    readerThread.close();
			    errReader.close();
			    process.destroy();
			}
            os.close();
		}
		catch (Throwable th) {
			System.err.println("ERROR: " + th.getMessage());
			th.printStackTrace(System.err);
		}

		final String listString = new String(os.toByteArray());
		final BufferedReader reader = new BufferedReader(new StringReader(listString));
		final List tests = new ArrayList();
		String line;
		while ((line = reader.readLine()) != null) {
		    line = line.trim();
			final StringTokenizer tokenizer = new StringTokenizer(line, " \t", false);
			if (!tokenizer.hasMoreTokens()) {
				continue;
			}

			final String first = tokenizer.nextToken();
			if (first.startsWith("Test") || first.startsWith("---")) {
				continue;
			}

			if (tokenizer.hasMoreTokens()) {
				final String hint = tokenizer.nextToken().trim();
				if (hint.equalsIgnoreCase("SKIP")) {
					continue;
				}
			}

			try {
				tests.add(new Integer(first));
			} catch (NumberFormatException ex) {
			    ex.printStackTrace();
			    continue;
			}
		}
		return tests;
	}

	static class ReaderThread extends Thread {

		private final BufferedReader myInputStream;
		private final PrintStream myHelpStream;
        private boolean myIsClosed;
        private Logger myPythonLogger;
        private StringBuffer myTestOutput;
        
		public ReaderThread(InputStream is, PrintStream helpStream, Logger logger) {
			myInputStream = new BufferedReader(new InputStreamReader(is));
			myHelpStream = helpStream;
			myPythonLogger = logger;
			myTestOutput = new StringBuffer();
			setDaemon(false);
		}
		
		public void close() {
		    if (!myIsClosed) {
		        myIsClosed = true;
		        SVNFileUtil.closeFile(myInputStream);
		    }		    
		}

		public void run() {
		    try {
				String line;
				while ((line = myInputStream.readLine()) != null) {
                    TestResult testResult = TestResult.parse(line);
                    // will be logged to python.log only
                    myPythonLogger.info(line);
                    if (testResult != null) {
                        testResult.setOutput(myTestOutput);
                        myTestOutput = new StringBuffer();

                        if (regularLoggingEnabled) {
                            for (int i = 0; i < ourLoggers.length; i++) {
                                ourLoggers[i].handleTest(testResult);
                            }
                        }

                    } else {
                        myTestOutput.append(line);
                        myTestOutput.append('\n');
                    }
                    
					if (myHelpStream != null) {
						myHelpStream.println(line);
						myHelpStream.flush();
					}
				}
			} catch (IOException e) {
			} finally {
			    if (!myIsClosed) {
			        close();
			    }
			}
		}
	}

	private static String loadDefaultTestSuite() throws IOException {
		final File file = new File("python-suite.txt");
		final BufferedReader reader = new BufferedReader(new FileReader(file));
		final StringBuffer defaultTestSuite = new StringBuffer();
		try {
			for (String line = reader.readLine(); line != null; line = reader.readLine()) {
				if (defaultTestSuite.length() > 0) {
					defaultTestSuite.append(",");
				}

				defaultTestSuite.append(line.trim());
			}
		}
		finally {
			reader.close();
		}

		return defaultTestSuite.toString();
	}
    
    public static Properties loadProperties(File file) throws IOException {
        FileInputStream is = new FileInputStream(file);
        Properties props = new Properties();
        props.load(is);
        is.close();
        return props;
    }
    
    public static String startCommandDaemon(Properties properties) throws IOException {
        int portNumber = getDaemonPortNumber();

        ourDaemon = new NGServer(null, portNumber);        
        Thread daemonThread = new Thread(ourDaemon);
        daemonThread.setDaemon(true);
        daemonThread.start();

        // create client scripts.
        generateScripts(properties);
        return new File("daemon").getAbsolutePath();
    }

    private static void generateScripts(Properties properties) throws IOException {
        int portNumber = getDaemonPortNumber();
        String svnHome = properties.getProperty("svn.home", "/usr/bin");
        File template = SVNFileUtil.isWindows ? new File("daemon/template.bat") : new File("daemon/template");
        File templatePy = SVNFileUtil.isWindows ? new File("daemon/template.py") : null;

        String pattern = properties.getProperty("python.tests.pattern", null);

        generateClientScript(template, new File("daemon/jsvn"), NailgunProcessor.class.getName(), "svn", portNumber, svnHome, pattern);
        generateClientScript(template, new File("daemon/jsvnadmin"), NailgunProcessor.class.getName(), "svnadmin", portNumber, svnHome, pattern);
//        generateClientScript(template, new File("daemon/jsvnversion"), NailgunProcessor.class.getName(), "svnversion", portNumber, svnHome, pattern);
        generateClientScript(template, new File("daemon/jsvnlook"), NailgunProcessor.class.getName(), "svnlook", portNumber, svnHome, pattern);
        generateClientScript(template, new File("daemon/jsvnsync"), NailgunProcessor.class.getName(), "svnsync", portNumber, svnHome, pattern);
        generateClientScript(template, new File("daemon/jsvndumpfilter"), NailgunProcessor.class.getName(), "svndumpfilter", portNumber, svnHome, pattern);

        generateProxyScript("jsvnmucc", "svnmucc", svnHome);
        generateProxyScript("jsvnversion", "svnversion", svnHome);

        if (SVNFileUtil.isWindows) {
            generateClientScript(templatePy, new File("daemon/jsvn.py"), NailgunProcessor.class.getName(), "svn", portNumber, svnHome, pattern);
            generateClientScript(templatePy, new File("daemon/jsvnadmin.py"), NailgunProcessor.class.getName(), "svnadmin", portNumber, svnHome, pattern);
            generateClientScript(templatePy, new File("daemon/jsvnversion.py"), NailgunProcessor.class.getName(), "svnversion", portNumber, svnHome, pattern);
            generateClientScript(templatePy, new File("daemon/jsvnlook.py"), NailgunProcessor.class.getName(), "svnlook", portNumber, svnHome, pattern);
            generateClientScript(templatePy, new File("daemon/jsvnsync.py"), NailgunProcessor.class.getName(), "svnsync", portNumber, svnHome, pattern);
            generateClientScript(templatePy, new File("daemon/jsvndumpfilter.py"), NailgunProcessor.class.getName(), "svndumpfilter", portNumber, svnHome, pattern);
        }

        if (pattern != null) {
            generateMatcher(new File("daemon/matcher.pl.template"), new File("daemon/matcher.pl"), pattern);
        } else {
           try {
               SVNFileUtil.deleteFile(new File("daemon/matcher.pl"));
           } catch (SVNException e) {}
        }
        SVNFileUtil.setExecutable(new File("daemon/snapshot"), Boolean.TRUE.toString().equalsIgnoreCase(properties.getProperty("snapshot", "false")));
    }

    private static void generateMatcher(String pattern) {
        if (pattern != null) {
            try {
                generateMatcher(new File("daemon/matcher.pl.template"), new File("daemon/matcher.pl"), pattern);
            } catch (IOException e) {}
        } else {
           try {
               SVNFileUtil.deleteFile(new File("daemon/matcher.pl"));
           } catch (SVNException e) {}
        }
    }

    private static void generateProxyScript(String jsvnName, String svnName, String svnHome) {
        File svnMuccScriptFile = new File("daemon/" + jsvnName);
        try {
            SVNFileUtil.writeToFile(svnMuccScriptFile, "#!/bin/bash\n" + svnHome + "/bin/" + svnName + " \"$@\" < /dev/stdin\nexit $?", "UTF-8");
            SVNFileUtil.setExecutable(svnMuccScriptFile, true);
        } catch (SVNException e) {
        }
    }
    
    public static int startSVNServe(Properties props) throws Throwable {
        String path = getRepositoryRoot(props);
        
        int portNumber = 3690;
        try {
            portNumber = Integer.parseInt(props.getProperty("svn.port", "3690"));
        } catch (NumberFormatException nfe) {
        }
        portNumber = findUnoccupiedPort(portNumber);
        
        String svnserve = props.getProperty("svnserve.path");
        String[] command = {svnserve, "-d", "--foreground", "--listen-port", portNumber + "", "-r", path};
        ourSVNServer = Runtime.getRuntime().exec(command);
        return portNumber;
    }
    
    public static void stopSVNServe() {
        if (ourSVNServer != null) {
            try {
                ourSVNServer.getInputStream().close();
                ourSVNServer.getErrorStream().close();
            } catch (IOException e) {
            }
            ourSVNServer.destroy();
            try {
                ourSVNServer.waitFor();
            } catch (InterruptedException e) {
            }
        }
    }

    public static int startApache(Properties props, Logger pythonLogger) throws Throwable {
        return apache(props, -1, true, pythonLogger);
    }

    public static void stopApache(Properties props, int port, Logger pythonLogger) throws Throwable {
        apache(props, port, false, pythonLogger);
        // delete apache log.
        File file = new File(System.getProperty("user.home"), "httpd." + port + ".error.log");
        SVNFileUtil.deleteFile(file);
    }
    
    private static int apache(Properties props, int port, boolean start, Logger pythonLogger) throws Throwable {
        String[] command = null;
        File configFile = File.createTempFile("jsvn.", ".apache.config.tmp");
        configFile.deleteOnExit();
        String path = configFile.getAbsolutePath().replace(File.separatorChar, '/');
        port = generateApacheConfig(configFile, props, port);

        String apache = props.getProperty("apache.path");
        command = new String[] {apache, "-f", path, "-k", (start ? "start" : "stop")};
        execCommand(command, start, pythonLogger);
        return port;
    }
    
    private static int generateApacheConfig(File destination, Properties props, int port) throws IOException {
        File template = new File(props.getProperty("apache.conf", "apache/httpd.template.conf"));
        byte[] contents = new byte[(int) template.length()];
        InputStream is = new FileInputStream(template);
        SVNFileUtil.readIntoBuffer(is, contents, 0, contents.length);
        is.close();
        
        File passwdFile = new File("apache/passwd");
        
        if (port < 0) {
            port = 8082;
            try {
                port = Integer.parseInt(props.getProperty("apache.port", "8082"));
            } catch (NumberFormatException nfe) {
            }
            port = findUnoccupiedPort(port);
        }
        
        String config = new String(contents);
        String root = props.getProperty("apache.root");
        config = config.replaceAll("%root%", root);
        config = config.replaceAll("%port%", port + "");
        String path = getRepositoryRoot(props);
        config = config.replaceAll("%repository.root%", path);
        config = config.replaceAll("%passwd%", passwdFile.getAbsolutePath().replace(File.separatorChar, '/'));
        config = config.replaceAll("%home%", System.getProperty("user.home").replace(File.separatorChar, '/'));
        
        String pythonTests = new File(props.getProperty("python.tests")).getAbsolutePath().replace(File.separatorChar, '/');
        config = config.replaceAll("%python.tests%", pythonTests);
        String apacheOptions = props.getProperty("apache.options", "");
        config = config.replaceAll("%apache.options%", apacheOptions);
        String apacheModules = props.getProperty("apache.svn.modules", root + "/modules");
        config = config.replaceAll("%apache.svn.modules%", apacheModules);
        
        FileOutputStream os = new FileOutputStream(destination);
        os.write(config.getBytes());
        os.close();
        return port;
    }

    public static int startTomcat(Properties props, Logger pythonLogger) throws Throwable {
        return tomcat(props, -1, -1, true, pythonLogger);
    }

    public static void stopTomcat(Properties props, Logger pythonLogger) throws Throwable {
        tomcat(props, -1, -1, false, pythonLogger);
    }

    private static int tomcat(Properties props, int serverPort, int connectorPort, boolean start, Logger pythonLogger) throws Throwable {
        if (start) {
            connectorPort = generateTomcatServerXML(props, serverPort, connectorPort);
        }

        String catalina = "tomcat/bin/catalina.sh";
        String[] command = new String[] {catalina, (start ? "start" : "stop")};
        execCommand(command, start, pythonLogger);
        return connectorPort;
    }

    private static int generateTomcatServerXML(Properties props, int serverPort, int connectorPort) throws IOException {
        File template = new File(props.getProperty("server.xml", "tomcat/conf/server.xml"));
        byte[] contents = new byte[(int) template.length()];
        InputStream is = new FileInputStream(template);
        SVNFileUtil.readIntoBuffer(is, contents, 0, contents.length);
        is.close();
        
        if (serverPort < 0) {
            serverPort = 8006;
            try {
                serverPort = Integer.parseInt(props.getProperty("tomcat.server.port", "8006"));
            } catch (NumberFormatException nfe) {
            }
            serverPort = findUnoccupiedPort(serverPort);
        }
        
        if (connectorPort < 0) {
            connectorPort = 8181;
            try {
                connectorPort = Integer.parseInt(props.getProperty("tomcat.connector.port", "8181"));
            } catch (NumberFormatException nfe) {
            }
            connectorPort = findUnoccupiedPort(connectorPort);
        }
        
        String config = new String(contents);
        config = config.replaceAll("%server.port%", serverPort + "");
        config = config.replaceAll("%connector.port%", connectorPort + "");
        
        FileOutputStream os = new FileOutputStream(template);
        os.write(config.getBytes());
        os.close();
        return connectorPort;
    }

    private static void generateClientScript(File src, File destination, String mainClass, String name, int port, String svnHome, String pattern) throws IOException {
        byte[] contents = new byte[(int) src.length()];
        InputStream is = new FileInputStream(src);
        SVNFileUtil.readIntoBuffer(is, contents, 0, contents.length);
        is.close();

        String script = new String(contents);
        script = script.replaceAll("%mainclass%", mainClass);
        script = script.replaceAll("%name%", name);
        script = script.replaceAll("%port%", Integer.toString(port));
        script = script.replaceAll("%svn_home%", new File(svnHome).getAbsolutePath().replace(File.separatorChar, '/'));
        script = script.replaceAll("%NG%", new File("daemon/ng").getAbsolutePath().replace(File.separatorChar, '/'));
        script = script.replaceAll("%svn_test_work%", new File("svn-python-tests/svn-test-work").getAbsolutePath().replace(File.separatorChar, '/'));
        if (pattern != null) {
            script = script.replace("%pattern%", pattern);
        }        
        if (!destination.getName().endsWith(".py")) {
            script = script.replace('/', File.separatorChar);
        }
        if (SVNFileUtil.isWindows && !destination.getName().endsWith(".py")) {
            destination = new File(destination.getParentFile(), destination.getName() + ".bat");
        } 
        
        FileOutputStream os = new FileOutputStream(destination);
        os.write(script.getBytes());
        os.close();
        
        SVNFileUtil.setExecutable(destination, true);
    }

    private static void generateMatcher(File src, File destination, String pattern) throws IOException {
        byte[] contents = new byte[(int) src.length()];
        InputStream is = new FileInputStream(src);
        SVNFileUtil.readIntoBuffer(is, contents, 0, contents.length);
        is.close();

        String script = new String(contents);
        script = script.replace("%pattern%", pattern);
        
        FileOutputStream os = new FileOutputStream(destination);
        os.write(script.getBytes());
        os.close();
        
        SVNFileUtil.setExecutable(destination, true);
    }
    
    private static int findUnoccupiedPort(int port) {
        ServerSocket socket = null;
        try {
            socket = new ServerSocket();
            socket.bind(null);
            return socket.getLocalPort();
        } catch (IOException e) {
            return port;
        } finally {
            if (socket != null) {
                try {
                    socket.close();
                } catch (IOException e) {
                }
            }
        }
    }

    private static String getRepositoryRoot(Properties props) {
        String path = props.getProperty("repository.root");
        path = path.replaceAll("%home%", System.getProperty("user.home").replace(File.separatorChar, '/'));
        path = path.replace(File.separatorChar, '/');
        new File(path).mkdirs();
        return path;
    }
    
    private static Process execCommand(String[] command, boolean wait, Logger pythonLogger) throws IOException {
        Process process = Runtime.getRuntime().exec(command);
        if (process != null) {
            try {
                new ReaderThread(process.getInputStream(), null, pythonLogger).start();
                new ReaderThread(process.getErrorStream(), null, pythonLogger).start();
                if (wait) {
                    int code = process.waitFor();
                    if (code != 0) {
                        StringBuffer commandLine = new StringBuffer();
                        for (int i = 0; i < command.length; i++) {
                            commandLine.append(command[i]);
                            if (i + 1 != command.length) {
                                commandLine.append(' ');
                            }
                        }
                        throw new IOException("process '"  +  commandLine + "' exit code is not 0 : " + code);
                    }
                }
            } catch (InterruptedException e) {
                throw new IOException("interrupted");
            }
        }
        return process;
    }
}
