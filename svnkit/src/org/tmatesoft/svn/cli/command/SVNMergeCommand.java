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
import org.tmatesoft.svn.core.wc.SVNDiffClient;
import org.tmatesoft.svn.core.wc.SVNDiffOptions;
import org.tmatesoft.svn.core.wc.SVNRevision;

/**
 * @version 1.1.0
 * @author  TMate Software Ltd.
 */
public class SVNMergeCommand extends SVNCommand {

    public void run(InputStream in, PrintStream out, PrintStream err) throws SVNException {
        run(out, err);
    }

	public void run(final PrintStream out, PrintStream err) throws SVNException {
        boolean useAncestry = !getCommandLine().hasArgument(SVNArgument.IGNORE_ANCESTRY);
        boolean recursive = !getCommandLine().hasArgument(SVNArgument.NON_RECURSIVE);
        boolean force = getCommandLine().hasArgument(SVNArgument.FORCE);
        boolean dryRun = getCommandLine().hasArgument(SVNArgument.DRY_RUN);

        getClientManager().setEventHandler(new SVNCommandEventProcessor(out, err, false, false));
        SVNDiffClient differ = getClientManager().getDiffClient();
        
        if (getCommandLine().hasArgument(SVNArgument.EXTENSIONS)) {
            SVNDiffOptions diffOptions = new SVNDiffOptions(getCommandLine().hasArgument(SVNArgument.IGNORE_ALL_WS),
                    getCommandLine().hasArgument(SVNArgument.IGNORE_WS_CHANGE), 
                    getCommandLine().hasArgument(SVNArgument.IGNORE_EOL_STYLE));
            differ.setMergeOptions(diffOptions);
        }

        
        if (getCommandLine().hasArgument(SVNArgument.REVISION) || getCommandLine().hasArgument(SVNArgument.CHANGE)) {
            // merge -rN:M urlOrPath@r wcPath
            SVNRevision rN = SVNRevision.UNDEFINED;
            SVNRevision rM = SVNRevision.UNDEFINED;
            String revStr = (String) getCommandLine().getArgumentValue(SVNArgument.REVISION);
            if (revStr == null && getCommandLine().hasArgument(SVNArgument.CHANGE)) {
                long changeRev = Long.parseLong((String) getCommandLine().getArgumentValue(SVNArgument.CHANGE));
                if (changeRev >= 0) {
                    rM = SVNRevision.create(changeRev);
                    rN = SVNRevision.create(changeRev - 1);
                } else {
                    rN = SVNRevision.create(-changeRev);
                    rM = SVNRevision.create((-changeRev) - 1);
                }
            }else if (revStr.indexOf(':') <= 0) {
                println(err, "svn: merge needs both source and target revisions to be specified");
                return;
            } else {
                rN = SVNRevision.parse(revStr.substring(0, revStr.indexOf(':')));
                rM = SVNRevision.parse(revStr.substring(revStr.indexOf(':') + 1));
            }
            if (!rN.isValid() || !rM.isValid() || rN == SVNRevision.WORKING || rM == SVNRevision.WORKING) {
                println(err, "svn: merge needs both source and target revisions to be specified");
                return;
            }
            if (getCommandLine().hasURLs()) {
                String url = getCommandLine().getURL(0);
                SVNRevision pegRev = getCommandLine().getPegRevision(0);
                File dstPath = null; // try to get path from urls.
                if (getCommandLine().getPathCount() > 0) {
                    dstPath = new File(getCommandLine().getPathAt(0));
                } 
                if (dstPath == null) {
                    dstPath = new File(SVNEncodingUtil.uriDecode(SVNPathUtil.tail(url)));
                    if (!dstPath.isFile()) {
                        dstPath = new File(".");
                    }
                }
                if (pegRev == SVNRevision.UNDEFINED) {
                    pegRev = SVNRevision.HEAD;
                }
                SVNURL svnURL = SVNURL.parseURIEncoded(url);
                differ.doMerge(svnURL, pegRev, rN, rM, dstPath, recursive, useAncestry, force, dryRun);
            } else if (getCommandLine().hasPaths()){
                File srcPath = new File(getCommandLine().getPathAt(0));
                SVNRevision pegRevision = getCommandLine().getPathPegRevision(0);
                if (pegRevision == SVNRevision.UNDEFINED) {
                    pegRevision = SVNRevision.HEAD;
                }                
                File dstPath = srcPath;
                if (getCommandLine().getPathCount() > 1) {
                    dstPath = new File(getCommandLine().getPathAt(1));
                } 
                differ.doMerge(srcPath, pegRevision, rN, rM, dstPath, recursive, useAncestry, force, dryRun);
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
            File dstPath = null;
            if (getCommandLine().getPathCount() > 0) {
                dstPath = new File(getCommandLine().getPathAt(0));
            }
            if (dstPath == null) {
                String c1 = SVNPathUtil.tail(url1);
                String c2 = SVNPathUtil.tail(url2);
                if (c1.equals(c2)) {
                    dstPath = new File(SVNEncodingUtil.uriDecode(c1));
                    if (!dstPath.isFile()) {
                        dstPath = new File(".");
                    }
                }
            }
            SVNURL svnURL1 = SVNURL.parseURIEncoded(url1);
            SVNURL svnURL2 = SVNURL.parseURIEncoded(url2);

            differ.doMerge(svnURL1, rN, svnURL2, rM, dstPath, recursive, useAncestry, force, dryRun);
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
            differ.doMerge(path1, rN, path2, rM, dstPath, recursive, useAncestry, force, dryRun);
        } else {
            println(err, "svn: unsupported merge call format");
        }
	}
}
