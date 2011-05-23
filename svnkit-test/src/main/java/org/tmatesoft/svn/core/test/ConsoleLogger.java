package org.tmatesoft.svn.core.test;

import java.io.IOException;
import java.util.Properties;

public class ConsoleLogger extends AbstractTestLogger {

	private long startTime;
	private int totalTestCount;
	private int failedTestCount;

	public void startTests(Properties configuration) throws IOException {
	}

	public void startServer(String name, String url) {
		System.out.println("RUNNING TESTS AGAINST '" + url + "'");
	}

	public void startSuite(String suiteName) {
        System.out.println("SUITE " + suiteName);
        startTime = System.currentTimeMillis();
	}

	public void handleTest(TestResult test) {
        System.out.println((test.isPass() ? "OK [" : "FAIL [") + test.getName() + "]");
        totalTestCount++;
        if (!test.isPass()) {
        	failedTestCount++;
        }
	}

	public void endSuite(String suiteName) {
		long time = (System.currentTimeMillis() - startTime);
		if (totalTestCount > 0) {
			System.out.println("SUITE " + suiteName + " took " + time + " ms.");
			System.out.println(failedTestCount + " of " + totalTestCount + " failed");
		} else {
			System.out.println("EMPTY SUITE");
		}
        totalTestCount = 0;
        failedTestCount = 0;
	}

	public void endServer(String name, String url) {
	}
	public void endTests(Properties configuration) {
	}

}
