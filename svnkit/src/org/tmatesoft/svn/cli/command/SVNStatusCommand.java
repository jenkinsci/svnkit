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
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;

import org.tmatesoft.svn.cli.SVNArgument;
import org.tmatesoft.svn.cli.SVNCommand;
import org.tmatesoft.svn.cli.SVNCommandLine;
import org.tmatesoft.svn.cli.SVNCommandStatusHandler;
import org.tmatesoft.svn.core.SVNCancelException;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.wc.SVNFileType;
import org.tmatesoft.svn.core.wc.ISVNEventHandler;
import org.tmatesoft.svn.core.wc.ISVNPathList;
import org.tmatesoft.svn.core.wc.ISVNStatusHandler;
import org.tmatesoft.svn.core.wc.SVNChangeList;
import org.tmatesoft.svn.core.wc.SVNCompositePathList;
import org.tmatesoft.svn.core.wc.SVNEvent;
import org.tmatesoft.svn.core.wc.SVNEventAction;
import org.tmatesoft.svn.core.wc.SVNPathList;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNStatus;
import org.tmatesoft.svn.core.wc.SVNStatusClient;
import org.tmatesoft.svn.core.wc.SVNWCUtil;
import org.tmatesoft.svn.core.wc.xml.SVNXMLSerializer;
import org.tmatesoft.svn.core.wc.xml.SVNXMLStatusHandler;
import org.tmatesoft.svn.util.ISVNDebugLog;

/**
 * @version 1.1.1
 * @author  TMate Software Ltd.
 */
public class SVNStatusCommand extends SVNCommand implements ISVNStatusHandler, ISVNEventHandler {
    private Map myChangeListToStatuses;
    private ISVNStatusHandler myRealHandler;
    private long myLastRevision;
    private ISVNEventHandler myEventHandler;
    private boolean myIsXML;
    private PrintStream myErrorOutput;
    private boolean myHasErrors;
    private ISVNDebugLog myDebugLog;
    private boolean myIsQuiet;

    public void run(InputStream in, PrintStream out, PrintStream err) throws SVNException {
        run(out, err);
    }

    public void run(PrintStream out, PrintStream err) throws SVNException {
        SVNCommandLine line = getCommandLine();

        String changelistName = (String) line.getArgumentValue(SVNArgument.CHANGELIST); 
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
        
        String paths[] = new String[line.getPathCount()];
        for(int i = 0; i < line.getPathCount(); i++) {
            paths[i] = line.getPathAt(i).trim();
        }
        for(int i = 0; i < paths.length; i++) {
            String path = paths[i];
            File validatedPath = new File(SVNPathUtil.validateFilePath(new File(path).getAbsolutePath()));
            if (SVNFileType.getType(validatedPath) == SVNFileType.DIRECTORY && !SVNWCUtil.isVersionedDirectory(validatedPath) &&
                    !SVNWCUtil.isVersionedDirectory(validatedPath.getParentFile())) {
                err.println("svn: warning: '" + path + "' is not a working copy");
                paths[i] = null;
                continue;
            } else if (SVNFileType.getType(validatedPath) == SVNFileType.DIRECTORY && !SVNWCUtil.isVersionedDirectory(validatedPath) &&
                    "..".equals(path)) { 
                err.println("svn: warning: '" + path + "' is not a working copy");
                paths[i] = null;
                continue;
            } else if ("..".equals(path)) {
                // hack for status test #2!
                paths[i] = "..";
                continue;
            }
            paths[i] = validatedPath.getAbsolutePath();
        }
        
        Collection targets = new LinkedList();
        for (int i = 0; i < paths.length; i++) {
            targets.add(new File(paths[i]));
        }
        if (targets.size() == 0 && (changelist == null || changelist.getPathsCount() == 0) && 
                !getCommandLine().hasURLs()) {
            targets.add(new File(".").getAbsoluteFile());
        }
        File[] files = (File[]) targets.toArray(new File[targets.size()]);
        SVNPathList pathList = SVNPathList.create(files, SVNRevision.UNDEFINED);
        SVNCompositePathList combinedPathList = SVNCompositePathList.create(pathList, changelist, false);
        
        if (combinedPathList == null || combinedPathList.getPathsCount() == 0) {
            return;
        }

        boolean showUpdates = getCommandLine().hasArgument(SVNArgument.SHOW_UPDATES);

        SVNDepth depth = SVNDepth.UNKNOWN;
        if (getCommandLine().hasArgument(SVNArgument.NON_RECURSIVE)) {
            depth = SVNDepth.IMMEDIATES;
        }
        String depthStr = (String) getCommandLine().getArgumentValue(SVNArgument.DEPTH);
        if (depthStr != null) {
            depth = SVNDepth.fromString(depthStr);
        }
        
        boolean reportAll = getCommandLine().hasArgument(SVNArgument.VERBOSE);
        boolean ignored = getCommandLine().hasArgument(SVNArgument.NO_IGNORE);
        myIsQuiet = getCommandLine().hasArgument(SVNArgument.QUIET);
        myIsXML = getCommandLine().hasArgument(SVNArgument.XML);
        myEventHandler = !myIsXML ? new SVNCommandEventProcessor(out, err, false) : null;
        getClientManager().setEventHandler(this);

        SVNStatusClient stClient = getClientManager().getStatusClient();
        myDebugLog = stClient.getDebugLog();
        ISVNStatusHandler handler = new SVNCommandStatusHandler(out, reportAll || showUpdates, reportAll, myIsQuiet, showUpdates);
        SVNXMLSerializer serializer = myIsXML ? new SVNXMLSerializer(out) : null;
        if (myIsXML) {
            handler = new SVNXMLStatusHandler(serializer);
            if (!getCommandLine().hasArgument(SVNArgument.INCREMENTAL)) {
                ((SVNXMLStatusHandler) handler).startDocument();
            }
        }

        myRealHandler = handler;
        myHasErrors = false;
        myChangeListToStatuses = new HashMap();

        PathListWrapper wrappedPathList = new PathListWrapper(combinedPathList, myIsXML ? (SVNXMLStatusHandler) handler : null);
        stClient.doStatus(wrappedPathList, SVNRevision.HEAD, depth, showUpdates, reportAll, ignored, false, this);
        wrappedPathList.maybeCloseOpenedTag();
        
        for (Iterator changelists = myChangeListToStatuses.keySet().iterator(); changelists.hasNext();) {
            String changeListName = (String) changelists.next();
            Collection statuses = (Collection) myChangeListToStatuses.get(changeListName);
            out.println("");
            out.println("--- Changelist '" + changeListName + "':");
            for (Iterator statusesIter = statuses.iterator(); statusesIter.hasNext();) {
                SVNStatus status = (SVNStatus) statusesIter.next();
                handler.handleStatus(status);
              }
              }
        
        if (myIsXML) {
            if (!getCommandLine().hasArgument(SVNArgument.INCREMENTAL)) {
                ((SVNXMLStatusHandler) handler).endDocument();
            }
            try {
                serializer.flush();
            } catch (IOException e) {
            }
        }
        if (myHasErrors) {
            System.exit(1);
        }
    }

