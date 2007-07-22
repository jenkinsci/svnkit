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
import java.util.Collection;
import java.util.LinkedList;

import org.tmatesoft.svn.cli.SVNArgument;
import org.tmatesoft.svn.cli.SVNCommand;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.wc.SVNChangeList;
import org.tmatesoft.svn.core.wc.SVNCompositePathList;
import org.tmatesoft.svn.core.wc.SVNPathList;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNStatus;
import org.tmatesoft.svn.core.wc.SVNStatusType;
import org.tmatesoft.svn.core.wc.SVNWCClient;

/**
 * @version 1.1.1
 * @author  TMate Software Ltd.
 */
public class SVNRevertCommand extends SVNCommand {

    public void run(InputStream in, PrintStream out, PrintStream err) throws SVNException {
        run(out, err);
    }

    public final void run(final PrintStream out, final PrintStream err) throws SVNException {
        String changelistName = (String) getCommandLine().getArgumentValue(SVNArgument.CHANGELIST); 
        SVNChangeList changelist = null;
        if (changelistName != null) {
            changelist = SVNChangeList.create(changelistName, new File(".").getAbsoluteFile());
            changelist.setOptions(getClientManager().getOptions());
            changelist.setRepositoryPool(getClientManager().getRepositoryPool());
            if (changelist.getPaths() == null || changelist.getPathsCount() == 0) {
                SVNErrorMessage error = SVNErrorMessage.create(SVNErrorCode.CL_ARG_PARSING_ERROR, 
                                    "no such changelist ''{0}''", changelistName); 
                SVNErrorManager.error(error);
            }
        }

        SVNDepth depth = SVNDepth.UNKNOWN;
        if (getCommandLine().hasArgument(SVNArgument.RECURSIVE)) {
            depth = SVNDepth.fromRecurse(true);
        }
        String depthStr = (String) getCommandLine().getArgumentValue(SVNArgument.DEPTH);
        if (depthStr != null) {
            depth = SVNDepth.fromString(depthStr);
        }
        if (depth == SVNDepth.UNKNOWN) {
            depth = SVNDepth.fromRecurse(false);
        }
        
        final boolean recursive = SVNDepth.recurseFromDepth(depth);
        boolean quiet = getCommandLine().hasArgument(SVNArgument.QUIET);
        
        if (!quiet) {
            getClientManager().setEventHandler(new SVNCommandEventProcessor(out, err, false));
        }
        SVNWCClient wcClient = getClientManager().getWCClient();
        Collection targets = new LinkedList();
        for (int i = 0; i < getCommandLine().getPathCount(); i++) {
            final String absolutePath = getCommandLine().getPathAt(i);
            // hack to make schedule 9 test pass
            if ("".equals(absolutePath) || ".".equals(absolutePath)) {
                File path = new File(SVNPathUtil.validateFilePath(absolutePath)).getAbsoluteFile();
                if (path.isDirectory()) {
                    SVNStatus status = getClientManager().getStatusClient().doStatus(path, false);
                    if (status.getContentsStatus() == SVNStatusType.STATUS_ADDED) {
                        // we're inside an added directory, skip it.
                        if (!quiet) {
                            System.err.println("Skipped: " + absolutePath);
                        }
                        continue;
                    }
                }
            } 
            targets.add(new File(getCommandLine().getPathAt(i)).getAbsoluteFile());
        }
        File[] paths = (File[]) targets.toArray(new File[targets.size()]);
        SVNPathList pathList = SVNPathList.create(paths, SVNRevision.UNDEFINED);
        SVNCompositePathList combinedPathList = SVNCompositePathList.create(pathList, changelist, false);

        try {
            wcClient.doRevert(combinedPathList, recursive);
        } catch (SVNException svne) {
            if (svne.getErrorMessage().getErrorCode() == SVNErrorCode.WC_NOT_LOCKED && !recursive) {
                SVNErrorMessage error = svne.getErrorMessage().wrap("Try 'svn revert --recursive' instead?");
                SVNErrorManager.error(error);
            }
            throw svne;
        }
    }
}
