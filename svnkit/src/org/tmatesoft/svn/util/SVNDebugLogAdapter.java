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
import java.util.logging.Level;

import org.tmatesoft.svn.core.internal.util.SVNLogInputStream;
import org.tmatesoft.svn.core.internal.util.SVNLogOutputStream;
import org.tmatesoft.svn.core.internal.util.SVNLogStream;

/**
 * @version 1.1.1
 * @author  TMate Software Ltd.
 */
public abstract class SVNDebugLogAdapter implements ISVNDebugLog {

    public void logError(String message) {
        log(message, Level.INFO);
    }

    public void logError(Throwable th) {
        log(th, Level.INFO);
    }

    public void logSevere(String message) {
        log(message, Level.SEVERE);
    }

    public void logSevere(Throwable th) {
        log(th, Level.SEVERE);
    }

    public void logFine(Throwable th) {
        log(th, Level.FINE);
    }

    public void logFine(String message) {
        log(message, Level.FINE);
    }

    public void logFiner(Throwable th) {
        log(th, Level.FINER);
    }

    public void logFiner(String message) {
        log(message, Level.FINER);
    }

    public void logFinest(Throwable th) {
        log(th, Level.FINEST);
    }

    public void logFinest(String message) {
        log(message, Level.FINEST);
    }

    public void flushStream(Object stream) {
        if (stream instanceof SVNLogInputStream) {
            SVNLogInputStream logStream = (SVNLogInputStream) stream;
            logStream.flushBuffer();
        } else if (stream instanceof SVNLogOutputStream) {
            SVNLogOutputStream logStream = (SVNLogOutputStream) stream;
            logStream.flushBuffer();
        }
    }

    public InputStream createLogStream(InputStream is) {
        return new SVNLogInputStream(is, this);
    }

    public OutputStream createLogStream(OutputStream os) {
        return new SVNLogOutputStream(os, this);
    }

    public OutputStream createInputLogStream() {
        return new SVNLogStream(this, false);
    }

    public OutputStream createOutputLogStream() {
        return new SVNLogStream(this, true);
    }

}
