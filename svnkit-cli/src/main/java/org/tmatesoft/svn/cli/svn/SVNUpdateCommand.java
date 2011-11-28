/*
 * ====================================================================
 * Copyright (c) 2004-2011 TMate Software Ltd.  All rights reserved.
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
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.wc.SVNPath;
import org.tmatesoft.svn.core.wc.ISVNChangelistHandler;
import org.tmatesoft.svn.core.wc.SVNChangelistClient;
import org.tmatesoft.svn.core.wc.SVNUpdateClient;


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
        if (!getSVNEnvironment().isQuiet()) {
            client.setEventHandler(new SVNNotifyPrinter(getSVNEnvironment()));
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
                // skip it.
                getSVNEnvironment().getOut().println("Skipped '" + targetName + "'");
                continue;
            }
            files.add(target.getFile());
        }
        File[] filesArray = (File[]) files.toArray(new File[files.size()]);
        client.doUpdate(filesArray, getSVNEnvironment().getStartRevision(), depth, 
                getSVNEnvironment().isForce(), depthIsSticky); 
    } 

}
