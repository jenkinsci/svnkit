/*
 * ====================================================================
 * Copyright (c) 2004 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://tmate.org/svn/license.html.
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */

package org.tmatesoft.svn.cli.command;

import java.io.File;
import java.io.PrintStream;

import org.tmatesoft.svn.cli.SVNArgument;
import org.tmatesoft.svn.cli.SVNCommand;
import org.tmatesoft.svn.core.io.SVNException;
import org.tmatesoft.svn.core.io.SVNRepositoryLocation;
import org.tmatesoft.svn.core.wc.ISVNEventListener;
import org.tmatesoft.svn.core.wc.SVNEvent;
import org.tmatesoft.svn.core.wc.SVNEventAction;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNUpdateClient;
import org.tmatesoft.svn.util.PathUtil;

/**
 * @author TMate Software Ltd.
 */
public class CheckoutCommand extends SVNCommand {

	public void run(final PrintStream out, final PrintStream err) throws SVNException {
		final String url = getCommandLine().getURL(0);
		final SVNRepositoryLocation location = SVNRepositoryLocation.parseURL(url);

		String path;
        if (getCommandLine().getPathCount() > 0) {
            path = getCommandLine().getPathAt(0);
        } else {
            path = new File("", PathUtil.decode(PathUtil.tail(url))).getAbsolutePath();
        }

        long revision = parseRevision(getCommandLine(), null, null);
        SVNUpdateClient updater = new SVNUpdateClient(getCredentialsProvider(), new ISVNEventListener() {
            private boolean isExternal = false;
            
            public void svnEvent(SVNEvent event) {
                if (event.getAction() == SVNEventAction.UPDATE_ADD) {
                    println(out, "A    " + getPath(event.getFile()));
                } else if (event.getAction() == SVNEventAction.UPDATE_COMPLETED) {
                    if (!isExternal) {
                        println(out, "Checked out revision " + event.getRevision() + ".");
                    } else {
                        println(out, "Checked out external at revision " + event.getRevision() + ".");
                        isExternal = false;
                    }
                    println(out);
                } else if (event.getAction() == SVNEventAction.UPDATE_EXTERNAL) {
                    println(out);
                    println(out, "Fetching external item into '" + event.getPath() + "'");
                    isExternal = true;
                }
            }
        });
        if (getCommandLine().getURLCount() == 1) {
            updater.doCheckout(url, new File(path), SVNRevision.UNDEFINED, SVNRevision.create(revision), !getCommandLine().hasArgument(SVNArgument.NON_RECURSIVE));
        } else {
            for(int i = 0; i < getCommandLine().getURLCount(); i++) {
                String curl = getCommandLine().getURL(i);
                File dstPath = new File(path, PathUtil.decode(PathUtil.tail(curl)));
                updater.doCheckout(url, dstPath, SVNRevision.UNDEFINED, SVNRevision.create(revision), !getCommandLine().hasArgument(SVNArgument.NON_RECURSIVE));
            }
        }
	}
}
