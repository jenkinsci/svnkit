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

import com.sun.jna.NativeLong;
import com.sun.jna.Pointer;
import com.sun.jna.WString;
import com.sun.jna.win32.StdCallLibrary;


/**
 * @version 1.1.2
 * @author  TMate Software Ltd.
 */
public interface ISVNKernel32Library extends StdCallLibrary {
    
    public long FILE_ATTRIBUTE_READONLY = 0x01;
    public long FILE_ATTRIBUTE_HIDDEN   = 0x02;
    public long FILE_ATTRIBUTE_NORMAL   = 0x80;
    
    public Pointer LocalFree(Pointer ptr);
    
    public int SetFileAttributesW(WString path, NativeLong attrs);

    public int MoveFileW(WString src, WString dst);

    public int MoveFileExW(WString src, WString dst, NativeLong flags);
}
