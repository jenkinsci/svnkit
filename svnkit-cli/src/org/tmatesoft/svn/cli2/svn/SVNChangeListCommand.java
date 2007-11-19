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
package org.tmatesoft.svn.cli2.svn;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.wc.SVNPath;
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
        options = SVNOption.addAuthOptions(options);
        options.add(SVNOption.REMOVE);
        options.add(SVNOption.TARGETS);
        options.add(SVNOption.CONFIG_DIR);
        options.add(SVNOption.CHANGELIST);
        return options;
    }

    public void run() throws SVNException {
        List targets = new ArrayList(); 
        String changelist = null;
        if (getSVNEnvironment().getChangelist() != null) {
            SVNPath target = new SVNPath("");
            SVNChangelistClient changelistClient = getSVNEnvironment().getClientManager().getChangelistClient();
            changelistClient.getChangelist(target.getFile(), getSVNEnvironment().getChangelist(), targets);
            if (targets.isEmpty()) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNKNOWN_CHANGELIST, 
                        "Unknown changelist ''{0}''", getSVNEnvironment().getChangelist());
                SVNErrorManager.error(err);
            }
        }
        if (getSVNEnvironment().getTargets() != null) {
            targets.addAll(getSVNEnvironment().getTargets());
        }
        targets = getSVNEnvironment().combineTargets(targets);
        if (getSVNEnvironment().isRemove()) {
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
            SVNPath target = new SVNPath(targetName);
            if (target.isFile()) {
                paths.add(target.getFile());
            }
        }
        File[] files = (File[]) paths.toArray(new File[paths.size()]);
        
        SVNChangelistClient client = getSVNEnvironment().getClientManager().getChangelistClient();
        client.setEventHandler(new SVNNotifyPrinter(getSVNEnvironment()));
        try {
            if (changelist != null) {
                client.addToChangelist(files, changelist);
            } else {
                client.removeFromChangelist(files, changelist);
            }
        } catch (SVNException e) {
            getSVNEnvironment().handleWarning(e.getErrorMessage(), 
                    new SVNErrorCode[] {SVNErrorCode.UNVERSIONED_RESOURCE, SVNErrorCode.WC_PATH_NOT_FOUND},
                    getSVNEnvironment().isQuiet());
        }
    }
}
