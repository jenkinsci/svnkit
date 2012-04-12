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
import org.tmatesoft.svn.core.wc.admin.ISVNChangedDirectoriesHandler;
import org.tmatesoft.svn.core.wc.admin.SVNLookClient;


/**
 * @version 1.3
 * @author  TMate Software Ltd.
 */
public class SVNLookDirsChangedCommand extends SVNLookCommand implements ISVNChangedDirectoriesHandler {

    public SVNLookDirsChangedCommand() {
        super("dirs-changed", null);
    }
    
    protected Collection createSupportedOptions() {
        List options = new LinkedList();
        options.add(SVNLookOption.REVISION);
        options.add(SVNLookOption.TRANSACTION);
        return options;
    }

    public void run() throws SVNException {
        SVNLookCommandEnvironment environment = getSVNLookEnvironment(); 
        SVNLookClient client = environment.getClientManager().getLookClient();
        if (environment.isRevision()) {
            client.doGetChangedDirectories(environment.getRepositoryFile(), 
                    getRevisionObject(), this);
        } else {
            client.doGetChangedDirectories(environment.getRepositoryFile(), environment.getTransaction(), this);
        }
    }

    public void handleDir(String path) throws SVNException {
        if (path.startsWith("/")) {
            path = path.substring(1);
        }
        if (!path.endsWith("/")) {
            path += "/"; 
        }
        getSVNLookEnvironment().getOut().println(path);
    }

}
