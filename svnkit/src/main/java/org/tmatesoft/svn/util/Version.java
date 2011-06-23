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
package org.tmatesoft.svn.util;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Properties;

import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;

/**
 * @version 1.3
 * @author  TMate Software Ltd.
 */
public class Version {

    private static String PROPERTIES_PATH = "/svnkit.build.properties";

    private static final String VERSION_STRING_PROPERTY = "svnkit.version.string";
    private static final String VERSION_MAJOR_PROPERTY = "svnkit.version.major";
    private static final String VERSION_MINOR_PROPERTY = "svnkit.version.minor";
    private static final String VERSION_MICRO_PROPERTY = "svnkit.version.micro";
    private static final String VERSION_REVISION_PROPERTY = "svnkit.version.revision";
    private static final String SVN_VERSION_PROPERTY = "svnkit.svn.version";  
    
    private static final String VERSION_STRING_DEFAULT = "SVN/1.7.0 SVNKit/1.7.0-dev (http://svnkit.com/) rSNAPSHOT";
    private static final String VERSION_MAJOR_DEFAULT = "1";
    private static final String VERSION_MINOR_DEFAULT = "7";
    private static final String VERSION_MICRO_DEFAULT = "0";
    private static final String VERSION_REVISION_DEFAULT = "SNAPSHOT";
    private static final String SVN_VERSION_DEFAULT = "1.7.0";
    private static String ourUserAgent;

    private static Properties ourProperties;
    
    static {
        ourUserAgent = System.getProperty("svnkit.http.userAgent");
    }

    public static String getVersionString() {
        loadProperties();
        return ourProperties.getProperty(VERSION_STRING_PROPERTY, VERSION_STRING_DEFAULT);
    }
    
    public static String getSVNVersion() {
        loadProperties();
        return ourProperties.getProperty(SVN_VERSION_PROPERTY, SVN_VERSION_DEFAULT);
    }
    
    public static void setUserAgent(String userAgent) {
        ourUserAgent = userAgent;
    }

    public static String getUserAgent() {
        if (ourUserAgent != null) {
            return ourUserAgent;
        }
        return getVersionString();
    }

    public static int getMajorVersion() {
        loadProperties();
        try {
            return Integer.parseInt(ourProperties.getProperty(
                    VERSION_MAJOR_PROPERTY, VERSION_MAJOR_DEFAULT));
        } catch (NumberFormatException nfe) {
            //
        }
        return Integer.parseInt(VERSION_MAJOR_DEFAULT);
    }

    public static int getMinorVersion() {
        loadProperties();
        try {
            return Integer.parseInt(ourProperties.getProperty(
                    VERSION_MINOR_PROPERTY, VERSION_MINOR_DEFAULT));
        } catch (NumberFormatException nfe) {
            //
        }
        return Integer.parseInt(VERSION_MINOR_DEFAULT);
    }

    public static int getMicroVersion() {
        loadProperties();
        try {
            return Integer.parseInt(ourProperties.getProperty(
                    VERSION_MICRO_PROPERTY, VERSION_MICRO_DEFAULT));
        } catch (NumberFormatException nfe) {
            //
        }
        return Integer.parseInt(VERSION_MICRO_DEFAULT);
    }

    /**
     * @deprecated use getRevisionString instead
     * @return
     */
    @Deprecated    
    public static long getRevisionNumber() {
        loadProperties();
        try {
            return Long.parseLong(ourProperties.getProperty(
                    VERSION_REVISION_PROPERTY, VERSION_REVISION_DEFAULT));
        } catch (NumberFormatException nfe) {
            //
        }
        try {
            return Long.parseLong(VERSION_REVISION_DEFAULT);
        } catch (NumberFormatException nfe) {
            //
        }
        return -1;
    }

    public static String getRevisionString() {
        loadProperties();
        return ourProperties.getProperty(VERSION_REVISION_PROPERTY, VERSION_REVISION_DEFAULT);
    }

    private static void loadProperties() {
        if (ourProperties != null) {
            return;
        }
        ourProperties = new Properties();
        URL resourceURL = Version.class.getResource(PROPERTIES_PATH);
        if (resourceURL != null) {
            if (resourceURL.toString().lastIndexOf(".jar!/") < 0) {
                return;
            }
        }
        InputStream is = Version.class.getResourceAsStream(PROPERTIES_PATH);
        if (is == null) {
            return;
        }
        try {
            ourProperties.load(is);
        } catch (IOException e) {
            //
        } finally {
            SVNFileUtil.closeFile(is);
        }

    }
}
