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

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.wc.admin.SVNAdminClient;


/**
 * @version 1.3
 * @author  TMate Software Ltd.
 */
public class SVNAdminHotCopyCommand extends SVNAdminCommand {

    public SVNAdminHotCopyCommand() {
        super("hotcopy", null);
    }

    protected Collection createSupportedOptions() {
        Collection options = new LinkedList();
        options.add(SVNAdminOption.CLEAN_LOGS);
        return options;
    }

    public void run() throws SVNException {
        SVNAdminClient client = getEnvironment().getClientManager().getAdminClient();
        client.doHotCopy(getLocalRepository(), getLocalRepository(1));
    }

}
