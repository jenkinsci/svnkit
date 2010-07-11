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

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.io.StringReader;
import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;
import java.util.StringTokenizer;
import java.util.logging.FileHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.tmatesoft.svn.core.internal.util.DefaultSVNDebugFormatter;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.test.daemon.SVNCommandDaemon;

/**
 * @version 1.3
 * @author  TMate Software Ltd.
 */
public class PythonTests {

	private static File ourPropertiesFile;
    private static Process ourSVNServer;
    
    private static AbstractTestLogger[] ourLoggers;
    private static SVNCommandDaemon ourDaemon;

    public static void main(String[] args) {
		String fileName = args[0];
		String libPath = args[1];
		if (libPath == null) {
		    libPath = "";
		}
		ourPropertiesFile = new File(fileName);
        ourLoggers = new AbstractTestLogger[] {new ConsoleLogger(), new XMLLogger()};

		Properties properties = null;
		String defaultTestSuite = null;
		try {
			properties = loadProperties(ourPropertiesFile);
			defaultTestSuite = loadDefaultTestSuite();
		} catch (IOException e) {
			System.out.println("can't load properties, exiting");
			System.exit(1);
		}
		
        Loggers loggers = setupLogging();
        
        for (int i = 0; i < ourLoggers.length; i++) {
            try{
                ourLoggers[i].startTests(properties);
            }catch(IOException ioe){
                ioe.getMessage();
                ioe.printStackTrace();
                System.exit(1);
            }
        }
        
        if (Boolean.TRUE.toString().equals(properties.getProperty("daemon")) && SVNFileUtil.isLinux) {
            try {
                libPath = startCommandDaemon(properties);
            } catch (IOException e) {
                e.getMessage();
                e.printStackTrace();
                System.exit(1);
            }
        }

        String pythonTestsRoot = properties.getProperty("python.tests", "python/cmdline");
		properties.setProperty("repository.root", new File(pythonTestsRoot).getAbsolutePath());
        String absTestsRootLocation = new File(pythonTestsRoot).getAbsolutePath().replace(File.separatorChar, '/');
        if(!absTestsRootLocation.startsWith("/")){
            absTestsRootLocation = "/" + absTestsRootLocation; 
        }
        String url = "file://" + absTestsRootLocation;
        if (Boolean.TRUE.toString().equals(properties.getProperty("python.file"))) {
            boolean started = false;
            try {
                for (int i = 0; i < ourLoggers.length; i++) {
                    ourLoggers[i].startServer("file", url);
                }
                started = true;
                runPythonTests(properties, defaultTestSuite, "fsfs", url, libPath, loggers.myPythonLogger);
            } catch (Throwable th) {
                th.printStackTrace();
            } finally {
                if (started) {
                    for (int i = 0; i < ourLoggers.length; i++) {
                        ourLoggers[i].endServer("file", url);
                    }
                }
            }
        }

        url = "svn://localhost";
        if (Boolean.TRUE.toString().equals(properties.getProperty("python.svn"))) {
            boolean started = false;
			try {
				int port = startSVNServe(properties);
                url += ":" + port;
                for (int i = 0; i < ourLoggers.length; i++) {
                    ourLoggers[i].startServer("svnserve", url);
                }
                started = true;
				runPythonTests(properties, defaultTestSuite, "svn", url, libPath, loggers.myPythonLogger);
			} catch (Throwable th) {
				th.printStackTrace();
			} finally {
				stopSVNServe();
                if (started) {
                    for (int i = 0; i < ourLoggers.length; i++) {
                        ourLoggers[i].endServer("svnserve", url);
                    }
                }
			}
		}

		if (Boolean.TRUE.toString().equals(properties.getProperty("python.http"))) {
            String apacheEnabled = properties.getProperty("apache", "true");
            if (Boolean.TRUE.toString().equals(apacheEnabled.trim())) {
                properties.setProperty("apache.conf", "apache/python.template.conf");
                boolean started = false;
                int port = -1;
                try {
                    port = startApache(properties, loggers.myPythonLogger);
                    url = "http://localhost:" + port;
                    for (int i = 0; i < ourLoggers.length; i++) {
                        ourLoggers[i].startServer("apache", url);
                    }
                    started = true;
                    runPythonTests(properties, defaultTestSuite, "dav", url, libPath, loggers.myPythonLogger);
                } catch (Throwable th) {
                    th.printStackTrace();
                } finally {
                    try {
                        stopApache(properties, port, loggers.myPythonLogger);
                        if (started) {
                            for (int i = 0; i < ourLoggers.length; i++) {
                                ourLoggers[i].endServer("apache", url);
                            }
                        }
                    } catch (Throwable th) {
                        th.printStackTrace();
                    }
                }
            }
			
			//now check the servlet flag
			String servletContainer = properties.getProperty("servlet.container", "false");
			if (Boolean.TRUE.toString().equals(servletContainer.trim())) {
			    boolean started = false;
	            int port = -1;
	            try {
	                port = startTomcat(properties, loggers.myPythonLogger);
	                url = "http://localhost:" + port + "/svnkit";
	                for (int i = 0; i < ourLoggers.length; i++) {
	                    ourLoggers[i].startServer("tomcat", url);
	                }
	                //wait a little until tomcat
	                Thread.sleep(1000);
	                started = true;
	                runPythonTests(properties, defaultTestSuite, "dav", url, libPath, loggers.myPythonLogger);
	            } catch (Throwable th) {
	                th.printStackTrace();
	            } finally {
	                try {
	                    stopTomcat(properties, loggers.myPythonLogger);
	                    if (started) {
	                        for (int i = 0; i < ourLoggers.length; i++) {
	                            ourLoggers[i].endServer("tomcat", url);
	                        }
	                    }
	                } catch (Throwable th) {
	                    th.printStackTrace();
	                }
	            }
			}
		}
		
        for (int i = 0; i < ourLoggers.length; i++) {
            ourLoggers[i].endTests(properties);
        }
        if (ourDaemon != null) {
            ourDaemon.shutdown();
        }
	}

