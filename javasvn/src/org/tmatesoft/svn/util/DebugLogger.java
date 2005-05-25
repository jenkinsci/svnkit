package org.tmatesoft.svn.util;

import java.io.InputStream;
import java.io.OutputStream;

/**
 * 
 * @author Marc Strapetz
 */
public interface DebugLogger {
	public boolean isFineEnabled();

	public boolean isInfoEnabled();

	public void logFine(String message);

	public void logInfo(String message);

	public boolean isErrorEnabled();

	public void logError(String message, Throwable th);

	public LoggingInputStream getLoggingInputStream(String protocol, InputStream stream);

	public LoggingOutputStream getLoggingOutputStream(String protocol, OutputStream stream);
}