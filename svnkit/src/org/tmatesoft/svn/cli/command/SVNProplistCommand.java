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
import org.tmatesoft.svn.core.SVNCancelException;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.util.SVNFormatUtil;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.wc.ISVNEventHandler;
import org.tmatesoft.svn.core.wc.ISVNPropertyHandler;
import org.tmatesoft.svn.core.wc.SVNChangeList;
import org.tmatesoft.svn.core.wc.SVNCompositePathList;
import org.tmatesoft.svn.core.wc.SVNEvent;
import org.tmatesoft.svn.core.wc.SVNPathList;
import org.tmatesoft.svn.core.wc.SVNPropertyData;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNWCClient;

/**
 * @version 1.1.1
 * @author  TMate Software Ltd.
 */
public class SVNProplistCommand extends SVNCommand implements ISVNPropertyHandler, ISVNEventHandler {

    private boolean myIsVerbose;
    private boolean myIsRecursive;
    private PrintStream myOut;
    private boolean myIsRevProp;
    private PrintStream myErrStream;

    public void run(InputStream in, PrintStream out, PrintStream err) throws SVNException {
        run(out, err);
    }

    public final void run(final PrintStream out, PrintStream err) throws SVNException {
        myErrStream = err;

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

        Map targets = new HashMap();
        for (int i = 0; i < getCommandLine().getPathCount(); i++) {
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
        myIsRevProp = getCommandLine().hasArgument(SVNArgument.REV_PROP);
        myIsVerbose = getCommandLine().hasArgument(SVNArgument.VERBOSE);
        myOut = out;
        myIsRecursive = myIsRecursive & !myIsRevProp;
        SVNRevision revision = SVNRevision.UNDEFINED;
        if (getCommandLine().hasArgument(SVNArgument.REVISION)) {
            revision = SVNRevision.parse((String) getCommandLine().getArgumentValue(SVNArgument.REVISION));
        }
        SVNWCClient wcClient = getClientManager().getWCClient();
        if (myIsRevProp) {
        if (getCommandLine().hasURLs()) {
            String url = getCommandLine().getURL(0);
                wcClient.doGetRevisionProperty(SVNURL.parseURIEncoded(url), null, revision, this);
            } else {
                File[] combinedPaths = combinedPathList.getPaths(); 
                File path = combinedPaths[0];
                wcClient.doGetRevisionProperty(path, null, revision, this);
            }
            } else {
            if (getCommandLine().hasURLs()) {
                for (int i = 0; i < getCommandLine().getURLCount(); i++) {
                    String url = getCommandLine().getURL(i);
                    SVNRevision pegRevision = getCommandLine().getPegRevision(i);
                wcClient.doGetProperty(SVNURL.parseURIEncoded(url), null, pegRevision, revision, myIsRecursive, this);
            }
            } else {
                wcClient.doGetProperty(combinedPathList, null, revision, depth, this);
            }
        }
    }

    private File myCurrentFile;
    private SVNURL myCurrentURL;
    private long myCurrentRev = -1;

    public void handleProperty(File path, SVNPropertyData property) throws SVNException {
        if (!path.equals(myCurrentFile)) {
            myOut.println("Properties on '" + SVNFormatUtil.formatPath(path) + "':");
            myCurrentFile = path;
        }
        myOut.print("  ");
        myOut.print(property.getName());
        if (myIsVerbose) {
            myOut.print(" : " + property.getValue());
        }
        myOut.println();
    }

    public void handleProperty(SVNURL url, SVNPropertyData property) throws SVNException {
        if (!url.equals(myCurrentURL)) {
            myOut.println("Properties on '" + url + "':");
            myCurrentURL = url;
        }
        myOut.print("  ");
        myOut.print(property.getName());
        if (myIsVerbose) {
            myOut.print(" : " + property.getValue());
        }
        myOut.println();
    }

    public void handleEvent(SVNEvent event, double progress) throws SVNException {
        if (event.getErrorMessage() != null && event.getErrorMessage().isWarning()) {
            SVNCommand.println(myErrStream, event.getErrorMessage().toString());
        }
    }
    
    public void handleProperty(long revision, SVNPropertyData property) throws SVNException {
        if (myCurrentRev < 0) {
            myCurrentRev = revision;
            myOut.println("Unversioned properties on revision " + revision + ":");
        }
        myOut.print("  ");
        myOut.print(property.getName());
        if (myIsVerbose) {
            myOut.print(" : " + property.getValue());
        }
        myOut.println();
    }
    
    public void checkCancelled() throws SVNCancelException {
    }
}
