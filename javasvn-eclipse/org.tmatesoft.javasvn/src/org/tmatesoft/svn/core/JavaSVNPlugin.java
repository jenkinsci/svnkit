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

import org.eclipse.core.runtime.Plugin;
import org.osgi.framework.BundleContext;
import org.tmatesoft.svn.core.internal.io.dav.DAVRepositoryFactory;
import org.tmatesoft.svn.core.internal.io.svn.SVNGanymedSession;
import org.tmatesoft.svn.core.internal.io.svn.SVNRepositoryFactoryImpl;
import org.tmatesoft.svn.util.SVNDebugLog;

/**
 * The main plugin class to be used in the desktop.
 */
public class JavaSVNPlugin extends Plugin {

    public JavaSVNPlugin() {
    }

    public void start(BundleContext context) throws Exception {
        super.start(context);
        SVNDebugLog.setLogger(new JavaSVNLogger(getBundle(), isDebugging()));
        
        DAVRepositoryFactory.setup();
        SVNRepositoryFactoryImpl.setup();
    }
    
    
	public void stop(BundleContext context) throws Exception {
		SVNGanymedSession.shutdown();
		super.stop(context);
	}
}
