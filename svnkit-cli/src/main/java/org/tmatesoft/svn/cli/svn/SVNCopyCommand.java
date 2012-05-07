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

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

import org.tmatesoft.svn.core.SVNCommitInfo;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.wc.SVNPath;
import org.tmatesoft.svn.core.wc.SVNCopyClient;
import org.tmatesoft.svn.core.wc.SVNCopySource;
import org.tmatesoft.svn.util.SVNLogType;


/**
 * @version 1.3
 * @author  TMate Software Ltd.
 */
public class SVNCopyCommand extends SVNCommand {

    public SVNCopyCommand() {
        super("copy", new String[] {"cp"});
    }

    public boolean isCommitter() {
        return true;
    }

    protected Collection createSupportedOptions() {
        Collection options = new LinkedList();
        options.add(SVNOption.REVISION);
        options.add(SVNOption.QUIET);
        options.add(SVNOption.IGNORE_EXTERNALS);
        options.add(SVNOption.PARENTS);
        options = SVNOption.addLogMessageOptions(options);
        return options;
    }

    public void run() throws SVNException {
        List targets = getSVNEnvironment().combineTargets(null, true);
        if (targets.size() < 2) {
            SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.CL_INSUFFICIENT_ARGS), SVNLogType.CLIENT);
        }
        SVNPath dst = new SVNPath((String) targets.remove(targets.size() - 1));
        if (!dst.isURL()) {
            if (getSVNEnvironment().getMessage() != null || getSVNEnvironment().getFileData() != null || getSVNEnvironment().getRevisionProperties() != null) {
                SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.CL_UNNECESSARY_LOG_MESSAGE,
                "Local, non-commit operations do not take a log message or revision properties"), SVNLogType.CLIENT);
            }
        }

        Collection sources = new ArrayList();        
        boolean sourceIsURL = false;
        for (int i = 0; i < targets.size(); i++) {
            String targetName = (String) targets.get(i);
            SVNPath source = new SVNPath(targetName, true);
            if (i == 0) {
                sourceIsURL = source.isURL();
            }
            if (source.isURL()) {
                sources.add(new SVNCopySource(source.getPegRevision(), getSVNEnvironment().getStartRevision(), source.getURL()));
            } else {
                sources.add(new SVNCopySource(source.getPegRevision(), getSVNEnvironment().getStartRevision(), source.getFile()));
            }
        }

        SVNCopyClient client = getSVNEnvironment().getClientManager().getCopyClient();
        if (!sourceIsURL && !dst.isURL()) {
            if (!getSVNEnvironment().isQuiet()) {
                client.setEventHandler(new SVNNotifyPrinter(getSVNEnvironment()));
            }
        } else if (!sourceIsURL && dst.isURL()){
            SVNNotifyPrinter printer = new SVNNotifyPrinter(getSVNEnvironment());
            printer.setWcToReposCopy(true);
            client.setEventHandler(printer);
        } else if (sourceIsURL && !dst.isURL()) {
            if (!getSVNEnvironment().isQuiet()) {
                client.setEventHandler(new SVNNotifyPrinter(getSVNEnvironment(), true, false, false));
            }
        }         
        client.setCommitHandler(getSVNEnvironment());
        SVNCopySource[] copySources = (SVNCopySource[]) sources.toArray(new SVNCopySource[sources.size()]);
        if (dst.isURL()) {
            SVNCommitInfo info = client.doCopy(copySources, dst.getURL(), false, getSVNEnvironment().isParents(), false,
                    getSVNEnvironment().getMessage(), 
                    getSVNEnvironment().getRevisionProperties());
                    
            if (!getSVNEnvironment().isQuiet()) {
                getSVNEnvironment().printCommitInfo(info);
            }
        } else {
            client.doCopy(copySources, dst.getFile(), false, getSVNEnvironment().isParents(), false);
        }
    }

}
