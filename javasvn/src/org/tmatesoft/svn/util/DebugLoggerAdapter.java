/*
 * Created on 17.02.2005
 *
 */
package org.tmatesoft.svn.util;

import java.io.InputStream;
import java.io.OutputStream;

/**
 * @author alex
 */
public class DebugLoggerAdapter implements DebugLogger, LoggingStreamLogger {

	public boolean isFineEnabled() {
		return false;
	}
	public boolean isInfoEnabled() {
		return false;
	}
	public void logFine(String message) {
	}
	public void logInfo(String message) {
	}
	public boolean isErrorEnabled() {
		return false;
	}
	public void logError(String message, Throwable th) {
	}
	public void logStream(String content, boolean writeNotRead) {
	}

	public LoggingInputStream getLoggingInputStream(String protocol, InputStream stream) {
		return new LoggingInputStream(stream, this);
	}
	public LoggingOutputStream getLoggingOutputStream(String protocol, OutputStream stream) {
		return new LoggingOutputStream(stream, this);
	}
}
