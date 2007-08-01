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
package org.tmatesoft.svn.cli.command;

import java.io.File;
import java.io.InputStream;
import java.io.PrintStream;
import java.util.Collection;
import java.util.Iterator;

import org.tmatesoft.svn.cli.SVNArgument;
import org.tmatesoft.svn.cli.SVNCommand;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.wc.SVNChangelistClient;


/**
 * @version 1.1.2
 * @author  TMate Software Ltd.
 */
public class SVNChangelistCommand extends SVNCommand {

    public void run(PrintStream out, PrintStream err) throws SVNException {
        String changelist = (String) getCommandLine().getArgumentValue(SVNArgument.CHANGELIST); 
        Collection changelistTargets = null;
        getClientManager().setEventHandler(new SVNCommandEventProcessor(out, err, false));
        SVNChangelistClient client = getClientManager().getChangelistClient();

        if (changelist != null) {
            changelistTargets = client.getChangelist(new File("."), changelist, changelistTargets);
            if (changelistTargets.isEmpty()) {
                SVNErrorMessage error = SVNErrorMessage.create(SVNErrorCode.CL_ARG_PARSING_ERROR, 
                                        "no such changelist ''{0}''", changelist); 
                SVNErrorManager.error(error);
            }
        }
        
        boolean removeChangelist = getCommandLine().hasArgument(SVNArgument.REMOVE);
        int targetsNum = changelistTargets != null ? changelistTargets.size() + getCommandLine().getPathCount() : getCommandLine().getPathCount(); 

        try {
            if (removeChangelist) {
                if (targetsNum < 1) {
                    SVNErrorMessage error = SVNErrorMessage.create(SVNErrorCode.CL_INSUFFICIENT_ARGS);
                    SVNErrorManager.error(error);
                }
                File[] paths = new File[targetsNum];
                int k = 0;
                for (int i = 0; i < getCommandLine().getPathCount(); i++) {
                    paths[k++] = new File(SVNPathUtil.validateFilePath(
                                            new File(getCommandLine().getPathAt(i)).getAbsolutePath())); 
                }
                if (changelistTargets != null) {
                    for (Iterator changelistTargetsIter = changelistTargets.iterator(); changelistTargetsIter.hasNext();) {
                        paths[k++] = (File) changelistTargetsIter.next();
                    }
                }
                client.removeFromChangelist(paths, null);
            } else {
               if (targetsNum < 2) {
                   SVNErrorMessage error = SVNErrorMessage.create(SVNErrorCode.CL_INSUFFICIENT_ARGS);
                   SVNErrorManager.error(error);
               }
               String changelistName = getCommandLine().getPathAt(0);
               File[] paths = new File[targetsNum - 1];
               int k = 0;
               for (int i = 1; i < getCommandLine().getPathCount(); i++) {
                   paths[k++] = new File(SVNPathUtil.validateFilePath(new File(getCommandLine().getPathAt(i)).getAbsolutePath())); 
               }
               if (changelistTargets != null) {
                   for (Iterator changelistTargetsIter = changelistTargets.iterator(); changelistTargetsIter.hasNext();) {
                       paths[k++] = (File) changelistTargetsIter.next();
                   }
               }
               client.addToChangelist(paths, changelistName);
            }
        } catch (SVNException e) {
            handleWarning(e.getErrorMessage(), new SVNErrorCode[] {SVNErrorCode.UNVERSIONED_RESOURCE, SVNErrorCode.WC_PATH_NOT_FOUND}, err);
        }
    }

    public void run(InputStream in, PrintStream out, PrintStream err) throws SVNException {
        run(out, err);
    }

}