    public void handleStatus(SVNStatus status) throws SVNException {
        if (status.getChangelistName() != null) {
            Collection statuses = (Collection) myChangeListToStatuses.get(status.getChangelistName());
            if (statuses == null) {
                statuses = new LinkedList();
                myChangeListToStatuses.put(status.getChangelistName(), statuses);
            }
            statuses.add(status);
            return;
        }
        myRealHandler.handleStatus(status);
    }

    public void handleEvent(SVNEvent event, double progress) throws SVNException {
        if (event.getErrorMessage() != null) {
            if (event.getErrorMessage().getErrorCode() == SVNErrorCode.WC_NOT_DIRECTORY) {
                SVNErrorMessage error = event.getErrorMessage(); 
                error.setType(SVNErrorMessage.TYPE_WARNING);
                if (!myIsQuiet) {
                    myErrorOutput.println(event.getErrorMessage());
                }
            } else {
                myDebugLog.info(event.getErrorMessage().getFullMessage());
                myErrorOutput.println(event.getErrorMessage());
                myHasErrors = true;
            }
        } else if (event.getAction() == SVNEventAction.STATUS_COMPLETED) {
            myLastRevision = event.getRevision();
        }
        if (!myIsXML) {
            myEventHandler.handleEvent(event, progress);
        }
    }

    public void checkCancelled() throws SVNCancelException {
    }

    private class PathListWrapper implements ISVNPathList, Iterator {
        private SVNCompositePathList myRealPathList;
        private SVNXMLStatusHandler myXMLHandler;
        private boolean hasOpenedTargetTag;
        
        public PathListWrapper(SVNCompositePathList pathList, SVNXMLStatusHandler xmlHandler) {
            myRealPathList = pathList;
            myXMLHandler = xmlHandler;
        }
        
        public File[] getPaths() throws SVNException {
            return myRealPathList.getPaths();
        }

        public int getPathsCount() throws SVNException {
            return myRealPathList.getPathsCount();
        }

        public Iterator getPathsIterator() throws SVNException {
            return myRealPathList.getPathsIterator();
        }

        public SVNRevision getPegRevision(File path) {
            return myRealPathList.getPegRevision(path);
        }

        public SVNRevision getPegRevision() {
            return myRealPathList.getPegRevision();
        }

        public boolean hasNext() {
            return myRealPathList.hasNext();
        }

        public Object next() {
            if (myXMLHandler != null && hasOpenedTargetTag) {
                myXMLHandler.endTarget(myLastRevision);
                hasOpenedTargetTag = false;
    }
            
            File path = (File) myRealPathList.next();

            if (myXMLHandler != null && !hasOpenedTargetTag) {
                myXMLHandler.startTarget(path);
                hasOpenedTargetTag = true;
            }
            return path;
        }

        public void remove() {
            myRealPathList.remove();
        }
        
        public void maybeCloseOpenedTag() {
            if (myXMLHandler != null && hasOpenedTargetTag) {
                myXMLHandler.endTarget(myLastRevision);
                hasOpenedTargetTag = false;
            }
        }
        
    }
    
}
