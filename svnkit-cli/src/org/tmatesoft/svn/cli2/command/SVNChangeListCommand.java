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
import org.tmatesoft.svn.core.wc.SVNChangelistClient;


/**
 * @version 1.1.2
 * @author  TMate Software Ltd.
 */
public class SVNChangeListCommand extends SVNCommand {

    public SVNChangeListCommand() {
        super("changelist", new String[] {"cl"});
    }

    protected Collection createSupportedOptions() {
        Collection options = new LinkedList();
        options.add(SVNOption.REMOVE);
        options.add(SVNOption.TARGETS);
        options.add(SVNOption.CONFIG_DIR);
        options.add(SVNOption.CHANGELIST);
        return options;
    }

    public void run() throws SVNException {
        List targets = new ArrayList(); 
        String changelist = null;
        if (getEnvironment().getChangelist() != null) {
            SVNCommandTarget target = new SVNCommandTarget("");
            SVNChangelistClient changelistClient = getEnvironment().getClientManager().getChangelistClient();
            changelistClient.getChangelist(target.getFile(), getEnvironment().getChangelist(), targets);
            if (targets.isEmpty()) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CL_ARG_PARSING_ERROR, "no such changelist ''{0}''", getEnvironment().getChangelist());
                SVNErrorManager.error(err);
            }
        }
        if (getEnvironment().getTargets() != null) {
            targets.addAll(getEnvironment().getTargets());
        }
        targets = getEnvironment().combineTargets(targets);
        if (getEnvironment().isRemove()) {
            if (targets.size() < 1) { 
                SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.CL_INSUFFICIENT_ARGS));
            }
            changelist = null;
        } else {
            if (targets.size() < 2) { 
                SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.CL_INSUFFICIENT_ARGS));
            }
            changelist = (String) targets.remove(0);
        }
        Collection paths = new ArrayList();
        for (Iterator ts = targets.iterator(); ts.hasNext();) {
            String targetName = (String) ts.next();
            SVNCommandTarget target = new SVNCommandTarget(targetName);
            if (target.isFile()) {
                paths.add(target.getFile());
            }
        }
        File[] files = (File[]) paths.toArray(new File[paths.size()]);
        
        SVNChangelistClient client = getEnvironment().getClientManager().getChangelistClient();
        client.setEventHandler(new SVNNotifyPrinter(getEnvironment()));
        try {
            if (changelist != null) {
                client.addToChangelist(files, changelist);
            } else {
                client.removeFromChangelist(files, changelist);
            }
        } catch (SVNException e) {
            getEnvironment().handleWarning(e.getErrorMessage(), new SVNErrorCode[] {SVNErrorCode.UNVERSIONED_RESOURCE, SVNErrorCode.WC_PATH_NOT_FOUND});
        }
    }
}
