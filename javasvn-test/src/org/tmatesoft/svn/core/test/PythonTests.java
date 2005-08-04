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
import java.io.PrintWriter;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;
import java.util.StringTokenizer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;

import org.tmatesoft.svn.util.SVNDebugLog;

/**
 * @author TMate Software Ltd.
 */
public class PythonTests {

	private static File ourPropertiesFile;
    private static Process ourSVNServer;
    //S
    private static File resultsFile;
    private static boolean isCreated;
    private static PrintWriter myWriter;
    //\S

    public static void main(String[] args) {
		String fileName = args[0];
		ourPropertiesFile = new File(fileName);

		Properties properties = null;
		String defaultTestSuite = null;
		try {
			properties = loadProperties(ourPropertiesFile);
			defaultTestSuite = loadDefaultTestSuite();
		} catch (IOException e) {
			System.out.println("can't load properties, exiting");
			System.exit(1);
		}

		//S
		resultsFile = new File("results.xml");
		if(!resultsFile.exists()){
		    try{
		        resultsFile.createNewFile();
		    }catch(IOException ioe){
		        System.err.println("Can't create an an output file '"+resultsFile.getName()+"' for results");
		    }
		}

		try{
		    myWriter = new PrintWriter(new FileWriter(resultsFile));
		    isCreated = true;
		}catch(IOException ioe){
		    System.err.println("Can't create an an output file '"+resultsFile.getName()+"' for results");
		}

		if(isCreated){
	        myWriter.println("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
	        myWriter.flush();
	        myWriter.println("<PythonTests>");
	        myWriter.flush();
		}
		//\S
		
		String pythonTestsRoot = properties.getProperty("python.tests");
		properties.setProperty("repository.root", new File(pythonTestsRoot).getAbsolutePath());
		String url = "svn://localhost";
		//S
		if(isCreated){
	        myWriter.println("<tests url=\""+url+"\">");
	        myWriter.flush();
	        myWriter.println("<server name=\"svnserve\" flag=\""+properties.getProperty("python.svn")+"\">");
	        myWriter.flush();
		}
		//\S
		if (Boolean.TRUE.toString().equals(properties.getProperty("python.svn"))) {
			try {
				startSVNServe(properties);
				runPythonTests(properties, defaultTestSuite, url);
			} catch (Throwable th) {
				th.printStackTrace();
			} finally {
				stopSVNServe();
			}
		}
		//S
		if(isCreated){
	        myWriter.println("</server>");
	        myWriter.flush();
	        myWriter.println("<server name=\"apache\" flag=\""+properties.getProperty("python.http")+"\">");
	        myWriter.flush();
		}
		//\S

		if (Boolean.TRUE.toString().equals(properties.getProperty("python.http"))) {
			url = "http://localhost:" + properties.getProperty("apache.port", "8082");
			properties.setProperty("apache.conf", "apache/python.template.conf");
			try {
				startApache(properties);
				runPythonTests(properties, defaultTestSuite, url);
			} catch (Throwable th) {
				th.printStackTrace();
			} finally {
				try {
					stopApache(properties);
				} catch (Throwable th) {
					th.printStackTrace();
				}
			}
		}
		//S
		if(isCreated){
	        myWriter.println("</server>");
	        myWriter.flush();
	        myWriter.println("</tests>");
	        myWriter.flush();
	        myWriter.println("</PythonTests>");
	        myWriter.flush();
			TransformerFactory tFactory = TransformerFactory.newInstance();
			try{
			    Transformer transformer = tFactory.newTransformer(new StreamSource("PythonTests.xsl"));
				transformer.transform(new StreamSource("results.xml"), new StreamResult(new FileOutputStream("results.html")));
			}catch(TransformerConfigurationException tce){
		        System.err.println("Can't create a transformer:"+tce.getMessage());
			}catch(TransformerException te){
		        System.err.println("Can't transform:"+te.getMessage());
			}catch(FileNotFoundException fnfe){
		        System.err.println("Can't find input xml file:"+fnfe.getMessage());
			}

		}
		
		//\S
	}

	private static void runPythonTests(Properties properties, String defaultTestSuite, String url) throws IOException {
		System.out.println("RUNNING TESTS AGAINST '" + url + "'");
		String pythonLauncher = properties.getProperty("python.launcher");
		String testSuite = properties.getProperty("python.tests.suite", defaultTestSuite);
		String options = properties.getProperty("python.tests.options", "");
		for (StringTokenizer tests = new StringTokenizer(testSuite, ","); tests.hasMoreTokens();) {
			final String testFileString = tests.nextToken();
			List tokens = tokenizeTestFileString(testFileString);

			//S
			if(isCreated){
		        myWriter.println("<suite name=\"" + tokens.get(0) + "\">");
		        myWriter.flush();
			}
			//\S
			
			final String testFile = tokens.get(0) + "_tests.py";
			tokens = tokens.subList(1, tokens.size());

			if (tokens.isEmpty()) {
				System.out.println("PROCESSING ALL " + testFile);
				processTestCase(pythonLauncher, testFile, options, "", url);
			}
			else {
			    final List availabledTestCases = getAvailableTestCases(pythonLauncher, testFile);
				final List testCases = combineTestCases(tokens, availabledTestCases);

				System.out.println("PROCESSING " + testFile + " " + testCases);
				for (Iterator it = testCases.iterator(); it.hasNext();) {
					final Integer testCase = (Integer)it.next();
					processTestCase(pythonLauncher, testFile, options, String.valueOf(testCase), url);
				}
			}
			//S
			if(isCreated){
		        myWriter.println("</suite>");
		        myWriter.flush();
			}
			//\S
		}
	}

	private static void processTestCase(String pythonLauncher, String testFile, String options, String testCase, String url) {
		String[] commands = new String[]{
			pythonLauncher,
			testFile,
			"-v",
			"--url=" + url,
			options,
			String.valueOf(testCase),
		};

		try {
			Process process = Runtime.getRuntime().exec(commands, null, new File("python/cmdline"));
			new ReaderThread(process.getInputStream(), null).start();
			new ReaderThread(process.getErrorStream(), null).start();
			try {
				process.waitFor();
			}
			catch (InterruptedException e) {
			}
		}
		catch (Throwable th) {
			System.err.println("ERROR: " + th.getMessage());
			th.printStackTrace(System.err);
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
		for (Iterator it = tokens.iterator(); it.hasNext();) {
			final String token = (String)it.next();
			if (token.equalsIgnoreCase("all")) {
				combinedTestCases.addAll(availableTestCases);
				continue;
			}

            if (token.indexOf("-") > 0) {
                // parse range
                String startNumber = token.substring(0, token.indexOf("-"));
                String endNumber = token.substring(token.indexOf("-") + 1);
                try {
                    int start = Integer.parseInt(startNumber);
                    int end = Integer.parseInt(endNumber);
                    if (start > end) {
                        int i = start;
                        start = end;
                        end = i;
                    }
                    for(int i = start; i <= end; i++) {
                        combinedTestCases.add(new Integer(i));
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
			} else {
                combinedTestCases.add(testCase);
			}
		}

		return combinedTestCases;
	}

	private static List getAvailableTestCases(String pythonLauncher, String testFile) throws IOException {
		final String[] commands = new String[]{pythonLauncher, testFile, "list"};
		final ByteArrayOutputStream os = new ByteArrayOutputStream();
		try {
			Process process = Runtime.getRuntime().exec(commands, null, new File("python/cmdline"));
            Thread readerThread = new ReaderThread(process.getInputStream(), new PrintStream(os));
            readerThread.start();
			new ReaderThread(process.getErrorStream(), null).start();
			try {
				process.waitFor();
                readerThread.join(5000);                
			}
			catch (InterruptedException e) {
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
				final String hint = tokenizer.nextToken();
				if (hint.equalsIgnoreCase("SKIP") || hint.equalsIgnoreCase("XFAIL")) {
					continue;
				}
			}

			try {
				tests.add(new Integer(first));
			}
			catch (NumberFormatException ex) {
				System.err.println("ERROR: " + ex.getMessage());
				ex.printStackTrace(System.err);
			}
		}

		return tests;
	}

	static class ReaderThread extends Thread {

		private final BufferedReader myInputStream;
		private final PrintStream myHelpStream;

		public ReaderThread(InputStream is, PrintStream helpStream) {
			myInputStream = new BufferedReader(new InputStreamReader(is));
			myHelpStream = helpStream;
			setDaemon(false);
		}

		public void run() {
			//S
		    String regexp = "([PASFIL]{4})(.*\\.py)[\u0020]*(\\d+)[:\u0020]+(.+)";
            Pattern pattern = Pattern.compile(regexp, Pattern.DOTALL);
		    //\S
		    
		    try {
				String line;
				while ((line = myInputStream.readLine()) != null) {
					SVNDebugLog.logInfo(line);
					if (myHelpStream != null) {
						myHelpStream.println(line);
						myHelpStream.flush();
					}
					System.err.flush();
					System.out.flush();

					if (line != null && (line.startsWith("PASS: ") || line.startsWith("FAIL: "))) {
						System.out.println(line);
						if(isCreated){
							Matcher matcher = pattern.matcher(line);
		                    if(matcher.find()){
		                        String name = matcher.group(4);
		                        name = name.replaceAll("\"", "'");
		                        String id = matcher.group(3);
		                        String result = matcher.group(1);
/*								System.out.println(name);
								System.out.println(id);
								System.out.println(result);
*/
		                        myWriter.println("<test name=\""+ name + "\" id=\"" + id + "\" result=\"" + result + "\"></test>");
		                	    myWriter.flush();
		                    }
			            }
					}
				}
			}
			catch (IOException e) {
				e.printStackTrace();
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
    public static void startSVNServe(Properties props) throws Throwable {
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

    public static void startApache(Properties props) throws Throwable {
        apache(props, true);
    }

    public static void stopApache(Properties props) throws Throwable {
        apache(props, false);
    }
    
    private static void apache(Properties props, boolean start) throws Throwable {
        String[] command = null;
        File configFile = File.createTempFile("svn", "test");
        String path = configFile.getAbsolutePath().replace(File.separatorChar, '/');
        generateApacheConfig(configFile, props);

        String apache = props.getProperty("apache.path");
        command = new String[] {apache, "-f", path, "-k", (start ? "start" : "stop")};
        execCommand(command, start);
    }
    
    private static void generateApacheConfig(File destination, Properties props) throws IOException {
        File template = new File(props.getProperty("apache.conf", "apache/httpd.template.conf"));
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
        
        String pythonTests = new File(props.getProperty("python.tests")).getAbsolutePath().replace(File.separatorChar, '/');
        config = config.replaceAll("%python.tests%", pythonTests);
        
        FileOutputStream os = new FileOutputStream(destination);
        os.write(config.getBytes());
        os.close();
    }
    
    private static String getRepositoryRoot(Properties props) {
        String path = props.getProperty("repository.root");
        path = path.replaceAll("%home%", System.getProperty("user.home").replace(File.separatorChar, '/'));
        path = path.replace(File.separatorChar, '/');
        new File(path).mkdirs();
        return path;
    }
    
    private static Process execCommand(String[] command, boolean wait) throws IOException {
        Process process = Runtime.getRuntime().exec(command);
        if (process != null) {
            try {
                new ReaderThread(process.getInputStream(), null).start();
                new ReaderThread(process.getErrorStream(), null).start();
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
    
}
