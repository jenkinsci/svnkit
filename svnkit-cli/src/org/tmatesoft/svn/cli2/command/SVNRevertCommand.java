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
import java.util.LinkedList;
import java.util.List;

import org.tmatesoft.svn.cli2.SVNCommand;
import org.tmatesoft.svn.cli2.SVNCommandTarget;
import org.tmatesoft.svn.cli2.SVNNotifyPrinter;
import org.tmatesoft.svn.cli2.SVNOption;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.wc.SVNChangelistClient;
import org.tmatesoft.svn.core.wc.SVNWCClient;


/**
 * @version 1.1.2
 * @author  TMate Software Ltd.
 */
public class SVNRevertCommand extends SVNCommand {

    public SVNRevertCommand() {
        super("revert", null);
    }

    protected Collection createSupportedOptions() {
        Collection options = new LinkedList();
        options.add(SVNOption.TARGETS);
        options.add(SVNOption.RECURSIVE);
        options.add(SVNOption.DEPTH);
        options.add(SVNOption.QUIET);
        options.add(SVNOption.CHANGELIST);
        options.add(SVNOption.CONFIG_DIR);
        return options;
    }

    public void run() throws SVNException {
        List targets = getEnvironment().combineTargets(getEnvironment().getTargets());
        if (getEnvironment().getChangelist() != null) {
            getEnvironment().setCurrentTarget(new SVNCommandTarget(""));
            SVNChangelistClient changelistClient = getEnvironment().getClientManager().getChangelistClient();
            changelistClient.getChangelist(getEnvironment().getCurrentTargetFile(), getEnvironment().getChangelist(), targets);
            if (targets.isEmpty()) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CL_ARG_PARSING_ERROR, "no such changelist ''{0}''", getEnvironment().getChangelist());
                SVNErrorManager.error(err);
            }
        }
        if (targets.isEmpty()) {
            SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.CL_INSUFFICIENT_ARGS));
        }
        SVNDepth depth = getEnvironment().getDepth();
        if (depth == SVNDepth.UNKNOWN) {
            depth = SVNDepth.fromRecurse(false);
        }
        SVNWCClient client = getEnvironment().getClientManager().getWCClient();
        if (!getEnvironment().isQuiet()) {
            client.setEventHandler(new SVNNotifyPrinter(getEnvironment()));
        }
        Collection pathsList = new ArrayList(targets.size());
        for(int i = 0; i < targets.size(); i++) {
            SVNCommandTarget target = new SVNCommandTarget((String) targets.get(i));
            if (target.isFile()) {
                pathsList.add(target.getFile());
                // check for reverting added directory inside it.
            }
        }
        File[] paths = (File[]) pathsList.toArray(new File[pathsList.size()]);
        getEnvironment().setCurrentTarget(new SVNCommandTarget(""));
        try {
            client.doRevert(paths, depth.isRecursive());
        } catch (SVNException e) {
            SVNErrorMessage err = e.getErrorMessage();
            if (!depth.isRecursive() && err.getErrorCode() == SVNErrorCode.WC_NOT_LOCKED) {
                err = err.wrap("Try 'svn revert --recursive' instead?");
            }
            SVNErrorManager.error(err);
        }
    }

}
