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
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.admin.ISVNPathHandler;
import org.tmatesoft.svn.core.wc.admin.SVNLookClient;


/**
 * @version 1.1
 * @author  TMate Software Ltd.
 */
public class SVNLookTreeCommand extends SVNCommand implements ISVNPathHandler {
    private PrintStream myOut;
    private boolean myIsIncludeIDs;
    private boolean myIsFullPaths;
    
    public void run(PrintStream out, PrintStream err) throws SVNException {
        if (!getCommandLine().hasPaths()) {
            SVNCommand.println(err, "jsvnlook: Repository argument required");
            System.exit(1);
        }
        
        myOut = out;
        myIsIncludeIDs = getCommandLine().hasArgument(SVNArgument.SHOW_IDS);
        myIsFullPaths = getCommandLine().hasArgument(SVNArgument.FULL_PATHS);
        File reposRoot = new File(getCommandLine().getPathAt(0));  
        String path = getCommandLine().getPathCount() < 2 ? null : SVNPathUtil.canonicalizeAbsPath(getCommandLine().getPathAt(1));
        SVNRevision revision = SVNRevision.HEAD;
        SVNLookClient lookClient = getClientManager().getLookClient();
        if (getCommandLine().hasArgument(SVNArgument.TRANSACTION)) {
            String transactionName = (String) getCommandLine().getArgumentValue(SVNArgument.TRANSACTION);
            lookClient.doGetTree(reposRoot, path, transactionName, myIsIncludeIDs, this);
            return;
        } else if (getCommandLine().hasArgument(SVNArgument.REVISION)) {
            revision = SVNRevision.parse((String) getCommandLine().getArgumentValue(SVNArgument.REVISION));
        } 
        lookClient.doGetTree(reposRoot, path, revision, myIsIncludeIDs, this);
    }

    public void handlePath(String path, String nodeID, int depth, boolean isDir) throws SVNException {
        String indentation = null;
        if (!myIsFullPaths) {
            indentation = "";
            for (int i = 0; i < depth; i++) {
                indentation += " ";
            }
        } 
        
        if (myIsFullPaths) {
            path = path.startsWith("/") && !"/".equals(path) ? path.substring(1) : path;
            if (isDir && !"/".equals(path) && !path.endsWith("/")) {
                path += "/";
            }
            SVNCommand.print(myOut, path);
        } else {
            path = !"/".equals(path) ? SVNPathUtil.tail(path) : path;
            if (isDir && !"/".equals(path) && !path.endsWith("/")) {
                path += "/";
            }
            SVNCommand.print(myOut, indentation + path);    
        }

        if (myIsIncludeIDs) {
            SVNCommand.print(myOut, " <" + (nodeID != null ? nodeID.toString() : "unknown") + ">");    
        }
        SVNCommand.println(myOut, "");    
    }

    public void run(InputStream in, PrintStream out, PrintStream err) throws SVNException {
        run(out, err);
    }

    public void handlePath(long revision, String path, String nodeID) throws SVNException {
    }

    public void handleDir(String path) throws SVNException {
    }
}
