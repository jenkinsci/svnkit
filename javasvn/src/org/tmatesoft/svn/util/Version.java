/*
 * Created on Feb 12, 2005
 */
package org.tmatesoft.svn.util;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * @author alex
 */
public class Version {
	
	private static String PROPERTIES_PATH = "build.properties";
	
	private static final String VERSION_STRING_PROPERTY = "javasvn.version.string";
	private static final String VERSION_MAJOR_PROPERTY = "javasvn.version.major";
	private static final String VERSION_MINOR_PROPERTY = "javasvn.version.minor";
	private static final String VERSION_MICRO_PROPERTY = "javasvn.version.micro";

	private static final String VERSION_STRING_DEFAULT = "JavaSVN (http://tmate.org/svn/)";
	private static final String VERSION_MAJOR_DEFAULT = "0";
	private static final String VERSION_MINOR_DEFAULT = "0";
	private static final String VERSION_MICRO_DEFAULT = "0";
	
	private static Properties ourProperties;

	public static String getVersionString() {
		loadProperties();
		return ourProperties.getProperty(VERSION_STRING_PROPERTY, VERSION_STRING_DEFAULT);
	}
	
	public static int getMajorVersion() {
		loadProperties();
		try {
			return Integer.parseInt(ourProperties.getProperty(VERSION_MAJOR_PROPERTY, VERSION_MAJOR_DEFAULT));
		} catch (NumberFormatException nfe) {
		}
		return 0;
	}
	
	public static int getMinorVersion() {
		loadProperties();
		try {
			return Integer.parseInt(ourProperties.getProperty(VERSION_MINOR_PROPERTY, VERSION_MINOR_DEFAULT));
		} catch (NumberFormatException nfe) {
		}
		return 0;
	}

	public static int getMicroVersion() {
		loadProperties();
		try {
			return Integer.parseInt(ourProperties.getProperty(VERSION_MICRO_PROPERTY, VERSION_MICRO_DEFAULT));
		} catch (NumberFormatException nfe) {
		}
		return 0;
	}
	
	private static void loadProperties() {
		if (ourProperties != null) {
			return;
		}
		InputStream is = Version.class.getClassLoader().getResourceAsStream(PROPERTIES_PATH);
		ourProperties = new Properties();
		if (is == null) {
			return;
		}
		try {
			ourProperties.load(is);
		} catch (IOException e) {
		} finally {
			try {
				is.close();
			} catch (IOException e1) {
			}
		}
		
	}}
