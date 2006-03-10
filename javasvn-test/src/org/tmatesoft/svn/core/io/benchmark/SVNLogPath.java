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
package org.tmatesoft.svn.core.io.benchmark;

import org.tmatesoft.svn.core.ISVNLogEntryHandler;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNLogEntry;
import org.tmatesoft.svn.core.io.SVNRepository;

public class SVNLogPath extends SVNMeasurable implements Runnable {
    
    private static final String[] DEFAULT_LOG_PATHS = new String[] {"/trunk"};
    private String[] myLogPaths;
    
    public SVNLogPath() {
        this(null);
    }
    
    public SVNLogPath(String path) {
        myLogPaths = path != null ? new String[] {path} : DEFAULT_LOG_PATHS;
    }

    protected void measure(SVNRepository repos) throws SVNException {
        long latest = repos.getLatestRevision();
        repos.log(myLogPaths, 0, latest, true, false, 0, new ISVNLogEntryHandler() {
            public void handleLogEntry(SVNLogEntry logEntry) throws SVNException {
            }
        });
    }

    protected String getName() {
        return "log(" + myLogPaths[0] + ")";
    }
    
}