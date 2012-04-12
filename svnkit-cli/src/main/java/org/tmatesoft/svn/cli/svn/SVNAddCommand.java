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
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import org.tmatesoft.svn.core.SVNDepth;
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
public class SVNAddCommand extends SVNCommand {

    public SVNAddCommand() {
        super("add", null);
    }

    protected Collection createSupportedOptions() {
        Collection options = new LinkedList();
        options.add(SVNOption.TARGETS);
        options.add(SVNOption.NON_RECURSIVE);
        options.add(SVNOption.DEPTH);
        options.add(SVNOption.QUIET);
        options.add(SVNOption.FORCE);
        options.add(SVNOption.NO_IGNORE);
        options.add(SVNOption.AUTOPROPS);
        options.add(SVNOption.NO_AUTOPROPS);
        options.add(SVNOption.PARENTS);
        return options;
    }

    public void run() throws SVNException {
        List targets = getSVNEnvironment().combineTargets(getSVNEnvironment().getTargets(), true);
        if (targets.isEmpty()) {
            SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.CL_INSUFFICIENT_ARGS), SVNLogType.CLIENT);
        }
        SVNDepth depth = getSVNEnvironment().getDepth();
        if (depth == SVNDepth.UNKNOWN) {
            depth = SVNDepth.INFINITY;
        }
        SVNWCClient client = getSVNEnvironment().getClientManager().getWCClient();
        if (!getSVNEnvironment().isQuiet()) {
            client.setEventHandler(new SVNNotifyPrinter(getSVNEnvironment()));
        }
        boolean hasMissingPaths = false;
        boolean hasPresentPaths = false;
        for (Iterator ts = targets.iterator(); ts.hasNext();) {
            String targetName = (String) ts.next();
            SVNPath target = new SVNPath(targetName);
            if (target.isURL()) {
                continue;
            }
            try {
                client.doAdd(target.getFile(), getSVNEnvironment().isForce(), false, 
                        getSVNEnvironment().isParents(), depth, getSVNEnvironment().isNoIgnore(), 
                        getSVNEnvironment().isParents());
            } catch (SVNException e) {
                hasMissingPaths |= e.getErrorMessage().getErrorCode() == SVNErrorCode.WC_PATH_NOT_FOUND; 
                hasPresentPaths |= e.getErrorMessage().getErrorCode() == SVNErrorCode.ENTRY_EXISTS; 
                getSVNEnvironment().handleWarning(e.getErrorMessage(), 
                        new SVNErrorCode[] {SVNErrorCode.ENTRY_EXISTS, SVNErrorCode.WC_PATH_NOT_FOUND}, getSVNEnvironment().isQuiet());
            }
        }
        if (hasMissingPaths) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.ILLEGAL_TARGET, "Could not add all targets because some targets don't exist");
            SVNErrorManager.error(err, SVNLogType.WC);
        }
        if (hasPresentPaths) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.ILLEGAL_TARGET, "Could not add all targets because some targets are already versioned");
            SVNErrorManager.error(err, SVNLogType.WC);
        }
    }

}
