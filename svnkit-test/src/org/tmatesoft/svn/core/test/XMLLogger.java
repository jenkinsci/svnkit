/*
 * ====================================================================
 * Copyright (c) 2004-2009 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.core.test;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.LinkedList;
import java.util.Map;
import java.util.Properties;

import org.tmatesoft.svn.core.internal.util.SVNHashMap;

/**
 * @version 1.2.0
 * @author  TMate Software Ltd.
 */
public class XMLLogger extends AbstractTestLogger {
    private String myXMLResultsFile;
    private PrintWriter myWriter;
    private LinkedList myResults;

    private Properties myConfiguration;
    private String curSuite;
    private String curServer;
    private int curSuitePassed;
    private int curSuiteCount;
    private long startTimeMillis;
    private long endTimeMillis;
    private Map myServersToURLs;
    private Map mySuitesStat;
    private Map myServers;
    
    public void startTests(Properties configuration) throws IOException {
		myConfiguration = configuration; 
		myXMLResultsFile = myConfiguration.getProperty("tests.xml.results", "python-tests-log.xml"); 		
		File resultsFile = new File(myXMLResultsFile);
		
		if(!resultsFile.exists()){
	        resultsFile.createNewFile();
		}

	    myWriter = new PrintWriter(new FileWriter(resultsFile));
	    myResults = new LinkedList();
	    myServers = new SVNHashMap();
        myServersToURLs = new SVNHashMap();
        
	    String resultString = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>";
	    myResults.addFirst(resultString);   
	    
	    startTimeMillis = Calendar.getInstance().getTimeInMillis();
	    resultString = "PythonTests";
	    myResults.addFirst(resultString);   
    }

    public void startServer(String name, String url) {
	    myServersToURLs.put(name, url);
        curServer = name;
	    String resultString = "server" + name;
        myResults.addFirst(resultString);   
        mySuitesStat = new SVNHashMap();
    
    }

    public void startSuite(String suiteName) {
        String resultString = "suite" + curServer + suiteName;
	    myResults.addFirst(resultString);
	    curSuite = suiteName;
        curSuiteCount = 0;
	    curSuitePassed = 0;
    }

    public void handleTest(TestResult test) {
        String name = validateTestName(test.getName());
        int id = test.getID();
        String result = test.isPass() ? "PASSED" : "FAILED";

        String resultString = "      <test name=\""+ name + "\" id=\"" + id + "\" result=\"" + result + "\"></test>";
	    myResults.addFirst(resultString);
	    curSuiteCount++;
	    if(test.isPass()){
	        curSuitePassed++;
	    }
    
    }

    private String validateTestName(String text) {
        text = text.replaceAll("&", "&amp;");
        text = text.replaceAll("\"", "&quot;");
        text = text.replaceAll(">", "&gt;");
        text = text.replaceAll("<", "&lt;");
        return text;
    }
    
    public void endSuite(String suiteName) {
	    String resultString = "    </suite>";
	    myResults.addFirst(resultString);
	    SuiteStatistics suiteStat = new SuiteStatistics(curSuiteCount, curSuitePassed);
	    mySuitesStat.put(curSuite, suiteStat);
    }

    public void endServer(String name, String url) {
	    String resultString = "  </server>";
	    myResults.addFirst(resultString);   
	    myServers.put(name, mySuitesStat);
    }

    public void endTests(Properties configuration) {
	    String resultString = "</PythonTests>";
	    myResults.addFirst(resultString);
	    
	    endTimeMillis = Calendar.getInstance().getTimeInMillis();
	    
        String currentServer = null;
	    String line = null;
	    while (!myResults.isEmpty()) {
	        line = (String) myResults.removeLast();
		    if (line.startsWith("PythonTests")) {
			    DateFormat simpleDateFormat = new SimpleDateFormat("yyyy.MM.dd  'at' HH:mm:ss z"); 
		        long elapsed = endTimeMillis - startTimeMillis;
			    int hours = (int) elapsed/3600000;
			    int minutes = (int) (elapsed - hours*3600000)/60000;
			    int seconds = (int) (elapsed - hours*3600000 - minutes*60000)/1000;
			    String elapsedTimeString = null;
			    if (hours == 0 && minutes == 0) {
			        elapsedTimeString = seconds + " seconds";
			    } else if(hours == 0) {
			        elapsedTimeString = minutes + " minutes " + seconds + " seconds";
			    } else {
			        elapsedTimeString = hours + " hours " + minutes + " minutes " + seconds + " seconds";
			    }
			    line = "<PythonTests start=\"" + simpleDateFormat.format(new Date(startTimeMillis)) + "\" elapsed=\"" + elapsedTimeString + "\">";
		    } else if (line.startsWith("server")) {
                currentServer = line.substring("server".length());
                line = "  <server name=\"" + currentServer + "\" url=\"" + myServersToURLs.get(currentServer) + "\">";
            } else if (line.startsWith("suite")) {
                String suiteName = line.substring(("suite" + currentServer).length());
                Map statistics = (Map) myServers.get(currentServer);
                if (statistics != null) {
                    SuiteStatistics stat = (SuiteStatistics)statistics.remove(suiteName);
    	            int failed = stat.suitesCount - stat.suitesPassed;
    	            line = "    <suite name=\"" + suiteName + "\" total=\"" + stat.suitesCount + "\" passed=\"" + stat.suitesPassed + "\" failed=\"" + failed + "\">";
                }
	        } 
	        myWriter.println(line);
		    myWriter.flush();
	    }
	    
    }

    private class SuiteStatistics{
        public int suitesCount;
        public int suitesPassed;

        public SuiteStatistics(int total, int passed){
            suitesCount = total;
            suitesPassed = passed;
        }
    }
}
