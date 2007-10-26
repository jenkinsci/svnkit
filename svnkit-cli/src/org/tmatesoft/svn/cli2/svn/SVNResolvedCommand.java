/*
 * ====================================================================
 * Copyright (c) 2004-2007 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html.
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.cli2.svn;

import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import org.tmatesoft.svn.cli2.SVNCommandTarget;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.wc.SVNConflictChoice;
import org.tmatesoft.svn.core.wc.SVNWCClient;


/**
 * @version 1.1.2
 * @author  TMate Software Ltd.
 */
public class SVNResolvedCommand extends SVNCommand {

    public SVNResolvedCommand() {
        super("resolved", null);
    }

    protected Collection createSupportedOptions() {
        Collection options = new LinkedList();
        options.add(SVNOption.TARGETS);
        options.add(SVNOption.RECURSIVE);
        options.add(SVNOption.DEPTH);
        options.add(SVNOption.QUIET);
        options.add(SVNOption.CONFIG_DIR);
        options.add(SVNOption.ACCEPT);
        return options;
    }

    public void run() throws SVNException {
        SVNWCAccept accept = getSVNEnvironment().getResolveAccept();
        SVNConflictChoice choice = null;
        if (accept == SVNWCAccept.INVALID) {
            choice = SVNConflictChoice.MERGED;
        } else if (accept == SVNWCAccept.BASE) {
            choice = SVNConflictChoice.BASE;
        } else if (accept == SVNWCAccept.THEIRS) {
            choice = SVNConflictChoice.THEIRS;
        } else if (accept == SVNWCAccept.MINE) {
            choice = SVNConflictChoice.MINE;
        } else {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CL_ARG_PARSING_ERROR, "invalid 'accept' ARG");
            SVNErrorManager.error(err);
        }
       
        List targets = getSVNEnvironment().combineTargets(getSVNEnvironment().getTargets());
        if (targets.isEmpty()) {
            SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.CL_INSUFFICIENT_ARGS));
        }
        SVNWCClient client = getSVNEnvironment().getClientManager().getWCClient();
        if (!getSVNEnvironment().isQuiet()) {
            client.setEventHandler(new SVNNotifyPrinter(getSVNEnvironment()));
        }
        SVNDepth depth = getSVNEnvironment().getDepth();
        if (depth == SVNDepth.UNKNOWN) {
            depth = SVNDepth.EMPTY;
        }
        for (Iterator ts = targets.iterator(); ts.hasNext();) {
            String targetName = (String) ts.next();
            SVNCommandTarget target = new SVNCommandTarget(targetName);
            if (target.isFile()) {
                try {
                    client.doResolve(target.getFile(), depth, choice);
                } catch (SVNException e) {
                    SVNErrorMessage err = e.getErrorMessage();
                    getSVNEnvironment().handleWarning(err, new SVNErrorCode[] {err.getErrorCode()}, getSVNEnvironment().isQuiet());
                }
            }
        }
    }

}
