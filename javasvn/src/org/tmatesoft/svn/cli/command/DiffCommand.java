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
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.Map;

import org.tmatesoft.svn.cli.SVNArgument;
import org.tmatesoft.svn.cli.SVNCommand;
import org.tmatesoft.svn.core.io.SVNException;
import org.tmatesoft.svn.core.wc.DefaultSVNDiffGenerator;
import org.tmatesoft.svn.core.wc.SVNDiffClient;
import org.tmatesoft.svn.core.wc.SVNRevision;

/**
 * @author TMate Software Ltd.
 */
public class DiffCommand extends SVNCommand {

	public void run(final PrintStream out, PrintStream err) throws SVNException {
        SVNDiffClient differ = new SVNDiffClient(getCredentialsProvider(), null);
        differ.setDiffGenerator(new DefaultSVNDiffGenerator() {
            public String getDisplayPath(File file) {
                return getPath(file).replace(File.separatorChar, '/');
            }

            public void displayPropDiff(String path, Map baseProps, Map diff, OutputStream result) throws SVNException {
                super.displayPropDiff(path.replace('/', File.separatorChar), baseProps, diff, result);
            }
            
        });
        boolean useAncestry = getCommandLine().hasArgument(SVNArgument.USE_ANCESTRY);
        boolean recursive = !getCommandLine().hasArgument(SVNArgument.NON_RECURSIVE);
        differ.getDiffGenerator().setDiffDeleted(!getCommandLine().hasArgument(SVNArgument.NO_DIFF_DELETED));
        differ.getDiffGenerator().setForcedBinaryDiff(getCommandLine().hasArgument(SVNArgument.FORCE));
        
        // now supports only '-rN:M target' case, when targets are wc paths
        if (getCommandLine().hasPaths()) {
            SVNRevision rN = SVNRevision.UNDEFINED;
            SVNRevision rM = SVNRevision.UNDEFINED;
            String revStr = (String) getCommandLine().getArgumentValue(SVNArgument.REVISION);
            if (revStr != null && revStr.indexOf(':') > 0) {
                rN = SVNRevision.parse(revStr.substring(0, revStr.indexOf(':')));
                rM = SVNRevision.parse(revStr.substring(revStr.indexOf(':') + 1));
            } else if (revStr != null) {
                rN = SVNRevision.parse(revStr);
            }
            
            for(int i = 0; i < getCommandLine().getPathCount(); i++) {
                String path = getCommandLine().getPathAt(i);
                differ.doDiff(new File(path).getAbsoluteFile(), rN, rM, recursive, useAncestry, out);
            }
        } else {
            throw new SVNException("diff command doesn't support this call");
        }
	}
}
