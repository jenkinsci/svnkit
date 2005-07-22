/*
 * ====================================================================
 * Copyright (c) 2004 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://tmate.org/svn/license.html.
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.util;

import java.io.InputStream;
import java.io.OutputStream;


/**
 * @version 1.0
 * @author  TMate Software Ltd.
 */
public interface ISVNDebugLogger {
    
    public void log(String message);
    
    public void log(Throwable th);
    
    public void log(String message, byte[] data);
    
    public void flushStream(Object stream);
    
    public InputStream createLogStream(InputStream is);

    public OutputStream createLogStream(OutputStream os);

}
