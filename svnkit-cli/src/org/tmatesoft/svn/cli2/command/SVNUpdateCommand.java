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
package org.tmatesoft.svn.cli2.command;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import org.tmatesoft.svn.cli2.SVNCommand;
import org.tmatesoft.svn.cli2.SVNCommandTarget;
import org.tmatesoft.svn.cli2.SVNNotifyPrinter;
import org.tmatesoft.svn.cli2.SVNOption;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.wc.ISVNPathList;
import org.tmatesoft.svn.core.wc.SVNChangelistClient;
import org.tmatesoft.svn.core.wc.SVNPathList;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNUpdateClient;


/**
 * @version 1.1.2
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
        options.add(SVNOption.QUIET);
        options.add(SVNOption.FORCE);
        options = SVNOption.addAuthOptions(options);
        options.add(SVNOption.CONFIG_DIR);
        options.add(SVNOption.IGNORE_EXTERNALS);
        options.add(SVNOption.CHANGELIST);
        return options;
    }

    public void run() throws SVNException {
        List targets = new ArrayList(); 
        if (getEnvironment().getChangelist() != null) {
            SVNCommandTarget target = new SVNCommandTarget("");
            SVNChangelistClient changelistClient = getEnvironment().getClientManager().getChangelistClient();
            changelistClient.getChangelist(target.getFile(), getEnvironment().getChangelist(), targets);
            if (targets.isEmpty()) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CL_ARG_PARSING_ERROR, "no such changelist ''{0}''", getEnvironment().getChangelist());
                SVNErrorManager.error(err);
            }
        }
        targets = getEnvironment().combineTargets(targets);
        if (targets.isEmpty()) {
            targets.add("");
        }
        SVNUpdateClient client = getEnvironment().getClientManager().getUpdateClient();
        if (!getEnvironment().isQuiet()) {
            client.setEventHandler(new SVNNotifyPrinter(getEnvironment()));
        }
        final List updateTargets = new ArrayList(targets.size());
        List files = new ArrayList(targets.size());
        for (Iterator ts = targets.iterator(); ts.hasNext();) {
            String targetName = (String) ts.next();
            SVNCommandTarget target = new SVNCommandTarget(targetName);
            if (!target.isFile()) {
                // skip it.
                getEnvironment().getOut().println("Skipped '" + targetName + "'");
                continue;
            }
            updateTargets.add(target);
            files.add(target.getFile());
        }
        File[] filesArray = (File[]) files.toArray(new File[files.size()]);
        ISVNPathList pathList = SVNPathList.create(filesArray, SVNRevision.UNDEFINED);
        client.doUpdate(pathList, getEnvironment().getStartRevision(), getEnvironment().getDepth(), getEnvironment().isForce()); 
    } 

}
