/*
 * ====================================================================
 * Copyright (c) 2004-2006 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.core.internal.wc;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import org.tmatesoft.svn.core.SVNNodeKind;

/**
 * @version 1.1.0
 * @author  TMate Software Ltd.
 */
public class SVNFileType {

    public static final SVNFileType UNKNOWN = new SVNFileType(0);
    public static final SVNFileType NONE = new SVNFileType(1);
    public static final SVNFileType FILE = new SVNFileType(2);
    public static final SVNFileType SYMLINK = new SVNFileType(3);
    public static final SVNFileType DIRECTORY = new SVNFileType(4);
    
    private static final boolean fastSymlinkResoution = !"false".equalsIgnoreCase(System.getProperty("svnkit.fastSymlinkResolution", System.getProperty("javasvn.fastSymlinkResolution")));
    private static final boolean canonPathCacheUsed = !"false".equalsIgnoreCase(System.getProperty("sun.io.useCanonCaches"));
    private static boolean detectSymlinks = !"false".equalsIgnoreCase(System.getProperty("svnkit.symlinks", System.getProperty("javasvn.symlinks", "true")));
    
    private static final Set ADMIN_FILE_PARENTS = new HashSet();
    
    static {
        ADMIN_FILE_PARENTS.add("text-base");
        ADMIN_FILE_PARENTS.add("prop-base");
        ADMIN_FILE_PARENTS.add("props");
        ADMIN_FILE_PARENTS.add("wcprops");
        ADMIN_FILE_PARENTS.add("tmp");
    }

    private int myType;

    private SVNFileType(int type) {
        myType = type;
    }

    public String toString() {
        switch(myType) {
            case 0: return "unknown";
            case 1: return "none";
            case 2: return "file";
            case 3: return "symlink";
            case 4: return "directory";
        }
        return Integer.toString(myType);
    }
    
    
    public static void setSymlinkSupportEnabled(boolean enabled) {
        detectSymlinks = enabled;
    }
    
    public static boolean isSymlinkSupportEnabled() {
        return detectSymlinks;
    }


    public static SVNFileType getType(File file) {
        if (file == null) {
            return SVNFileType.UNKNOWN;
        }
        if (detectSymlinks && !SVNFileUtil.isWindows && !isAdminFile(file)) {
            if (canonPathCacheUsed && !fastSymlinkResoution && SVNFileUtil.isSymlink(file)) {
                return SVNFileType.SYMLINK;
            } else if (!canonPathCacheUsed || fastSymlinkResoution) {            
                if (!file.exists()) {
                    File[] children = file.getParentFile() != null ? file.getParentFile().listFiles() : null;
                    for (int i = 0; children != null && i < children.length; i++) {
                        File child = children[i];
                        if (child.getName().equals(file.getName())) {
                            if (SVNFileUtil.isSymlink(file)) {
                                return SVNFileType.SYMLINK;
                            }
                        }
                    }
                } else {
                    String absolutePath = file.getAbsolutePath();
                    String canonicalPath;
                    try {
                        canonicalPath = file.getCanonicalPath();
                    } catch (IOException e) {
                        canonicalPath = file.getAbsolutePath();
                    }
                    if (!absolutePath.equals(canonicalPath) && SVNFileUtil.isSymlink(file)) {
                        return SVNFileType.SYMLINK;
                    }
                }
            }
        }

        if (file.isFile()) {
            return SVNFileType.FILE;
        } else if (file.isDirectory()) {
            return SVNFileType.DIRECTORY;
        } else if (!file.exists()) {
            return SVNFileType.NONE;
        }
        return SVNFileType.UNKNOWN;
    }

    public static boolean equals(SVNFileType type, SVNNodeKind nodeKind) {
        if (nodeKind == SVNNodeKind.DIR) {
            return type == SVNFileType.DIRECTORY;
        } else if (nodeKind == SVNNodeKind.FILE) {
            return type == SVNFileType.FILE || type == SVNFileType.SYMLINK;
        } else if (nodeKind == SVNNodeKind.NONE) {
            return type == SVNFileType.NONE;
        } else if (nodeKind == SVNNodeKind.UNKNOWN) {
            return type == SVNFileType.UNKNOWN;
        }
        return false;
    }
    
    private static boolean isAdminFile(File file) {
        String path = file.getAbsolutePath().replace(File.separatorChar, '/');
        String adminDir = "/" + SVNFileUtil.getAdminDirectoryName();
        return path.lastIndexOf(adminDir + "/") > 0 || path.endsWith(adminDir);
    }

    public int getID() {
        return myType;
    }

    public boolean isFile() {
        return this == SVNFileType.FILE || this == SVNFileType.SYMLINK;
    }
    
    public static SVNNodeKind getNodeKind(SVNFileType type) {
        if (type == null || type == SVNFileType.NONE || type == SVNFileType.UNKNOWN) {
            return SVNNodeKind.NONE;
        } else if (type == SVNFileType.DIRECTORY) {
            return SVNNodeKind.DIR;
        }
        return SVNNodeKind.FILE;
    }

}
