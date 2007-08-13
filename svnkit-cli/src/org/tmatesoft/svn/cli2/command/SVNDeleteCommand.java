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

import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import org.tmatesoft.svn.cli2.SVNCommand;
import org.tmatesoft.svn.cli2.SVNCommandTarget;
import org.tmatesoft.svn.cli2.SVNCommandUtil;
import org.tmatesoft.svn.cli2.SVNNotifyPrinter;
import org.tmatesoft.svn.cli2.SVNOption;
import org.tmatesoft.svn.core.SVNCommitInfo;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.wc.SVNCommitClient;
import org.tmatesoft.svn.core.wc.SVNWCClient;


/**
 * @version 1.1.2
 * @author  TMate Software Ltd.
 */
public class SVNDeleteCommand extends SVNCommand {

    public SVNDeleteCommand() {
        super("delete", new String[] {"del", "remove", "rm"});
    }

    public boolean isCommitter() {
        return true;
    }

    protected Collection createSupportedOptions() {
        Collection options = new LinkedList();
        options.add(SVNOption.FORCE);
        options.add(SVNOption.QUIET);
        options.add(SVNOption.TARGETS);
        options = SVNOption.addLogMessageOptions(options);
        options = SVNOption.addAuthOptions(options);
        options.add(SVNOption.CONFIG_DIR);
        options.add(SVNOption.KEEP_LOCAL);
        return options;
    }

    public void run() throws SVNException {
        List targets = getEnvironment().combineTargets(getEnvironment().getTargets());
        if (targets.isEmpty()) {
            SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.CL_INSUFFICIENT_ARGS));
        }
        boolean hasURLs = false;
        boolean hasPaths = false;
        for (Iterator ts = targets.iterator(); ts.hasNext();) {
            String targetName = (String) ts.next();
            if (!SVNCommandUtil.isURL(targetName)) {
                if (getEnvironment().getMessage() != null || getEnvironment().getFileData() != null || getEnvironment().getRevisionProperties() != null) {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CL_UNNECESSARY_LOG_MESSAGE,
                            "Local, non-commit operations do not take a log message or revision properties");
                    SVNErrorManager.error(err);
                }
                hasPaths = true;
            } else {
                hasURLs = true;
            }
        }
        if (hasURLs && hasPaths) {
            SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.CL_ARG_PARSING_ERROR, "Specify either URLs or local paths, not both"));
        }
        if (hasURLs) {
            SVNCommitClient client = getEnvironment().getClientManager().getCommitClient();
            if (!getEnvironment().isQuiet()) {
                client.setEventHandler(new SVNNotifyPrinter(getEnvironment()));
            }
            client.setCommitHandler(getEnvironment());
            SVNURL[] urls = new SVNURL[targets.size()];
            for (int i = 0; i < targets.size(); i++) {
                String url = (String) targets.get(i);
                urls[i] = SVNURL.parseURIEncoded(url);
            }
            try {
                SVNCommitInfo info = client.doDelete(urls, getEnvironment().getMessage(), getEnvironment().getRevisionProperties());
                getEnvironment().printCommitInfo(info);
            } catch (SVNException e) {
                SVNErrorMessage err = e.getErrorMessage();
                SVNErrorManager.error(err);
            }
        } else {
            SVNWCClient client = getEnvironment().getClientManager().getWCClient();
            if (!getEnvironment().isQuiet()) {
                client.setEventHandler(new SVNNotifyPrinter(getEnvironment()));
            }
            try {
                for (Iterator ts = targets.iterator(); ts.hasNext();) {
                    String targetName = (String) ts.next();
                    SVNCommandTarget target = new SVNCommandTarget(targetName);
                    client.doDelete(target.getFile(), getEnvironment().isForce(), !getEnvironment().isKeepLocal(), false);
                }
            } catch (SVNException e) {
                SVNErrorMessage err = e.getErrorMessage();
                if (err != null) {
                    SVNErrorCode code = err.getErrorCode();
                    if (code == SVNErrorCode.UNVERSIONED_RESOURCE || code == SVNErrorCode.CLIENT_MODIFIED) {
                        err = err.wrap("Use --force to override this restriction");
                    }
                }
                SVNErrorManager.error(err);
            }
        }
    }

}
