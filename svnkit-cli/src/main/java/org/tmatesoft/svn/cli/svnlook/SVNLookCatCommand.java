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

import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.wc.admin.SVNLookClient;
import org.tmatesoft.svn.util.SVNLogType;


/**
 * @version 1.1.2
 * @author  TMate Software Ltd.
 */
public class SVNLookCatCommand extends SVNLookCommand {

    protected SVNLookCatCommand() {
        super("cat", null);
    }

    protected Collection createSupportedOptions() {
        List options = new LinkedList();
        options.add(SVNLookOption.REVISION);
        options.add(SVNLookOption.TRANSACTION);
        return options;
    }

    public void run() throws SVNException {
        SVNLookCommandEnvironment environment = getSVNLookEnvironment(); 
        String path = environment.getFirstArgument();
        if (environment.getFirstArgument() == null) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CL_INSUFFICIENT_ARGS, 
                    "Missing repository path argument");
            SVNErrorManager.error(err, SVNLogType.CLIENT);
        } 

        SVNLookClient client = environment.getClientManager().getLookClient();
        if (environment.isRevision()) {
            client.doCat(environment.getRepositoryFile(), path, getRevisionObject(), environment.getOut());
        } else {
            client.doCat(environment.getRepositoryFile(), path, environment.getTransaction(), environment.getOut());
        }
    }

}
