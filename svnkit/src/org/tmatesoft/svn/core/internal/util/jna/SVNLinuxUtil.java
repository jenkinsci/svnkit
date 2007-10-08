/*
 * ====================================================================
 * Copyright (c) 2004-2007 TMate Software Ltd.  All rights reserved.
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

import com.sun.jna.Memory;

/**
 * @version 1.1.2
 * @author TMate Software Ltd.
 */
public class SVNLinuxUtil {

    private static final Memory ourSharedMemory = new Memory(1024);

    public static SVNFileType getFileType(File file) {
        if (file == null) {
            return null;
        }
        String path = file.getAbsolutePath();
        if (path.endsWith("/") && path.length() > 1) {
            path = path.substring(0, path.length() - 1);
        }
        try {
            ISVNCLibrary cLibrary = JNALibraryLoader.getCLibrary();
            if (cLibrary == null) {
                return null;
            }
            synchronized (ourSharedMemory) {
                ourSharedMemory.clear();
                int rc;
                synchronized (cLibrary) {
                    rc = SVNFileUtil.isOSX || SVNFileUtil.isBSD ? 
                            cLibrary.lstat(path, ourSharedMemory) : 
                            cLibrary.__lxstat64(0, path, ourSharedMemory);
                }
                if (rc < 0) {
                    if (file.exists() || file.isDirectory() || file.isFile()) {
                        return null;
                    }
                    return SVNFileType.NONE;
                }
                int mode = SVNFileUtil.isOSX || SVNFileUtil.isBSD ?
                        ourSharedMemory.getInt(8) : ourSharedMemory.getInt(16);
                int type = mode & 0170000;
                if (type == 0120000) {
                    return SVNFileType.SYMLINK;
                } else if (type == 0040000) {
                    return SVNFileType.DIRECTORY;
                } else if (type == 0100000) {
                    return SVNFileType.FILE;
                } else {
                    if (file.exists() || file.isDirectory() || file.isFile()) {
                        return null;
                    }
                    return SVNFileType.NONE;
                }
            }
        } catch (Throwable th) {
            //
        }
        return null;
    }

    public static Boolean isExecutable(File file) {
        if (file == null) {
            return null;
        }
        String path = file.getAbsolutePath();
        if (path.endsWith("/") && path.length() > 1) {
            path = path.substring(0, path.length() - 1);
        }
        try {
            ISVNCLibrary cLibrary = JNALibraryLoader.getCLibrary();
            if (cLibrary == null) {
                return null;
            }
            synchronized (ourSharedMemory) {
                ourSharedMemory.clear();
                int rc;
                synchronized (cLibrary) {
                    rc = SVNFileUtil.isOSX || SVNFileUtil.isBSD ? 
                            cLibrary.lstat(path, ourSharedMemory) : 
                            cLibrary.__lxstat64(0, path, ourSharedMemory);
                }
                if (rc < 0) {
                    return null;
                }
                int mode = SVNFileUtil.isOSX || SVNFileUtil.isBSD ?
                        ourSharedMemory.getInt(8) : ourSharedMemory.getInt(16);
                int access = mode & 0777;
                return Boolean.valueOf((access & 0111) != 0);
            }
        } catch (Throwable th) {
            //
        }
        return null;
    }

    public static String getLinkTarget(File file) {
        if (file == null) {
            return null;
        }
        String path = file.getAbsolutePath();
        if (path.endsWith("/") && path.length() > 1) {
            path = path.substring(0, path.length() - 1);
        }
        try {
            ISVNCLibrary cLibrary = JNALibraryLoader.getCLibrary();
            if (cLibrary == null) {
                return null;
            }
            synchronized (ourSharedMemory) {
                ourSharedMemory.clear();
                int rc;
                synchronized (cLibrary) {
                    rc = cLibrary.readlink(path, ourSharedMemory, 1024);
                }
                if (rc <= 0) {
                    return null;
                }
                byte[] buffer = new byte[rc];
                ourSharedMemory.read(0, buffer, 0, rc);
                return new String(buffer, 0, rc);
            }
        } catch (Throwable th) {
            //
        }
        return null;
    }

    public static boolean setExecutable(File file, boolean set) {
        if (file == null) {
            return false;
        }
        String path = file.getAbsolutePath();
        if (path.endsWith("/") && path.length() > 1) {
            path = path.substring(0, path.length() - 1);
        }
        try {
            ISVNCLibrary cLibrary = JNALibraryLoader.getCLibrary();
            if (cLibrary == null) {
                return false;
            }
            synchronized (ourSharedMemory) {
                ourSharedMemory.clear();
                int rc;
                synchronized (cLibrary) {
                    rc = SVNFileUtil.isOSX || SVNFileUtil.isBSD ? 
                            cLibrary.lstat(path, ourSharedMemory) : 
                            cLibrary.__lxstat64(0, path, ourSharedMemory);
                }
                if (rc < 0) {
                    return false;
                }
                int mode = SVNFileUtil.isOSX || SVNFileUtil.isBSD ?
                        ourSharedMemory.getInt(8) : ourSharedMemory.getInt(16);
                int access = mode & 0777;
                synchronized (cLibrary) {
                    rc = cLibrary.chmod(path, set ? 0111 | access : 0111 ^ access);
                }
                return rc < 0 ? false : true;
            }
        } catch (Throwable th) {
            //
        }
        return false;
    }

    public static boolean createSymlink(File file, String linkName) {
        if (file == null || linkName == null) {
            return false;
        }
        String path = file.getAbsolutePath();
        if (path.endsWith("/") && path.length() > 1) {
            path = path.substring(0, path.length() - 1);
        }
        try {
            ISVNCLibrary cLibrary = JNALibraryLoader.getCLibrary();
            if (cLibrary == null) {
                return false;
            }
            int rc;
            synchronized (cLibrary) {
                rc = cLibrary.symlink(path, linkName);
            }
            return rc < 0 ? false : true;
        } catch (Throwable th) {
            //
        }
        return false;
    }
}