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

import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.wc.admin.SVNAdminClient;
import org.tmatesoft.svn.util.SVNLogType;


/**
 * @version 1.3
 * @author  TMate Software Ltd.
 */
public class SVNAdminSetUUIDCommand extends SVNAdminCommand {

    public SVNAdminSetUUIDCommand() {
        super("setuuid", null);
    }

    protected Collection createSupportedOptions() {
        return null;
    }

    public void run() throws SVNException {
        String uuid = null;
        if (getEnvironment().getArguments().size() > 2) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CL_ARG_PARSING_ERROR);
            SVNErrorManager.error(err, SVNLogType.CLIENT);
        } else if (getEnvironment().getArguments().size() > 1) {
            uuid = (String) getEnvironment().getArguments().get(1);
        }
        SVNAdminClient client = getEnvironment().getClientManager().getAdminClient();
        client.doSetUUID(getLocalRepository(), uuid);
    }

}
