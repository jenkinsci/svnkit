package org.tmatesoft.svn.util;

/**
 * @author Marc Strapetz
 */
public interface DebugLogger {
	boolean isFineEnabled();

	boolean isInfoEnabled();

	void logFine(String message);

	public void logInfo(String message);

	boolean isErrorEnabled();

	void logError(String message, Throwable th);

	boolean isSVNLoggingEnabled();
}