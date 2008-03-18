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

import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.wc.SVNPath;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.wc.SVNChangelistClient;
import org.tmatesoft.svn.core.wc.SVNWCClient;


/**
 * @version 1.1.2
 * @author  TMate Software Ltd.
 */
public class SVNUnLockCommand extends SVNCommand {

    public SVNUnLockCommand() {
        super("unlock", null);
    }

    protected Collection createSupportedOptions() {
        Collection options = new LinkedList();
        options.add(SVNOption.TARGETS);
        options.add(SVNOption.FORCE);
        options.add(SVNOption.CHANGELIST);
        return options;
    }

    public void run() throws SVNException {
        Collection targets = new ArrayList(); 
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
        targets = getSVNEnvironment().combineTargets(targets, true);
        if (targets.isEmpty()) {
            SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.CL_INSUFFICIENT_ARGS));
        }
        SVNWCClient client = getSVNEnvironment().getClientManager().getWCClient();
        client.setEventHandler(new SVNNotifyPrinter(getSVNEnvironment()));
        Collection paths = new ArrayList();
        Collection urls = new ArrayList();
        for (Iterator ts = targets.iterator(); ts.hasNext();) {
            String targetName = (String) ts.next();
            SVNPath target = new SVNPath(targetName);
            if (target.isURL()) {
                urls.add(target.getURL());
            } else {
                paths.add(target.getFile());
            }
        }
        if (!paths.isEmpty()) {
            File[] filesArray = (File[]) paths.toArray(new File[paths.size()]);
            client.doUnlock(filesArray, getSVNEnvironment().isForce());
        }
        if (!urls.isEmpty()) {
            SVNURL[] urlsArray = (SVNURL[]) urls.toArray(new SVNURL[urls.size()]);
            client.doUnlock(urlsArray, getSVNEnvironment().isForce());
        }
    }

}
