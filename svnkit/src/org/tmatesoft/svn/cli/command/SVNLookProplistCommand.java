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
import java.util.Iterator;
import java.util.Map;

import org.tmatesoft.svn.cli.SVNArgument;
import org.tmatesoft.svn.cli.SVNCommand;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.admin.SVNLookClient;


/**
 * @version 1.1
 * @author  TMate Software Ltd.
 */
public class SVNLookProplistCommand extends SVNCommand {

    public void run(PrintStream out, PrintStream err) throws SVNException {
        if (!getCommandLine().hasPaths()) {
            SVNCommand.println(err, "jsvnlook: Repository argument required");
            System.exit(1);
        }

        boolean isRevProp = getCommandLine().hasArgument(SVNArgument.REV_PROP);
        boolean isVerbose = getCommandLine().hasArgument(SVNArgument.VERBOSE);
        File reposRoot = new File(getCommandLine().getPathAt(0));  
        SVNRevision revision = SVNRevision.HEAD;
        SVNLookClient lookClient = getClientManager().getLookClient();
        
        if (getCommandLine().hasArgument(SVNArgument.TRANSACTION)) {
            String transactionName = (String) getCommandLine().getArgumentValue(SVNArgument.TRANSACTION);
            Map props = null;
            if (isRevProp) {
                props = lookClient.doGetRevisionProperties(reposRoot, transactionName);
            } else {
                String path = getCommandLine().getPathCount() < 2 ? null : SVNPathUtil.canonicalizeAbsPath(getCommandLine().getPathAt(2));
                props = lookClient.doGetProperties(reposRoot, path, transactionName);
            }
            printProps(out, props, isVerbose);
            return;
        } else if (getCommandLine().hasArgument(SVNArgument.REVISION)) {
            revision = SVNRevision.parse((String) getCommandLine().getArgumentValue(SVNArgument.REVISION));
        } 

        Map props = null;
        if (isRevProp) {
            props = lookClient.doGetRevisionProperties(reposRoot, revision);
        } else {
            String path = getCommandLine().getPathCount() < 2 ? null : SVNPathUtil.canonicalizeAbsPath(getCommandLine().getPathAt(2));
            props = lookClient.doGetProperties(reposRoot, path, revision);
        }
        printProps(out, props, isVerbose);
    }

    public void run(InputStream in, PrintStream out, PrintStream err) throws SVNException {
        run(out, err);
    }
    
    public void printProps(PrintStream out, Map props, boolean isVerbose) {
        if (props != null) {
            for (Iterator propNames = props.keySet().iterator(); propNames.hasNext(); ) {
                String propName = (String) propNames.next();
                if (isVerbose) {
                    String propVal = (String) props.get(propName);
                    SVNCommand.println(out, "  " + propName + " : " + propVal);    
                } else {
                    SVNCommand.println(out, "  " + propName);    
                }
            }
        }
    }

}
