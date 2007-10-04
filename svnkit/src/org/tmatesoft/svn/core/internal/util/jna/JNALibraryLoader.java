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

import com.sun.jna.Native;


/**
 * @version 1.1.2
 * @author  TMate Software Ltd.
 */
public class JNALibraryLoader {
    
    private static ISVNWinCryptLibrary ourWinCryptLibrary;
    private static ISVNKernel32Library ourKenrelLibrary;

    static {
        Native.setPreserveLastError(true);
        Native.setProtected(true);
        // load win32 libraries.
        try {
            ourWinCryptLibrary = (ISVNWinCryptLibrary) Native.loadLibrary("Crypt32", ISVNWinCryptLibrary.class);
            ourKenrelLibrary = (ISVNKernel32Library) Native.loadLibrary("Kernel32", ISVNKernel32Library.class);
        } catch (Throwable th) {
            ourWinCryptLibrary = null;
            ourKenrelLibrary = null;
        }
    }
    
    public static synchronized ISVNWinCryptLibrary getWinCryptLibrary() {
        return ourWinCryptLibrary;
    }

    public static synchronized ISVNKernel32Library getKernelLibrary() {
        return ourKenrelLibrary;
    }
}
