package org.tmatesoft.svn.core.test;

import java.io.IOException;
import java.util.Properties;

public class ConsoleLogger extends AbstractTestLogger {

	private long startTime;
	private int totalTestCount;
	private int failedTestCount;
    private String suiteName;

	@Override
	public void startTests(Properties configuration) throws IOException {
	}

	@Override
	public void startServer(String name, String url) {
		System.out.println("RUNNING TESTS AGAINST '" + url + "'");
	}

	@Override
	public void startSuite(String suiteName) {
        System.out.println("SUITE " + suiteName);
        startTime = System.currentTimeMillis();
        this.suiteName = suiteName;
	}

	@Override
	public void handleTest(TestResult test) {
	    String testNumber = Integer.toString(test.getID());
	    while(testNumber.length() < 3) {
	        testNumber = "0" + testNumber;
	    }
	    String testId = suiteName + "." + testNumber;
        System.out.println((test.isPass() ? "OK " : "FAIL ") + testId  + " ["+ test.getName() + "]");
        totalTestCount++;
        if (!test.isPass()) {
        	failedTestCount++;
        }
	}

	@Override
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

	@Override
	public void endServer(String name, String url) {
	}

	@Override
	public void endTests(Properties configuration) {
	}

}
