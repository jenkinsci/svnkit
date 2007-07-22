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
import java.util.Collection;
import java.util.LinkedList;

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
public class SVNPropdelCommand extends SVNCommand implements ISVNEventHandler {

    private boolean myIsQuiet;
    private PrintStream myErrStream;
    private boolean myIsRecursive;

    public void run(InputStream in, PrintStream out, PrintStream err) throws SVNException {
        run(out, err);
    }

    public final void run(final PrintStream out, PrintStream err) throws SVNException {
        myErrStream = err;
        
        final String propertyName = getCommandLine().getPathAt(0);
        
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
        Collection targets = new LinkedList();
        for (int i = 1; i < getCommandLine().getPathCount(); i++) {
            targets.add(new File(getCommandLine().getPathAt(i)).getAbsoluteFile());
        }
        if (targets.size() == 0 && (changelist == null || changelist.getPathsCount() == 0) && 
                !getCommandLine().hasURLs()) {
            targets.add(new File(".").getAbsoluteFile());
        }
        File[] paths = (File[]) targets.toArray(new File[targets.size()]);
        SVNPathList pathList = SVNPathList.create(paths, SVNRevision.UNDEFINED);
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
        boolean revProp = getCommandLine().hasArgument(SVNArgument.REV_PROP);
        boolean force = getCommandLine().hasArgument(SVNArgument.FORCE);
        myIsQuiet = getCommandLine().hasArgument(SVNArgument.QUIET);

        SVNRevision revision = SVNRevision.UNDEFINED;
        if (getCommandLine().hasArgument(SVNArgument.REVISION)) {
            revision = SVNRevision.parse((String) getCommandLine().getArgumentValue(SVNArgument.REVISION));
        }

        getClientManager().setEventHandler(this);
        SVNWCClient wcClient = getClientManager().getWCClient();

        if (revProp) {
            if (getCommandLine().hasURLs()) {
                wcClient.doSetRevisionProperty(SVNURL.parseURIEncoded(getCommandLine().getURL(0)),
                        revision, propertyName, null, force, new PropertyHandler(propertyName, out));
            } else {
                File[] combinedPaths = combinedPathList.getPaths(); 
                File tgt = combinedPaths[0];
                wcClient.doSetRevisionProperty(tgt, revision, propertyName, null, force, new PropertyHandler(propertyName, out));
            }
        } else if (revision != null && revision != SVNRevision.UNDEFINED) {
            SVNErrorMessage error = SVNErrorMessage.create(SVNErrorCode.CL_ARG_PARSING_ERROR, "Cannot specify revision for deleting versioned property ''{0}''", propertyName);
            SVNErrorManager.error(error);
        } else {
            PropertyHandler handler = new PropertyHandler(propertyName, out);
            wcClient.doSetProperty(combinedPathList, propertyName, null, force, myIsRecursive, handler);
            handler.handlePendingFile();
        }
    }

    public void handleEvent(SVNEvent event, double progress) throws SVNException {
        if (event.getErrorMessage() != null && event.getErrorMessage().isWarning()) {
            SVNCommand.println(myErrStream, event.getErrorMessage().toString());
        }
    }
    
    public void checkCancelled() throws SVNCancelException {
    }
    
    private class PropertyHandler implements ISVNPropertyHandler {
        private File myCurrentFile;
        private PrintStream myOutput;
        private String myPropertyName;
        
        public PropertyHandler(String propName, PrintStream out) {
            myOutput = out;
            myPropertyName = propName;
        }
        
        public void handleProperty(File path, SVNPropertyData property) throws SVNException {
            if (!myIsQuiet) {
                if (myIsRecursive) {
                    if (myCurrentFile != null) {
                        String rootPath = myCurrentFile.getAbsolutePath();
                        if (path.getAbsolutePath().indexOf(rootPath) == -1) {
                            myOutput.println("property '" + property.getName() + "' deleted (recursively) from '" + SVNFormatUtil.formatPath(myCurrentFile) + "'.");
                            myCurrentFile = path;
                        }
                    } else {
                        myCurrentFile = path;
                    }
                } else {
                    myOutput.println("property '" + property.getName() + "' deleted from '" + SVNFormatUtil.formatPath(path) + "'.");
                }
            }
        }

        public void handleProperty(long revision, SVNPropertyData property) throws SVNException {
            if (!myIsQuiet) {
                myOutput.println("property '" + property.getName() +"' deleted from repository revision " + revision);
            }
        }
        
        public void handlePendingFile() {
            if (!myIsQuiet) {
                if (myIsRecursive && myCurrentFile != null) {
                    myOutput.println("property '" + myPropertyName + "' deleted from '" + SVNFormatUtil.formatPath(myCurrentFile) + "'.");
                    myCurrentFile = null;
                }
            }
        }

        public void handleProperty(SVNURL url, SVNPropertyData property) throws SVNException {
        }
        
    }    
}
