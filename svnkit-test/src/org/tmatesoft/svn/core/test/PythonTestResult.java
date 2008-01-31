/*
 * ====================================================================
 * Copyright (c) 2004-2008 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.core.test;

import java.util.regex.Matcher;
import java.util.regex.Pattern;


/**
 * @version 1.1.1
 * @author  TMate Software Ltd.
 */
public class PythonTestResult {
    
    private boolean myIsPass;
    private String myName;
    private int myID;
    
    public static PythonTestResult parse(String line) {
        if (line != null && (line.startsWith("PASS: ") || line.startsWith("FAIL: "))) {
            String regexp = "([PASFIL]{4})(.*\\.py)[\u0020]*(\\d+)[:\u0020]+(.+)";
            Pattern pattern = Pattern.compile(regexp, Pattern.DOTALL);
            
            Matcher matcher = pattern.matcher(line);
            if(matcher.find()){
                String name = matcher.group(4);
                name = name.replaceAll("\"", "'");
                String id = matcher.group(3);
                String result = matcher.group(1);
                return new PythonTestResult(name, id, "PASS".equalsIgnoreCase(result));
            }
        }
        return null;
    }
    
    private PythonTestResult(String test, String id, boolean pass) {
        myName = test;
        myID = Integer.parseInt(id);
        myIsPass = pass;
    }

    public int getID() {
        return myID;
    }

    public boolean isPass() {
        return myIsPass;
    }

    public String getName() {
        return myName;
    }
}
