/*
 * ====================================================================
 * Copyright (c) 2004-2012 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html.
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.cli.svnlook;

import java.text.MessageFormat;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.wc.admin.ISVNTreeHandler;
import org.tmatesoft.svn.core.wc.admin.SVNAdminPath;
import org.tmatesoft.svn.core.wc.admin.SVNLookClient;


/**
 * @version 1.3
 * @author  TMate Software Ltd.
 */
public class SVNLookTreeCommand extends SVNLookCommand implements ISVNTreeHandler {

    protected SVNLookTreeCommand() {
        super("tree", null);
    }

    protected Collection createSupportedOptions() {
        List options = new LinkedList();
        options.add(SVNLookOption.REVISION);
        options.add(SVNLookOption.TRANSACTION);
        options.add(SVNLookOption.NON_RECURSIVE);
        options.add(SVNLookOption.SHOW_IDS);
        options.add(SVNLookOption.FULL_PATHS);
        return options;
    }

    public void run() throws SVNException {
        SVNLookCommandEnvironment environment = getSVNLookEnvironment(); 
        SVNLookClient client = environment.getClientManager().getLookClient();
        
        String path = environment.getFirstArgument();
        if (environment.isRevision()) {
            client.doGetTree(environment.getRepositoryFile(), path, 
                    getRevisionObject(), environment.isShowIDs(), !environment.isNonRecursive(), this);
        } else {
            client.doGetTree(environment.getRepositoryFile(), path, environment.getTransaction(), 
                    environment.isShowIDs(), !environment.isNonRecursive(), this);
        }
    }

    public void handlePath(SVNAdminPath adminPath) throws SVNException {
        if (adminPath != null) {
            String indentation = null;
            if (!getSVNLookEnvironment().isFullPaths()) {
                indentation = "";
                for (int i = 0; i < adminPath.getTreeDepth(); i++) {
                    indentation += " ";
                }
            } 
            
            String path = adminPath.getPath();
            if (getSVNLookEnvironment().isFullPaths()) {
                path = path.startsWith("/") && !"/".equals(path) ? path.substring(1) : path;
                if (adminPath.isDir() && !"/".equals(path) && !path.endsWith("/")) {
                    path += "/";
                }
                getSVNLookEnvironment().getOut().print(path);
            } else {
                path = !"/".equals(path) ? SVNPathUtil.tail(path) : path;
                if (adminPath.isDir() && !"/".equals(path) && !path.endsWith("/")) {
                    path += "/";
                }
                getSVNLookEnvironment().getOut().print(indentation + path);
            }
    
            if (getSVNLookEnvironment().isShowIDs()) {
                String message = MessageFormat.format(" <{0}>", new Object[] { adminPath.getNodeID() != null ? adminPath.getNodeID() : "unknown" });
                getSVNLookEnvironment().getOut().print(message);
            }
            getSVNLookEnvironment().getOut().println();
        }
    }

}
