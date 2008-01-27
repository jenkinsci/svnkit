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
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;

import org.tmatesoft.svn.cli2.SVNCommandUtil;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.SVNPropertyValue;
import org.tmatesoft.svn.core.internal.wc.SVNPath;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.wc.SVNChangelistClient;
import org.tmatesoft.svn.core.wc.SVNPropertyData;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNWCClient;


/**
 * @version 1.1.2
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
        options = SVNOption.addAuthOptions(options);

        options.add(SVNOption.CONFIG_DIR);
        options.add(SVNOption.CHANGELIST);
        
        return options;
    }

    public void run() throws SVNException {
        String propertyName = getSVNEnvironment().popArgument();
        if (propertyName == null) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CL_INSUFFICIENT_ARGS);
            SVNErrorManager.error(err);
        }

        Collection targets = new ArrayList(); 
        if (getSVNEnvironment().getChangelist() != null) {
            SVNPath target = new SVNPath("");
            SVNChangelistClient changelistClient = getSVNEnvironment().getClientManager().getChangelistClient();
            changelistClient.getChangelist(target.getFile(), getSVNEnvironment().getChangelist(), targets);
            if (targets.isEmpty()) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNKNOWN_CHANGELIST, 
                        "Unknown changelist ''{0}''", getSVNEnvironment().getChangelist());
                SVNErrorManager.error(err);
            }
        }
        if (getSVNEnvironment().getTargets() != null) {
            targets.addAll(getSVNEnvironment().getTargets());
        }
        targets = getSVNEnvironment().combineTargets(targets);
        if (targets.isEmpty()) {
            targets.add("");
        }
        
        if (getSVNEnvironment().isRevprop()) {
            SVNURL revPropURL = getRevpropURL(getSVNEnvironment().getStartRevision(), targets);
            getSVNEnvironment().getClientManager().getWCClient().doSetRevisionProperty(revPropURL, getSVNEnvironment().getStartRevision(), propertyName, null, getSVNEnvironment().isForce(), this);
        } else if (getSVNEnvironment().getStartRevision() != SVNRevision.UNDEFINED) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CL_ARG_PARSING_ERROR, 
                    "Cannot specify revision for deleting versioned property ''{0}''", propertyName);
            SVNErrorManager.error(err);
        } else {
            SVNDepth depth = getSVNEnvironment().getDepth();
            if (depth == SVNDepth.UNKNOWN) {
                depth = SVNDepth.EMPTY;
            }
            SVNWCClient client = getSVNEnvironment().getClientManager().getWCClient();
            for (Iterator ts = targets.iterator(); ts.hasNext();) {
                String targetName = (String) ts.next();
                SVNPath target = new SVNPath(targetName);
                if (target.isFile()) {
                    boolean success = true;
                    try {
                        client.doSetProperty(target.getFile(), propertyName, null, getSVNEnvironment().isForce(), depth.isRecursive(), this);
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
