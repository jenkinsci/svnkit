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
package org.tmatesoft.svn.cli.svn;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.wc.SVNPath;
import org.tmatesoft.svn.core.wc.ISVNChangelistHandler;
import org.tmatesoft.svn.core.wc.SVNChangelistClient;
import org.tmatesoft.svn.core.wc.SVNUpdateClient;
import org.tmatesoft.svn.util.SVNLogType;


/**
 * @version 1.3
 * @author  TMate Software Ltd.
 */
public class SVNUpdateCommand extends SVNCommand {

    public SVNUpdateCommand() {
        super("update", new String[] {"up"});
    }

    protected Collection createSupportedOptions() {
        Collection options = new LinkedList();
        options.add(SVNOption.REVISION);
        options.add(SVNOption.NON_RECURSIVE);
        options.add(SVNOption.DEPTH);
        options.add(SVNOption.SET_DEPTH);
        options.add(SVNOption.QUIET);
        options.add(SVNOption.DIFF3_CMD);
        options.add(SVNOption.FORCE);
        options.add(SVNOption.IGNORE_EXTERNALS);
        options.add(SVNOption.CHANGELIST);
        options.add(SVNOption.EDITOR_CMD);
        options.add(SVNOption.ACCEPT);
        options.add(SVNOption.PARENTS);
        return options;
    }

    public void run() throws SVNException {
        List targets = new LinkedList(); 
        if (getSVNEnvironment().getTargets() != null) {
            targets.addAll(getSVNEnvironment().getTargets());
        }
        targets = getSVNEnvironment().combineTargets(targets, true);
        if (targets.isEmpty()) {
            targets.add("");
        }
        
        List fileTargets = new LinkedList();
        for (Iterator targetsIter = targets.iterator(); targetsIter.hasNext();) {
            String targetName = (String) targetsIter.next();
            SVNPath target = new SVNPath(targetName);
            fileTargets.add(target.getFile());
        }
        
        if (getSVNEnvironment().getChangelists() != null) {
            Collection changeLists = getSVNEnvironment().getChangelistsCollection(); 
            SVNDepth clDepth = getSVNEnvironment().getDepth();
            if (clDepth == SVNDepth.UNKNOWN) {
                clDepth = SVNDepth.INFINITY;
            }
            SVNChangelistClient changelistClient = getSVNEnvironment().getClientManager().getChangelistClient();
            final List targetPaths = new LinkedList(); 
            ISVNChangelistHandler handler = new ISVNChangelistHandler() {
                public void handle(File path, String changelistName) {
                    targetPaths.add(path.getAbsolutePath());
                }
            };
            changelistClient.doGetChangeListPaths(changeLists, fileTargets, clDepth, handler);
            targets = targetPaths;
        }
        
        SVNUpdateClient client = getSVNEnvironment().getClientManager().getUpdateClient();
        SVNNotifyPrinter printer = new SVNNotifyPrinter(getSVNEnvironment());
        if (!getSVNEnvironment().isQuiet()) {
            client.setEventHandler(printer);
        }
        
        SVNDepth depth = getSVNEnvironment().getDepth();
        boolean depthIsSticky = false;
        if (getSVNEnvironment().getSetDepth() != SVNDepth.UNKNOWN) {
            depth = getSVNEnvironment().getSetDepth();
            depthIsSticky = true;
        }
        
        List files = new ArrayList(targets.size());
        for (Iterator ts = targets.iterator(); ts.hasNext();) {
            String targetName = (String) ts.next();
            SVNPath target = new SVNPath(targetName);
            if (!target.isFile()) {
                getSVNEnvironment().getOut().println("Skipped '" + targetName + "'");
                continue;
            }
            files.add(target.getFile());
        }
        File[] filesArray = (File[]) files.toArray(new File[files.size()]);
        long[] results = client.doUpdate(filesArray, getSVNEnvironment().getStartRevision(), depth, 
                getSVNEnvironment().isForce(), depthIsSticky, getSVNEnvironment().isParents());

        if (!getSVNEnvironment().isQuiet()) {
            StringBuffer status = new StringBuffer();
            printUpdateSummary(filesArray, results, status);
            printer.printConflictStatus(status);
            getSVNEnvironment().getOut().print(status);
        }
        
        if (printer.hasExternalErrors()) {
            SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.CL_ERROR_PROCESSING_EXTERNALS, 
                "Failure occurred processing one or more externals definitions"), SVNLogType.CLIENT);
        }
    }

    private void printUpdateSummary(File[] targets, long[] results, StringBuffer status) {
        if (targets == null || targets.length < 2 || results == null || results.length < 2) {
            return;
        }
        status.append("Summary of updates:\n");
        for (int i = 0; i < targets.length; i++) {
            long rev = i < results.length ? results[i] : -1;
            if (rev < 0) {
                continue;
            }
            status.append("  Updated '" + getSVNEnvironment().getRelativePath(targets[i]) + "' to r" + rev + ".\n");
        }
    } 

}
