/*
 * ====================================================================
 * Copyright (c) 2004-2012 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.cli.svnsync;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

import org.tmatesoft.svn.core.ISVNLogEntryHandler;
import org.tmatesoft.svn.core.SVNCancelException;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNLogEntry;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.wc.SVNPath;
import org.tmatesoft.svn.core.wc.SVNEvent;
import org.tmatesoft.svn.core.wc.admin.ISVNAdminEventHandler;
import org.tmatesoft.svn.core.wc.admin.SVNAdminClient;
import org.tmatesoft.svn.core.wc.admin.SVNAdminEvent;
import org.tmatesoft.svn.core.wc.admin.SVNAdminEventAction;
import org.tmatesoft.svn.util.SVNLogType;


/**
 * @author TMate Software Ltd.
 * @version 1.3
 */
public class SVNSyncSynchronizeCommand extends SVNSyncCommand implements ISVNAdminEventHandler {

    public SVNSyncSynchronizeCommand() {
        super("synchronize", new String[]{"sync"}, 0);
    }
    
    protected Collection createSupportedOptions() {
        LinkedList options = new LinkedList();
        options.add(SVNSyncOption.NON_INTERACTIVE);
        options.add(SVNSyncOption.NO_AUTH_CACHE);
        options.add(SVNSyncOption.USERNAME);
        options.add(SVNSyncOption.PASSWORD);
        options.add(SVNSyncOption.TRUST_SERVER_CERT);
        options.add(SVNSyncOption.SOURCE_USERNAME);
        options.add(SVNSyncOption.SOURCE_PASSWORD);
        options.add(SVNSyncOption.SYNC_USERNAME);
        options.add(SVNSyncOption.SYNC_PASSWORD);
        options.add(SVNSyncOption.CONFIG_DIR);
        options.add(SVNSyncOption.QUIET);
        return options;
    }

    public void run() throws SVNException {
        List targets = getEnvironment().combineTargets(null, false);
        if (targets.size() < 1) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CL_INSUFFICIENT_ARGS);
            SVNErrorManager.error(err, SVNLogType.CLIENT);
        }
        if (targets.size() > 1) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CL_ARG_PARSING_ERROR);
            SVNErrorManager.error(err, SVNLogType.CLIENT);
        }
        
        SVNPath toURL = new SVNPath((String) targets.get(0));
        if (!toURL.isURL()) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CL_ARG_PARSING_ERROR, 
                    "Path ''{0}'' is not a URL", toURL.getTarget());
            SVNErrorManager.error(err, SVNLogType.CLIENT);
        }
        
        SVNAdminClient client = getEnvironment().getClientManager().getAdminClient();
        client.setEventHandler(this);
        client.setReplayHandler(new ISVNLogEntryHandler() {
           public void handleLogEntry(SVNLogEntry logEntry) throws SVNException {
               getEnvironment().getOut().println("Committed revision " + logEntry.getRevision() + "."); 
           } 
        });
        client.doSynchronize(toURL.getURL());
    }

    public void handleAdminEvent(SVNAdminEvent event, double progress) throws SVNException {
        if (event.getAction() == SVNAdminEventAction.REVISION_PROPERTIES_COPIED || 
                event.getAction() == SVNAdminEventAction.NORMALIZED_PROPERTIES) {
            if (!getSVNSyncEnvironment().isQuiet()) {
                getSVNSyncEnvironment().getOut().println(event.getMessage());
            }
        }
    }

    public void handleEvent(SVNEvent event, double progress) throws SVNException {
    }

    public void checkCancelled() throws SVNCancelException {
    }
}
