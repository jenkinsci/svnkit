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
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.wc.SVNPath;
import org.tmatesoft.svn.core.wc.SVNCommitClient;
import org.tmatesoft.svn.util.SVNLogType;


/**
 * @version 1.3
 * @author  TMate Software Ltd.
 */
public class SVNImportCommand extends SVNCommand {

    public SVNImportCommand() {
        super("import", null);
    }
    
    public boolean isCommitter() {
        return true;
    }

    protected Collection createSupportedOptions() {
        Collection options = new LinkedList();
        options.add(SVNOption.QUIET);
        options.add(SVNOption.NON_RECURSIVE);
        options.add(SVNOption.DEPTH);
        options.add(SVNOption.AUTOPROPS);
        options.add(SVNOption.FORCE);
        options.add(SVNOption.NO_AUTOPROPS);
        options = SVNOption.addLogMessageOptions(options);
        options.add(SVNOption.NO_IGNORE);
        return options;
    }

    public void run() throws SVNException {
        List targets = getSVNEnvironment().combineTargets(new ArrayList(), true);
        SVNPath url = null;
        SVNPath src = null;
        if (targets.isEmpty()) {
            SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.CL_INSUFFICIENT_ARGS,
                    "Repository URL required when importing"), SVNLogType.CLIENT);
        } else if (targets.size() > 2) {
            SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.CL_ARG_PARSING_ERROR,
                "Too many arguments to import command"), SVNLogType.CLIENT);
        } else if (targets.size() == 1) {
            src = new SVNPath("");
            url = new SVNPath((String) targets.get(0));
        } else {
            src = new SVNPath((String) targets.get(0));
            url = new SVNPath((String) targets.get(1));
        }
        if (!url.isURL()) {
            SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.CL_ARG_PARSING_ERROR,
                    "Invalid URL ''{0}''", url.getTarget()), SVNLogType.CLIENT);
        }
        SVNCommitClient client = getSVNEnvironment().getClientManager().getCommitClient();
        if (!getSVNEnvironment().isQuiet()) {
            client.setEventHandler(new SVNNotifyPrinter(getSVNEnvironment()));
        }
        SVNDepth depth = getSVNEnvironment().getDepth();
        if (depth == SVNDepth.UNKNOWN) {
            depth = SVNDepth.INFINITY;
        }
        client.setCommitHandler(getSVNEnvironment());
        SVNCommitInfo info = client.doImport(src.getFile(), url.getURL(), getSVNEnvironment().getMessage(), 
                getSVNEnvironment().getRevisionProperties(), !getSVNEnvironment().isNoIgnore(), 
                getSVNEnvironment().isForce(), depth);
        if (!getSVNEnvironment().isQuiet()) {
            getSVNEnvironment().printCommitInfo(info);
        }
    }

}
