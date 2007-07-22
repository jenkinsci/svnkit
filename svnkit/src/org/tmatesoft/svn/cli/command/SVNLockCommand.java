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
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.wc.SVNChangeList;
import org.tmatesoft.svn.core.wc.SVNCompositePathList;
import org.tmatesoft.svn.core.wc.SVNPathList;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNWCClient;

/**
 * @version 1.1.1
 * @author  TMate Software Ltd.
 */
public class SVNLockCommand extends SVNCommand {

    public void run(InputStream in, PrintStream out, PrintStream err) throws SVNException {
        run(out, err);
    }

    public void run(PrintStream out, PrintStream err) throws SVNException {
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

        boolean force = getCommandLine().hasArgument(SVNArgument.FORCE);
        String message = (String) getCommandLine().getArgumentValue(SVNArgument.MESSAGE);
        
        Collection files = new LinkedList();
        for (int i = 0; i < getCommandLine().getPathCount(); i++) {
            files.add(new File(getCommandLine().getPathAt(i)));
        }
        File[] filesArray = (File[]) files.toArray(new File[files.size()]);
        files.clear();

        for (int i = 0; i < getCommandLine().getURLCount(); i++) {
            files.add(getCommandLine().getURL(i));
        }
        String[] urls = (String[]) files.toArray(new String[files.size()]);
        SVNURL[] svnURLs = new SVNURL[urls.length];
        for (int i = 0; i < urls.length; i++) {
            svnURLs[i] = SVNURL.parseURIEncoded(urls[i]);
        }
        
        SVNPathList pathList = SVNPathList.create(filesArray, SVNRevision.UNDEFINED);
        SVNCompositePathList combinedPathList = SVNCompositePathList.create(pathList, changelist, false); 

        int targetsNum = (combinedPathList != null ? combinedPathList.getPathsCount() : 0)  +
                          urls.length;
        if (targetsNum < 1) {
            SVNErrorMessage error = SVNErrorMessage.create(SVNErrorCode.CL_INSUFFICIENT_ARGS);
            SVNErrorManager.error(error);
        }

        getClientManager().setEventHandler(new SVNCommandEventProcessor(out, err, false));
        SVNWCClient wcClient = getClientManager().getWCClient();
        
        if (combinedPathList != null) {
            wcClient.doLock(combinedPathList, force, message);
        }
        
        if (urls.length > 0) {
            wcClient.doLock(svnURLs, force, message);
        }
    }
}
