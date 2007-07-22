/*
 * ====================================================================
 * Copyright (c) 2004-2007 TMate Software Ltd.  All rights reserved.
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
import java.util.HashMap;
import java.util.Map;

import org.tmatesoft.svn.cli.SVNArgument;
import org.tmatesoft.svn.cli.SVNCommand;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.util.SVNFormatUtil;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.wc.ISVNPropertyHandler;
import org.tmatesoft.svn.core.wc.SVNChangeList;
import org.tmatesoft.svn.core.wc.SVNCompositePathList;
import org.tmatesoft.svn.core.wc.SVNPathList;
import org.tmatesoft.svn.core.wc.SVNPropertyData;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNWCClient;

/**
 * @version 1.1.1
 * @author  TMate Software Ltd.
 */
public class SVNPropgetCommand extends SVNCommand implements ISVNPropertyHandler {

    private boolean myIsStrict;
    private boolean myIsRecursive;
    private PrintStream myOut;

    public void run(InputStream in, PrintStream out, PrintStream err) throws SVNException {
        run(out, err);
    }

    public final void run(final PrintStream out, PrintStream err) throws SVNException {
        String propertyName = getCommandLine().getPathAt(0);
        
        String changelistName = (String) getCommandLine().getArgumentValue(SVNArgument.CHANGELIST); 
        SVNChangeList changelist = null;
        if (changelistName != null) {
            changelist = SVNChangeList.create(changelistName, new File(".").getAbsoluteFile());
            changelist.setOptions(getClientManager().getOptions());
            changelist.setRepositoryPool(getClientManager().getRepositoryPool());
            if (changelist.getPaths() == null || changelist.getPathsCount() == 0) {
                SVNErrorMessage error = SVNErrorMessage.create(SVNErrorCode.CL_ARG_PARSING_ERROR, 
                                    "no such changelist ''{0}''", changelistName); 
                SVNErrorManager.error(error);
            }
        }
        
        SVNDepth depth = SVNDepth.UNKNOWN;
        if (getCommandLine().hasArgument(SVNArgument.RECURSIVE)) {
            depth = SVNDepth.fromRecurse(true);
        }
        String depthStr = (String) getCommandLine().getArgumentValue(SVNArgument.DEPTH);
        if (depthStr != null) {
            depth = SVNDepth.fromString(depthStr);
        }
        if (depth == SVNDepth.UNKNOWN) {
            depth = SVNDepth.EMPTY;
        }
        
        myIsRecursive = SVNDepth.recurseFromDepth(depth);
        boolean revProp = getCommandLine().hasArgument(SVNArgument.REV_PROP);
        myIsStrict = getCommandLine().hasArgument(SVNArgument.STRICT);
        myOut = out;
        myIsRecursive = myIsRecursive & !revProp;
        SVNRevision revision = SVNRevision.UNDEFINED;
        if (getCommandLine().hasArgument(SVNArgument.REVISION)) {
            revision = SVNRevision.parse((String) getCommandLine().getArgumentValue(SVNArgument.REVISION));
        }
        
        Map targets = new HashMap();
        for (int i = 1; i < getCommandLine().getPathCount(); i++) {
            targets.put(new File(getCommandLine().getPathAt(i)).getAbsoluteFile(), getCommandLine().getPathPegRevision(i));
        }
        if (targets.size() == 0 && (changelist == null || changelist.getPathsCount() == 0) && 
                !getCommandLine().hasURLs()) {
            targets.put(new File(".").getAbsoluteFile(), SVNRevision.UNDEFINED);
        }
        File[] paths = (File[]) targets.keySet().toArray(new File[targets.size()]);
        SVNRevision[] pegRevs = (SVNRevision[]) targets.values().toArray(new SVNRevision[targets.size()]);
        SVNPathList pathList = SVNPathList.create(paths, pegRevs);
        SVNCompositePathList combinedPathList = SVNCompositePathList.create(pathList, changelist, false);
        
        SVNWCClient wcClient = getClientManager().getWCClient();
        if (revProp) {
            if (getCommandLine().hasURLs()) {
                String url = getCommandLine().getURL(0);
                wcClient.doGetRevisionProperty(SVNURL.parseURIEncoded(url), propertyName, revision, this);
            } else {
                File[] combinedPaths = combinedPathList.getPaths(); 
                File path = combinedPaths[0];
                wcClient.doGetRevisionProperty(path, propertyName, revision, this);
            }
        } else {
            if (getCommandLine().hasURLs()) {
                for (int i = 0; i < getCommandLine().getURLCount(); i++) {
                    String url = getCommandLine().getURL(i);
                    SVNRevision pegRevision = getCommandLine().getPegRevision(i);
                    wcClient.doGetProperty(SVNURL.parseURIEncoded(url), propertyName, pegRevision, revision, myIsRecursive, this);
                }
            } else {
                wcClient.doGetProperty(combinedPathList, propertyName, revision, depth, this);
            }
        }
    }

    public void handleProperty(File path, SVNPropertyData property) throws SVNException {
        if (!myIsStrict && myIsRecursive) {
            myOut.print(SVNFormatUtil.formatPath(path) + " - ");
        }
        myOut.print(property.getValue());
        if (!myIsStrict) {
            myOut.println();
        }
    }

    public void handleProperty(SVNURL url, SVNPropertyData property) throws SVNException {
        if (!myIsStrict && myIsRecursive) {
            myOut.print(url + " - ");
        }
        myOut.print(property.getValue());
        if (!myIsStrict) {
            myOut.println();
        }
    }
    
    public void handleProperty(long revision, SVNPropertyData property) throws SVNException {
        myOut.print(property.getValue());
        if (!myIsStrict) {
            myOut.println();
        }
    }
    
}
