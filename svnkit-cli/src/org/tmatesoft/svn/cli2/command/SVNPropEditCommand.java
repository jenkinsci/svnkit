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

import java.io.IOException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;

import org.tmatesoft.svn.cli2.SVNCommandTarget;
import org.tmatesoft.svn.cli2.SVNCommandUtil;
import org.tmatesoft.svn.cli2.SVNOption;
import org.tmatesoft.svn.core.SVNCommitInfo;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.wc.SVNPropertyData;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNWCClient;


/**
 * @version 1.1.2
 * @author  TMate Software Ltd.
 */
public class SVNPropEditCommand extends SVNPropertiesCommand {

    public SVNPropEditCommand() {
        super("propedit", new String[] {"pedit", "pe"});
    }

    protected Collection createSupportedOptions() {
        Collection options = new LinkedList();
        options.add(SVNOption.REVISION);
        options.add(SVNOption.REVPROP);
        
        options = SVNOption.addLogMessageOptions(options);
        options = SVNOption.addAuthOptions(options);

        options.add(SVNOption.FORCE);
        options.add(SVNOption.CONFIG_DIR);
        
        return options;
    }

    public void run() throws SVNException {
        String propertyName = getEnvironment().popArgument();
        if (propertyName == null) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CL_INSUFFICIENT_ARGS);
            SVNErrorManager.error(err);
        }
        Collection targets = new ArrayList(); 
        targets = getEnvironment().combineTargets(targets);
        
        if (getEnvironment().isRevprop()) {
            if (targets.isEmpty()) {
                targets.add("");
            }            
            SVNURL revPropURL = getRevpropURL(getEnvironment().getStartRevision(), targets);
            SVNWCClient client = getEnvironment().getClientManager().getWCClient();
            long rev = client.doGetRevisionProperty(revPropURL, propertyName, getEnvironment().getStartRevision(), this);
            SVNPropertyData property = getRevisionProperty(rev);
            String propertyValue = property != null ? property.getValue() : "";
            byte[] newValue = SVNCommandUtil.runEditor(getEnvironment(), propertyValue, "svn-prop");
            String newPropertyValue = null;
            try {
                newPropertyValue = newValue == null ? null : new String(newValue, "UTF-8");
            } catch (IOException e) {
                SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.IO_ERROR, e.getMessage()));
            }
            if (newPropertyValue != null && !newPropertyValue.equals(propertyValue)) {
                clearCollectedProperties();
                client.doSetRevisionProperty(revPropURL, SVNRevision.create(rev), propertyName, newPropertyValue, getEnvironment().isForce(), this);
                String message = "Set new value for property ''{0}'' on revision {1}";
                message = MessageFormat.format(message, new Object[] {propertyName, new Long(rev)});
                getEnvironment().getOut().println(message);
            } else {
                String message = "No changes to property ''{0}'' on revision {1}";
                message = MessageFormat.format(message, new Object[] {propertyName, new Long(rev)});
                getEnvironment().getOut().println(message);
            }
        } else if (getEnvironment().getStartRevision() != SVNRevision.UNDEFINED) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CL_ARG_PARSING_ERROR, 
                    "Cannot specify revision for editing versioned property ''{0}''", propertyName);
            SVNErrorManager.error(err);
        } else {
            if (targets.isEmpty()) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CL_INSUFFICIENT_ARGS, 
                        "Explicit target argument required", propertyName);
                SVNErrorManager.error(err);
            }
            SVNWCClient client = getEnvironment().getClientManager().getWCClient();
            for (Iterator ts = targets.iterator(); ts.hasNext();) {
                String targetName = (String) ts.next();
                SVNCommandTarget target = new SVNCommandTarget(targetName);
                if (target.isFile()) {
                    if (getEnvironment().getMessage() != null || getEnvironment().getFileData() != null || getEnvironment().getRevisionProperties() != null) {
                        SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CL_UNNECESSARY_LOG_MESSAGE, 
                                "Local, non-commit operations do not take a log message or revision properties");
                        SVNErrorManager.error(err);
                    }
                    SVNPropertyData property = client.doGetProperty(target.getFile(), propertyName, SVNRevision.UNDEFINED, SVNRevision.WORKING, false);
                    String propertyValue = property != null ? property.getValue() : "";
                    byte[] newValue = SVNCommandUtil.runEditor(getEnvironment(), propertyValue, "svn-prop");
                    String newPropertyValue = null;
                    try {
                        newPropertyValue = newValue == null ? null : new String(newValue, "UTF-8");
                    } catch (IOException e) {
                        SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.IO_ERROR, e.getMessage()));
                    }
                    if (newPropertyValue != null && !newPropertyValue.equals(propertyValue)) {
                        checkBooleanProperty(propertyValue, newPropertyValue);
                        client.doSetProperty(target.getFile(), propertyName, newPropertyValue, getEnvironment().isForce(), false, this);
                        String message = "Set new value for property ''{0}'' on ''{1}''";
                        String path = SVNCommandUtil.getLocalPath(targetName);
                        message = MessageFormat.format(message, new Object[] {propertyName, path});
                        getEnvironment().getOut().println(message);
                    } else {
                        String message = "No changes to property ''{0}'' on ''{1}''";
                        String path = SVNCommandUtil.getLocalPath(targetName);
                        message = MessageFormat.format(message, new Object[] {propertyName, path});
                        getEnvironment().getOut().println(message);
                    }
                } else {
                    SVNPropertyData property = client.doGetProperty(target.getURL(), propertyName, SVNRevision.UNDEFINED, SVNRevision.HEAD, false);
                    String propertyValue = property != null ? property.getValue() : "";
                    byte[] newValue = SVNCommandUtil.runEditor(getEnvironment(), propertyValue, "svn-prop");
                    String newPropertyValue = null;
                    try {
                        newPropertyValue = newValue == null ? null : new String(newValue, "UTF-8");
                    } catch (IOException e) {
                        SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.IO_ERROR, e.getMessage()));
                    }
                    if (newPropertyValue != null && !newPropertyValue.equals(propertyValue)) {
                        checkBooleanProperty(propertyValue, newPropertyValue);
                        client.setCommitHandler(getEnvironment());
                        SVNCommitInfo info = client.doSetProperty(target.getURL(), propertyName, newPropertyValue, SVNRevision.HEAD, getEnvironment().getMessage(), getEnvironment().getRevisionProperties(), getEnvironment().isForce(), this);
                        String message = "Set new value for property ''{0}'' on ''{1}''";
                        message = MessageFormat.format(message, new Object[] {propertyName, targetName});
                        getEnvironment().getOut().println(message);
                        if (!getEnvironment().isQuiet()) {
                            getEnvironment().printCommitInfo(info);
                        }
                    } else {
                        String message = "No changes to property ''{0}'' on ''{1}''";
                        message = MessageFormat.format(message, new Object[] {propertyName, targetName});
                        getEnvironment().getOut().println(message);
                    }
                }
                clearCollectedProperties();
            }
        }
    }
}
