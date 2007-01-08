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
package org.tmatesoft.svn.cli.command;

import java.io.File;
import java.io.InputStream;
import java.io.PrintStream;

import org.tmatesoft.svn.cli.SVNArgument;
import org.tmatesoft.svn.cli.SVNCommand;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.admin.ISVNHistoryHandler;
import org.tmatesoft.svn.core.wc.admin.SVNAdminPath;
import org.tmatesoft.svn.core.wc.admin.SVNLookClient;


/**
 * @version 1.1.1
 * @author  TMate Software Ltd.
 * @since   1.1.1
 */
public class SVNLookHistoryCommand extends SVNCommand implements ISVNHistoryHandler {
    private PrintStream myOut;
    private boolean myIsIncludeIDs;
    
    public void run(PrintStream out, PrintStream err) throws SVNException {
        if (!getCommandLine().hasPaths()) {
            SVNCommand.println(err, "jsvnlook: Repository argument required");
            System.exit(1);
        }
        if (getCommandLine().hasArgument(SVNArgument.TRANSACTION)) {
            SVNCommand.println(err, "Subcommand 'history' doesn't accept option '-t [--transaction] arg'");
            System.exit(1);
        }
        
        myOut = out;
        File reposRoot = new File(getCommandLine().getPathAt(0));  
        String path = null;
        if (getCommandLine().getPathCount() > 1) {
            path = SVNPathUtil.canonicalizeAbsPath(getCommandLine().getPathAt(1));
        }
        myIsIncludeIDs = getCommandLine().hasArgument(SVNArgument.SHOW_IDS);
        
        SVNRevision revision = SVNRevision.HEAD;
        SVNLookClient lookClient = getClientManager().getLookClient();
        if (getCommandLine().hasArgument(SVNArgument.REVISION)) {
            revision = SVNRevision.parse((String) getCommandLine().getArgumentValue(SVNArgument.REVISION));
        } 
        if (myIsIncludeIDs) {
            SVNCommand.println(myOut, "REVISION   PATH <ID>");
            SVNCommand.println(myOut, "--------   ---------");
        } else {
            SVNCommand.println(myOut, "REVISION   PATH");
            SVNCommand.println(myOut, "--------   ----");
        }
        lookClient.doGetHistory(reposRoot, path, revision, myIsIncludeIDs, this);
    }

    public void run(InputStream in, PrintStream out, PrintStream err) throws SVNException {
        run(out, err);
    }
        
    public void handlePath(SVNAdminPath path) throws SVNException {
        if (path != null) {
            if (myIsIncludeIDs) {
                SVNCommand.println(myOut, path.getRevision() + "   " + path.getPath() + " <" + path.getNodeID() + ">");
            } else {
                SVNCommand.println(myOut, path.getRevision() + "   " + path.getPath());
            }
        }
    }

}