    private static Loggers setupLogging() {
        Loggers loggers = new Loggers();
        loggers.myPythonLogger = setupLogger("python", Level.INFO);
        return loggers;
    }

    private static Logger setupLogger(String name, Level level) {
        Logger python = Logger.getLogger(name);
        python.setUseParentHandlers(false);
        python.setLevel(level);
        return python;
    }
    
    private static Handler createLogHandler(String logName) throws IOException {
      FileHandler fileHandler = new FileHandler(System.getProperty("ant.basedir", "") + "/build/logs/" + logName + ".log", 0, 1, false);
      fileHandler.setLevel(Level.INFO);
      fileHandler.setFormatter(new DefaultSVNDebugFormatter());
      return fileHandler;
    }

	private static void runPythonTests(Properties properties, String defaultTestSuite, String type, String url, String libPath, Logger pythonLogger) throws IOException {
		System.out.println("RUNNING TESTS AGAINST '" + url + "'");
		String pythonLauncher = properties.getProperty("python.launcher");
		String testSuite = properties.getProperty("python.tests.suite", defaultTestSuite);
		String options = properties.getProperty("python.tests.options", "");
        String testsLocation = properties.getProperty("python.tests", "python/cmdline");
        String listOption = properties.getProperty("python.tests.listOption", "list");
		String fsfsConfig = properties.getProperty("fsfs.config");
		for (StringTokenizer tests = new StringTokenizer(testSuite, ","); tests.hasMoreTokens();) {
			final String testFileString = tests.nextToken();
			List tokens = tokenizeTestFileString(testFileString);

            String suiteName = (String) tokens.get(0);
			for (int i = 0; i < ourLoggers.length; i++) {
                ourLoggers[i].startSuite(suiteName);
            }
			
			final String testFile = suiteName + "_tests.py";
			tokens = tokens.subList(1, tokens.size());
			
			Handler logHandler = createLogHandler(type + "_" + suiteName + "_python");
			pythonLogger.addHandler(logHandler);
			try {
    			if (tokens.isEmpty() || (tokens.size() == 1 && "ALL".equals(tokens.get(0)))) {
                    System.out.println("PROCESSING " + testFile + " [ALL]");
                    processTestCase(pythonLauncher, testsLocation, testFile, options, null, url, libPath, fsfsConfig, pythonLogger);
    			} else {
    	            final List availabledTestCases = getAvailableTestCases(pythonLauncher, testsLocation, testFile, listOption, pythonLogger);
    	            final List testCases = !tokens.isEmpty() ? combineTestCases(tokens, availabledTestCases) : availabledTestCases;
    	            System.out.println("PROCESSING " + testFile + " " + testCases);
    	            processTestCase(pythonLauncher, testsLocation, testFile, options, testCases, url, libPath, fsfsConfig, pythonLogger);
    			}
			} finally {
			    logHandler.close();
			    pythonLogger.removeHandler(logHandler);
			}
            for (int i = 0; i < ourLoggers.length; i++) {
                ourLoggers[i].endSuite(suiteName);
            }
		}
	}

