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
package org.tmatesoft.svn.core;

import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Plugin;
import org.osgi.framework.BundleContext;
import org.tmatesoft.svn.core.internal.io.dav.DAVRepositoryFactory;
import org.tmatesoft.svn.core.internal.io.svn.SVNJSchSession;
import org.tmatesoft.svn.core.internal.io.svn.SVNRepositoryFactoryImpl;
import org.tmatesoft.svn.core.internal.ws.fs.FSEntryFactory;
import org.tmatesoft.svn.util.DebugLog;

/**
 * The main plugin class to be used in the desktop.
 */
public class JavaSVNPlugin extends Plugin {

    public JavaSVNPlugin() {
    }

    public void start(BundleContext context) throws Exception {
        super.start(context);
		//SVNPromptCredentialsProvider.setCredentialsStorage(new JavaSVNCredentialsStorage());
        DebugLog.setLogger(new JavaSVNLogger(getBundle(), isDebugging()));
        initProxy();
        
        DAVRepositoryFactory.setup();
        SVNRepositoryFactoryImpl.setup();
         
        FSEntryFactory.setup();
    }
    
    
	public void stop(BundleContext context) throws Exception {
		SVNJSchSession.shutdown();
		super.stop(context);
	}
    
    private void initProxy() {
        String proxyHost = Platform.getPreferencesService().getString("org.eclipse.update.core", "org.eclipse.update.core.proxy.host", "", null);
        String proxyPort = Platform.getPreferencesService().getString("org.eclipse.update.core", "org.eclipse.update.core.proxy.port", "", null);
        String proxyEnabled = Platform.getPreferencesService().getString("org.eclipse.update.core", "org.eclipse.update.core.proxy.enabled", "false", null);
        if (System.getProperty("http.proxySet") == null) {
            System.setProperty("http.proxyHost", proxyHost == null ? "" : proxyHost);
            System.setProperty("http.proxyPort", proxyPort == null ? "" : proxyPort);
            System.setProperty("http.proxySet", proxyEnabled == null ? "false" : proxyEnabled);
            DebugLog.log("proxy set from update prefs: " + System.getProperty("http.proxyHost") + ":" + System.getProperty("http.proxyPort"));
        } else {
            DebugLog.log("proxy already set: " + System.getProperty("http.proxyHost") + ":" + System.getProperty("http.proxyPort"));
        }
        DebugLog.log("proxy enabled: " + System.getProperty("http.proxySet"));
        
    }
}
