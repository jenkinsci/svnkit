/*
 * ====================================================================
 * Copyright (c) 2004-2006 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://tmate.org/svn/license.html.
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.core;

import java.io.InputStream;
import java.io.OutputStream;

import org.eclipse.core.runtime.ILog;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;
import org.osgi.framework.Bundle;
import org.tmatesoft.svn.util.SVNDebugLogAdapter;

/**
 * @author alex
 */
public class JavaSVNLog extends SVNDebugLogAdapter {
	
	private static final String DEBUG_FINE = "/debug/fine";
	private static final String DEBUG_INFO = "/debug/info";
	private static final String DEBUG_ERROR = "/debug/error";

	private boolean myIsFineEnabled;
	private boolean myIsInfoEnabled;
	private boolean myIsErrorEnabled;
	
	private ILog myLog;
	private String myPluginID;

	public JavaSVNLog(Bundle bundle, boolean enabled) {		
		myLog = Platform.getLog(bundle);
		myPluginID = bundle.getSymbolicName();

		// enabled even when not in debug mode
		myIsErrorEnabled = Boolean.TRUE.toString().equals(Platform.getDebugOption(myPluginID + DEBUG_ERROR));

		// debug mode have to be enabled
		myIsFineEnabled = enabled && Boolean.TRUE.toString().equals(Platform.getDebugOption(myPluginID + DEBUG_FINE));
		myIsInfoEnabled = enabled && Boolean.TRUE.toString().equals(Platform.getDebugOption(myPluginID + DEBUG_INFO));
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

	private Status createStatus(int severity, String message, Throwable th) {
		return new Status(severity, myPluginID, IStatus.OK, message == null ? "" : message, th);
	}

    public void info(String message) {
        if (isInfoEnabled()) {
            myLog.log(createStatus(IStatus.INFO, message, null));
        }
    }

    public void info(Throwable th) {
        if (isInfoEnabled()) {
            myLog.log(createStatus(IStatus.INFO, th != null ? th.getMessage() : "", th));
        }
    }

    public void error(String message) {
        if (isErrorEnabled()) {
            myLog.log(createStatus(IStatus.ERROR, message, null));
        }
    }

    public void error(Throwable th) {
        if (isErrorEnabled()) {
            myLog.log(createStatus(IStatus.ERROR, th != null ? th.getMessage() : "", th));
        }
    }

    public void log(String message, byte[] data) {
        if (isFineEnabled()) {
            myLog.log(createStatus(IStatus.INFO, message + " : " + new String(data), null));
        }
    }

    public InputStream createLogStream(InputStream is) {
        if (isFineEnabled()) {
            return super.createLogStream(is);
        }
        return is;
    }

    public OutputStream createLogStream(OutputStream os) {
        if (isFineEnabled()) {
            return super.createLogStream(os);
        }
        return os;
    }
}
