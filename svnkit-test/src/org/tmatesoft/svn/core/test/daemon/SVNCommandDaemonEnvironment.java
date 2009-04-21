/*
 * ====================================================================
 * Copyright (c) 2004-2008 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html.
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.core.test.daemon;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.logging.FileHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.tmatesoft.svn.cli.svn.SVN;
import org.tmatesoft.svn.cli.svnadmin.SVNAdmin;
import org.tmatesoft.svn.cli.svndumpfilter.SVNDumpFilter;
import org.tmatesoft.svn.cli.svnlook.SVNLook;
import org.tmatesoft.svn.cli.svnsync.SVNSync;
import org.tmatesoft.svn.cli.svnversion.SVNVersion;
import org.tmatesoft.svn.core.internal.util.DefaultSVNDebugFormatter;
import org.tmatesoft.svn.core.internal.util.SVNHashMap;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.util.SVNLogType;

public class SVNCommandDaemonEnvironment {
    
    private Collection myArguments = new ArrayList();
    private byte[] myStdIn = new byte[0];
    private String myCurrentDirectory = "";
    private String myProgramName = "svn";
    private Map myVariables = new SVNHashMap();
    
    private ByteArrayOutputStream myCommandOutput = new ByteArrayOutputStream();
    private ByteArrayOutputStream myCommandError = new ByteArrayOutputStream();
    private String myTestType;
    
    public SVNCommandDaemonEnvironment(String testType) {
        myTestType = testType;
    }

    public void addArgumentLine(String line) {
        if (line == null || "".equals(line)) {
            return;
        }
        if (line.startsWith("arg=")) {
            myArguments.add(line.substring("arg=".length()));
        } else if (line.startsWith("dir=")) {
            myCurrentDirectory = line.substring("dir=".length());
        } else if (line.startsWith("program=")) {
            myProgramName = line.substring("program=".length());
        } else if (line.startsWith("env.") && line.indexOf('=') > 0) {
            String name = line.substring("env.".length(), line.indexOf('='));
            String value = line.indexOf('=') + 1 < line.length() ? line.substring(line.indexOf('=') + 1) : "";
            if ("".equals(value.trim())) {
                value = null;
            }
            myVariables.put(name, value);
        }
    }
    
    public void setStdIn(byte[] stdin) {
        myStdIn = stdin;
    }
    
    public int run() {
        String testName = (String) myVariables.get("SVN_CURRENT_TEST");
        String doNotSleep = (String) myVariables.get("SVN_I_LOVE_CORRUPTED_WORKING_COPIES_SO_DISABLE_SLEEP_FOR_TIMESTAMPS");
        String editor = (String) myVariables.get("SVN_EDITOR");
        String mergeTool = (String) myVariables.get("SVN_MERGE");
        String testFunction = (String) myVariables.get("SVNTEST_EDITOR_FUNC");
        
        String oldUserDir = System.getProperty("user.dir");
        PrintStream oldOut = System.out;
        PrintStream oldErr = System.err;
        InputStream oldIn = System.in;
        
        PrintStream commandOut = new PrintStream(myCommandOutput);
        PrintStream commandErr = new PrintStream(myCommandError);
        String[] commandArgs = (String[]) myArguments.toArray(new String[myArguments.size()]);
        Handler logHandler = null;
        int rc = 0;
        try {
            if (testName != null) {
                logHandler = createTestLogger(testName);
                Logger.getLogger(SVNLogType.DEFAULT.getName()).addHandler(logHandler);
                Logger.getLogger(SVNLogType.NETWORK.getName()).addHandler(logHandler);
                Logger.getLogger(SVNLogType.WC.getName()).addHandler(logHandler);
                Logger.getLogger(SVNLogType.CLIENT.getName()).addHandler(logHandler);
            }
            SVNFileUtil.setTestEnvironment(editor, mergeTool, testFunction);
            SVNFileUtil.setSleepForTimestamp(doNotSleep == null || !"yes".equals(doNotSleep));
            System.setProperty("user.dir", myCurrentDirectory);
            System.setOut(commandOut);
            System.setErr(commandErr);
            System.setIn(new ByteArrayInputStream(myStdIn));
            if ("svn".equals(myProgramName)) {
                SVN.main(commandArgs);
            } else if ("svnadmin".equals(myProgramName)) {
                SVNAdmin.main(commandArgs);
            } else if ("svnlook".equals(myProgramName)) {
                SVNLook.main(commandArgs);
            } else if ("svnversion".equals(myProgramName)) {
                SVNVersion.main(commandArgs);
            } else if ("svnsync".equals(myProgramName)) {
                SVNSync.main(commandArgs);
            } else if ("svndumpfilter".equals(myProgramName)) {
                SVNDumpFilter.main(commandArgs);
            }
        } catch (SVNCommandExitException e) {
            return e.getCode();
        } catch (Throwable th) {
            Logger.getLogger(SVNLogType.DEFAULT.getName()).log(Level.SEVERE, th.getMessage() != null ? th.getMessage() : "", th);                    
            return 1;
        } finally {            
            if (logHandler != null) {
                logHandler.close();
                Logger.getLogger(SVNLogType.DEFAULT.getName()).removeHandler(logHandler);
                Logger.getLogger(SVNLogType.NETWORK.getName()).removeHandler(logHandler);
                Logger.getLogger(SVNLogType.WC.getName()).removeHandler(logHandler);
                Logger.getLogger(SVNLogType.CLIENT.getName()).removeHandler(logHandler);
            }
            SVNFileUtil.setTestEnvironment(null, null, null);
            SVNFileUtil.setSleepForTimestamp(true);
            System.setProperty("user.dir", oldUserDir);
            System.setIn(oldIn);
            System.setOut(oldOut);
            System.setErr(oldErr);
            if (testName != null) {
                commandOut.println("##teamcity[publishArtifacts '" + getPathFromTestName(testName) + "']");
            }
            commandOut.close();
            commandErr.close();
        }
        return rc;
    }
    
    public byte[] getStdOut() {
        return myCommandOutput.toByteArray();
    }
    
    public byte[] getStdErr() {
        return myCommandError.toByteArray();
    }
    
    private Handler createTestLogger(String testName) throws IOException {
        File logFile = new File(System.getProperty("ant.basedir", ""));
        String path = getPathFromTestName(testName); 
        logFile = new File(logFile, path);
        FileHandler fileHandler = new FileHandler(logFile.getAbsolutePath(), 0, 1, true);
        fileHandler.setLevel(Level.FINEST);
        fileHandler.setFormatter(new DefaultSVNDebugFormatter());
        return fileHandler;
    }

    private String getPathFromTestName(String testName) {
        return "build/logs/" + (myTestType != null ? myTestType : "") + "_" + testName.trim() + ".log"; 
    }
}