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
package org.tmatesoft.svn.core.io.benchmark;

import org.tmatesoft.svn.core.ISVNLogEntryHandler;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNLogEntry;
import org.tmatesoft.svn.core.io.SVNRepository;

/**
 * @version 1.2.0
 * @author  TMate Software Ltd.
 */
class SVNLogRoot extends SVNMeasurable implements Runnable {

    protected void measure(SVNRepository repos) throws SVNException {
        long latest = repos.getLatestRevision();
        repos.log(null, 0, latest, true, false, 0, new ISVNLogEntryHandler() {
            public void handleLogEntry(SVNLogEntry logEntry) throws SVNException {
            }
        });
    }

    protected String getName() {
        return "log(/)";
    }
    
}