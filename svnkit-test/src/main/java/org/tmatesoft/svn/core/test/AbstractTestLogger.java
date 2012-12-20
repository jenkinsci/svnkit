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

import java.util.Properties;
import java.io.IOException;


/**
 * @version 1.3
 * @author  TMate Software Ltd.
 */
public abstract class AbstractTestLogger {
    
    public abstract void startTests(Properties configuration) throws IOException;

    public abstract void startServer(String name, String url);
    
    public abstract void startSuite(String suiteName);

    public abstract void handleTest(TestResult test);
    
    public abstract void endSuite(String suiteName);
    
    public abstract void endServer(String name, String url);
    
    public abstract void endTests(Properties configuration);
}
