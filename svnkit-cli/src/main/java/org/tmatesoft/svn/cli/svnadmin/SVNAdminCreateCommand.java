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
public class SVNAdminCreateCommand extends SVNAdminCommand {

    public SVNAdminCreateCommand() {
        super("create", null);
    }

    protected Collection createSupportedOptions() {
        Collection options = new LinkedList();
        options.add(SVNAdminOption.BDB_TXN_NOSYNC);
        options.add(SVNAdminOption.BDB_LOG_KEEP);
        options.add(SVNAdminOption.CONFIG_DIR);
        options.add(SVNAdminOption.FS_TYPE);
        options.add(SVNAdminOption.PRE_14_COMPATIBLE);
        options.add(SVNAdminOption.PRE_15_COMPATIBLE);
        options.add(SVNAdminOption.PRE_16_COMPATIBLE);
        options.add(SVNAdminOption.PRE_17_COMPATIBLE);
        options.add(SVNAdminOption.WITH_17_COMPATIBLE);
        return options;
    }

    public void run() throws SVNException {
        SVNAdminClient client = getEnvironment().getClientManager().getAdminClient();
        client.doCreateRepository(getLocalRepository(), null, false, false,
                getSVNAdminEnvironment().isPre14Compatible(), getSVNAdminEnvironment().isPre15Compatible(), getSVNAdminEnvironment().isPre16Compatible(),
                getSVNAdminEnvironment().isPre17Compatible(), getSVNAdminEnvironment().isWith17Compatible() );
    }

}
