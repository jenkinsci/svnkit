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
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.wc.DefaultSVNDiffGenerator;
import org.tmatesoft.svn.core.wc.SVNDiffClient;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.util.DebugLog;
import org.tmatesoft.svn.util.PathUtil;
import org.tmatesoft.svn.util.SVNUtil;

/**
 * @author TMate Software Ltd.
 */
public class DiffCommand extends SVNCommand {

    public void run(final PrintStream out, PrintStream err) throws SVNException {
        boolean error = false;
        SVNDiffClient differ = getClientManager().getDiffClient();
        differ.setDiffGenerator(new DefaultSVNDiffGenerator() {
            public String getDisplayPath(File file) {
                return SVNUtil.getPath(file).replace(File.separatorChar, '/');
            }
            public void displayFileDiff(String path, File file1, File file2,
                                        String rev1, String rev2, String mimeType1, String mimeType2,
                                        OutputStream result) throws SVNException {
                super.displayFileDiff(path, file1, file2, rev1, rev2, mimeType1, mimeType2, result);
            }
            public void displayPropDiff(String path, Map baseProps, Map diff, OutputStream result) throws SVNException {
                super.displayPropDiff(path.replace('/', File.separatorChar), baseProps, diff, result);
            }
        });

        boolean useAncestry = getCommandLine().hasArgument(SVNArgument.USE_ANCESTRY);
        boolean recursive = !getCommandLine().hasArgument(SVNArgument.NON_RECURSIVE);
        differ.getDiffGenerator().setDiffDeleted(!getCommandLine().hasArgument(SVNArgument.NO_DIFF_DELETED));
        differ.getDiffGenerator().setForcedBinaryDiff(getCommandLine().hasArgument(SVNArgument.FORCE));

        if (getCommandLine().getURLCount() == 2 && !getCommandLine().hasPaths()) {
            // diff url1[@r] url2[@r]
            String url1 = getCommandLine().getURL(0);
            String url2 = getCommandLine().getURL(1);
            SVNRevision peg1 = getCommandLine().getPegRevision(0);
            SVNRevision peg2 = getCommandLine().getPegRevision(1);
            if (peg1 == SVNRevision.UNDEFINED) {
                peg1 = SVNRevision.HEAD;
            }
            if (peg2 == SVNRevision.UNDEFINED) {
                peg2 = SVNRevision.HEAD;
            }

            differ.doDiff(url1, peg1, url2, peg2, peg1, peg2, recursive, useAncestry, out);
        } else {
            SVNRevision rN = SVNRevision.UNDEFINED;
            SVNRevision rM = SVNRevision.UNDEFINED;
            String revStr = (String) getCommandLine().getArgumentValue(SVNArgument.REVISION);
            if (revStr != null && revStr.indexOf(':') > 0) {
                rN = SVNRevision.parse(revStr.substring(0, revStr.indexOf(':')));
                rM = SVNRevision.parse(revStr.substring(revStr.indexOf(':') + 1));
            } else if (revStr != null) {
                rN = SVNRevision.parse(revStr);
            }
            if (getCommandLine().hasArgument(SVNArgument.OLD)) {
                // diff [-rN[:M]] --old=url[@r] [--new=url[@r]] [path...]
                String oldPath = (String) getCommandLine().getArgumentValue(SVNArgument.OLD);
                String newPath = (String) getCommandLine().getArgumentValue(SVNArgument.NEW);
                if (newPath == null) {
                    newPath = oldPath;
                }
                if (oldPath.startsWith("=")) {
                    oldPath = oldPath.substring(1);
                }
                if (newPath.startsWith("=")) {
                    newPath = newPath.substring(1);
                }
                SVNRevision peg1 = SVNRevision.UNDEFINED;
                SVNRevision peg2 = SVNRevision.UNDEFINED;
                if (oldPath.indexOf('@') > 0) {
                    peg1 = SVNRevision.parse(oldPath.substring(oldPath.lastIndexOf('@') + 1));
                    oldPath = oldPath.substring(0, oldPath.lastIndexOf('@'));
                }
                if (newPath.indexOf('@') > 0) {
                    peg2 = SVNRevision.parse(newPath.substring(newPath.lastIndexOf('@') + 1));
                    newPath = newPath.substring(0, newPath.lastIndexOf('@'));
                }
                if (getCommandLine().getPathCount() == 0) {
                    getCommandLine().setPathAt(0, "");
                }
                DebugLog.log("--old: " + oldPath);
                DebugLog.log("--new: " + newPath);
                for (int i = 0; i < getCommandLine().getPathCount(); i++) {
                    String p = getCommandLine().getPathAt(i);
                    p = p.replace(File.separatorChar, '/');
                    DebugLog.log("--path: " + p);
                    if (".".equals(p)) {
                        p = "";
                    }
                    String oP = PathUtil.append(oldPath, p);
                    String nP = PathUtil.append(newPath, p);
                    try {
                        if (!getCommandLine().isURL(oP) && getCommandLine().isURL(nP)) {
                            differ.doDiff(new File(oP).getAbsoluteFile(), nP, peg2, rN, rM, recursive, useAncestry, out);
                        } else if (getCommandLine().isURL(oP) && !getCommandLine().isURL(nP)) {
                            differ.doDiff(oP, peg1, new File(nP).getAbsoluteFile(), rN, rM, recursive, useAncestry, out);
                        } else if (getCommandLine().isURL(oP) && getCommandLine().isURL(nP)) {
                            differ.doDiff(oP, peg1, nP, peg2, rN, rM, recursive, useAncestry, out);
                        } else {
                            differ.doDiff(new File(oP).getAbsoluteFile(), new File(nP).getAbsoluteFile(), rN, rM, recursive, useAncestry, out);
                        }
                    } catch (SVNException e) {
                        DebugLog.error(e);
                        DebugLog.log(e.getMessage());
                        error = true;
                        println(err, e.getMessage());
                    }
                }
            } else {
                // diff [-rN[:M]] target[@r] [...]
                for(int i = 0; i < getCommandLine().getPathCount(); i++) {
                    String path = getCommandLine().getPathAt(i);
                    try {
                        differ.doDiff(new File(path).getAbsoluteFile(), rN, rM, recursive, useAncestry, out);
                    } catch (SVNException e) {
                        DebugLog.log("exception caught: " + e.getMessage());
                        error = true;
                        println(err, e.getMessage());
                    }
                }
                for(int i = 0; i < getCommandLine().getURLCount(); i++) {
                    String url = getCommandLine().getURL(i);
                    SVNRevision peg = getCommandLine().getPegRevision(i);
                    try {
                        differ.doDiff(url, peg, url, peg, rN , rM, recursive, useAncestry, out);
                    } catch (SVNException e) {
                        error = true;
                        println(err, e.getMessage());
                    }
                }
            }
        }
        if (error) {
            System.exit(1);
        }
    }
}