	private static void processTestCase(String pythonLauncher, String testsLocation, String testFile, String options, List testCases, 
	        String url, String libPath, String fsfsConfigPath, Logger pythonLogger) {
	    Collection commandsList = new ArrayList();
        commandsList.add(pythonLauncher);
        commandsList.add(testFile);
        commandsList.add("--v");
        commandsList.add("--cleanup");
        commandsList.add("--use-jsvn");        
        commandsList.add("--bin=" + libPath);        
        commandsList.add("--url=" + url);
        if (fsfsConfigPath != null) {
            commandsList.add("--config-file=" + new File(fsfsConfigPath).getAbsolutePath());
        }
        
        if (options != null && !"".equals(options.trim())) {
            commandsList.add(options);
        }
        if (testCases != null) {
            for (Iterator cases = testCases.iterator(); cases.hasNext();) {
                Integer testCase = (Integer) cases.next();
                commandsList.add(String.valueOf(testCase));
            }
        }
        
        String[] commands = (String[]) commandsList.toArray(new String[commandsList.size()]); 

		try {
			Process process = Runtime.getRuntime().exec(commands, null, new File(testsLocation));
			ReaderThread inReader = new ReaderThread(process.getInputStream(), null, pythonLogger);
			inReader.start();
			ReaderThread errReader = new ReaderThread(process.getErrorStream(), null, pythonLogger);
			errReader.start();
			try {
				process.waitFor();
			} catch (InterruptedException e) {
			} finally {
			    inReader.close();
			    errReader.close();
			    process.destroy();
			}
		} catch (Throwable th) {
			pythonLogger.log(Level.SEVERE, "", th);
		}
	}

	private static List tokenizeTestFileString(String testFileString) {
		final StringTokenizer tokenizer = new StringTokenizer(testFileString, " ", false);
		final List tokens = new ArrayList();
		while (tokenizer.hasMoreTokens()) {
			tokens.add(tokenizer.nextToken());
			continue;
		}

		return tokens;
	}

