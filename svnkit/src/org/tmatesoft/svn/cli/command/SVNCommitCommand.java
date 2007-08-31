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

import org.tmatesoft.svn.cli.SVNArgument;
import org.tmatesoft.svn.cli.SVNCommand;
import org.tmatesoft.svn.core.SVNCommitInfo;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.wc.SVNCommitClient;

import java.io.File;
import java.io.InputStream;
import java.io.PrintStream;
import java.util.Map;

/**
 * @version 1.1.1
 * @author  TMate Software Ltd.
 */
public class SVNCommitCommand extends SVNCommand {

    public void run(InputStream in, PrintStream out, PrintStream err) throws SVNException {
        run(out, err);
    }
    
    public void run(final PrintStream out, PrintStream err) throws SVNException {
        checkEditorCommand();
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
        
        boolean keepLocks = getCommandLine().hasArgument(SVNArgument.NO_UNLOCK);
        String changelistName = (String) getCommandLine().getArgumentValue(SVNArgument.CHANGELIST); 
        boolean keepChangelist = getCommandLine().hasArgument(SVNArgument.KEEP_CHANGELIST);
        final String message = getCommitMessage();

        File[] localPaths = new File[getCommandLine().getPathCount()];
        for (int i = 0; i < getCommandLine().getPathCount(); i++) {
            matchTabsInPath(getCommandLine().getPathAt(i), err);
            localPaths[i] = new File(getCommandLine().getPathAt(i));
        }
        boolean quiet = getCommandLine().hasArgument(SVNArgument.QUIET);
        if (!quiet) {
            getClientManager().setEventHandler(new SVNCommandEventProcessor(out, err, false));
        }
        Map revProps = (Map) getCommandLine().getArgumentValue(SVNArgument.WITH_REVPROP); 
        
        SVNCommitClient client = getClientManager().getCommitClient();
        SVNCommitInfo result = client.doCommit(localPaths, keepLocks, message, 
                                               revProps, changelistName, keepChangelist, false, 
                                               depth);
        if (result != SVNCommitInfo.NULL && !quiet) {
            out.println();
            out.println("Committed revision " + result.getNewRevision() + ".");
        }
        if (result.getErrorMessage() != null && result.getErrorMessage().getErrorCode() == SVNErrorCode.REPOS_POST_COMMIT_HOOK_FAILED && !quiet) {
            out.println();
            out.println(result.getErrorMessage());
        }
    }

    private void checkEditorCommand() throws SVNException {
        final String editorCommand = (String) getCommandLine().getArgumentValue(SVNArgument.EDITOR_CMD);
        if (editorCommand == null) {
            return;
        }
        SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CL_NO_EXTERNAL_EDITOR, "Commit failed. Can''t handle external editor " + editorCommand);
        throw new SVNException(err);
    }
}
