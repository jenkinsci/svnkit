/*
 * ====================================================================
 * Copyright (c) 2004-2012 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.core.javahl;


import java.util.logging.Level;

import org.tigris.subversion.javahl.JavaHLObjectFactory;
import org.tigris.subversion.javahl.ProgressEvent;
import org.tigris.subversion.javahl.ProgressListener;
import org.tmatesoft.svn.util.SVNDebugLogAdapter;
import org.tmatesoft.svn.util.SVNLogType;


/**
 * @author TMate Software Ltd.
 * @version 1.3
 */
public class JavaHLProgressLog extends SVNDebugLogAdapter {

    private ProgressListener myProgressListener;
    private long myProgress;

    public JavaHLProgressLog(ProgressListener progressListener) {
        myProgressListener = progressListener;
        reset();
    }

    public void log(SVNLogType logType, String message, byte[] data) {
        myProgress += data.length;
        ProgressEvent event = JavaHLObjectFactory.createProgressEvent(myProgress, -1L);
        myProgressListener.onProgress(event);
    }

    public void reset() {
        myProgress = 0;
    }

    public void log(SVNLogType logType, Throwable th, Level logLevel) {
    }

    public void log(SVNLogType logType, String message, Level logLevel) {
    }
}
