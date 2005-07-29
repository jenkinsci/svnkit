/*
 * Created on 17.02.2005
 * 
 */
package org.tmatesoft.svn.util;

import java.io.InputStream;
import java.io.OutputStream;

import org.tmatesoft.svn.core.internal.util.SVNLogInputStream;
import org.tmatesoft.svn.core.internal.util.SVNLogOutputStream;

/**
 * @version 1.0
 * @author TMate Software Ltd.
 */
public class SVNDebugLoggerAdapter implements ISVNDebugLogger {

    public void logInfo(String message) {
    }

    public void logError(String message) {
    }

    public void logInfo(Throwable th) {
    }

    public void logError(Throwable th) {
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
