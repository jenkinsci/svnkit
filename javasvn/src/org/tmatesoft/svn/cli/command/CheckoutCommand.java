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

import org.tmatesoft.svn.cli.SVNArgument;
import org.tmatesoft.svn.cli.SVNCommand;
import org.tmatesoft.svn.core.io.SVNException;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNUpdateClient;
import org.tmatesoft.svn.util.PathUtil;

import java.io.File;
import java.io.PrintStream;

/**
 * @author TMate Software Ltd.
 */
public class CheckoutCommand extends SVNCommand {

	public void run(final PrintStream out, final PrintStream err) throws SVNException {
		final String url = getCommandLine().getURL(0);

		String path;
        if (getCommandLine().getPathCount() > 0) {
            path = getCommandLine().getPathAt(0);
        } else {
            path = new File(".", PathUtil.decode(PathUtil.tail(url))).getAbsolutePath();
        }

        SVNRevision revision = parseRevision(getCommandLine());
        if (!revision.isValid()) {
            revision = SVNRevision.HEAD;
        }
        SVNUpdateClient updater = new SVNUpdateClient(getOptions(),
                new SVNCommandEventProcessor(out, err, true));
        if (getCommandLine().getURLCount() == 1) {
            updater.doCheckout(url, new File(path), SVNRevision.UNDEFINED, revision, !getCommandLine().hasArgument(SVNArgument.NON_RECURSIVE));
        } else {
            for(int i = 0; i < getCommandLine().getURLCount(); i++) {
                String curl = getCommandLine().getURL(i);
                File dstPath = new File(path, PathUtil.decode(PathUtil.tail(curl)));
                updater.doCheckout(url, dstPath, SVNRevision.UNDEFINED, revision, !getCommandLine().hasArgument(SVNArgument.NON_RECURSIVE));
            }
        }
	}
}
