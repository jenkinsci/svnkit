/*
 * ====================================================================
 * Copyright (c) 2004-2008 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.core;

import org.eclipse.core.runtime.Plugin;
import org.osgi.framework.BundleContext;
import org.tmatesoft.svn.core.internal.io.dav.DAVRepositoryFactory;
import org.tmatesoft.svn.core.internal.io.fs.FSRepositoryFactory;
import org.tmatesoft.svn.core.internal.io.svn.SVNSSHSession;
import org.tmatesoft.svn.core.internal.io.svn.SVNRepositoryFactoryImpl;
import org.tmatesoft.svn.util.SVNDebugLog;

/**
 * The main plugin class to be used in the desktop.
 * 
 * @version 1.1.1
 * @author  TMate Software Ltd.
 */
public class SVNKitPlugin extends Plugin {

    public SVNKitPlugin() {
    }

    public void start(BundleContext context) throws Exception {
        super.start(context);
        SVNDebugLog.setDefaultLog(new SVNKitLog(getBundle(), isDebugging()));
        
        DAVRepositoryFactory.setup();
        SVNRepositoryFactoryImpl.setup();
        FSRepositoryFactory.setup();
    }
    
    
	public void stop(BundleContext context) throws Exception {
		SVNSSHSession.shutdown();
		super.stop(context);
	}
}
