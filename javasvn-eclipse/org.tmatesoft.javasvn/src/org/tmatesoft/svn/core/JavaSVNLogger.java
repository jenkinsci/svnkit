/*
 * Created on Feb 12, 2005
 */
package org.tmatesoft.svn.core;

import java.io.InputStream;
import java.io.OutputStream;

import org.eclipse.core.runtime.ILog;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;
import org.osgi.framework.Bundle;
import org.tmatesoft.svn.util.DebugLogger;
import org.tmatesoft.svn.util.LoggingInputStream;
import org.tmatesoft.svn.util.LoggingOutputStream;
import org.tmatesoft.svn.util.LoggingStreamLogger;

/**
 * @author alex
 */
public class JavaSVNLogger implements DebugLogger, LoggingStreamLogger {
	
	private static final String DEBUG_FINE = "/debug/fine";
	private static final String DEBUG_INFO = "/debug/info";
	private static final String DEBUG_ERROR = "/debug/error";
	private static final String DEBUG_TRACE_SVN = "/debug/trace/svn";
	private static final String DEBUG_TRACE_HTTP = "/debug/trace/http";

	private boolean myIsFineEnabled;
	private boolean myIsInfoEnabled;
	private boolean myIsErrorEnabled;
	private boolean myIsHTTPTraceEnabled;
	private boolean myIsSVNTraceEnabled;
	
	private ILog myLog;
	private String myPluginID;

	public JavaSVNLogger(Bundle bundle, boolean enabled) {		
		myLog = Platform.getLog(bundle);
		myPluginID = bundle.getSymbolicName();

		// enabled even when not in debug mode
		myIsErrorEnabled = Boolean.TRUE.toString().equals(Platform.getDebugOption(myPluginID + DEBUG_ERROR));

		// debug mode have to be enabled
		myIsFineEnabled = enabled && Boolean.TRUE.toString().equals(Platform.getDebugOption(myPluginID + DEBUG_FINE));
		myIsInfoEnabled = enabled && Boolean.TRUE.toString().equals(Platform.getDebugOption(myPluginID + DEBUG_INFO));
		myIsHTTPTraceEnabled = enabled && Boolean.TRUE.toString().equals(Platform.getDebugOption(myPluginID + DEBUG_TRACE_HTTP));
		myIsSVNTraceEnabled = enabled && Boolean.TRUE.toString().equals(Platform.getDebugOption(myPluginID + DEBUG_TRACE_SVN));
	}

	public boolean isFineEnabled() {
		return myIsFineEnabled;
	}

	public boolean isInfoEnabled() {
		return myIsInfoEnabled;
	}

	public boolean isErrorEnabled() {
		return myIsErrorEnabled;
	}

	public void logFine(String message) {
		if (isFineEnabled()) {
			myLog.log(createStatus(IStatus.INFO, message, null));
		}
	}

	public void logInfo(String message) {
		if (isFineEnabled()) {
			myLog.log(createStatus(IStatus.INFO, message, null));
		}
	}

	public void logError(String message, Throwable th) {
		if (isFineEnabled()) {
			myLog.log(createStatus(IStatus.INFO, message, th));
		}
	}

	public LoggingInputStream getLoggingInputStream(String protocol, InputStream stream) {
		boolean enabled = ("http".equals(protocol) && myIsHTTPTraceEnabled) || 
			("svn".equals(protocol) && myIsSVNTraceEnabled); 
		return new LoggingInputStream(stream, enabled ? this : null);
	}

	public LoggingOutputStream getLoggingOutputStream(String protocol, OutputStream stream) {
		boolean enabled = ("http".equals(protocol) && myIsHTTPTraceEnabled) || 
		("svn".equals(protocol) && myIsSVNTraceEnabled); 
		return new LoggingOutputStream(stream, enabled ? this : null);
	}

	public void logStream(String content, boolean writeNotRead) {
		final String prefix = writeNotRead ? "JAVASVN.SENT: " : "JAVASVN.READ: ";
		content = content.replaceAll("\n", "\\\\n");
		content = content.replaceAll("\r", "\\\\r");
		myLog.log(createStatus(IStatus.INFO, prefix + content, null));
	}

	private Status createStatus(int severity, String message, Throwable th) {
		return new Status(severity, myPluginID, IStatus.OK, message, th);
	}
}
