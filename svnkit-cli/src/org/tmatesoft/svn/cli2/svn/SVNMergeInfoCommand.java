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
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.tmatesoft.svn.cli2.SVNCommandUtil;
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
        options.add(SVNOption.FROM_SOURCE);
        return options;
    }

    public void run() throws SVNException {
        List targets = getSVNEnvironment().combineTargets(null, true);
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
            Map mergeInfo = null;
            if (target.isFile()) {
                mergeInfo = client.getMergedMergeInfo(target.getFile(), pegRevision);
                String path = getSVNEnvironment().getRelativePath(target.getFile());
                path = SVNCommandUtil.getLocalPath(path);
                message = MessageFormat.format(message, new Object[] { path });
            } else {
                mergeInfo = client.getMergedMergeInfo(target.getURL(), pegRevision);
                message = MessageFormat.format(message, new Object[] { target.getURL() });
            }
            
            getSVNEnvironment().getOut().println(message);
            if (mergeInfo == null) {
                mergeInfo = new HashMap();
            }
            
            SVNURL rootURL = null;
            if (target.isFile()) {
                rootURL = client.getReposRoot(target.getFile(), null, SVNRevision.BASE, null, null); 
            } else {
                rootURL = client.getReposRoot(null, target.getURL(), SVNRevision.HEAD, null, null);
            }
            
            String fromSource = getSVNEnvironment().getFromSource();
            if (fromSource != null) {
                SVNURL mergeSourceURL = SVNURL.parseURIEncoded(fromSource);
            	SVNMergeRangeList mergedRanges = (SVNMergeRangeList) mergeInfo.get(mergeSourceURL);
            	if (mergedRanges == null) {
            		mergedRanges = new SVNMergeRangeList(new SVNMergeRange[0]);
            	}
            	showMergeInfoForSource(mergeSourceURL, mergedRanges, target, pegRevision, rootURL, client);
            } else if (!mergeInfo.isEmpty()) {
                for (Iterator entries = mergeInfo.entrySet().iterator(); entries.hasNext();) {
                    Map.Entry urlToRangeList = (Map.Entry) entries.next();
                    SVNURL mergeSrcURL = (SVNURL) urlToRangeList.getKey();
                    SVNMergeRangeList rangeList = (SVNMergeRangeList) urlToRangeList.getValue();
                    showMergeInfoForSource(mergeSrcURL, rangeList, target, pegRevision, rootURL, client);
                }
            }
        }
    }
    
    private void printMergeRanges(SVNMergeRangeList rangeList) {
        SVNMergeRange[] ranges = rangeList.getRanges();
        for (int i = 0; i < ranges.length; i++) {
            SVNMergeRange range = ranges[i];
            String message = MessageFormat.format("r{0}:{1}{2}", new Object[] { 
                    new Long(range.getStartRevision()), new Long(range.getEndRevision()), 
                            i == ranges.length - 1 ? "" : ", " });
            getSVNEnvironment().getOut().print(message);
        }
        getSVNEnvironment().getOut().println();
    }

    private void showMergeInfoForSource(SVNURL mergeSourceURL, SVNMergeRangeList mergeRanges, SVNPath path, 
    		SVNRevision pegRevision, SVNURL rootURL, SVNDiffClient client) throws SVNException {
    	String mergeSourceString = mergeSourceURL.toDecodedString();
    	String rootURLString = rootURL.toDecodedString();
        String relPath = SVNPathUtil.getPathAsChild(rootURLString, mergeSourceString);
        if (relPath != null) {
            relPath = "/" + relPath;
        } else {
            relPath = "/";
        }

    	String message = MessageFormat.format("  Source path: {0}", new Object[] { relPath });
        getSVNEnvironment().getOut().println(message);
        getSVNEnvironment().getOut().print("    Merged ranges: ");
        printMergeRanges(mergeRanges);
        getSVNEnvironment().getOut().print("    Eligible ranges: ");
        SVNMergeRangeList availableRanges = null;
        try {
            if (path.isURL()) {
                availableRanges = client.getAvailableMergeInfo(path.getURL(), pegRevision, mergeSourceURL); 
            } else {
                availableRanges = client.getAvailableMergeInfo(path.getFile(), pegRevision, mergeSourceURL);
            }
            printMergeRanges(availableRanges);
        } catch (SVNException svne) {
            SVNErrorCode errCode = svne.getErrorMessage().getErrorCode();
            if (errCode == SVNErrorCode.FS_NOT_FOUND || errCode == SVNErrorCode.RA_DAV_PATH_NOT_FOUND) {
                getSVNEnvironment().getOut().println("(source no longer available in HEAD)");
            } else {
            	getSVNEnvironment().getOut().println();
            	throw svne;
            }
        }

    }
}
