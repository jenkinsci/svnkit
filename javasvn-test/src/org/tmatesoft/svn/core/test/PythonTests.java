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

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Properties;
import java.util.StringTokenizer;
import java.util.logging.Level;

import org.tmatesoft.svn.util.DebugLog;

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
            for(StringTokenizer tests = new StringTokenizer(testSuite, ","); tests.hasMoreTokens();) {
                String testFile = tests.nextToken();
                testFile = testFile.trim();                
                String testNumber = "";
                if (testFile.indexOf(' ') >= 0) {
                    testNumber = testFile.substring(testFile.lastIndexOf(' ') + 1);
                    testFile = testFile.substring(0, testFile.indexOf(' '));
                }
                testFile = testFile + "_tests.py";
                String[] commands = new String[] {
                        pythonLauncher,
                        testFile,
                        "-v",
                        "--url=svn://localhost",
                        options,
                        testNumber,
                };      
                System.out.println("RUNNING PYTHON TESTS: " + testFile + " " + testNumber);
                try {
                    Process process = Runtime.getRuntime().exec(commands, null, new File("python/cmdline"));
                    new ReaderThread(process.getInputStream()).start();
                    new ReaderThread(process.getErrorStream()).start();
                    try {
                        process.waitFor();
                    } catch (InterruptedException e) {                
                    }
                } catch (Throwable th) {
                    System.err.println("ERROR: " + th.getMessage());
                    th.printStackTrace(System.err);
                }
            }
        } catch(Throwable th) {
            th.printStackTrace();
        } finally {
            AllTests.stopSVNServe();
        }
   }

    static class ReaderThread extends Thread {
        
        private final BufferedReader myInputStream;

        public ReaderThread(InputStream is) {
            myInputStream = new BufferedReader(new InputStreamReader(is));
            setDaemon(false);            
        }

        public void run() {
            try {
                String line;
                while((line = myInputStream.readLine()) != null) {
                    DebugLog.log(Level.CONFIG, line);
                    System.err.flush();
                    System.out.flush();
                    if (line != null && (line.startsWith("PASS: ") || line.startsWith("FAIL: "))) {
                        System.out.println(line);
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            } 
        }
    }
}
