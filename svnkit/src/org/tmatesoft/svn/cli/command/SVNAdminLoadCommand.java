/*
 * ====================================================================
 * Copyright (c) 2004-2006 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html.
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
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.wc.SVNUUIDAction;
import org.tmatesoft.svn.core.wc.admin.ISVNDumpHandler;
import org.tmatesoft.svn.core.wc.admin.SVNAdminClient;


/**
 * @version 1.1
 * @author  TMate Software Ltd.
 */
public class SVNAdminLoadCommand extends SVNCommand {

    public void run(PrintStream out, PrintStream err) throws SVNException {
        run(System.in, out, err);
    }

    public void run(InputStream in, PrintStream out, PrintStream err) throws SVNException {
        if (!getCommandLine().hasPaths()) {
            SVNCommand.println(out, "jsvnadmin: Repository argument required");
            System.exit(1);
        }
        File reposRoot = new File(getCommandLine().getPathAt(0));  

        boolean ignoreUUID = getCommandLine().hasArgument(SVNArgument.IGNORE_UUID);
        boolean forceUUID = getCommandLine().hasArgument(SVNArgument.FORCE_UUID);
        SVNUUIDAction uuidAction = null;
        if (!ignoreUUID && !forceUUID) {
            uuidAction = SVNUUIDAction.DEFAULT;
        } else if (ignoreUUID) {
            uuidAction = SVNUUIDAction.IGNORE_UUID;
        } else {
            uuidAction = SVNUUIDAction.FORCE_UUID;
        }
        
        boolean usePreCommitHook = getCommandLine().hasArgument(SVNArgument.USE_PRECOMMIT_HOOK);
        boolean usePostCommitHook = getCommandLine().hasArgument(SVNArgument.USE_POSTCOMMIT_HOOK);
        
        String parentDir = (String) getCommandLine().getArgumentValue(SVNArgument.PARENT_DIR);

        SVNAdminClient adminClient = getClientManager().getAdminClient();
        LoadHandler handler = null;
        if (!getCommandLine().hasArgument(SVNArgument.QUIET)) {
            handler = new LoadHandler(out);
        }        
        
        adminClient.doLoad(reposRoot, in, usePreCommitHook, usePostCommitHook, uuidAction, parentDir, handler);
    }

    private class LoadHandler implements ISVNDumpHandler {
        private PrintStream myOut;
        
        public LoadHandler(PrintStream out){
            myOut = out;
        }
        
        public void handleLoadRevision(long rev, long original) throws SVNException {
            if (rev == original) {
                myOut.print("\n------- Committed revision " + rev + " >>>\n\n");
            } else {
                myOut.print("\n------- Committed new rev " + rev + " (loaded from original rev " + original + ") >>>\n\n");
            }
        }

        public void handleDumpRevision(long rev) throws SVNException {
        }
    
    }
}
