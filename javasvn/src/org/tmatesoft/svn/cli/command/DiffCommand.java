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
import org.tmatesoft.svn.core.wc.DefaultSVNDiffGenerator;
import org.tmatesoft.svn.core.wc.SVNDiffClient;

/**
 * @author TMate Software Ltd.
 */
public class DiffCommand extends SVNCommand {

	public void run(final PrintStream out, PrintStream err) throws SVNException {
		if (getCommandLine().getPathCount() != 1) {
			err.println("'diff' needs exactly one path to diff");
			return;
		}

		final String absolutePath = getCommandLine().getPathAt(0);
        SVNDiffClient differ = new SVNDiffClient(null, null, null);
        differ.setDiffGenerator(new DefaultSVNDiffGenerator() {
            public String getDisplayPath(File file) {
                return getPath(file).replace(File.separatorChar, '/');
            }
        });
        differ.doDiff(new File(absolutePath).getAbsoluteFile(), 
                !getCommandLine().hasArgument(SVNArgument.NON_RECURSIVE),
                false,
                getCommandLine().hasArgument(SVNArgument.FORCE),
                out);
	}
}
