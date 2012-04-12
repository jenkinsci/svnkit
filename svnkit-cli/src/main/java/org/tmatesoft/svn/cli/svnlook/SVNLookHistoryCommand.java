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
package org.tmatesoft.svn.cli.svnlook;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.wc.admin.ISVNHistoryHandler;
import org.tmatesoft.svn.core.wc.admin.SVNAdminPath;
import org.tmatesoft.svn.core.wc.admin.SVNLookClient;


/**
 * @version 1.3
 * @author  TMate Software Ltd.
 */
public class SVNLookHistoryCommand extends SVNLookCommand implements ISVNHistoryHandler {

    public SVNLookHistoryCommand() {
        super("history", null);
    }

    protected Collection createSupportedOptions() {
        List options = new LinkedList();
        options.add(SVNLookOption.REVISION);
        options.add(SVNLookOption.SHOW_IDS);
        options.add(SVNLookOption.LIMIT);
        return options;
    }

    public void run() throws SVNException {
        SVNLookCommandEnvironment environment = getSVNLookEnvironment(); 
        if (environment.isShowIDs()) {
            environment.getOut().println("REVISION   PATH <ID>");
            environment.getOut().println("--------   ---------");
        } else {
            environment.getOut().println("REVISION   PATH");
            environment.getOut().println("--------   ----");
        }

        SVNLookClient client = environment.getClientManager().getLookClient();
        client.doGetHistory(environment.getRepositoryFile(), environment.getFirstArgument(), 
                getRevisionObject(), environment.isShowIDs(), environment.getLimit(), 
                this);
    }

    public void handlePath(SVNAdminPath path) throws SVNException {
        if (path != null) {
            SVNLookCommandEnvironment environment = getSVNLookEnvironment();
            if (environment.isShowIDs()) {
                environment.getOut().println(path.getRevision() + "   " + path.getPath() + " <" + 
                        path.getNodeID() + ">");
            } else {
                environment.getOut().println(path.getRevision() + "   " + path.getPath());
            }
        }
    }

}
