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
import org.tmatesoft.svn.core.internal.io.fs.FSFS;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.internal.wc.SVNAdminHelper;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.admin.SVNLookClient;


/**
 * @version 1.1
 * @author  TMate Software Ltd.
 */
public class SVNLookPropgetCommand extends SVNCommand {

    public void run(PrintStream out, PrintStream err) throws SVNException {
        if (!getCommandLine().hasPaths()) {
            SVNCommand.println(err, "jsvnlook: Repository argument required");
            System.exit(1);
        }

        boolean isRevProp = getCommandLine().hasArgument(SVNArgument.REV_PROP);
        File reposRoot = new File(getCommandLine().getPathAt(0));  
        String propertyName = getCommandLine().getPathCount() < 2 ? null : getCommandLine().getPathAt(1);
        SVNRevision revision = SVNRevision.HEAD;
        SVNLookClient lookClient = getClientManager().getLookClient();
        
        if (getCommandLine().hasArgument(SVNArgument.TRANSACTION)) {
            String transactionName = (String) getCommandLine().getArgumentValue(SVNArgument.TRANSACTION);
            String path = null;
            String value = null;
            if (isRevProp) {
                value = lookClient.doGetRevisionProperty(reposRoot, propertyName, transactionName);
            } else {
                path = getCommandLine().getPathCount() < 3 ? null : SVNPathUtil.canonicalizeAbsPath(getCommandLine().getPathAt(2));
                value = lookClient.doGetProperty(reposRoot, propertyName, path, transactionName);
            }
            if (value == null) {
                if (path == null) {
                    SVNCommand.println(err, "Property '" + propertyName + "' not found on transaction '" + transactionName + "'");
                    System.exit(1);
                } else {
                    SVNCommand.println(err, "Property '" + propertyName + "' not found on path '" + path + "' in transaction '" + transactionName + "'");
                    System.exit(1);
                }
            }
            SVNCommand.print(out, value);
            return;
        } else if (getCommandLine().hasArgument(SVNArgument.REVISION)) {
            revision = SVNRevision.parse((String) getCommandLine().getArgumentValue(SVNArgument.REVISION));
        } 

        String path = null;
        String value = null;
        if (isRevProp) {
            value = lookClient.doGetRevisionProperty(reposRoot, propertyName, revision);
        } else {
            path = getCommandLine().getPathCount() < 3 ? null : SVNPathUtil.canonicalizeAbsPath(getCommandLine().getPathAt(2));
            value = lookClient.doGetProperty(reposRoot, propertyName, path, revision);
        }
        if (value == null) {
            long revNum = -1;
            if (SVNRevision.isValidRevisionNumber(revision.getNumber())) {
                 revNum = revision.getNumber();
            } else {
                FSFS fsfs = SVNAdminHelper.openRepository(reposRoot);
                revNum = SVNAdminHelper.getRevisionNumber(revision, fsfs.getYoungestRevision(), fsfs);
            }
            if (path == null) {
                SVNCommand.println(err, "Property '" + propertyName + "' not found on revision " + revNum);
                System.exit(1);
            } else {
                SVNCommand.println(err, "Property '" + propertyName + "' not found on path '" + path + "' in revision " + revNum);
                System.exit(1);
            }
        }
        SVNCommand.print(out, value);
    }

    public void run(InputStream in, PrintStream out, PrintStream err) throws SVNException {
        run(out, err);
    }

}
