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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Properties;
import java.util.logging.Level;

import junit.framework.Test;
import junit.framework.TestSuite;

import org.tmatesoft.svn.core.internal.ws.fs.FSUtil;
import org.tmatesoft.svn.core.test.diff.SVNSequenceDeltaGeneratorTest;
import org.tmatesoft.svn.core.test.diff.SVNSequenceLineReaderTest;
import org.tmatesoft.svn.core.test.diff.internal.ws.fs.FSMergerBySequenceTest;
import org.tmatesoft.svn.util.DebugLog;
import org.tmatesoft.svn.util.PathUtil;

/**
 * @author TMate Software Ltd.
 * 
 * use properties file:
 * 
 */
public class AllTests extends TestSuite {

    private static Process ourSVNServer;
    private static File ourPropertiesFile;
    private static boolean ourIsAnon;
    private static File ourFixtureRoot;
    private static File ourConfigDir;

    public AllTests(String url) {
        super(url);
        String regexp = "^.*$";
        try {
            Properties props = loadProperties(ourPropertiesFile);
            regexp = props.getProperty("tests.mask", regexp);
        } catch (IOException e) {
        }
        AbstractRepositoryTest.addToSuite(this, url, TestSVNRepository.class, regexp);
        AbstractRepositoryTest.addToSuite(this, url, TestSVNFSEntries.class, regexp);
        AbstractRepositoryTest.addToSuite(this, url, TestSVNWorkspace.class, regexp);
        AbstractRepositoryTest.addToSuite(this, url, TestSVNStatus.class, regexp);
        AbstractRepositoryTest.addToSuite(this, url, TestSVNFileContent.class, regexp);
    }
    

    public static void main(String[] args) {
        String fileName = "test.properties";
        boolean isText = false;

        for(int i = 0; args != null && i < args.length; i++) {
            if ("-text".equals(args[i])) {
                isText = true;
            } else {
                fileName = args[i];
            }
        }
        ourPropertiesFile = new File(fileName);
        if (!ourPropertiesFile.exists() || ourPropertiesFile.isDirectory()) {
            System.err.println();
            System.err.println("USAGE: java org.tmatesoft.svn.core.test.AllTest [-text] [TEST_PROPERTIES[=test.properties]]");
            System.err.println("EXAMPLE: java org.tmatesoft.svn.core.test.AllTest -text test2.properties");
            System.exit(0);
        }
        Properties properties = null;
        try {
            properties = loadProperties(ourPropertiesFile);
        } catch (IOException e) {
            error("can't load properties from " + ourPropertiesFile.getAbsolutePath(), e);
            System.exit(1);
        }
        
        List urls = new  ArrayList(properties.size());
        for(Enumeration urlKeys = properties.propertyNames(); urlKeys.hasMoreElements();) {
            String key = (String) urlKeys.nextElement();
            if (key.startsWith("url.")) {
                urls.add(properties.getProperty(key));
            }
        }

        try {
            setUp(properties);
        } catch (Throwable e) {
            error("can't set up repository", e);
            try {
                tearDown(properties);
            } catch (Throwable e1) {
                error("can't tear down repository", e1);
            }
            System.exit(1);
        }
        
        // 4. run tests
        final TestSuite allTests = new TestSuite("All Tests");
        if (!properties.containsKey("tests.mask")) {
            allTests.addTestSuite(SVNSequenceDeltaGeneratorTest.class);
            allTests.addTestSuite(SVNSequenceLineReaderTest.class);
            allTests.addTestSuite(FSMergerBySequenceTest.class);
        }
        for(int i = 0; i < urls.size(); i++) {
            allTests.addTest(new AllTests((String) urls.get(i)));
        }
        
        if (!isText) {
            final Properties props = properties;
            junit.swingui.TestRunner runner = new junit.swingui.TestRunner() {
                public Test getTest(String suiteClassName) {
                    return allTests;
                }
                public void terminate() {
                    try {
                        tearDown(props);
                    } catch (Throwable e) {
                        error("can't tear down repository", e);
                    }
                    super.terminate();
                }
            };
            runner.start(new String[] {"Test"});
        } else {
            junit.textui.TestRunner.run(allTests);
            try {
                tearDown(properties);
            } catch (Throwable e) {
                error("can't tear down repository", e);
            }
        }
    }
    
    public static String getImportMessage() {
        return "Initial Import";
    }
    
    public static File createPlayground() throws IOException {
        Properties props = loadProperties(ourPropertiesFile);
        
        String path = getRepositoryRoot(props);
        String name = findVacantRoot(new File(path), "wc");
        
        File dir =  new File(path, name);
        dir.mkdirs();
        return dir;
    }
    
