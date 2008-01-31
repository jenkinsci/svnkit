/*
 * ====================================================================
 * Copyright (c) 2004-2008 TMate Software Ltd.  All rights reserved.
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

import com.sun.jna.NativeLong;
import com.sun.jna.WString;


/**
 * @version 1.1.2
 * @author  TMate Software Ltd.
 */
class SVNWin32Util {
    
    public static boolean setWritable(File file) {
        if (file == null) {
            return false;
        }
        ISVNKernel32Library library = JNALibraryLoader.getKernelLibrary();
        if (library == null) {
            // use ugly way.
            return false;
        }
        synchronized (library) {
            try {
                int rc = library.SetFileAttributesW(new WString(file.getAbsolutePath()), new NativeLong(ISVNKernel32Library.FILE_ATTRIBUTE_NORMAL));
                return rc != 0;
            } catch (Throwable th) {
            }
        }
        return false;
    }

    public static boolean setHidden(File file) {
        if (file == null) {
            return false;
        }
        ISVNKernel32Library library = JNALibraryLoader.getKernelLibrary();
        if (library == null) {
            // use ugly way.
            return false;
        }
        synchronized (library) {
            try {
                int rc = library.SetFileAttributesW(new WString(file.getAbsolutePath()), new NativeLong(ISVNKernel32Library.FILE_ATTRIBUTE_HIDDEN));
                return rc != 0;
            } catch (Throwable th) {
            }
            return false;
        }
    }

    public static boolean moveFile(File src, File dst) {
        if (src == null || dst == null) {
            return false;
        }
        ISVNKernel32Library library = JNALibraryLoader.getKernelLibrary();
        if (library == null) {
            // use ugly way.
            return false;
        }
        if (dst.isFile() && !dst.canWrite()) {
            setWritable(dst);
            src.setReadOnly();
        }
        synchronized (library) {
            try {
                int rc = library.MoveFileExW(new WString(src.getAbsoluteFile().getAbsolutePath()), new WString(dst.getAbsoluteFile().getAbsolutePath()), new NativeLong(1));
                return rc != 0;
            } catch (Throwable th) {
                th.printStackTrace();
            }
        }
        return false;
    }

}
