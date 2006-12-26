/*
 * ====================================================================
 * Copyright (c) 2004-2006 TMate Software Ltd.  All rights reserved.
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

import org.tmatesoft.svn.cli.SVNArgument;
import org.tmatesoft.svn.cli.SVNCommand;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.util.SVNEncodingUtil;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNUpdateClient;

/**
 * @version 1.1.0
 * @author  TMate Software Ltd.
 */
public class SVNCheckoutCommand extends SVNCommand {

    public void run(InputStream in, PrintStream out, PrintStream err) throws SVNException {
        run(out, err);
    }

    public void run(final PrintStream out, final PrintStream err) throws SVNException {
		final String url = getCommandLine().getURL(0);

		String path;
        if (getCommandLine().getPathCount() > 0) {
            path = getCommandLine().getPathAt(0);
        } else {
            path = new File(".", SVNEncodingUtil.uriDecode(SVNPathUtil.tail(url))).getAbsolutePath();
        }

        SVNRevision revision = parseRevision(getCommandLine());
        getClientManager().setEventHandler(new SVNCommandEventProcessor(out, err, true));
        SVNUpdateClient updater = getClientManager().getUpdateClient();
        if (getCommandLine().getURLCount() == 1) {
            SVNRevision pegRevision = getCommandLine().getPegRevision(0);
            updater.doCheckout(SVNURL.parseURIEncoded(url), new File(path), pegRevision, revision, !getCommandLine().hasArgument(SVNArgument.NON_RECURSIVE));
        } else {
            for(int i = 0; i < getCommandLine().getURLCount(); i++) {
                String curl = getCommandLine().getURL(i);
                File dstPath = new File(path, SVNEncodingUtil.uriDecode(SVNPathUtil.tail(curl)));
                SVNRevision pegRevision = getCommandLine().getPegRevision(i);
                updater.doCheckout(SVNURL.parseURIEncoded(url), dstPath, pegRevision, revision, !getCommandLine().hasArgument(SVNArgument.NON_RECURSIVE));
            }
        }
	}
}
