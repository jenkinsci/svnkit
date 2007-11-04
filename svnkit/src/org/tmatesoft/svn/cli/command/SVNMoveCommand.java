/*
 * ====================================================================
 * Copyright (c) 2004-2007 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */

package org.tmatesoft.svn.cli.command;

import java.io.File;
import java.io.InputStream;
import java.io.PrintStream;
import java.util.Map;

import org.tmatesoft.svn.cli.SVNArgument;
import org.tmatesoft.svn.cli.SVNCommand;
import org.tmatesoft.svn.core.SVNCommitInfo;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.wc.SVNCopyClient;
import org.tmatesoft.svn.core.wc.SVNCopySource;
import org.tmatesoft.svn.core.wc.SVNRevision;

/**
 * @version 1.1.1
 * @author  TMate Software Ltd.
 */
public class SVNMoveCommand extends SVNCommand {

    public void run(InputStream in, PrintStream out, PrintStream err) throws SVNException {
        run(out, err);
    }

	public void run(PrintStream out, PrintStream err) throws SVNException {
    	if (getCommandLine().hasPaths() && getCommandLine().hasURLs()) {
    		err.println("only URL->URL or WC->WC copy is supported");
    		return;
    	}
		if (getCommandLine().hasURLs()) {
			runRemote(out, err);
		} else {
			runLocally(out, err);
		}
	}

	private void runRemote(PrintStream out, PrintStream err) throws SVNException {
        if (getCommandLine().getURLCount() < 2) {
            SVNErrorMessage msg = SVNErrorMessage.create(SVNErrorCode.CL_INSUFFICIENT_ARGS, "Please enter SRC and DST URLs");
            throw new SVNException(msg);
        }

        String dstURL = getCommandLine().getURL(getCommandLine().getURLCount() - 1);
        if (matchTabsInURL(dstURL, err)) {
            return;
        }
        SVNURL dstSVNURL = SVNURL.parseURIEncoded(dstURL);
        String commitMessage = (String) getCommandLine().getArgumentValue(SVNArgument.MESSAGE);
        getClientManager().setEventHandler(new SVNCommandEventProcessor(out, err, false));
        SVNCopyClient updater = getClientManager().getCopyClient();
        Map revProps = (Map) getCommandLine().getArgumentValue(SVNArgument.WITH_REVPROP); 
        SVNRevision srcRevision = SVNRevision.parse((String) getCommandLine().getArgumentValue(SVNArgument.REVISION));
        boolean makeParents = getCommandLine().hasArgument(SVNArgument.PARENTS);

        SVNCopySource[] sources = new SVNCopySource[getCommandLine().getURLCount() - 1]; 
        for (int i = 0; i < getCommandLine().getURLCount() - 1; i++) {
            String srcURL = getCommandLine().getURL(i);
            if (matchTabsInURL(srcURL, err)) {
                return;
            }
            sources[i] = new SVNCopySource(SVNRevision.UNDEFINED, srcRevision, SVNURL.parseURIEncoded(srcURL));
        }
        SVNCommitInfo result = updater.doCopy(sources, dstSVNURL, true, false, makeParents, commitMessage, revProps);
        if (result != SVNCommitInfo.NULL) {
            out.println();
            out.println("Committed revision " + result.getNewRevision() + ".");
        }

	}

	private void runLocally(final PrintStream out, PrintStream err) throws SVNException {
        if (getCommandLine().getPathCount() < 2) {
            SVNErrorMessage msg = SVNErrorMessage.create(SVNErrorCode.CL_INSUFFICIENT_ARGS, "Please enter SRC and DST path");
            throw new SVNException(msg);
        }
        
        String commitMessage = (String) getCommandLine().getArgumentValue(SVNArgument.MESSAGE);
        boolean makeParents = getCommandLine().hasArgument(SVNArgument.PARENTS);
        Map revisionProps = (Map) getCommandLine().getArgumentValue(SVNArgument.WITH_REVPROP);
        boolean hasFile = getCommandLine().hasArgument(SVNArgument.FILE);
        if (commitMessage != null || hasFile || revisionProps != null) {
            SVNErrorMessage error = SVNErrorMessage.create(SVNErrorCode.CL_UNNECESSARY_LOG_MESSAGE, "Local, non-commit operations do not take a log message or revision properties");
            SVNErrorManager.error(error);
        }
        final String absoluteDstPath = getCommandLine().getPathAt(getCommandLine().getPathCount() - 1);
        File absoluteDstFile = new File(absoluteDstPath);
        if (matchTabsInPath(absoluteDstPath, err)) {
            return;
        }
        
        SVNCopySource[] sources = new SVNCopySource[getCommandLine().getPathCount() - 1];
        for (int i = 0; i < getCommandLine().getPathCount() - 1; i++) {
            final String absoluteSrcPath = getCommandLine().getPathAt(i);
            if (matchTabsInPath(absoluteSrcPath, err)) {
                return;
            }
            sources[i] = new SVNCopySource(SVNRevision.UNDEFINED, SVNRevision.WORKING, new File(absoluteSrcPath));
        }

        getClientManager().setEventHandler(new SVNCommandEventProcessor(out, err, false));
        SVNCopyClient updater = getClientManager().getCopyClient();
        updater.doCopy(sources, absoluteDstFile, true, makeParents, false);
    }
}
