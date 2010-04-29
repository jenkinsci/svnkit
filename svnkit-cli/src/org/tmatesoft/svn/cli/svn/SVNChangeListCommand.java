/*
 * ====================================================================
 * Copyright (c) 2004-2010 TMate Software Ltd.  All rights reserved.
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
import org.tmatesoft.svn.core.wc.SVNChangelistClient;
import org.tmatesoft.svn.util.SVNLogType;


/**
 * @version 1.3
 * @author  TMate Software Ltd.
 */
public class SVNChangeListCommand extends SVNCommand {

    public SVNChangeListCommand() {
        super("changelist", new String[] {"cl"});
    }

    protected Collection createSupportedOptions() {
        Collection options = new LinkedList();
        options.add(SVNOption.QUIET);
        options.add(SVNOption.RECURSIVE);
        options.add(SVNOption.DEPTH);
        options.add(SVNOption.REMOVE);
        options.add(SVNOption.TARGETS);
        options.add(SVNOption.CHANGELIST);
        return options;
    }

    public void run() throws SVNException {
        List targets = new ArrayList(); 
        String changelist = null;
        if (getSVNEnvironment().getTargets() != null) {
            targets.addAll(getSVNEnvironment().getTargets());
        }
        targets = getSVNEnvironment().combineTargets(targets, true);

        if (getSVNEnvironment().isRemove()) {
            if (targets.size() < 1) { 
                SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.CL_INSUFFICIENT_ARGS), SVNLogType.CLIENT);
            }
            changelist = null;
        } else {
            if (targets.size() < 2) { 
                SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.CL_INSUFFICIENT_ARGS), SVNLogType.CLIENT);
            }
            changelist = (String) targets.remove(0);
        }
        
        Collection paths = new ArrayList();
        for (Iterator ts = targets.iterator(); ts.hasNext();) {
            String targetName = (String) ts.next();
            SVNPath target = new SVNPath(targetName);
            paths.add(target.getFile());
        }
        File[] files = (File[]) paths.toArray(new File[paths.size()]);
        
        SVNDepth depth = getSVNEnvironment().getDepth();
        if (depth == SVNDepth.UNKNOWN) {
            depth = SVNDepth.EMPTY;
        }
        
        SVNChangelistClient client = getSVNEnvironment().getClientManager().getChangelistClient();
        if (!getSVNEnvironment().isQuiet()) {
            client.setEventHandler(new SVNNotifyPrinter(getSVNEnvironment()));
        }
        try {
            if (changelist != null) {
                client.doAddToChangelist(files, depth, changelist, getSVNEnvironment().getChangelists());
            } else {
                client.doRemoveFromChangelist(files, depth, getSVNEnvironment().getChangelists());
            }
        } catch (SVNException e) {
            getSVNEnvironment().handleWarning(e.getErrorMessage(), 
                    new SVNErrorCode[] {SVNErrorCode.UNVERSIONED_RESOURCE, SVNErrorCode.WC_PATH_NOT_FOUND},
                    getSVNEnvironment().isQuiet());
        }
    }
}
