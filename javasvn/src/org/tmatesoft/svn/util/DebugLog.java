/*
 * ====================================================================
 * Copyright (c) 2004 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://tmate.org/svn/license.html.
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */

package org.tmatesoft.svn.util;

import java.io.File;

/**
 * @author TMate Software Ltd.
 */
public class DebugLog {

	private static DebugLogger ourLogger = new DebugDefaultLogger();
	private static final File ourSafeModeTrigger = new File(".javasvn.safemode");

	public static void setLogger(DebugLogger logger) {
		ourLogger = logger;
	}

	public static boolean isEnabled() {
		return ourLogger != null;
	}

	public static void log(String message) {
		if (ourLogger == null || !ourLogger.isFineEnabled()) {
			return;
		}
		ourLogger.logFine(message);
	}

	public static void logInfo(String message) {
		if (ourLogger == null || !ourLogger.isInfoEnabled()) {
			return;
		}
		ourLogger.logInfo(message);
	}

	public static void benchmark(String message) {
		if (ourLogger == null || !ourLogger.isInfoEnabled()) {
			return;
		}
		ourLogger.logInfo(message);
	}

	public static void error(String message) {
		if (ourLogger == null || !ourLogger.isErrorEnabled()) {
			return;
		}
		ourLogger.logError(message, null);
	}

	public static void error(Throwable th) {
		if (ourLogger == null || !ourLogger.isErrorEnabled()) {
			return;
		}
		ourLogger.logError(th.getMessage(), th);
	}

	public static boolean isSafeMode() {
		if (ourLogger == null) {
			return false;
		}
		if (isSafeModeDefault() && System.getProperty("javasvn.safemode") == null) {
			return true;
		}
		return Boolean.getBoolean("javasvn.safemode");
	}

	public static boolean isGeneratorDisabled() {
		if (ourLogger == null) {
			return false;
		}
		if (isSafeModeDefault()) {
			// have to enable explicitly
			return !Boolean.getBoolean("javasvn.generator.enabled");
		}
		if (System.getProperty("javasvn.generator.enabled") == null) {
			return false;
		}
		return !Boolean.getBoolean("javasvn.generator.enabled");
	}

	public static boolean isSVNLoggingEnabled() {
		if (ourLogger == null) {
			return false;
		}

		return ourLogger.isSVNLoggingEnabled();
	}

	static boolean isSafeModeDefault() {
		if (ourLogger == null) {
			return false;
		}
		return ourSafeModeTrigger.exists();
	}
}
