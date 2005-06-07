package org.tmatesoft.svn.core.internal.wc;

import org.tmatesoft.svn.core.io.SVNNodeKind;

import java.io.File;
import java.io.IOException;

/**
 * Created by IntelliJ IDEA.
 * User: alex
 * Date: 07.06.2005
 * Time: 21:20:24
 * To change this template use File | Settings | File Templates.
 */
public class SVNFileType {

    public static final SVNFileType UNKNOWN = new SVNFileType(5);
    public static final SVNFileType NONE = new SVNFileType(4);
    public static final SVNFileType FILE = new SVNFileType(3);
    public static final SVNFileType SYMLINK = new SVNFileType(2);
    public static final SVNFileType DIRECTORY = new SVNFileType(1);

    private int myType;

    private SVNFileType(int type) {
        myType = type;
    }

    public String toString() {
        return Integer.toString(myType);
    }

    public static SVNFileType getType(File file) {
        if (file == null) {
            return SVNFileType.UNKNOWN;
        }
        String absolutePath = file.getAbsolutePath();
        String canonicalPath;
        try {
            canonicalPath = file.getCanonicalPath();
        } catch (IOException e) {
            canonicalPath = file.getAbsolutePath();
        }
        if (!file.exists()) {
            File[] children = file.getParentFile().listFiles();
            for (int i = 0; children != null && i < children.length; i++) {
                File child = children[i];
                if (child.getName().equals(file.getName()) && SVNFileUtil.isSymlink(file)) {
                    return SVNFileType.SYMLINK;
                }
            }
        } else if (!absolutePath.equals(canonicalPath) && SVNFileUtil.isSymlink(file)) {
            return SVNFileType.SYMLINK;
        }

        if (file.isDirectory()) {
            return SVNFileType.DIRECTORY;
        } else if (file.isFile()) {
            return SVNFileType.FILE;
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
}
