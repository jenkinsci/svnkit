package org.tmatesoft.svn.util;

/**
 * @author Marc Strapetz
 */
public interface LoggingStreamLogger {
	public void logStream(String content, boolean writeNotRead);
}