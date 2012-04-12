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
package org.tmatesoft.svn.cli.svnadmin;

import java.util.Collection;
import java.util.LinkedList;

import org.tmatesoft.svn.core.SVNCancelException;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.wc.SVNEvent;
import org.tmatesoft.svn.core.wc.admin.ISVNAdminEventHandler;
import org.tmatesoft.svn.core.wc.admin.SVNAdminClient;
import org.tmatesoft.svn.core.wc.admin.SVNAdminEvent;
import org.tmatesoft.svn.core.wc.admin.SVNAdminEventAction;
import org.tmatesoft.svn.core.wc.admin.SVNUUIDAction;


/**
 * @version 1.3
 * @author  TMate Software Ltd.
 */
public class SVNAdminLoadCommand extends SVNAdminCommand implements ISVNAdminEventHandler {

    private boolean myIsNodeOpened;

    public SVNAdminLoadCommand() {
        super("load", null);
    }

    protected Collection createSupportedOptions() {
        Collection options = new LinkedList();
        options.add(SVNAdminOption.QUIET);
        options.add(SVNAdminOption.IGNORE_UUID);
        options.add(SVNAdminOption.FORCE_UUID);
        options.add(SVNAdminOption.USE_PRE_COMMIT_HOOK);
        options.add(SVNAdminOption.USE_POST_COMMIT_HOOK);
        options.add(SVNAdminOption.PARENT_DIR);
        return options;
    }

    public void run() throws SVNException {
        SVNAdminClient client = getEnvironment().getClientManager().getAdminClient();
        if (!getSVNAdminEnvironment().isQuiet()) {
            client.setEventHandler(this);
        }
        SVNUUIDAction uuidAction = SVNUUIDAction.DEFAULT;
        if (getSVNAdminEnvironment().isForceUUID()) {
            uuidAction = SVNUUIDAction.FORCE_UUID;
        } else if (getSVNAdminEnvironment().isIgnoreUUID()) {
            uuidAction = SVNUUIDAction.IGNORE_UUID;
        }
        client.doLoad(getLocalRepository(), getEnvironment().getIn(), 
                getSVNAdminEnvironment().isUsePreCommitHook(),
                getSVNAdminEnvironment().isUsePostCommitHook(),
                uuidAction, getSVNAdminEnvironment().getParentDir());
    }

    public void handleAdminEvent(SVNAdminEvent event, double progress) throws SVNException {
        if (event.getAction() != SVNAdminEventAction.REVISION_LOAD && myIsNodeOpened) {
            getEnvironment().getOut().println(" done.");
            myIsNodeOpened = false;
        }
        if (event.getAction() == SVNAdminEventAction.REVISION_LOADED) {
            getEnvironment().getOut().println();
        }
        getEnvironment().getOut().print(event.getMessage());
        if (event.getAction() == SVNAdminEventAction.REVISION_LOADED || event.getAction() == SVNAdminEventAction.REVISION_LOAD) {
            getEnvironment().getOut().println();
        }
        myIsNodeOpened = event.getAction() != SVNAdminEventAction.REVISION_LOAD;
    }

    public void handleEvent(SVNEvent event, double progress) throws SVNException {
    }

    public void checkCancelled() throws SVNCancelException {
        getEnvironment().checkCancelled();
    }

}