	private static List combineTestCases(List tokens, List availableTestCases) {
		final List combinedTestCases = new ArrayList();
		if (availableTestCases.isEmpty()) {
		    return combinedTestCases;
		}
		Integer endInt = (Integer) availableTestCases.get(availableTestCases.size() - 1);
		Integer startInt = (Integer) availableTestCases.get(0);
		boolean isAllSpecified = false;
		for (Iterator it = tokens.iterator(); it.hasNext();) {
			final String token = (String)it.next();
			if (token.equalsIgnoreCase("all")) {
				isAllSpecified = true;
			    combinedTestCases.addAll(availableTestCases);
				continue;
			}

            if (token.indexOf("-") > 0 || (token.indexOf("-") == 0 && !isAllSpecified)) {
                // parse range
                String startNumber = token.substring(0, token.indexOf("-"));
                String endNumber = token.substring(token.indexOf("-") + 1);
                try {
                    int start = startInt.intValue();
                    int end = endInt.intValue();
                    if (!"".equals(startNumber)) {
                        start = Integer.parseInt(startNumber);
                    }
                    
                    if (!"".equals(endNumber)) {
                        end = Integer.parseInt(endNumber);
                    }
                    
                    if (start > end) {
                        int i = start;
                        start = end;
                        end = i;
                    }
                    for(int i = start; i <= end; i++) {
                        if (availableTestCases.contains(new Integer(i))) {
                            combinedTestCases.add(new Integer(i));
                        }
                    }
                } catch (NumberFormatException nfe) {
                }
                continue;
            }
			final Integer testCase;
			try {
				testCase = new Integer(token);
			} catch (NumberFormatException ex) {
				System.err.println("ERROR: " + ex.getMessage());
				ex.printStackTrace(System.err);
				continue;
			}

			if (testCase.intValue() < 0) {
				combinedTestCases.remove(new Integer(-testCase.intValue()));
			} else if (availableTestCases.contains(testCase)) {
                combinedTestCases.add(testCase);
			}
		}

		return combinedTestCases;
	}

	private static List getAvailableTestCases(String pythonLauncher, String testsLocation, String testFile, String listOption, Logger pythonLogger) throws IOException {
		final String[] commands = new String[]{pythonLauncher, testFile, listOption};
		final ByteArrayOutputStream os = new ByteArrayOutputStream();
		try {
			Process process = Runtime.getRuntime().exec(commands, null, new File(testsLocation));
            ReaderThread readerThread = new ReaderThread(process.getInputStream(), new PrintStream(os), pythonLogger);
            readerThread.start();
			ReaderThread errReader = new ReaderThread(process.getErrorStream(), null, pythonLogger);
			errReader.start();
			try {
				process.waitFor();
                readerThread.join(5000);                
			}
			catch (InterruptedException e) {
			} finally {
			    readerThread.close();
			    errReader.close();
			    process.destroy();
			}
            os.close();
		}
		catch (Throwable th) {
			System.err.println("ERROR: " + th.getMessage());
			th.printStackTrace(System.err);
		}

		final String listString = new String(os.toByteArray());
		final BufferedReader reader = new BufferedReader(new StringReader(listString));
		final List tests = new ArrayList();
		String line;
		while ((line = reader.readLine()) != null) {
			final StringTokenizer tokenizer = new StringTokenizer(line, " \t", false);
			if (!tokenizer.hasMoreTokens()) {
				continue;
			}

			final String first = tokenizer.nextToken();
			if (first.startsWith("Test") || first.startsWith("---")) {
				continue;
			}

			if (tokenizer.hasMoreTokens()) {
				final String hint = tokenizer.nextToken().trim();
				if (hint.equalsIgnoreCase("SKIP")) {
					continue;
				}
			}

			try {
				tests.add(new Integer(first));
			} catch (NumberFormatException ex) {
			    continue;
			}
		}
		return tests;
	}

	static class ReaderThread extends Thread {

		private final BufferedReader myInputStream;
		private final PrintStream myHelpStream;
        private boolean myIsClosed;
        private Logger myPythonLogger;
        
		public ReaderThread(InputStream is, PrintStream helpStream, Logger logger) {
			myInputStream = new BufferedReader(new InputStreamReader(is));
			myHelpStream = helpStream;
			myPythonLogger = logger;
			setDaemon(false);
		}
		
