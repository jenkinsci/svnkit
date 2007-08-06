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

import java.io.UnsupportedEncodingException;
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
import org.tmatesoft.svn.core.SVNProperty;
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
public class SVNPropSetCommand extends SVNPropertiesCommand {

    public SVNPropSetCommand() {
        super("propset", new String[] {"pset", "ps"});
    }

    protected Collection createSupportedOptions() {
        Collection options = new LinkedList();
        options.add(SVNOption.FILE);
        options.add(SVNOption.ENCODING);
        options.add(SVNOption.QUIET);
        options.add(SVNOption.REVISION);
        options.add(SVNOption.TARGETS);
        options.add(SVNOption.RECURSIVE);
        options.add(SVNOption.DEPTH);
        options.add(SVNOption.REVPROP);
        options = SVNOption.addAuthOptions(options);

        options.add(SVNOption.FORCE);
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
        String propertyValue = null;
        if (getEnvironment().getFileData() != null) {
            String encoding = getEnvironment().getEncoding();
            if (encoding == null) {
                encoding = "UTF-8";
            }
            try {
                propertyValue = new String(getEnvironment().getFileData(), encoding);
            } catch (UnsupportedEncodingException e) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, e.getMessage());
                SVNErrorManager.error(err);
            }
        } else {
            propertyValue = getEnvironment().popArgument();
        }
        if (propertyValue == null) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CL_INSUFFICIENT_ARGS);
            SVNErrorManager.error(err);
        }

        Collection targets = new ArrayList(); 
        if (getEnvironment().getChangelist() != null) {
            getEnvironment().setCurrentTarget(new SVNCommandTarget(""));
            SVNChangelistClient changelistClient = getEnvironment().getClientManager().getChangelistClient();
            changelistClient.getChangelist(getEnvironment().getCurrentTargetFile(), getEnvironment().getChangelist(), targets);
            if (targets.isEmpty()) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CL_ARG_PARSING_ERROR, "no such changelist ''{0}''", getEnvironment().getChangelist());
                SVNErrorManager.error(err);
            }
        }
        if (getEnvironment().getTargets() != null) {
            targets.addAll(getEnvironment().getTargets());
        }
        targets = getEnvironment().combineTargets(targets);
        
        if (getEnvironment().isRevprop()) {
            if (targets.isEmpty()) {
                targets.add("");
            }
            SVNURL revPropURL = getRevpropURL(getEnvironment().getStartRevision(), targets);
            getEnvironment().getClientManager().getWCClient().doSetRevisionProperty(revPropURL, getEnvironment().getStartRevision(), propertyName, propertyValue, getEnvironment().isForce(), this);
        } else if (getEnvironment().getStartRevision() != SVNRevision.UNDEFINED) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CL_ARG_PARSING_ERROR, 
                    "Cannot specify revision for setting versioned property ''{0}''", propertyName);
            SVNErrorManager.error(err);
        } else {
            SVNDepth depth = getEnvironment().getDepth();
            if (depth == SVNDepth.UNKNOWN) {
                depth = SVNDepth.EMPTY;
            }
            if (targets.isEmpty()) {
                if (getEnvironment().getFileData() == null) {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CL_INSUFFICIENT_ARGS, 
                            "Explicit target required (''{0}'' interpreted as prop value)", propertyValue);
                    SVNErrorManager.error(err);
                } else {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CL_INSUFFICIENT_ARGS, "Explicit target argument required");
                    SVNErrorManager.error(err);
                }
            }
            SVNWCClient client = getEnvironment().getClientManager().getWCClient();
            for (Iterator ts = targets.iterator(); ts.hasNext();) {
                String targetName = (String) ts.next();
                SVNCommandTarget target = new SVNCommandTarget(targetName);
                if (target.isFile()) {
                    getEnvironment().setCurrentTarget(target);
                    boolean success = true;
                    try {
                        client.doSetProperty(target.getFile(), propertyName, propertyValue, getEnvironment().isForce(), depth.isRecursive(), this);
                    } catch (SVNException e) {
                        success = getEnvironment().handleWarning(e.getErrorMessage(), new SVNErrorCode[] {SVNErrorCode.UNVERSIONED_RESOURCE, SVNErrorCode.ENTRY_NOT_FOUND});
                    }
                    clearCollectedProperties();
                    if (!getEnvironment().isQuiet()) {
                        checkBooleanProperty(propertyName, propertyValue);
                        if (success) {
                            String path = SVNCommandUtil.getLocalPath(targetName);
                            String message = depth.isRecursive() ? 
                                    "property ''{0}'' set (recursively) on ''{1}''" :
                                        "property ''{0}'' set on ''{1}''";
                            message = MessageFormat.format(message, new Object[] {propertyName, path});
                            getEnvironment().getOut().println(message);
                        }
                    }
                } 
            }
        }
    }
    
    protected void checkBooleanProperty(String name, String value) throws SVNException {
        if (!SVNProperty.isBooleanProperty(name)) {
            return;
        }
        value = value.trim();
        if ("".equals(value) || "off".equalsIgnoreCase(value) || "no".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value)) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.BAD_PROPERTY_VALUE, "To turn off the {0} property, use ''svn propdel'';\n" +
            		"setting the property to ''{1}'' will not turn it off.", new Object[] {name, value});
            getEnvironment().handleWarning(err, new SVNErrorCode[] {SVNErrorCode.BAD_PROPERTY_VALUE});
        }
    }

    public void handleProperty(long revision, SVNPropertyData property) throws SVNException {
        super.handleProperty(revision, property);
        if (!getEnvironment().isQuiet()) {
            String message = "property ''{0}'' set on repository revision {1}";
            message = MessageFormat.format(message, new Object[] {property.getName(), new Long(revision)});
            getEnvironment().getOut().println(message);
        }
    }
    
    

}
