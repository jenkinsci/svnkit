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

import java.util.Collection;
import java.util.Iterator;
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
import org.tmatesoft.svn.core.wc.SVNWCClient;


/**
 * @version 1.1.2
 * @author  TMate Software Ltd.
 */
public class SVNAddCommand extends SVNCommand {

    public SVNAddCommand() {
        super("add", null);
    }

    protected Collection createSupportedOptions() {
        Collection options = new LinkedList();
        options.add(SVNOption.TARGETS);
        options.add(SVNOption.NON_RECURSIVE);
        options.add(SVNOption.DEPTH);
        options.add(SVNOption.QUIET);
        options.add(SVNOption.CONFIG_DIR);
        options.add(SVNOption.FORCE);
        options.add(SVNOption.NO_IGNORE);
        options.add(SVNOption.AUTOPROPS);
        options.add(SVNOption.NO_AUTOPROPS);
        options.add(SVNOption.PARENTS);
        return options;
    }

    public void run() throws SVNException {
        List targets = getEnvironment().combineTargets(getEnvironment().getTargets());
        if (targets.isEmpty()) {
            SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.CL_INSUFFICIENT_ARGS));
        }
        SVNDepth depth = getEnvironment().getDepth();
        if (depth == SVNDepth.UNKNOWN) {
            depth = SVNDepth.INFINITY;
        }
        SVNWCClient client = getEnvironment().getClientManager().getWCClient();
        if (!getEnvironment().isQuiet()) {
            client.setEventHandler(new SVNNotifyPrinter(getEnvironment()));
        }
        for (Iterator ts = targets.iterator(); ts.hasNext();) {
            String targetName = (String) ts.next();
            SVNCommandTarget target = new SVNCommandTarget(targetName);
            if (target.isURL()) {
                continue;
            }
            try {
                getEnvironment().setCurrentTarget(target);
                client.doAdd(target.getFile(), getEnvironment().isForce(), false, getEnvironment().isParents(), depth.isRecursive(), 
                        getEnvironment().isNoIgnore(), getEnvironment().isParents());
            } catch (SVNException e) {
                getEnvironment().handleWarning(e.getErrorMessage(), new SVNErrorCode[] {SVNErrorCode.ENTRY_EXISTS, SVNErrorCode.WC_PATH_NOT_FOUND});
            }
        }
    }

}