		public void close() {
		    if (!myIsClosed) {
		        myIsClosed = true;
		        SVNFileUtil.closeFile(myInputStream);
		    }		    
		}

		public void run() {
		    try {
				String line;
				while ((line = myInputStream.readLine()) != null) {
                    TestResult testResult = TestResult.parse(line);
                    // will be logged to python.log only
                    myPythonLogger.info(line);
                    if (testResult != null) {
                        for (int i = 0; i < ourLoggers.length; i++) {
                            ourLoggers[i].handleTest(testResult);
                        }
                    }
					if (myHelpStream != null) {
						myHelpStream.println(line);
						myHelpStream.flush();
					}
				}
			} catch (IOException e) {
			} finally {
			    if (!myIsClosed) {
			        close();
			    }
			}
		}
	}

	private static String loadDefaultTestSuite() throws IOException {
		final File file = new File("python-suite.txt");
		final BufferedReader reader = new BufferedReader(new FileReader(file));
		final StringBuffer defaultTestSuite = new StringBuffer();
		try {
			for (String line = reader.readLine(); line != null; line = reader.readLine()) {
				if (defaultTestSuite.length() > 0) {
					defaultTestSuite.append(",");
				}

				defaultTestSuite.append(line.trim());
			}
		}
		finally {
			reader.close();
		}

		return defaultTestSuite.toString();
	}
    
    public static Properties loadProperties(File file) throws IOException {
        FileInputStream is = new FileInputStream(file);
        Properties props = new Properties();
        props.load(is);
        is.close();
        return props;
    }
    
    public static String startCommandDaemon(Properties properties) throws IOException {
        int portNumber = 1729;
        portNumber = findUnoccupiedPort(portNumber);
        ourDaemon = new SVNCommandDaemon(portNumber);
        Thread daemonThread = new Thread(ourDaemon);
        daemonThread.setDaemon(true);
        daemonThread.start();
        
        // create client scripts.
        String svnHome = properties.getProperty("svn.home", "/usr/bin");

        generateClientScript(new File("daemon/template"), new File("daemon/jsvn"), "svn", portNumber, svnHome);
        generateClientScript(new File("daemon/template"), new File("daemon/jsvnadmin"), "svnadmin", portNumber, svnHome);
        generateClientScript(new File("daemon/template"), new File("daemon/jsvnversion"), "svnversion", portNumber, svnHome);
        generateClientScript(new File("daemon/template"), new File("daemon/jsvnlook"), "svnlook", portNumber, svnHome);
        generateClientScript(new File("daemon/template"), new File("daemon/jsvnsync"), "svnsync", portNumber, svnHome);
        generateClientScript(new File("daemon/template"), new File("daemon/jsvndumpfilter"), "svndumpfilter", portNumber, svnHome);

	String pattern = properties.getProperty("python.tests.pattern", null);
        if (pattern != null) {
	   generateMatcher(new File("daemon/matcher.pl"), new File("daemon/matcher.pl"), pattern);
        } else {
           SVNFileUtil.setExecutable(new File("daemon/matcher.pl"), false);
        }
        return new File("daemon").getAbsolutePath();
    }
    
    public static int startSVNServe(Properties props) throws Throwable {
        String path = getRepositoryRoot(props);
        
        int portNumber = 3690;
        try {
            portNumber = Integer.parseInt(props.getProperty("svn.port", "3690"));
        } catch (NumberFormatException nfe) {
        }
        portNumber = findUnoccupiedPort(portNumber);
        
        String svnserve = props.getProperty("svnserve.path");
        String[] command = {svnserve, "-d", "--foreground", "--listen-port", portNumber + "", "-r", path};
        ourSVNServer = Runtime.getRuntime().exec(command);
        return portNumber;
    }
    
    public static void stopSVNServe() {
        if (ourSVNServer != null) {
            try {
                ourSVNServer.getInputStream().close();
                ourSVNServer.getErrorStream().close();
            } catch (IOException e) {
            }
            ourSVNServer.destroy();
            try {
                ourSVNServer.waitFor();
            } catch (InterruptedException e) {
            }
        }
    }

