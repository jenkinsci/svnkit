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


/**
 * @version 1.0
 * @author  TMate Software Ltd.
 */
public class ConsoleLogger extends AbstractPythonTestLogger {
    private String curSuite;
    
    public void startTests(Properties configuration) {
    }

    public void startServer(String name, String url) {
        System.out.println("Starting server: "+name+", url: "+url);
    }

    public void startSuite(String suiteName) {
        curSuite = suiteName;
    }

    public void handleTest(PythonTestResult test) {
        String testOut = (test.isPass() ? "PASSED: " : "FAILED: ") + curSuite + "_tests.py " + test.getID() + ": " + test.getName();
        System.out.println(testOut);
    }

    public void endSuite(String suiteName) {
    }

    public void endServer(String name, String url) {
        System.out.println("Stopping server: "+name+", url: "+url);
    }

    public void endTests(Properties configuration) {
    }

}
