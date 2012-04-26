/*
 * ====================================================================
 * Copyright (c) 2004-2012 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html.
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.core;

import org.eclipse.osgi.framework.log.FrameworkLog;
import org.eclipse.osgi.framework.log.FrameworkLogEntry;
import org.eclipse.osgi.service.debug.DebugOptions;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.util.tracker.ServiceTracker;
import org.tmatesoft.svn.core.internal.io.svn.SVNSSHConnector;
import org.tmatesoft.svn.util.ISVNDebugLog;
import org.tmatesoft.svn.util.SVNDebugLog;


/**
 * @version 1.1.2
 * @author  TMate Software Ltd.
 */
public class SVNKitActivator implements BundleActivator {
    
    private ServiceTracker myDebugTracker = null;
    private ServiceTracker myFLogTracker = null;
    private String myPluginID;

    public void start(BundleContext context) throws Exception {
        myPluginID = context.getBundle().getSymbolicName();
        
        myDebugTracker = new ServiceTracker(context, DebugOptions.class.getName(), null);
        myDebugTracker.open();

        myFLogTracker = new ServiceTracker(context, FrameworkLog.class.getName(), null);
        myFLogTracker.open();

        ISVNDebugLog debugLog = new SVNKitLog(this);
        SVNDebugLog.setDefaultLog(debugLog);
    }
    
    public boolean getDebugOption(String option) {
        if (myDebugTracker == null) {
            return false;
        }
        DebugOptions options = (DebugOptions) myDebugTracker.getService();
        if (options != null) {
            option = myPluginID + option;
            String value = options.getOption(option);
            return "true".equalsIgnoreCase(value);
        }
        return false;
    }
    
    public FrameworkLogEntry createFrameworkLogEntry(int level, String message, Throwable th) {
        return new FrameworkLogEntry(myPluginID, level, 0, message, 0, th, null);
    }

    public FrameworkLog getFrameworkLog() {
        if (myFLogTracker == null) {
            return null;
        }
        FrameworkLog log = (FrameworkLog) myFLogTracker.getService();
        if (log != null) {
            return log;
        }
        return null;
    }

    public void stop(BundleContext context) throws Exception {
        SVNSSHConnector.shutdown();
        
        try {
            if (myDebugTracker != null) {
                myDebugTracker.close();
            }
        } finally {
            myDebugTracker = null;
        }
        try {
            if (myFLogTracker != null) {
                myFLogTracker.close();
            }
        } finally {
            myFLogTracker = null;
        }
    }

}
