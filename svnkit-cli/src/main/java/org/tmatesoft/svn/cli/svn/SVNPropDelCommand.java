/*
 * ====================================================================
 * Copyright (c) 2004-2012 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html.
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.cli.svn;

import java.io.File;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;

import org.tmatesoft.svn.cli.SVNCommandUtil;
import org.tmatesoft.svn.core.SVNCancelException;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.wc.SVNPath;
import org.tmatesoft.svn.core.wc.ISVNEventHandler;
import org.tmatesoft.svn.core.wc.SVNEvent;
import org.tmatesoft.svn.core.wc.SVNEventAction;
import org.tmatesoft.svn.core.wc.SVNPropertyData;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNWCClient;
import org.tmatesoft.svn.util.SVNLogType;


/**
 * @version 1.3
 * @author  TMate Software Ltd.
 */
public class SVNPropDelCommand extends SVNPropertiesCommand {

    public SVNPropDelCommand() {
        super("propdel", new String[] {"pdel", "pd"});
    }

    protected Collection createSupportedOptions() {
        Collection options = new LinkedList();
        options.add(SVNOption.QUIET);
        options.add(SVNOption.RECURSIVE);
        options.add(SVNOption.DEPTH);
        options.add(SVNOption.REVISION);
        options.add(SVNOption.REVPROP);
        options.add(SVNOption.CHANGELIST);
        return options;
    }

    public void run() throws SVNException {
        String propertyName = getSVNEnvironment().popArgument();
        if (propertyName == null) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CL_INSUFFICIENT_ARGS);
            SVNErrorManager.error(err, SVNLogType.CLIENT);
        }

        Collection targets = new ArrayList(); 
        if (getSVNEnvironment().getTargets() != null) {
            targets.addAll(getSVNEnvironment().getTargets());
        }
        targets = getSVNEnvironment().combineTargets(targets, true);
        if (targets.isEmpty()) {
            targets.add("");
        }
        
        if (getSVNEnvironment().isRevprop()) {            
            String target = checkRevPropTarget(getSVNEnvironment().getStartRevision(), targets);
            if (SVNCommandUtil.isURL(target)) {
                SVNURL url = SVNURL.parseURIEncoded(target);
                getSVNEnvironment().getClientManager().getWCClient().doSetRevisionProperty(url, getSVNEnvironment().getStartRevision(), propertyName, null, getSVNEnvironment().isForce(), this);
            } else {
                File targetFile = new SVNPath(target).getFile();
                getSVNEnvironment().getClientManager().getWCClient().doSetRevisionProperty(targetFile, getSVNEnvironment().getStartRevision(), propertyName, null, getSVNEnvironment().isForce(), this);
            }
        } else if (getSVNEnvironment().getStartRevision() != SVNRevision.UNDEFINED) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CL_ARG_PARSING_ERROR, 
                    "Cannot specify revision for deleting versioned property ''{0}''", propertyName);
            SVNErrorManager.error(err, SVNLogType.CLIENT);
        } else {
            SVNDepth depth = getSVNEnvironment().getDepth();
            if (depth == SVNDepth.UNKNOWN) {
                depth = SVNDepth.EMPTY;
            }

            Collection changeLists = getSVNEnvironment().getChangelistsCollection();
            SVNWCClient client = getSVNEnvironment().getClientManager().getWCClient();
            final boolean[] deletedNonExistent = new boolean[] {false}; 
            client.setEventHandler(new ISVNEventHandler() {
                public void handleEvent(SVNEvent event, double progress) throws SVNException {
                    if (event.getAction() == SVNEventAction.PROPERTY_DELETE_NONEXISTENT) {
                        deletedNonExistent[0] = true;
                    }
                }
                public void checkCancelled() throws SVNCancelException {
                    getSVNEnvironment().checkCancelled();
                }
            });
            for (Iterator ts = targets.iterator(); ts.hasNext();) {
                String targetName = (String) ts.next();
                SVNPath target = new SVNPath(targetName);
                if (target.isFile()) {
                    boolean success = true;
                    try {
                        if (target.isFile()){
                            client.doSetProperty(target.getFile(), propertyName, null, 
                                    getSVNEnvironment().isForce(), depth, this, changeLists);                                
                        } else {
                            client.setCommitHandler(getSVNEnvironment());
                            client.doSetProperty(target.getURL(), propertyName, null, SVNRevision.HEAD, getSVNEnvironment().getMessage(),
                                    getSVNEnvironment().getRevisionProperties(), getSVNEnvironment().isForce(), this);
                        }
                        if (deletedNonExistent[0]) {
                            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CLIENT_PROPERTY_NAME, "Attempting to delete nonexistent property ''{0}''", propertyName);
                            getSVNEnvironment().getOut().println(err.getFullMessage());
                            success = false;
                        }
                    } catch (SVNException e) {
                        success = getSVNEnvironment().handleWarning(e.getErrorMessage(), 
                                new SVNErrorCode[] {SVNErrorCode.UNVERSIONED_RESOURCE, SVNErrorCode.ENTRY_NOT_FOUND},
                                getSVNEnvironment().isQuiet());
                    }
                    clearCollectedProperties();
                    if (success && !getSVNEnvironment().isQuiet()) {
                        if (success) {
                            String path = SVNCommandUtil.getLocalPath(targetName);
                            String message = depth.isRecursive() ? 
                                    "property ''{0}'' deleted (recursively) from ''{1}''." :
                                    "property ''{0}'' deleted from ''{1}''.";
                            message = MessageFormat.format(message, new Object[] {propertyName, path});
                            getSVNEnvironment().getOut().println(message);
                        }
                    }
                } 
            }
        }
    }

    public void handleProperty(long revision, SVNPropertyData property) throws SVNException {
        super.handleProperty(revision, property);
        if (!getSVNEnvironment().isQuiet()) {
            String message = "property ''{0}'' deleted from repository revision {1}";
            message = MessageFormat.format(message, new Object[] {property.getName(), new Long(revision)});
            getSVNEnvironment().getOut().println(message);
        }
    }
}
