/*
 * ====================================================================
 * Copyright (c) 2004 TMate Software Ltd.  All rights reserved.
" *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://tmate.org/svn/license.html.
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */

package org.tmatesoft.svn.core.test;

import java.io.*;
import java.util.*;
import java.util.logging.*;
import org.tmatesoft.svn.util.*;

/**
 * @author TMate Software Ltd.
 */
public class PythonTests {

	private static File ourPropertiesFile;

	public static void main(String[] args) {
		String fileName = args[0];
		ourPropertiesFile = new File(fileName);

		try {
			// 1. start svnserve
			Properties properties = AllTests.loadProperties(ourPropertiesFile);
			String pythonTestsRoot = properties.getProperty("python.tests");
			properties.setProperty("repository.root", new File(pythonTestsRoot).getAbsolutePath());

			AllTests.startSVNServe(properties);

			// 3. run python tests.
			String pythonLauncher = properties.getProperty("python.launcher");
			String testSuite = properties.getProperty("python.tests.suite");
			String options = properties.getProperty("python.tests.options", "");
			for (StringTokenizer tests = new StringTokenizer(testSuite, ","); tests.hasMoreTokens();) {
				final String testFileString = tests.nextToken();
				List tokens = tokenizeTestFileString(testFileString);

				final String testFile = tokens.get(0) + "_tests.py";
				tokens = tokens.subList(1, tokens.size());

				if (tokens.isEmpty()) {
					System.out.println("PROCESSING ALL " + testFile);
					processTestCase(pythonLauncher, testFile, options, "");
				}
				else {
					final List availabledTestCases = getAvailableTestCases(pythonLauncher, testFile);
					final List testCases = combineTestCases(tokens, availabledTestCases);

					System.out.println("PROCESSING " + testFile + " " + testCases);
					for (Iterator it = testCases.iterator(); it.hasNext();) {
						final Integer testCase = (Integer)it.next();
						processTestCase(pythonLauncher, testFile, options, String.valueOf(testCase));
					}
				}
			}
		}
		catch (Throwable th) {
			th.printStackTrace();
		}
		finally {
			AllTests.stopSVNServe();
		}
	}

	private static void processTestCase(String pythonLauncher, String testFile, String options, String testCase) {
		String[] commands = new String[]{
			pythonLauncher,
			testFile,
			"-v",
			"--url=svn://localhost",
			options,
			String.valueOf(testCase),
		};

		try {
			Process process = Runtime.getRuntime().exec(commands, null, new File("python/cmdline"));
			new ReaderThread(process.getInputStream(), null).start();
			new ReaderThread(process.getErrorStream(), null).start();
			try {
				process.waitFor();
			}
			catch (InterruptedException e) {
			}
		}
		catch (Throwable th) {
			System.err.println("ERROR: " + th.getMessage());
			th.printStackTrace(System.err);
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
		for (Iterator it = tokens.iterator(); it.hasNext();) {
			final String token = (String)it.next();
			if (token.equalsIgnoreCase("all")) {
				combinedTestCases.addAll(availableTestCases);
				continue;
			}

			final Integer testCase;
			try {
				testCase = new Integer(token);
			}
			catch (NumberFormatException ex) {
				System.err.println("ERROR: " + ex.getMessage());
				ex.printStackTrace(System.err);
				continue;
			}

			if (testCase.intValue() < 0) {
				combinedTestCases.remove(new Integer(-testCase.intValue()));
			}
			else {
				combinedTestCases.add(testCase);
			}
		}

		return combinedTestCases;
	}

	private static List getAvailableTestCases(String pythonLauncher, String testFile) throws IOException {
		final String[] commands = new String[]{pythonLauncher, testFile, "list"};
		final ByteArrayOutputStream os = new ByteArrayOutputStream();
		try {
			Process process = Runtime.getRuntime().exec(commands, null, new File("python/cmdline"));
			new ReaderThread(process.getInputStream(), new PrintStream(os)).start();
			new ReaderThread(process.getErrorStream(), null).start();
			try {
				Thread.sleep(1000);
				process.waitFor();
			}
			catch (InterruptedException e) {
			}
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
			final StringTokenizer tokenizer = new StringTokenizer(line, " \t", false);
			if (!tokenizer.hasMoreTokens()) {
				continue;
			}

			final String first = tokenizer.nextToken();
			if (first.startsWith("Test") || first.startsWith("---")) {
				continue;
			}

			if (tokenizer.hasMoreTokens()) {
				final String hint = tokenizer.nextToken();
				if (hint.equalsIgnoreCase("SKIP")) {
					continue;
				}
			}

			try {
				tests.add(new Integer(first));
			}
			catch (NumberFormatException ex) {
				System.err.println("ERROR: " + ex.getMessage());
				ex.printStackTrace(System.err);
			}
		}

		return tests;
	}

	static class ReaderThread extends Thread {

		private final BufferedReader myInputStream;
		private final PrintStream myHelpStream;

		public ReaderThread(InputStream is, PrintStream helpStream) {
			myInputStream = new BufferedReader(new InputStreamReader(is));
			myHelpStream = helpStream;
			setDaemon(false);
		}

		public void run() {
			try {
				String line;
				while ((line = myInputStream.readLine()) != null) {
					DebugLog.log(Level.CONFIG, line);
					if (myHelpStream != null) {
						myHelpStream.println(line);
						myHelpStream.flush();
					}
					System.err.flush();
					System.out.flush();

					if (line != null && (line.startsWith("PASS: ") || line.startsWith("FAIL: "))) {
						System.out.println(line);
					}
				}
			}
			catch (IOException e) {
				e.printStackTrace();
			}
		}
	}
}
