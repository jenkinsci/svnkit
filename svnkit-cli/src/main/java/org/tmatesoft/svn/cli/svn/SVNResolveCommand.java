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
import org.tmatesoft.svn.core.wc.SVNConflictChoice;
import org.tmatesoft.svn.core.wc.SVNWCClient;
import org.tmatesoft.svn.util.SVNLogType;


/**
 * @version 1.3
 * @author  TMate Software Ltd.
 */
public class SVNResolveCommand extends SVNCommand {

    public SVNResolveCommand() {
        super("resolve", null);
    }

    protected Collection createSupportedOptions() {
        Collection options = new LinkedList();
        options.add(SVNOption.TARGETS);
        options.add(SVNOption.RECURSIVE);
        options.add(SVNOption.DEPTH);
        options.add(SVNOption.QUIET);
        options.add(SVNOption.ACCEPT);
        return options;
    }

    public void run() throws SVNException {
        SVNConflictAcceptPolicy accept = getSVNEnvironment().getResolveAccept();
        SVNConflictChoice choice = null;
        if (accept == SVNConflictAcceptPolicy.WORKING) {
            choice = SVNConflictChoice.MERGED;
        } else if (accept == SVNConflictAcceptPolicy.BASE) {
            choice = SVNConflictChoice.BASE;
        } else if (accept == SVNConflictAcceptPolicy.THEIRS_CONFLICT) {
            choice = SVNConflictChoice.THEIRS_CONFLICT;
        } else if (accept == SVNConflictAcceptPolicy.MINE_CONFLICT) {
            choice = SVNConflictChoice.MINE_CONFLICT;
        } else if (accept == SVNConflictAcceptPolicy.MINE_FULL) {
            choice = SVNConflictChoice.MINE_FULL;
        } else if (accept == SVNConflictAcceptPolicy.THEIRS_FULL) {
            choice = SVNConflictChoice.THEIRS_FULL;
        } else if (accept == null || accept == SVNConflictAcceptPolicy.UNSPECIFIED) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CL_ARG_PARSING_ERROR, 
                    "missing --accept option");
            SVNErrorManager.error(err, SVNLogType.CLIENT);
        } else {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CL_ARG_PARSING_ERROR, 
                    "invalid 'accept' ARG");
            SVNErrorManager.error(err, SVNLogType.CLIENT);
        } 
       
        List targets = getSVNEnvironment().combineTargets(getSVNEnvironment().getTargets(), true);
        if (targets.isEmpty()) {
            SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.CL_INSUFFICIENT_ARGS), SVNLogType.CLIENT);
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
            SVNPath target = new SVNPath(targetName);
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