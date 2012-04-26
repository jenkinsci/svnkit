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
package org.tmatesoft.svn.cli.svn;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.wc.SVNPath;
import org.tmatesoft.svn.core.wc.SVNWCClient;
import org.tmatesoft.svn.util.SVNLogType;


/**
 * @version 1.3
 * @author  TMate Software Ltd.
 */
public class SVNCatCommand extends SVNCommand {

    public SVNCatCommand() {
        super("cat", null);
    }

    protected Collection createSupportedOptions() {
        Collection options = new LinkedList();
        options.add(SVNOption.REVISION);
        return options;
    }

    public void run() throws SVNException {
        List targets = getSVNEnvironment().combineTargets(null, true);
        if (targets.isEmpty()) {
            SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.CL_INSUFFICIENT_ARGS), SVNLogType.CLIENT);
        }
        SVNWCClient client = getSVNEnvironment().getClientManager().getWCClient();

        boolean seenNonExistentTarget = false;
        
        for(int i = 0; i < targets.size(); i++) {
            SVNPath target = new SVNPath((String) targets.get(i), true);
            try {
                if (target.isURL()) {
                    client.doGetFileContents(target.getURL(), target.getPegRevision(), getSVNEnvironment().getStartRevision(), true, getSVNEnvironment().getOut());
                } else {
                    client.doGetFileContents(target.getFile(), target.getPegRevision(), getSVNEnvironment().getStartRevision(), true, getSVNEnvironment().getOut());
                }
            } catch (SVNException e) {
                SVNErrorMessage err = e.getErrorMessage();
                getSVNEnvironment().handleWarning(err, 
                        new SVNErrorCode[] {SVNErrorCode.UNVERSIONED_RESOURCE, SVNErrorCode.ENTRY_NOT_FOUND, SVNErrorCode.CLIENT_IS_DIRECTORY, SVNErrorCode.FS_NOT_FOUND}, 
                        getSVNEnvironment().isQuiet());
                seenNonExistentTarget = true;
            }
        }
        
        if (seenNonExistentTarget) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.ILLEGAL_TARGET, "Could not cat all targets because some targets don't exist");
            SVNErrorManager.error(err, SVNLogType.CLIENT);
        }
    }

}
