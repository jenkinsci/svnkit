/*
 * ====================================================================
 * Copyright (c) 2004 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://tmate.org/svn/license.html.
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.core.test;

import java.util.Properties;
import java.io.File;
import java.io.PrintWriter;
import java.io.FileWriter;
import java.io.FileOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Map;
import java.util.HashMap;
import java.util.LinkedList;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Calendar;

import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;


/**
 * @version 1.0
 * @author  TMate Software Ltd.
 */
public class XMLLogger extends AbstractPythonTestLogger {
    private String myXMLResultsFile;
    private PrintWriter myWriter;
    private LinkedList myResults;
    private String myXSLStylesheet;
    private String myHTMLResultsFile;

    private Properties myConfiguration;
    private String curSuite;
    private int curSuitePassed;
    private int curSuiteCount;
    private long startTimeMillis;
    private long endTimeMillis;

    private Map mySuitesStat;
    
    public void startTests(Properties configuration) throws IOException {
		myConfiguration = configuration; 

		myXMLResultsFile = myConfiguration.getProperty("tests.xml.results", "results.xml"); 		
		myHTMLResultsFile = myConfiguration.getProperty("tests.html.results", "../www/download/results.html");
		myXSLStylesheet = myConfiguration.getProperty("tests.xsl", "PythonTests.xsl");
		File resultsFile = new File(myXMLResultsFile);
		
		if(!resultsFile.exists()){
	        resultsFile.createNewFile();
		}

	    myWriter = new PrintWriter(new FileWriter(resultsFile));
	    myResults = new LinkedList();
	    mySuitesStat = new HashMap();
	    
	    String resultString = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>";
	    myResults.addFirst(resultString);   
	    
	    startTimeMillis = Calendar.getInstance().getTimeInMillis();
	    resultString = "PythonTests";
	    myResults.addFirst(resultString);   
    }

    public void startServer(String name, String url) {
	    String resultString = "  <server name=\"" + name + "\" url=\"" + url + "\">";
	    myResults.addFirst(resultString);   
    }

    public void startSuite(String suiteName) {
	    String resultString = "suite" + suiteName;
	    myResults.addFirst(resultString);
	    curSuite = suiteName;
	    curSuiteCount = 0;
	    curSuitePassed = 0;
    }

    public void handleTest(PythonTestResult test) {
        String name = test.getName();
        int id = test.getID();
        String result = test.isPass() ? "PASSED" : "FAILED";

        String resultString = "      <test name=\""+ name + "\" id=\"" + id + "\" result=\"" + result + "\"></test>";
	    myResults.addFirst(resultString);
	    curSuiteCount++;
	    if(test.isPass()){
	        curSuitePassed++;
	    }
    
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
    }

    public void endTests(Properties configuration) throws Throwable {
	    String resultString = "</PythonTests>";
	    myResults.addFirst(resultString);
	    
	    endTimeMillis = Calendar.getInstance().getTimeInMillis();
	    
	    String line = null;
	    while(true){
		    if(!myResults.isEmpty()){
		        line = (String)myResults.removeLast();
		    }else{
		        break;
		    }
		    if(line.startsWith("PythonTests")){
			    DateFormat simpleDateFormat = new SimpleDateFormat("yyyy.MM.dd  'at' HH:mm:ss z"); 
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
			    
			    line = "<PythonTests start=\"" + simpleDateFormat.format(new Date(startTimeMillis)) + "\" elapsed=\"" + elapsedTimeString + "\">";
		    }
		    
		    if(line.startsWith("suite")){
	            String suiteName = line.substring("suite".length());
	            SuiteStatistics stat = (SuiteStatistics)mySuitesStat.remove(suiteName);
	            int failed = stat.suitesCount - stat.suitesPassed;
	            line = "    <suite name=\"" + suiteName + "\" total=\"" + stat.suitesCount + "\" passed=\"" + stat.suitesPassed + "\" failed=\"" + failed + "\">";
	        }
	        myWriter.println(line);
		    myWriter.flush();
	    }
	    

	    TransformerFactory tFactory = TransformerFactory.newInstance();
		try{
		    Transformer transformer = tFactory.newTransformer(new StreamSource(myXSLStylesheet));
			transformer.transform(new StreamSource(myXMLResultsFile), new StreamResult(new FileOutputStream(myHTMLResultsFile)));
		}catch(TransformerConfigurationException tce){
		    throw new Throwable("Can't create a transformer: "+tce.getMessage());
		}catch(TransformerException te){
	        throw new Throwable("Can't transform: "+te.getMessage());
		}catch(FileNotFoundException fnfe){
	        throw new Throwable("Can't find input xml file: "+fnfe.getMessage());
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
