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
import java.util.LinkedList;
import java.util.List;

import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.wc.SVNFileType;
import org.tmatesoft.svn.core.internal.wc.SVNPath;
import org.tmatesoft.svn.core.internal.wc.admin.SVNEntry;
import org.tmatesoft.svn.core.internal.wc.admin.SVNWCAccess;
import org.tmatesoft.svn.core.wc.SVNWCClient;
import org.tmatesoft.svn.util.SVNLogType;


/**
 * @version 1.3
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
        return options;
    }

    public void run() throws SVNException {
        List targets = getSVNEnvironment().combineTargets(getSVNEnvironment().getTargets(), true);
        if (targets.isEmpty()) {
            SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.CL_INSUFFICIENT_ARGS), SVNLogType.CLIENT);
        }
        SVNDepth depth = getSVNEnvironment().getDepth();
        if (depth == SVNDepth.UNKNOWN) {
            depth = SVNDepth.EMPTY;
        }
        SVNWCClient client = getSVNEnvironment().getClientManager().getWCClient();
        if (!getSVNEnvironment().isQuiet()) {
            client.setEventHandler(new SVNNotifyPrinter(getSVNEnvironment()));
        }
        Collection pathsList = new ArrayList(targets.size());
        for(int i = 0; i < targets.size(); i++) {
            SVNPath target = new SVNPath((String) targets.get(i));
            if (target.isFile()) {
                if ("".equals(target.getTarget())) {
                    if (isScheduledForAddition(target.getFile())) {
                        SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_INVALID_OP_ON_CWD, 
                                "Cannot revert addition of current directory; please try again from the parent directory");
                        SVNErrorManager.error(err, SVNLogType.CLIENT);
                    }
                }
                pathsList.add(target.getFile());
            }
        }
        
        Collection changeLists = getSVNEnvironment().getChangelistsCollection();
        File[] paths = (File[]) pathsList.toArray(new File[pathsList.size()]);
        try {
            client.doRevert(paths, depth, changeLists);
        } catch (SVNException e) {
            SVNErrorMessage err = e.getErrorMessage();
            if (!depth.isRecursive() && err.getErrorCode() == SVNErrorCode.WC_NOT_LOCKED) {
                err = err.wrap("Try 'svn revert --recursive' instead?");
            } else if (!depth.isRecursive() && err.getErrorCode() == SVNErrorCode.WC_INVALID_OPERATION_DEPTH) {
                err = err.wrap("Try 'svn revert --depth infinity' instead?");
            }
            SVNErrorManager.error(err, SVNLogType.CLIENT);
        }
    }
    
    private boolean isScheduledForAddition(File dir) {
        if (SVNFileType.getType(dir) != SVNFileType.DIRECTORY) {
            return false;
        }
        SVNWCAccess wcAccess = SVNWCAccess.newInstance(null);
        try {
            wcAccess.probeOpen(dir, false, 0);
            SVNEntry entry = wcAccess.getVersionedEntry(dir, false);
            if (entry != null && "".equals(entry.getName()) && entry.isScheduledForAddition()) {
                return true;
            }
        } catch (SVNException e) {
        } finally {
            try {
                wcAccess.close();
            } catch (SVNException e) {
            }
        }
        return false;
    }

}