    public static int startApache(Properties props, Logger pythonLogger) throws Throwable {
        return apache(props, -1, true, pythonLogger);
    }

    public static void stopApache(Properties props, int port, Logger pythonLogger) throws Throwable {
        apache(props, port, false, pythonLogger);
        // delete apache log.
        File file = new File(System.getProperty("user.home"), "httpd." + port + ".error.log");
        SVNFileUtil.deleteFile(file);
    }
    
    private static int apache(Properties props, int port, boolean start, Logger pythonLogger) throws Throwable {
        String[] command = null;
        File configFile = File.createTempFile("jsvn.", ".apache.config.tmp");
        configFile.deleteOnExit();
        String path = configFile.getAbsolutePath().replace(File.separatorChar, '/');
        port = generateApacheConfig(configFile, props, port);

        String apache = props.getProperty("apache.path");
        command = new String[] {apache, "-f", path, "-k", (start ? "start" : "stop")};
        execCommand(command, start, pythonLogger);
        return port;
    }
    
    private static int generateApacheConfig(File destination, Properties props, int port) throws IOException {
        File template = new File(props.getProperty("apache.conf", "apache/httpd.template.conf"));
        byte[] contents = new byte[(int) template.length()];
        InputStream is = new FileInputStream(template);
        SVNFileUtil.readIntoBuffer(is, contents, 0, contents.length);
        is.close();
        
        File passwdFile = new File("apache/passwd");
        
        if (port < 0) {
            port = 8082;
            try {
                port = Integer.parseInt(props.getProperty("apache.port", "8082"));
            } catch (NumberFormatException nfe) {
            }
            port = findUnoccupiedPort(port);
        }
        
        String config = new String(contents);
        String root = props.getProperty("apache.root");
        config = config.replaceAll("%root%", root);
        config = config.replaceAll("%port%", port + "");
        String path = getRepositoryRoot(props);
        config = config.replaceAll("%repository.root%", path);
        config = config.replaceAll("%passwd%", passwdFile.getAbsolutePath().replace(File.separatorChar, '/'));
        config = config.replaceAll("%home%", System.getProperty("user.home").replace(File.separatorChar, '/'));
        
        String pythonTests = new File(props.getProperty("python.tests")).getAbsolutePath().replace(File.separatorChar, '/');
        config = config.replaceAll("%python.tests%", pythonTests);
        String apacheOptions = props.getProperty("apache.options", "");
        config = config.replaceAll("%apache.options%", apacheOptions);
        String apacheModules = props.getProperty("apache.svn.modules", root + "/modules");
        config = config.replaceAll("%apache.svn.modules%", apacheModules);
        
        FileOutputStream os = new FileOutputStream(destination);
        os.write(config.getBytes());
        os.close();
        return port;
    }

    public static int startTomcat(Properties props, Logger pythonLogger) throws Throwable {
        return tomcat(props, -1, -1, true, pythonLogger);
    }

    public static void stopTomcat(Properties props, Logger pythonLogger) throws Throwable {
        tomcat(props, -1, -1, false, pythonLogger);
    }

    private static int tomcat(Properties props, int serverPort, int connectorPort, boolean start, Logger pythonLogger) throws Throwable {
        if (start) {
            connectorPort = generateTomcatServerXML(props, serverPort, connectorPort);
        }

        String catalina = "tomcat/bin/catalina.sh";
        String[] command = new String[] {catalina, (start ? "start" : "stop")};
        execCommand(command, start, pythonLogger);
        return connectorPort;
    }

