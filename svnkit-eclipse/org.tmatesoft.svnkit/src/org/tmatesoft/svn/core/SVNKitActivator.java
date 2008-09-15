/*
 * ====================================================================
 * Copyright (c) 2004-2008 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html.
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.core;

import org.eclipse.osgi.service.debug.DebugOptions;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.service.log.LogService;
import org.osgi.util.tracker.ServiceTracker;
import org.tmatesoft.svn.core.internal.io.dav.DAVRepositoryFactory;
import org.tmatesoft.svn.core.internal.io.fs.FSRepositoryFactory;
import org.tmatesoft.svn.core.internal.io.svn.SVNRepositoryFactoryImpl;
import org.tmatesoft.svn.core.internal.io.svn.SVNSSHSession;
import org.tmatesoft.svn.util.ISVNDebugLog;
import org.tmatesoft.svn.util.SVNDebugLog;


/**
 * @version 1.1.2
 * @author  TMate Software Ltd.
 */
public class SVNKitActivator implements BundleActivator {
    
    private ServiceTracker myDebugTracker = null;
    private ServiceTracker myLogTracker = null;
    private String myPluginID;

    public void start(BundleContext context) throws Exception {
        myPluginID = context.getBundle().getSymbolicName();
        
        DAVRepositoryFactory.setup();
        SVNRepositoryFactoryImpl.setup();
        FSRepositoryFactory.setup();
        
        myDebugTracker = new ServiceTracker(context, DebugOptions.class.getName(), null);
        myDebugTracker.open();
        
        myLogTracker = new ServiceTracker(context, LogService.class.getName(), null);
        myLogTracker.open();

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
            if (value != null)
                return value.equalsIgnoreCase("true"); //$NON-NLS-1$
        }
        return false;
    }

    public LogService getLogService() {
        if (myLogTracker == null) {
            return null;
        }
        LogService log = (LogService) myLogTracker.getService();
        if (log != null) {
            return log;
        }
        return null;
    }

    public void stop(BundleContext context) throws Exception {
        SVNSSHSession.shutdown();
        
        try {
            if (myDebugTracker != null) {
                myDebugTracker.close();
            }
        } finally {
            myDebugTracker = null;
        }
        try {
            if (myLogTracker != null) {
                myLogTracker.close();
            }
        } finally {
            myLogTracker = null;
        }
    }

}
