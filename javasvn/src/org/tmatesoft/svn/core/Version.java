/*
 * ====================================================================
 * Copyright (c) 2004 TMate Software Ltd. All rights reserved.
 * 
 * This software is licensed as described in the file COPYING, which you should
 * have received as part of this distribution. The terms are also available at
 * http://tmate.org/svn/license.html. If newer versions of this license are
 * posted there, you may use a newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.core;

import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;

import java.util.Enumeration;
import java.util.Properties;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

/**
 * @version 1.0
 * @author TMate Software Ltd.
 */
public class Version {

    public static final int MAJOR_VERSION;

    public static final int MINOR_VERSION;

    public static final int MICRO_VERSION;

    public static final long REVISION;

    public static final boolean IS_STABLE;

    public static String getVersionID() {
        return "JavaSVN " + MAJOR_VERSION + "." + MINOR_VERSION + "."
                + MICRO_VERSION + " (revision #" + REVISION + "), "
                + (IS_STABLE ? "production version" : "development version");
    }

    static {
        Enumeration propertyFiles;
        Properties allProperties = new Properties();
        try {
            propertyFiles = Version.class.getClassLoader().getResources(
                    "build.properties");
        } catch (IOException e) {
            propertyFiles = null;
        }
        while (propertyFiles != null && propertyFiles.hasMoreElements()) {
            URL url = (URL) propertyFiles.nextElement();
            InputStream is = null;
            Properties properties = new Properties();
            try {
                is = url.openStream();
                properties.load(is);
            } catch (IOException e) {
                //
            } finally {
                SVNFileUtil.closeFile(is);
            }
            allProperties.putAll(properties);
        }

        IS_STABLE = Boolean.valueOf(
                allProperties.getProperty("javasvn.version.stable", "false"))
                .booleanValue();
        REVISION = Long.parseLong(allProperties.getProperty(
                "javasvn.version.revision", "0"));

        MAJOR_VERSION = Integer.parseInt(allProperties.getProperty(
                "javasvn.version.major", "0"));
        MINOR_VERSION = Integer.parseInt(allProperties.getProperty(
                "javasvn.version.minor", "0"));
        MICRO_VERSION = Integer.parseInt(allProperties.getProperty(
                "javasvn.version.micro", "0"));
    }
}