    private static int generateTomcatServerXML(Properties props, int serverPort, int connectorPort) throws IOException {
        File template = new File(props.getProperty("server.xml", "tomcat/conf/server.xml"));
        byte[] contents = new byte[(int) template.length()];
        InputStream is = new FileInputStream(template);
        SVNFileUtil.readIntoBuffer(is, contents, 0, contents.length);
        is.close();
        
        if (serverPort < 0) {
            serverPort = 8006;
            try {
                serverPort = Integer.parseInt(props.getProperty("tomcat.server.port", "8006"));
            } catch (NumberFormatException nfe) {
            }
            serverPort = findUnoccupiedPort(serverPort);
        }
        
        if (connectorPort < 0) {
            connectorPort = 8181;
            try {
                connectorPort = Integer.parseInt(props.getProperty("tomcat.connector.port", "8181"));
            } catch (NumberFormatException nfe) {
            }
            connectorPort = findUnoccupiedPort(connectorPort);
        }
        
        String config = new String(contents);
        config = config.replaceAll("%server.port%", serverPort + "");
        config = config.replaceAll("%connector.port%", connectorPort + "");
        
        FileOutputStream os = new FileOutputStream(template);
        os.write(config.getBytes());
        os.close();
        return connectorPort;
    }

    private static void generateClientScript(File src, File destination, String name, int port, String svnHome) throws IOException {
        byte[] contents = new byte[(int) src.length()];
        InputStream is = new FileInputStream(src);
        SVNFileUtil.readIntoBuffer(is, contents, 0, contents.length);
        is.close();

        String script = new String(contents);
        script = script.replaceAll("%name%", name);
        script = script.replaceAll("%port%", Integer.toString(port));
        script = script.replaceAll("%svn_home%", svnHome);
        
        FileOutputStream os = new FileOutputStream(destination);
        os.write(script.getBytes());
        os.close();
        
        SVNFileUtil.setExecutable(destination, true);
    }

    private static void generateMatcher(File src, File destination, String pattern) throws IOException {
        byte[] contents = new byte[(int) src.length()];
        InputStream is = new FileInputStream(src);
        SVNFileUtil.readIntoBuffer(is, contents, 0, contents.length);
        is.close();

        String script = new String(contents);
        script = script.replace("%pattern%", pattern);
        
        FileOutputStream os = new FileOutputStream(destination);
        os.write(script.getBytes());
        os.close();
        
        SVNFileUtil.setExecutable(destination, true);
    }
    
    private static int findUnoccupiedPort(int port) {
        ServerSocket socket = null;
        try {
            socket = new ServerSocket();
            socket.bind(null);
            return socket.getLocalPort();
        } catch (IOException e) {
            return port;
        } finally {
            if (socket != null) {
                try {
                    socket.close();
                } catch (IOException e) {
                }
            }
        }
    }

    private static String getRepositoryRoot(Properties props) {
        String path = props.getProperty("repository.root");
        path = path.replaceAll("%home%", System.getProperty("user.home").replace(File.separatorChar, '/'));
        path = path.replace(File.separatorChar, '/');
        new File(path).mkdirs();
        return path;
    }
    
    private static Process execCommand(String[] command, boolean wait, Logger pythonLogger) throws IOException {
        Process process = Runtime.getRuntime().exec(command);
        if (process != null) {
            try {
                new ReaderThread(process.getInputStream(), null, pythonLogger).start();
                new ReaderThread(process.getErrorStream(), null, pythonLogger).start();
                if (wait) {
                    int code = process.waitFor();
                    if (code != 0) {
                        StringBuffer commandLine = new StringBuffer();
                        for (int i = 0; i < command.length; i++) {
                            commandLine.append(command[i]);
                            if (i + 1 != command.length) {
                                commandLine.append(' ');
                            }
                        }
                        throw new IOException("process '"  +  commandLine + "' exit code is not 0 : " + code);
                    }
                }
            } catch (InterruptedException e) {
                throw new IOException("interrupted");
            }
        }
        return process;
    }
    
    private static class Loggers {
        private Logger myPythonLogger;
    }
}
