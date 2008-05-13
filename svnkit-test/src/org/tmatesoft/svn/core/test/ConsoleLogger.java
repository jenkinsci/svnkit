/*
 * ====================================================================
 * Copyright (c) 2004-2007 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.core.test;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Properties;


/**
 * @version 1.1.1
 * @author  TMate Software Ltd.
 */
public class ConsoleLogger extends AbstractTestLogger {
    private String curSuite;
    private long startTimeMillis;
    private long endTimeMillis;
    private int curSuiteCount;
    private int curSuitePassed;
    
    public void startTests(Properties configuration) {
	    startTimeMillis = Calendar.getInstance().getTimeInMillis();
	    DateFormat simpleDateFormat = new SimpleDateFormat("yyyy.MM.dd  'at' HH:mm:ss z"); 
	    System.out.println("Python tests started on "+simpleDateFormat.format(new Date(startTimeMillis)));
    }

    public void startServer(String name, String url) {
        System.out.println("Starting server: "+name+", url: "+url);
    }

    public void startSuite(String suiteName) {
        curSuite = suiteName;
	    curSuiteCount = 0;
	    curSuitePassed = 0;
        
    }

    public void handleTest(TestResult test) {
        String testOut = (test.isPass() ? "PASSED: " : "FAILED: ") + curSuite + "_tests.py " + test.getID() + ": " + test.getName();
        System.out.println(testOut);
	    curSuiteCount++;
	    if(test.isPass()){
	        curSuitePassed++;
	    }
    }

    public void endSuite(String suiteName) {
        System.out.println(suiteName+" suite: total: " + curSuiteCount + ", passed: " + curSuitePassed + ", failed: " + (curSuiteCount - curSuitePassed));
    }

    public void endServer(String name, String url) {
        System.out.println("Stopping server: "+name+", url: "+url);
    }

    public void endTests(Properties configuration) {
	    endTimeMillis = Calendar.getInstance().getTimeInMillis();
        long elapsed = endTimeMillis - startTimeMillis;
	    int hours = (int)elapsed/3600000;
	    int minutes = (int)(elapsed - hours*3600000)/60000;
	    int seconds = (int)(elapsed - hours*3600000 - minutes*60000)/1000;
	    String elapsedTimeString = null;
	    if(hours == 0 && minutes == 0){
	        elapsedTimeString = seconds + " seconds";
	    }else if(hours == 0){
	        elapsedTimeString = minutes + " minutes " + seconds + " seconds";
	    }else{
	        elapsedTimeString = hours + " hours " + minutes + " minutes " + seconds + " seconds";
	    }
	    System.out.println("Total time elapsed: " + elapsedTimeString);
    }

}