    public static String createRepository(String baseURL, String fixture) throws Throwable {
        Properties props = loadProperties(ourPropertiesFile);
        
        String path = getRepositoryRoot(props);
        String name = findVacantRoot(new File(path), "repo");
        
        File canonRepos = new File(path, "template" + Math.abs(baseURL.hashCode()));
        if (!canonRepos.exists()) {
            // move also fixture root to this folder
            FSUtil.deleteAll(new File(path, "fixture"));
            FSUtil.copyAll(new File(fixture), new File(path), "fixture", new FileFilter() {
                public boolean accept(File pathname) {
                    return pathname != null && !".svn".equals(pathname.getName());
                }
            });
            ourFixtureRoot = new File(path, "fixture");
            FSUtil.copyAll(new File("svn-config"), new File(path), "svn-config", null);
            ourConfigDir = new File(path, "svn-config");

            // create canon repos.
            String svnadmin = props.getProperty("svnadmin.path");
            String svnadminOptions = props.getProperty("svnadmin.options", null);
            String[] command = null;
            if (svnadminOptions != null) 
                command = new String[] {svnadmin, "create", svnadminOptions, canonRepos.getAbsolutePath()};
            else {
                command = new String[] {svnadmin, "create", canonRepos.getAbsolutePath()};
            }
            execCommand(command);

            File configFile = new File(canonRepos.getAbsolutePath() + File.separatorChar + "conf", "svnserve.conf");          
            FileOutputStream os = new FileOutputStream(configFile);
            os.write("[general]\nanon-access=none\npassword-db=passwd\n".getBytes());
            os.close();
            File passwdFile = new File(canonRepos.getAbsolutePath() + File.separatorChar + "conf", "passwd");          
            os = new FileOutputStream(passwdFile);
            os.write("[users]\nuser = test\n".getBytes());
            os.close();

            File hook = new File(canonRepos.getAbsolutePath() + File.separatorChar + "hooks", "pre-revprop-change.bat");
            hook.createNewFile();
            if (!isWindows()) {
                hook = new File(canonRepos.getAbsolutePath() + File.separatorChar + "hooks", "pre-revprop-change.tmpl");
                hook = FSUtil.copyAll(hook, new File(canonRepos, "hooks"), "pre-revprop-change", null);
                execCommand(new String[] {"chmod", "uog+x", hook.getAbsolutePath()}, true);
                os = new FileOutputStream(hook);
                os.write("#!/bin/sh".getBytes());
                os.close();
            }
            
            String reposPath = PathUtil.append(baseURL, "template" + Math.abs(baseURL.hashCode()));
            runSVNCommand("import", new String[] {getFixtureRoot().getAbsolutePath(), reposPath, "-m", getImportMessage()});
        } 
        String adminPath = props.getProperty("svnadmin.path");
        execCommand(new String[] {adminPath, "hotcopy", canonRepos.getAbsolutePath(), new File(path, name).getAbsolutePath()});
        return baseURL + name;            
    }
    
    private static String findVacantRoot(File file, String prefix) {
        String[] files = file.list();
        int counter = files != null ? files.length : 0;
        while(true) {
            String name = prefix + counter;
            File child = new File(file, name);
            if (!child.exists()) {
                return name;
            }
            counter++;
        }
    }
    public static void runSVNAnonCommand(String command, String[] params) throws IOException {
        Properties props = loadProperties(ourPropertiesFile);
        String[] realCommand = new String[params.length + 4];
        realCommand[0] = props.getProperty("svn.path");
        realCommand[1] = command;        
        realCommand[2] = "--config-dir";
        realCommand[3] = ourConfigDir.getAbsolutePath();
        for(int i = 0; i < params.length; i++) {
            realCommand[i + 4] = params[i];
        }
        execCommand(realCommand, true);            
    }
    
    public static void runSVNCommand(String command, String[] params) throws IOException {
        if (ourIsAnon) {
            runSVNAnonCommand(command, params);
            return;
        }
        Properties props = loadProperties(ourPropertiesFile);
        String[] realCommand = new String[params.length + 9];
        realCommand[0] = props.getProperty("svn.path");
        
        realCommand[1] = "--username";
        realCommand[2] = "user";
        realCommand[3] = "--password";
        realCommand[4] = "test";
        realCommand[5] = "--no-auth-cache";
        realCommand[6] = "--config-dir";
        realCommand[7] = ourConfigDir.getAbsolutePath();
        realCommand[8] = command;        
        for(int i = 0; i < params.length; i++) {
            realCommand[i + 9] = params[i];
        }
        execCommand(realCommand, true);            
    }

    public static void startSVNServe(Properties props) throws Throwable {
        if (!"true".equalsIgnoreCase(props.getProperty("svn.start"))) {
            return;
        }
        String port = props.getProperty("svn.port", "3690");
        String path = getRepositoryRoot(props);
        
        String svnserve = props.getProperty("svnserve.path");
        String[] command = {svnserve, "-d", "--foreground", "--listen-port", port, "-r", path};
        ourSVNServer = execCommand(command, false);
    }
    
    public static void stopSVNServe() {
        if (ourSVNServer != null) {
            ourSVNServer.destroy();
        }
    }

    private static void startApache(Properties props) throws Throwable {
        apache(props, true);
    }

