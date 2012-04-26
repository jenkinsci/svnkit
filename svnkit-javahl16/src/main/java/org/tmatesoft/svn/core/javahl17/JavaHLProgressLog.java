package org.tmatesoft.svn.core.javahl17;

import java.util.logging.Level;

import org.apache.subversion.javahl.ProgressEvent;
import org.apache.subversion.javahl.callback.ProgressCallback;
import org.tmatesoft.svn.util.SVNDebugLogAdapter;
import org.tmatesoft.svn.util.SVNLogType;

public class JavaHLProgressLog extends SVNDebugLogAdapter {

    private ProgressCallback progressCallback;
    private long progress;

    public JavaHLProgressLog(ProgressCallback progressCallback) {
        this.progressCallback = progressCallback;
         reset();
    }

    public void log(SVNLogType logType, String message, byte[] data) {
        progress += data.length;
        progressCallback.onProgress(createProgressEvent(progress));
    }

    public void reset() {
        progress = 0;
    }

    public void log(SVNLogType logType, Throwable th, Level logLevel) {
    }

    public void log(SVNLogType logType, String message, Level logLevel) {
    }

    private ProgressEvent createProgressEvent(long progress) {
        return new ProgressEvent(progress, -1L);
    }
}
