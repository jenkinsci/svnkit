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
package org.tmatesoft.svn.cli.svndumpfilter;


import java.util.Iterator;

import org.tmatesoft.svn.core.SVNCancelException;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.wc.SVNEvent;
import org.tmatesoft.svn.core.wc.admin.ISVNAdminEventHandler;
import org.tmatesoft.svn.core.wc.admin.SVNAdminClient;
import org.tmatesoft.svn.core.wc.admin.SVNAdminEvent;
import org.tmatesoft.svn.core.wc.admin.SVNAdminEventAction;


/**
 * @version 1.3
 * @author  TMate Software Ltd.
 */
public class SVNDumpFilterIncludeCommand extends SVNDumpFilterCommand implements ISVNAdminEventHandler {
    private boolean myHasPrintedRenumberedRevisionsHeader;
    private boolean myIsPrintFinalEOL;

    public SVNDumpFilterIncludeCommand() {
        super("include", null, 0);
    }
    
    public void run() throws SVNException {
        SVNDumpFilterCommandEnvironment environment = getSVNDumpFilterEnvironment();
        if (!environment.isQuiet()) {
            if (environment.isDropEmptyRevisions()) {
                environment.getErr().println("Including (and dropping empty revisions for) prefixes:");
            } else {
                environment.getErr().println("Including prefixes:");
            }
            
            for (Iterator prefixesIter = environment.getPrefixes().iterator(); prefixesIter.hasNext();) {
                String prefix = (String) prefixesIter.next();
                environment.getErr().println("   '" + prefix + "'");
            }
            environment.getErr().println();
        }

        SVNAdminClient client = getEnvironment().getClientManager().getAdminClient();
        client.setEventHandler(this);
        client.doFilter(environment.getIn(), environment.getOut(), false, environment.isRenumberRevisions(), 
                environment.isDropEmptyRevisions(), environment.isPreserveRevisionProperties(), 
                environment.getPrefixes(), environment.isSkipMissingMergeSources());
        if (!environment.isQuiet() && myIsPrintFinalEOL) {
            environment.getErr().println();
        }
    }

    public void handleAdminEvent(SVNAdminEvent event, double progress) throws SVNException {
        SVNDumpFilterCommandEnvironment environment = getSVNDumpFilterEnvironment();
        if (!environment.isQuiet()) {
            SVNAdminEventAction action = event.getAction();
            if (action == SVNAdminEventAction.DUMP_FILTER_REVISION_COMMITTED || 
                    action == SVNAdminEventAction.DUMP_FILTER_REVISION_SKIPPED) {
                environment.getErr().println(event.getMessage());
            } else if (action == SVNAdminEventAction.DUMP_FILTER_TOTAL_REVISIONS_DROPPED) {
                environment.getErr().println();
                environment.getErr().println(event.getMessage());
                environment.getErr().println();
                environment.getErr().println();
            } else if (action == SVNAdminEventAction.DUMP_FILTER_RENUMBERED_REVISION || 
                    action == SVNAdminEventAction.DUMP_FILTER_DROPPED_RENUMBERED_REVISION) {
                if (!myHasPrintedRenumberedRevisionsHeader) {
                    environment.getErr().println("Revisions renumbered as follows:");
                    myHasPrintedRenumberedRevisionsHeader = true;
                }
                environment.getErr().println("   " + event.getMessage());
            } else if (action == SVNAdminEventAction.DUMP_FILTER_TOTAL_NODES_DROPPED) {
                if (myHasPrintedRenumberedRevisionsHeader) {
                    environment.getErr().println();
                }
                environment.getErr().println(event.getMessage());
                myIsPrintFinalEOL = true;
            } else if (action == SVNAdminEventAction.DUMP_FILTER_DROPPED_NODE) {
                environment.getErr().println("   " + event.getMessage());
            }
        }
    }
    
    public void handleEvent(SVNEvent event, double progress) throws SVNException {
    }
    
    public void checkCancelled() throws SVNCancelException {
    }

}
