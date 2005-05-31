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
import org.tmatesoft.svn.core.wc.SVNDiffClient;
import org.tmatesoft.svn.core.wc.SVNRevision;

/**
 * @author TMate Software Ltd.
 */
public class MergeCommand extends SVNCommand {

	public void run(final PrintStream out, PrintStream err) throws SVNException {
        boolean useAncestry = !getCommandLine().hasArgument(SVNArgument.IGNORE_ANCESTRY);
        boolean recursive = !getCommandLine().hasArgument(SVNArgument.NON_RECURSIVE);
        boolean force = getCommandLine().hasArgument(SVNArgument.FORCE);
        boolean dryRun = getCommandLine().hasArgument(SVNArgument.DRY_RUN);

        SVNDiffClient differ = new SVNDiffClient(getCredentialsProvider(), new SVNCommandEventProcessor(out, false, false));
        
        if (getCommandLine().hasArgument(SVNArgument.REVISION)) {
            // merge -rN:M urlOrPath@r wcPath
            SVNRevision rN = SVNRevision.UNDEFINED;
            SVNRevision rM = SVNRevision.UNDEFINED;
            String revStr = (String) getCommandLine().getArgumentValue(SVNArgument.REVISION);
            if (revStr.indexOf(':') <= 0) {
                println(err, "svn: merge needs both source and target revisions to be specified");
                return;
            }
            rN = SVNRevision.parse(revStr.substring(0, revStr.indexOf(':')));
            rM = SVNRevision.parse(revStr.substring(revStr.indexOf(':') + 1));
            if (!rN.isValid() || !rM.isValid() || rN == SVNRevision.WORKING || rM == SVNRevision.WORKING) {
                println(err, "svn: merge needs both source and target revisions to be specified");
                return;
            }
            if (getCommandLine().hasURLs()) {
                String url = getCommandLine().getURL(0);
                SVNRevision pegRev = getCommandLine().getPegRevision(0);
                File dstPath = new File(getCommandLine().getPathAt(0));
                if (pegRev == SVNRevision.UNDEFINED) {
                    pegRev = SVNRevision.HEAD;
                }
                differ.doMerge(url, pegRev, rN, rM, dstPath, recursive, useAncestry, dryRun);
            } else if (getCommandLine().hasPaths()){
                File srcPath = new File(getCommandLine().getPathAt(0));
                File dstPath = new File(".");
                SVNRevision pegRevision = getCommandLine().getPathPegRevision(0);
                if (pegRevision == SVNRevision.UNDEFINED) {
                    pegRevision = SVNRevision.HEAD;
                }                
                if (getCommandLine().getPathCount() > 1) {
                    dstPath = new File(getCommandLine().getPathAt(1));
                }
                differ.doMerge(srcPath, pegRevision, rN, rM, dstPath, recursive, useAncestry, dryRun);
            }
        } else if (getCommandLine().getURLCount() == 2) {
            // merge url1@r url2@r wcPath
            String url1 = getCommandLine().getURL(0);
            SVNRevision rN = getCommandLine().getPegRevision(0);
            if (!rN.isValid()) {
                rN = SVNRevision.HEAD;
            }
            String url2 = getCommandLine().getURL(1);
            SVNRevision rM = getCommandLine().getPegRevision(1);
            if (!rM.isValid()) {
                rM = SVNRevision.HEAD;
            }
            File dstPath = new File(getCommandLine().getPathAt(0));
            differ.doMerge(url1, url2, rN, rM, dstPath, recursive, useAncestry, dryRun);
        } else if (getCommandLine().getPathCount() >= 2){
            // merge wcPath1@r wcPath2@r wcPath
            File path1 = new File(getCommandLine().getPathAt(0));
            SVNRevision rN = getCommandLine().getPathPegRevision(0);
            if (!rN.isValid()) {
                rN = SVNRevision.HEAD;
            }
            File path2 = new File(getCommandLine().getPathAt(1));
            SVNRevision rM = getCommandLine().getPathPegRevision(1);
            if (!rM.isValid()) {
                rM = SVNRevision.HEAD;
            }
            File dstPath = new File(".");
            if (getCommandLine().getPathCount() > 2) {
                dstPath = new File(getCommandLine().getPathAt(2));
            }
            differ.doMerge(path1, path2, rN, rM, dstPath, recursive, useAncestry, dryRun);
        } else {
            println(err, "svn: unsupported merge call format");
        }
	}
}
