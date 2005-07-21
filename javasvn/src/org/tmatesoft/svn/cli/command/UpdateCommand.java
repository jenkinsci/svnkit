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
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.util.SVNFormatUtil;
import org.tmatesoft.svn.core.internal.wc.SVNWCAccess;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNUpdateClient;
import org.tmatesoft.svn.util.DebugLog;

/**
 * @author TMate Software Ltd.
 */
public class UpdateCommand extends SVNCommand {

    public void run(final PrintStream out, final PrintStream err) throws SVNException {
        boolean error = false;
        for (int i = 0; i < getCommandLine().getPathCount(); i++) {
            final String path;
            path = getCommandLine().getPathAt(i);

            SVNRevision revision = parseRevision(getCommandLine());
            if (!revision.isValid()) {
                revision = SVNRevision.HEAD;
            }
            getClientManager().setEventHandler(new SVNCommandEventProcessor(out, err, false));
            SVNUpdateClient updater = getClientManager().getUpdateClient();
            
            File file = new File(path).getAbsoluteFile();
            if (!file.exists()) {
                File parent = file.getParentFile();
                if (!parent.exists() || !SVNWCAccess.isVersionedDirectory(parent)) {
                    if (!getCommandLine().hasArgument(SVNArgument.QUIET)) {
                        println(out, "Skipped '" +  SVNFormatUtil.formatPath(file).replace('/', File.separatorChar) + "'");
                    }
                    return;
                }
            }
            try {
                updater.doUpdate(file.getAbsoluteFile(), revision, !getCommandLine().hasArgument(SVNArgument.NON_RECURSIVE));
            } catch (Throwable th) {
                DebugLog.error(th);
                println(err, th.getMessage());
                println(err);
                error = true;
            }
        }
        if (error) {
            System.exit(1);
        }
    }
}
