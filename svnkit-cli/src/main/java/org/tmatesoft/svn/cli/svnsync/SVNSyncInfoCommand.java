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
package org.tmatesoft.svn.cli.svnsync;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.wc.SVNPath;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.admin.SVNAdminClient;
import org.tmatesoft.svn.core.wc.admin.SVNSyncInfo;
import org.tmatesoft.svn.util.SVNLogType;


/**
 * @version 1.3
 * @author  TMate Software Ltd.
 */
public class SVNSyncInfoCommand extends SVNSyncCommand {

    public SVNSyncInfoCommand() {
        super("info", null, 1);
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
        SVNSyncInfo info = client.doInfo(toURL.getURL());
        getSVNSyncEnvironment().getOut().println("Source URL: " + info.getSrcURL());
        if (info.getSourceRepositoryUUID() != null) {
            getSVNSyncEnvironment().getOut().println("Source Repository UUID: " + info.getSourceRepositoryUUID());
        }
        
        if (SVNRevision.isValidRevisionNumber(info.getLastMergedRevision())) {
            getSVNSyncEnvironment().getOut().println("Last Merged Revision: " + info.getLastMergedRevision());
        }
        
    }

}
