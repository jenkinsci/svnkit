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
package org.tmatesoft.svn.cli.svndumpfilter;


import org.tmatesoft.svn.core.SVNCancelException;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.wc.SVNEvent;
import org.tmatesoft.svn.core.wc.admin.ISVNAdminEventHandler;
import org.tmatesoft.svn.core.wc.admin.SVNAdminEvent;


/**
 * @version 1.1.2
 * @author  TMate Software Ltd.
 */
public class SVNDumpFilterExcludeCommand extends SVNDumpFilterCommand implements ISVNAdminEventHandler {

    public SVNDumpFilterExcludeCommand() {
        super("exclude", null);
    }

    public void run() throws SVNException {
        SVNDumpFilterCommandEnvironment environment = getSVNDumpFilterEnvironment();
        if (!environment.isQuiet()) {
            if (environment.isDropEmptyRevisions()) {
                environment.getErr().println("Excluding (and dropping empty revisions for) prefixes:");
            } else {
                environment.getErr().println("Excluding prefixes:");
            }
        }
    }

    public void handleAdminEvent(SVNAdminEvent event, double progress) throws SVNException {
    }
    
    public void handleEvent(SVNEvent event, double progress) throws SVNException {
    }
    
    public void checkCancelled() throws SVNCancelException {
    }

}
