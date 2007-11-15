/*
 * ====================================================================
 * Copyright (c) 2004-2007 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html.
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.cli2.svn;

import java.text.MessageFormat;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNMergeRange;
import org.tmatesoft.svn.core.SVNMergeRangeList;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.internal.wc.SVNPath;
import org.tmatesoft.svn.core.wc.SVNDiffClient;
import org.tmatesoft.svn.core.wc.SVNRevision;


/**
 * @version 1.1.2
 * @author  TMate Software Ltd.
 */
public class SVNMergeInfoCommand extends SVNCommand {

    public SVNMergeInfoCommand() {
        super("mergeinfo", null);
    }
    
    protected Collection createSupportedOptions() {
        Collection options = new LinkedList();
        options.add(SVNOption.REVISION);
        options = SVNOption.addAuthOptions(options);
        options.add(SVNOption.CONFIG_DIR);
        return options;
    }

    public void run() throws SVNException {
        List targets = getSVNEnvironment().combineTargets(null);
        if (targets.isEmpty()) {
            targets.add("");
        }

        SVNDiffClient client = getSVNEnvironment().getClientManager().getDiffClient();
        for(int i = 0; i < targets.size(); i++) {
            SVNPath target = new SVNPath((String) targets.get(i), true);
            SVNRevision pegRevision = target.getPegRevision();
            if (pegRevision == SVNRevision.UNDEFINED) {
                if (target.isURL()) {
                    pegRevision = SVNRevision.HEAD;
                } else {
                    pegRevision = SVNRevision.BASE;
                }
            }
            
            String message = "Path: {0}";
            Map urlsToRangeLists = null;
            if (target.isFile()) {
                urlsToRangeLists = client.getMergeInfo(target.getFile(), pegRevision);
                message = MessageFormat.format(message, new Object[] { target.getFile() });
            } else {
                urlsToRangeLists = client.getMergeInfo(target.getURL(), pegRevision);
                message = MessageFormat.format(message, new Object[] { target.getURL() });
            }
            
            getSVNEnvironment().getOut().println(message);
            if (urlsToRangeLists == null) {
                getSVNEnvironment().getOut().println();
                continue;
            }
            
            SVNURL rootURL = null;
            if (target.isFile()) {
                rootURL = client.getReposRoot(target.getFile(), null, SVNRevision.BASE, null, null); 
            } else {
                rootURL = client.getReposRoot(null, target.getURL(), SVNRevision.HEAD, null, null);
            }
            
            String rootPath = rootURL.getPath();
            for (Iterator entries = urlsToRangeLists.entrySet().iterator(); entries.hasNext();) {
                Map.Entry urlToRangeList = (Map.Entry) entries.next();
                SVNURL mergeSrcURL = (SVNURL) urlToRangeList.getKey();
                SVNMergeRangeList rangeList = (SVNMergeRangeList) urlToRangeList.getValue();
                String fullPath = mergeSrcURL.getPath();
                String path = SVNPathUtil.getPathAsChild(rootPath, fullPath);
                if (path != null) {
                    path = "/" + path;
                } else {
                    path = "/";
                }
                message = MessageFormat.format("  Source path: {0}", new Object[] { path });
                getSVNEnvironment().getOut().println(message);
                getSVNEnvironment().getOut().print("    Merged ranges: ");
                printMergeRanges(rangeList);
                getSVNEnvironment().getOut().print("    Eligible ranges: ");
                try {
                    if (target.isURL()) {
                        rangeList = client.getAvailableMergeInfo(target.getURL(), pegRevision, mergeSrcURL); 
                    } else {
                        rangeList = client.getAvailableMergeInfo(target.getFile(), pegRevision, mergeSrcURL);
                    }
                    printMergeRanges(rangeList);
                } catch (SVNException svne) {
                    SVNErrorCode errCode = svne.getErrorMessage().getErrorCode();
                    if (errCode == SVNErrorCode.FS_NOT_FOUND || errCode == SVNErrorCode.RA_DAV_PATH_NOT_FOUND) {
                        getSVNEnvironment().getOut().println("(source no longer available in HEAD)");
                    } else {
                        throw svne;
                    }
                }
            }
            getSVNEnvironment().getOut().println();
        }
    }
    
    private void printMergeRanges(SVNMergeRangeList rangeList) {
        SVNMergeRange[] ranges = rangeList.getRanges();
        for (int i = 0; i < ranges.length; i++) {
            SVNMergeRange range = ranges[i];
            String message = MessageFormat.format("r{0,number,integer}:{1,number,integer}{2}", new Object[] { 
                    new Long(range.getStartRevision()), new Long(range.getEndRevision()), 
                            i == ranges.length - 1 ? "" : ", " });
            getSVNEnvironment().getOut().print(message);
        }
        getSVNEnvironment().getOut().println();
    }

}
