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
import org.tmatesoft.svn.core.SVNCancelException;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.wc.ISVNEventHandler;
import org.tmatesoft.svn.core.wc.SVNEvent;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNUpdateClient;
import org.tmatesoft.svn.util.ISVNDebugLog;

/**
 * @version 1.1.1
 * @author  TMate Software Ltd.
 */
public class SVNUpdateCommand extends SVNCommand implements ISVNEventHandler {

    private boolean myIsQuiet;
    private boolean myHasErrors;
    private PrintStream myErrorOutput;
    private ISVNDebugLog myDebugLog; 
    private ISVNEventHandler myRealHandler;
    
    public void run(InputStream in, PrintStream out, PrintStream err) throws SVNException {
        run(out, err);
    }

    public void run(final PrintStream out, final PrintStream err) throws SVNException {
        String changelistName = (String) getCommandLine().getArgumentValue(SVNArgument.CHANGELIST); 
//        SVNChangeList changelist = null;
        if (changelistName != null) {
/*            changelist = SVNChangeList.create(changelistName, new File(".").getAbsoluteFile());
            changelist.setOptions(getClientManager().getOptions());
            changelist.setRepositoryPool(getClientManager().getRepositoryPool());
            if (changelist.getPaths() == null || changelist.getPathsCount() == 0) {
                SVNErrorMessage error = SVNErrorMessage.create(SVNErrorCode.CL_ARG_PARSING_ERROR, 
                                    "no such changelist ''{0}''", changelistName); 
                SVNErrorManager.error(error);
            }
*/            
        }
        Collection targets = new LinkedList();
        for (int i = 0; i < getCommandLine().getPathCount(); i++) {
            targets.add(new File(getCommandLine().getPathAt(i)).getAbsoluteFile());
        }

/*        if (targets.size() == 0 && (changelist == null || changelist.getPathsCount() == 0) &&
                !getCommandLine().hasURLs()) {
            targets.add(new File(".").getAbsoluteFile());
        }
*/        
        File[] paths = (File[]) targets.toArray(new File[targets.size()]);
//        SVNPathList pathList = SVNPathList.create(paths, SVNRevision.UNDEFINED);
//        SVNCompositePathList combinedPathList = SVNCompositePathList.create(pathList, changelist, false);

        SVNDepth depth = SVNDepth.UNKNOWN;
        if (getCommandLine().hasArgument(SVNArgument.NON_RECURSIVE)) {
            depth = SVNDepth.fromRecurse(false);
        }
        String depthStr = (String) getCommandLine().getArgumentValue(SVNArgument.DEPTH);
        if (depthStr != null) {
            depth = SVNDepth.fromString(depthStr);
        }
        
        boolean force = getCommandLine().hasArgument(SVNArgument.FORCE);
        myIsQuiet = getCommandLine().hasArgument(SVNArgument.QUIET); 
        getClientManager().setEventHandler(this);
        if (!myIsQuiet) {
            myRealHandler = new SVNCommandEventProcessor(out, err, false);
        }
        
        SVNUpdateClient updater = getClientManager().getUpdateClient();
        
        myDebugLog = updater.getDebugLog();
        myHasErrors = false;
        myErrorOutput = err;
        SVNRevision revision = parseRevision(getCommandLine());
//        updater.doUpdate(combinedPathList.getPaths(), revision, depth, force);
        
        for (int i = 0; i < getCommandLine().getURLCount(); i++) {
            String url = getCommandLine().getURL(i);
            if (!myIsQuiet) {
                println(out, "Skipped '" +  url + "'");
            }
            continue;
        }

        if (myHasErrors) {
            System.exit(1);
        }
    }

    public void handleEvent(SVNEvent event, double progress) throws SVNException {
        if (event.getErrorMessage() != null) {
            myDebugLog.info(event.getErrorMessage().getFullMessage());
            myHasErrors = true;
            if (!myIsQuiet) {
                println(myErrorOutput, event.getErrorMessage().getFullMessage());
                println(myErrorOutput);
            }
        }
        if (!myIsQuiet) {
            myRealHandler.handleEvent(event, progress);
        }
    }

    public void checkCancelled() throws SVNCancelException {
    }

}
