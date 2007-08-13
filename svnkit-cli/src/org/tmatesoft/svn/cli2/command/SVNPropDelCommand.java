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
package org.tmatesoft.svn.cli2.command;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;

import org.tmatesoft.svn.cli2.SVNCommandTarget;
import org.tmatesoft.svn.cli2.SVNCommandUtil;
import org.tmatesoft.svn.cli2.SVNOption;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
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
        String propertyName = getEnvironment().popArgument();
        if (propertyName == null) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CL_INSUFFICIENT_ARGS);
            SVNErrorManager.error(err);
        }

        Collection targets = new ArrayList(); 
        if (getEnvironment().getChangelist() != null) {
            SVNCommandTarget target = new SVNCommandTarget("");
            SVNChangelistClient changelistClient = getEnvironment().getClientManager().getChangelistClient();
            changelistClient.getChangelist(target.getFile(), getEnvironment().getChangelist(), targets);
            if (targets.isEmpty()) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CL_ARG_PARSING_ERROR, "no such changelist ''{0}''", getEnvironment().getChangelist());
                SVNErrorManager.error(err);
            }
        }
        if (getEnvironment().getTargets() != null) {
            targets.addAll(getEnvironment().getTargets());
        }
        targets = getEnvironment().combineTargets(targets);
        if (targets.isEmpty()) {
            targets.add("");
        }
        
        if (getEnvironment().isRevprop()) {
            SVNURL revPropURL = getRevpropURL(getEnvironment().getStartRevision(), targets);
            getEnvironment().getClientManager().getWCClient().doSetRevisionProperty(revPropURL, getEnvironment().getStartRevision(), propertyName, null, getEnvironment().isForce(), this);
        } else if (getEnvironment().getStartRevision() != SVNRevision.UNDEFINED) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CL_ARG_PARSING_ERROR, 
                    "Cannot specify revision for deleting versioned property ''{0}''", propertyName);
            SVNErrorManager.error(err);
        } else {
            SVNDepth depth = getEnvironment().getDepth();
            if (depth == SVNDepth.UNKNOWN) {
                depth = SVNDepth.EMPTY;
            }
            SVNWCClient client = getEnvironment().getClientManager().getWCClient();
            for (Iterator ts = targets.iterator(); ts.hasNext();) {
                String targetName = (String) ts.next();
                SVNCommandTarget target = new SVNCommandTarget(targetName);
                if (target.isFile()) {
                    boolean success = true;
                    try {
                        client.doSetProperty(target.getFile(), propertyName, null, getEnvironment().isForce(), depth.isRecursive(), this);
                    } catch (SVNException e) {
                        success = getEnvironment().handleWarning(e.getErrorMessage(), new SVNErrorCode[] {SVNErrorCode.UNVERSIONED_RESOURCE, SVNErrorCode.ENTRY_NOT_FOUND});
                    }
                    clearCollectedProperties();
                    if (success && !getEnvironment().isQuiet()) {
                        if (success) {
                            String path = SVNCommandUtil.getLocalPath(targetName);
                            String message = depth.isRecursive() ? 
                                    "property ''{0}'' deleted (recursively) from ''{1}''" :
                                    "property ''{0}'' deleted from ''{1}''";
                            message = MessageFormat.format(message, new Object[] {propertyName, path});
                            getEnvironment().getOut().println(message);
                        }
                    }
                } 
            }
        }
    }

    public void handleProperty(long revision, SVNPropertyData property) throws SVNException {
        super.handleProperty(revision, property);
        if (!getEnvironment().isQuiet()) {
            String message = "property ''{0}'' deleted from repository revision {1}";
            message = MessageFormat.format(message, new Object[] {property.getName(), new Long(revision)});
            getEnvironment().getOut().println(message);
        }
    }
}
