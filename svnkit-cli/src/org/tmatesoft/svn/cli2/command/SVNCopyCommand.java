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

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

import org.tmatesoft.svn.cli2.SVNCommand;
import org.tmatesoft.svn.cli2.SVNCommandTarget;
import org.tmatesoft.svn.cli2.SVNNotifyPrinter;
import org.tmatesoft.svn.cli2.SVNOption;
import org.tmatesoft.svn.core.SVNCommitInfo;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.wc.SVNCopyClient;
import org.tmatesoft.svn.core.wc.SVNCopySource;


/**
 * @version 1.1.2
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
        options.add(SVNOption.PARENTS);
        options = SVNOption.addLogMessageOptions(options);
        options = SVNOption.addAuthOptions(options);
        options.add(SVNOption.CONFIG_DIR);
        return options;
    }

    public void run() throws SVNException {
        List targets = getEnvironment().combineTargets(null);
        if (targets.size() < 2) {
            SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.CL_INSUFFICIENT_ARGS));
        }
        SVNCommandTarget dst = new SVNCommandTarget((String) targets.remove(targets.size() - 1));
        if (!dst.isURL()) {
            if (getEnvironment().getMessage() != null || getEnvironment().getFileData() != null || getEnvironment().getRevisionProperties() != null) {
                SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.CL_UNNECESSARY_LOG_MESSAGE,
                "Local, non-commit operations do not take a log message or revision properties"));
            }
        }

        Collection sources = new ArrayList();        
        boolean sourceIsURL = false;
        for (int i = 0; i < targets.size(); i++) {
            String targetName = (String) targets.get(i);
            SVNCommandTarget source = new SVNCommandTarget(targetName, true);
            if (i == 0) {
                sourceIsURL = source.isURL();
            }
            if (source.isURL()) {
                sources.add(new SVNCopySource(source.getPegRevision(), getEnvironment().getStartRevision(), source.getURL()));
            } else {
                sources.add(new SVNCopySource(source.getPegRevision(), getEnvironment().getStartRevision(), source.getFile()));
            }
        }

        SVNCopyClient client = getEnvironment().getClientManager().getCopyClient();
        if (!sourceIsURL && !dst.isURL()) {
            if (!getEnvironment().isQuiet()) {
                client.setEventHandler(new SVNNotifyPrinter(getEnvironment()));
            }
        } else if (!sourceIsURL && dst.isURL()){
            // skip
        } else if (sourceIsURL && !dst.isURL()) {
            if (!getEnvironment().isQuiet()) {
                client.setEventHandler(new SVNNotifyPrinter(getEnvironment(), true, false, false));
            }
        }         
        client.setCommitHandler(getEnvironment());
        SVNCopySource[] copySources = (SVNCopySource[]) sources.toArray(new SVNCopySource[sources.size()]);
        if (dst.isURL()) {
            SVNCommitInfo info = client.doCopy(copySources, dst.getURL(), false, false, 
                    getEnvironment().isParents(), getEnvironment().getMessage(), getEnvironment().getRevisionProperties());
            if (!getEnvironment().isQuiet()) {
                getEnvironment().printCommitInfo(info);
            }
        } else {
            client.doCopy(copySources, dst.getFile(), false, getEnvironment().isParents());
        }
    }

}
