/*
 * ====================================================================
 * Copyright (c) 2004-2010 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html.
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.core.internal.util.jna;

import java.io.File;

import org.tmatesoft.svn.core.internal.wc.SVNFileType;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;


/**
 * @version 1.3
 * @author  TMate Software Ltd.
 */
public class SVNJNAUtil {
    
    private static boolean ourIsJNAEnabled;
    private static boolean ourIsJNAPresent;
    private static final String JNA_CLASS_NAME = "com.sun.jna.Library";
    
    static {
        try {
            ClassLoader loader = SVNJNAUtil.class.getClassLoader();
            if (loader == null) {
                loader = ClassLoader.getSystemClassLoader();
            }
            if (loader != null && loader.loadClass(JNA_CLASS_NAME) != null) {
                ourIsJNAPresent = true;
            }
        } catch (ClassNotFoundException e) {
            ourIsJNAPresent = false;
        }
        String jnaEnabledProperty = System.getProperty("svnkit.useJNA", "true");
        ourIsJNAEnabled = Boolean.valueOf(jnaEnabledProperty).booleanValue();
    }
    
    public static void setJNAEnabled(boolean enabled) {
        synchronized (SVNJNAUtil.class) {
            ourIsJNAEnabled = enabled;
        }
    }
    
    public static boolean isJNAPresent() {
        synchronized (SVNJNAUtil.class) {
            return ourIsJNAPresent && ourIsJNAEnabled;
        }
    }

    // linux.
    
    public static SVNFileType getFileType(File file) {
        if (isJNAPresent()) {
            return SVNLinuxUtil.getFileType(file);
        }
        return null;
    }

    public static Boolean isExecutable(File file) {
        if (isJNAPresent()) {
            return SVNLinuxUtil.isExecutable(file);
        }
        return null;
    }

    public static String getLinkTarget(File file) {
        if (isJNAPresent()) {
            return SVNLinuxUtil.getLinkTarget(file);
        }
        return null;
    }

    public static boolean setExecutable(File file, boolean set) {
        if (isJNAPresent()) {
            return SVNLinuxUtil.setExecutable(file, set);
        }
        return false;
    }

    public static boolean setSGID(File file) {
        if (isJNAPresent()) {
            return SVNLinuxUtil.setSGID(file);
        }
        return false;
    }

    public static boolean createSymlink(File file, String linkName) {
        if (isJNAPresent()) {
            return SVNLinuxUtil.createSymlink(file, linkName);
        }
        return false;
    }

    // linux and win32.
    public static boolean setWritable(File file) {
        if (isJNAPresent()) {
            return SVNFileUtil.isWindows ?
                    SVNWin32Util.setWritable(file) :
                    SVNLinuxUtil.setWritable(file);
        }
        return false;
    }

    // win32
    public static boolean setHidden(File file) {
        if (isJNAPresent()) {
            return SVNWin32Util.setHidden(file);
        }
        return false;
    }

    public static boolean moveFile(File src, File dst) {
        if (isJNAPresent()) {
            return SVNWin32Util.moveFile(src, dst);
        }
        return false;
    }
    
    public static String decrypt(String encryptedData) {
        if (isJNAPresent()) {
            return SVNWinCrypt.decrypt(encryptedData);
        }
        return null;
    }
    
    public static String encrypt(String rawData) {
        if (isJNAPresent()) {
            return SVNWinCrypt.encrypt(rawData);
        }
        return null;
    }

    public synchronized static boolean isWinCryptEnabled() {
        return isJNAPresent() && SVNWinCrypt.isEnabled();
    }
    
    public static String getApplicationDataPath(boolean common) {
        if (isJNAPresent()) {
            return SVNWin32Util.getApplicationDataPath(common);
        }
        return null;
    }
}
