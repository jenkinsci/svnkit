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
package org.tmatesoft.svn.core.internal.wc;

import java.io.File;
import java.io.IOException;

import org.tmatesoft.svn.core.SVNNodeKind;

/**
 * @version 1.0
 * @author TMate Software Ltd.
 */
public class SVNFileType {

    public static final SVNFileType UNKNOWN = new SVNFileType(0);

    public static final SVNFileType NONE = new SVNFileType(1);

    public static final SVNFileType FILE = new SVNFileType(2);

    public static final SVNFileType SYMLINK = new SVNFileType(3);

    public static final SVNFileType DIRECTORY = new SVNFileType(4);

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

    public static SVNFileType getType(File file) {
        if (file == null) {
            return SVNFileType.UNKNOWN;
        }
        if (!SVNFileUtil.isWindows) {
            String absolutePath = file.getAbsolutePath();
            String canonicalPath;
            try {
                canonicalPath = file.getCanonicalPath();
            } catch (IOException e) {
                canonicalPath = file.getAbsolutePath();
            }
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
            } else if (!absolutePath.equals(canonicalPath) && SVNFileUtil.isSymlink(file)) {
                return SVNFileType.SYMLINK;
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

    public int getID() {
        return myType;
    }

    public boolean isFile() {
        return this == SVNFileType.FILE || this == SVNFileType.SYMLINK;
    }
}
