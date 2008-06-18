/*
 * ====================================================================
 * Copyright (c) 2004-2007 TMate Software Ltd.  All rights reserved.
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
 * @version 1.1.1
 * @author  TMate Software Ltd.
 */
public interface ISVNDebugLog {
    
    public void logInfo(String message);

    public void logInfo(Throwable th);

    public void logSevere(Throwable th);

    public void logSevere(String message);

    public void logFine(Throwable th);

    public void logFine(String message);

    public void logFiner(Throwable th);

    public void logFiner(String message);

    public void logFinest(Throwable th);

    public void logFinest(String message);
    
    public void log(String message, byte[] data);

    public void flushStream(Object stream);
    
    public InputStream createLogStream(InputStream is);
    
    public OutputStream createInputLogStream();

    public OutputStream createLogStream(OutputStream os);

    public OutputStream createOutputLogStream();

}
