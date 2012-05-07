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
import java.util.Iterator;
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
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.util.SVNLogType;


/**
 * @version 1.3
 * @author  TMate Software Ltd.
 */
public class SVNMoveCommand extends SVNCommand {

    public SVNMoveCommand() {
        super("move", new String[] {"mv", "rename", "ren"});
    }

    public boolean isCommitter() {
        return true;
    }

    protected Collection createSupportedOptions() {
        Collection options = new LinkedList();
        options.add(SVNOption.REVISION);
        options.add(SVNOption.QUIET);
        options.add(SVNOption.FORCE);
        options.add(SVNOption.PARENTS);
        options = SVNOption.addLogMessageOptions(options);
        return options;
    }

    public void run() throws SVNException {
        List targets = getSVNEnvironment().combineTargets(null, true);
        if (targets.size() < 2) {
            SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.CL_INSUFFICIENT_ARGS), SVNLogType.CLIENT);
        }
        if (getSVNEnvironment().getStartRevision() != SVNRevision.UNDEFINED && 
                getSVNEnvironment().getStartRevision() != SVNRevision.HEAD) {
            SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.UNSUPPORTED_FEATURE,
                    "Cannot specify revision (except HEAD) with move operation"), SVNLogType.CLIENT);
        }
        SVNPath dst = new SVNPath((String) targets.remove(targets.size() - 1));
        if (!dst.isURL()) {
            if (getSVNEnvironment().getMessage() != null || getSVNEnvironment().getFileData() != null || getSVNEnvironment().getRevisionProperties() != null) {
                SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.CL_UNNECESSARY_LOG_MESSAGE,
                "Local, non-commit operations do not take a log message or revision properties"), SVNLogType.CLIENT);
            }
        }

        SVNCopyClient client = getSVNEnvironment().getClientManager().getCopyClient();
        if (!getSVNEnvironment().isQuiet()) {
            client.setEventHandler(new SVNNotifyPrinter(getSVNEnvironment()));
        }
        client.setCommitHandler(getSVNEnvironment());
        Collection sources = new ArrayList();
        for (Iterator ts = targets.iterator(); ts.hasNext();) {
            String targetName = (String) ts.next();
            SVNPath source = new SVNPath(targetName);
            if (source.isURL()) {
                sources.add(new SVNCopySource(SVNRevision.HEAD, SVNRevision.UNDEFINED, source.getURL()));
            } else {
                sources.add(new SVNCopySource(getSVNEnvironment().getStartRevision(), SVNRevision.UNDEFINED, source.getFile()));
            }
        }
        SVNCopySource[] copySources = (SVNCopySource[]) sources.toArray(new SVNCopySource[sources.size()]);
        try {
            if (dst.isURL()) {
                SVNCommitInfo info = client.doCopy(copySources, dst.getURL(), true, getSVNEnvironment().isParents(), 
                        false, getSVNEnvironment().getMessage(), getSVNEnvironment().getRevisionProperties());
                if (!getSVNEnvironment().isQuiet()) {
                    getSVNEnvironment().printCommitInfo(info);
                }
            } else {
                client.doCopy(copySources, dst.getFile(), true, getSVNEnvironment().isParents(), false);
            }
        } catch (SVNException e) {
            SVNErrorMessage err = e.getErrorMessage();
            SVNErrorCode code = err.getErrorCode();
            if (code == SVNErrorCode.UNVERSIONED_RESOURCE || code == SVNErrorCode.CLIENT_MODIFIED) {
                err = err.wrap("Use --force to override this restriction");
            }
            SVNErrorManager.error(err, e, SVNLogType.CLIENT);
        }
    }

}
