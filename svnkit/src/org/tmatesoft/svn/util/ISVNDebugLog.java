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
package org.tmatesoft.svn.util;

import java.io.InputStream;
import java.io.OutputStream;


/**
 * @version 1.1.0
 * @author  TMate Software Ltd.
 */
public interface ISVNDebugLog {
    
    public void info(String message);

    public void error(String message);
    
    public void info(Throwable th);

    public void error(Throwable th);
    
    public void log(String message, byte[] data);
    
    public void flushStream(Object stream);
    
    public InputStream createLogStream(InputStream is);

    public OutputStream createLogStream(OutputStream os);

}