    private static void stopApache(Properties props) throws Throwable {
        apache(props, false);
    }
    
    private static void apache(Properties props, boolean start) throws Throwable {
        if (!"true".equalsIgnoreCase(props.getProperty("apache.start"))) {
            return;
        }
        String[] command = null;
        File configFile = File.createTempFile("svn", "test");
        String path = configFile.getAbsolutePath().replace(File.separatorChar, '/');
        generateApacheConfig(configFile, props);

        String apache = props.getProperty("apache.path");
        command = new String[] {apache, "-f", path, "-k", (start ? "start" : "stop")};
        execCommand(command, start);
    }
    
    private static void generateApacheConfig(File destination, Properties props) throws IOException {
        File template = new File("apache/httpd.template.conf");
        byte[] contents = new byte[(int) template.length()];
        InputStream is = new FileInputStream(template);
        is.read(contents);
        is.close();
        
        File passwdFile = new File("apache/passwd");
        
        String config = new String(contents);
        config = config.replaceAll("%root%", props.getProperty("apache.root"));
        config = config.replaceAll("%port%", props.getProperty("apache.port"));
        String path = getRepositoryRoot(props);
        config = config.replaceAll("%repository.root%", path);
        config = config.replaceAll("%passwd%", passwdFile.getAbsolutePath().replace(File.separatorChar, '/'));
        config = config.replaceAll("%home%", System.getProperty("user.home"));
        
        FileOutputStream os = new FileOutputStream(destination);
        os.write(config.getBytes());
        os.close();
    }

    private static void error(String message, Throwable e) {
        message = message == null ? "<no message>" : message;
        System.err.println();
        System.err.println("ERROR: " + message);
        if (e != null) {
            System.err.println("EXCEPTION THROWN: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void setUp(Properties properties) throws Throwable {
        deleteRepository(properties);
        try {
            System.err.print("starting svnserve... ");
            startSVNServe(properties);
            System.err.println(ourSVNServer == null ? "disabled" : "done");
            System.err.print("starting apache... ");
            startApache(properties);
            System.err.println("true".equalsIgnoreCase(properties.getProperty("apache.start")) ? "done" : "disabled");
        } finally {
            System.err.println();
        }
    }
    
    private static void tearDown(Properties props) throws Throwable {
        try {
            System.err.print("stopping svnserve... ");
            if (ourSVNServer != null) {
                ourSVNServer.destroy();
            }
            System.err.println("done");
            System.err.print("stopping apache... \n");
            stopApache(props);
            System.err.println("done");
        } finally {
            System.err.println();
        }
    }

    private static void deleteRepository(Properties props) {
        String path = getRepositoryRoot(props);
        if (isWindows()) {
            try {
                execCommand(new String[] {"cmd.exe", "/C", "rmdir", "/S", "/Q", path});
                new File(path).mkdirs();
                return;
            } catch (IOException e) {
            }
        }
        File dir = new File(path);
        File[] children = dir.listFiles();
        if (children == null) {
            return;
        }
        for (int i = 0; i < children.length; i++) {
            deleteAll(children[i]);
        }
        dir.mkdirs();
    }

    public static void deleteAll(File dir) {
        if (dir == null) {
            return;
        }
        File[] children = dir.listFiles();
        if (children != null) {
            for (int i = 0; i < children.length; i++) {
                File child = children[i];
                deleteAll(child);
            }
        }
        dir.delete();
//        dir.deleteOnExit();
    }

    private static Process execCommand(String[] command) throws IOException {
        return execCommand(command, true);        
    }
    
    private static Process execCommand(String[] command, boolean wait) throws IOException {
        Process process = Runtime.getRuntime().exec(command);
        if (process != null) {
            try {
                new ReaderThread(process.getInputStream()).start();
                new ReaderThread(process.getErrorStream()).start();
                if (wait) {
                    int code = process.waitFor();
                    if (code != 0) {
                        throw new IOException("process '"  +  command[0] + "' exit code is not 0 : " + code);
                    }
                }
            } catch (InterruptedException e) {
                throw new IOException("interrupted");
            }
        }
        return process;
    }
    
    public static Properties loadProperties(File file) throws IOException {
        FileInputStream is = new FileInputStream(file);
        Properties props = new Properties();
        props.load(is);
        is.close();
        return props;
    }
    
    private static boolean isWindows() {
        return System.getProperty("os.name").toLowerCase().indexOf("windows") >= 0;
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
                }
            } catch (IOException e) {
                e.printStackTrace();
            } 
        }
    }

    public static void setAnonymousAccess(boolean b) {
        ourIsAnon = b;
    }

    public static File getFixtureRoot() {
        return ourFixtureRoot;
    }
    
    private static String getRepositoryRoot(Properties props) {
        String path = props.getProperty("repository.root");
        path = path.replaceAll("%home%", System.getProperty("user.home").replace(File.separatorChar, '/'));
        path = path.replace(File.separatorChar, '/');
        new File(path).mkdirs();
        return path;
    }
}
