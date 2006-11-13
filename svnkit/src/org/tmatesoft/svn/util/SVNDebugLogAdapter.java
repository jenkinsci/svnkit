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

import org.tmatesoft.svn.core.internal.util.SVNLogInputStream;
import org.tmatesoft.svn.core.internal.util.SVNLogOutputStream;

/**
 * @version 1.1.0
 * @author  TMate Software Ltd.
 */
public class SVNDebugLogAdapter implements ISVNDebugLog {

    public void info(String message) {
    }

    public void error(String message) {
    }

    public void info(Throwable th) {
    }

    public void error(Throwable th) {
    }

    public void log(String message, byte[] data) {
    }

    public void flushStream(Object stream) {
        if (stream instanceof SVNLogInputStream) {
            SVNLogInputStream logStream = (SVNLogInputStream) stream;
            logStream.flushBuffer(true);
        } else if (stream instanceof SVNLogOutputStream) {
            SVNLogOutputStream logStream = (SVNLogOutputStream) stream;
            logStream.flushBuffer(true);
        }
    }

    public InputStream createLogStream(InputStream is) {
        return new SVNLogInputStream(is, this);
    }

    public OutputStream createLogStream(OutputStream os) {
        return new SVNLogOutputStream(os, this);
    }
}
