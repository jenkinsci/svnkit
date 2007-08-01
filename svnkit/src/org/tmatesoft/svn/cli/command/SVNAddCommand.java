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
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.wc.SVNWCClient;

/**
 * @version 1.1.1
 * @author  TMate Software Ltd.
 */
public class SVNAddCommand extends SVNCommand {

    public final void run(final PrintStream out, PrintStream err) throws SVNException {
        SVNDepth depth = SVNDepth.UNKNOWN;
        if (getCommandLine().hasArgument(SVNArgument.NON_RECURSIVE)) {
            depth = SVNDepth.fromRecurse(false);
        }
        String depthStr = (String) getCommandLine().getArgumentValue(SVNArgument.DEPTH);
        if (depthStr != null) {
            depth = SVNDepth.fromString(depthStr);
        }
        if (depth == SVNDepth.UNKNOWN) {
            depth = SVNDepth.INFINITY;
        }
        boolean recursive = SVNDepth.recurseFromDepth(depth);
        boolean force = getCommandLine().hasArgument(SVNArgument.FORCE);
        boolean disableAutoProps = getCommandLine().hasArgument(SVNArgument.NO_AUTO_PROPS);
        boolean enableAutoProps = getCommandLine().hasArgument(SVNArgument.AUTO_PROPS);
        boolean addParents = getCommandLine().hasArgument(SVNArgument.PARENTS);

        getClientManager().setEventHandler(new SVNCommandEventProcessor(out, err, false));
        SVNWCClient wcClient = getClientManager().getWCClient();

        if (disableAutoProps) {
            wcClient.getOptions().setUseAutoProperties(false);
        }
        if (enableAutoProps) {
            wcClient.getOptions().setUseAutoProperties(true);
        }
        boolean noIgnore = getCommandLine().hasArgument(SVNArgument.NO_IGNORE);
        for (int i = 0; i < getCommandLine().getPathCount(); i++) {
            final String absolutePath = getCommandLine().getPathAt(i);
            matchTabsInPath(absolutePath, err);
            try {
                wcClient.doAdd(new File(absolutePath), force, false, addParents, recursive, noIgnore);
            } catch (SVNException e) {
                handleWarning(e.getErrorMessage(), new SVNErrorCode[] {SVNErrorCode.ENTRY_EXISTS, SVNErrorCode.WC_PATH_NOT_FOUND}, err);
            }
        }
    }
    
    public void run(InputStream in, PrintStream out, PrintStream err) throws SVNException {
        run(out, err);
    }

}
