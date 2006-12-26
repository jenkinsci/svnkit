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
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.admin.ISVNChangeEntryHandler;
import org.tmatesoft.svn.core.wc.admin.SVNChangeEntry;
import org.tmatesoft.svn.core.wc.admin.SVNLookClient;


/**
 * @version 1.1
 * @author  TMate Software Ltd.
 */
public class SVNLookChangedCommand extends SVNCommand implements ISVNChangeEntryHandler {
    private PrintStream myOut;
    
    public void run(PrintStream out, PrintStream err) throws SVNException {
        if (!getCommandLine().hasPaths()) {
            SVNCommand.println(err, "jsvnlook: Repository argument required");
            System.exit(1);
        }
        
        File reposRoot = new File(getCommandLine().getPathAt(0));  
        myOut = out;
        SVNRevision revision = SVNRevision.HEAD;
        SVNLookClient lookClient = getClientManager().getLookClient();
        boolean isCopyInfoIncluded = getCommandLine().hasArgument(SVNArgument.COPY_INFO); 
        
        if (getCommandLine().hasArgument(SVNArgument.TRANSACTION)) {
            String transactionName = (String) getCommandLine().getArgumentValue(SVNArgument.TRANSACTION);
            lookClient.doGetChanged(reposRoot, transactionName, this, isCopyInfoIncluded);
            return;
        } else if (getCommandLine().hasArgument(SVNArgument.REVISION)) {
            revision = SVNRevision.parse((String) getCommandLine().getArgumentValue(SVNArgument.REVISION));
        } 
        
        lookClient.doGetChanged(reposRoot, revision, this, isCopyInfoIncluded);
    }

    public void run(InputStream in, PrintStream out, PrintStream err) throws SVNException {
        run(out, err);
    }

    public void handleEntry(SVNChangeEntry entry) throws SVNException {
        String[] status = new String[3];
        status[0] = "" + entry.getType();
        status[1] = entry.hasPropertyModifications() ? "" + SVNChangeEntry.TYPE_UPDATED : " ";
        status[2] = entry.getCopyFromPath() != null ? "+" : " ";
        String path = !entry.getPath().endsWith("/") && entry.getKind() == SVNNodeKind.DIR ? entry.getPath() + "/" : entry.getPath(); 
        path = path.startsWith("/") ? path.substring(1) : path;
        SVNCommand.println(myOut, status[0] + status[1] + status[2] + " " + path);
        if (entry.getCopyFromPath() != null) {
            String copyFromPath = entry.getCopyFromPath();
            if (copyFromPath.startsWith("/")) {
                copyFromPath = copyFromPath.substring(1);    
            }
            if (!copyFromPath.endsWith("/") && entry.getKind() == SVNNodeKind.DIR) {
                copyFromPath += "/";    
            }
            SVNCommand.println(myOut, "    (from " + copyFromPath + ":r" + entry.getCopyFromRevision() + ")");
        }
    }

}
