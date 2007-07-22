/*
 * ====================================================================
 * Copyright (c) 2004-2007 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */

package org.tmatesoft.svn.cli.command;

import java.io.File;
import java.io.InputStream;
import java.io.PrintStream;

import org.tmatesoft.svn.cli.SVNArgument;
import org.tmatesoft.svn.cli.SVNCommand;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.wc.SVNResolveAccept;
import org.tmatesoft.svn.core.wc.SVNWCClient;

/**
 * @version 1.1.1
 * @author  TMate Software Ltd.
 */
public class SVNResolvedCommand extends SVNCommand {

    public void run(InputStream in, PrintStream out, PrintStream err) throws SVNException {
        run(out, err);
    }

    public void run(final PrintStream out, PrintStream err) throws SVNException {
        SVNDepth depth = SVNDepth.UNKNOWN;
        if (getCommandLine().hasArgument(SVNArgument.RECURSIVE)) {
            depth = SVNDepth.fromRecurse(true);
        }
        String depthStr = (String) getCommandLine().getArgumentValue(SVNArgument.DEPTH);
        if (depthStr != null) {
            depth = SVNDepth.fromString(depthStr);
        }
        if (depth == SVNDepth.UNKNOWN) {
            depth = SVNDepth.EMPTY;
        }
        
        final boolean recursive = SVNDepth.recurseFromDepth(depth);
        SVNResolveAccept accept = SVNResolveAccept.DEFAULT;
        if (getCommandLine().hasArgument(SVNArgument.ACCEPT)) {
            String acceptStr = (String) getCommandLine().getArgumentValue(SVNArgument.ACCEPT);
            accept = SVNResolveAccept.fromString(acceptStr);
            if (accept == SVNResolveAccept.INVALID) {
                SVNErrorMessage error = SVNErrorMessage.create(SVNErrorCode.CL_ARG_PARSING_ERROR, "''{0}'' is not a valid accept value; try ''left'', ''right'', or ''working''", acceptStr);
                SVNErrorManager.error(error);
            }
        }
        getClientManager().setEventHandler(new SVNCommandEventProcessor(out, err, false));
        SVNWCClient wcClient  = getClientManager().getWCClient();
        boolean error = false;
        for (int i = 0; i < getCommandLine().getPathCount(); i++) {
            final String absolutePath = getCommandLine().getPathAt(i);
            try {
                wcClient.doResolve(new File(absolutePath), recursive, accept);
            } catch (SVNException e) {
                err.println(e.getMessage());
                error = true;
            }
        }
        if (error) {
            System.exit(1);
        }
    }
}
