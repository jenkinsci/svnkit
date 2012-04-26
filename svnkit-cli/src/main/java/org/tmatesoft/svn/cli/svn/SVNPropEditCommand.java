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
import org.tmatesoft.svn.core.SVNCommitInfo;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNPropertyValue;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.wc.SVNPath;
import org.tmatesoft.svn.core.internal.wc.SVNPropertiesManager;
import org.tmatesoft.svn.core.wc.SVNPropertyData;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNWCClient;
import org.tmatesoft.svn.util.SVNLogType;


/**
 * @author TMate Software Ltd.
 * @version 1.3
 */
public class SVNPropEditCommand extends SVNPropertiesCommand {

    public SVNPropEditCommand() {
        super("propedit", new String[]{"pedit", "pe"});
    }

    protected Collection createSupportedOptions() {
        Collection options = new LinkedList();
        options.add(SVNOption.REVISION);
        options.add(SVNOption.REVPROP);
        options = SVNOption.addLogMessageOptions(options);
        options.add(SVNOption.FORCE);

        return options;
    }

    public void run() throws SVNException {
        String propertyName = getSVNEnvironment().popArgument();
        if (propertyName == null) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CL_INSUFFICIENT_ARGS);
            SVNErrorManager.error(err, SVNLogType.CLIENT);
        }
        if (!SVNPropertiesManager.isValidPropertyName(propertyName)) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CLIENT_PROPERTY_NAME,
                    "''{0}'' is not a valid Subversion property name", propertyName);
            SVNErrorManager.error(err, SVNLogType.CLIENT);
        }

        Collection targets = new ArrayList();
        targets = getSVNEnvironment().combineTargets(targets, true);

        if (getSVNEnvironment().isRevprop()) {
            if (targets.isEmpty()) {
                targets.add("");
            }
//            SVNURL revPropURL = getRevpropURL();
            SVNWCClient client = getSVNEnvironment().getClientManager().getWCClient();
            String target = checkRevPropTarget(getSVNEnvironment().getStartRevision(), targets);
            SVNURL revPropURL = null;
            File targetPath = null;
            long rev;
            if (SVNCommandUtil.isURL(target)) {
                revPropURL = SVNURL.parseURIEncoded(target);
                rev = client.doGetRevisionProperty(revPropURL, propertyName, getSVNEnvironment().getStartRevision(), this);
            } else {
                targetPath = new SVNPath(target).getFile();
                rev = client.doGetRevisionProperty(targetPath, propertyName, getSVNEnvironment().getStartRevision(), this);
            }
            SVNPropertyData property = getRevisionProperty(rev);
            SVNPropertyValue propertyValue = property != null ? property.getValue() : SVNPropertyValue.create("");
            byte[] propBytes = SVNPropertyValue.getPropertyAsBytes(propertyValue);            
            byte[] bytes = SVNCommandUtil.runEditor(getSVNEnvironment(), getSVNEnvironment().getEditorCommand(), propBytes, "svn-prop");
            SVNPropertyValue newPropertyValue = SVNPropertyValue.create(propertyName, bytes);
            if (newPropertyValue != null && !newPropertyValue.equals(propertyValue)) {
                clearCollectedProperties();
                if (revPropURL != null) {
                    client.doSetRevisionProperty(revPropURL, SVNRevision.create(rev), propertyName, newPropertyValue, getSVNEnvironment().isForce(), this);
                } else if (targetPath != null) {
                    client.doSetRevisionProperty(targetPath, SVNRevision.create(rev), propertyName, newPropertyValue, getSVNEnvironment().isForce(), this);
                }
                String message = "Set new value for property ''{0}'' on revision {1}";
                message = MessageFormat.format(message, new Object[]{propertyName, new Long(rev)});
                getSVNEnvironment().getOut().println(message);
            } else {
                String message = "No changes to property ''{0}'' on revision {1}";
                message = MessageFormat.format(message, new Object[]{propertyName, new Long(rev)});
                getSVNEnvironment().getOut().println(message);
            }
        } else if (getSVNEnvironment().getStartRevision() != SVNRevision.UNDEFINED) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CL_ARG_PARSING_ERROR,
                    "Cannot specify revision for editing versioned property ''{0}''", propertyName);
            SVNErrorManager.error(err, SVNLogType.CLIENT);
        } else {
            if (targets.isEmpty()) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CL_INSUFFICIENT_ARGS,
                        "Explicit target argument required", propertyName);
                SVNErrorManager.error(err, SVNLogType.CLIENT);
            }
            SVNWCClient client = getSVNEnvironment().getClientManager().getWCClient();
            for (Iterator ts = targets.iterator(); ts.hasNext();) {
                String targetName = (String) ts.next();
                SVNPath target = new SVNPath(targetName);
                if (target.isFile()) {
                    if (getSVNEnvironment().getMessage() != null || getSVNEnvironment().getFileData() != null || getSVNEnvironment().getRevisionProperties() != null) {
                        SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CL_UNNECESSARY_LOG_MESSAGE,
                                "Local, non-commit operations do not take a log message or revision properties");
                        SVNErrorManager.error(err, SVNLogType.CLIENT);
                    }
                    SVNPropertyData property = client.doGetProperty(target.getFile(), propertyName, 
                            SVNRevision.UNDEFINED, SVNRevision.WORKING);
                    SVNPropertyValue propertyValue = property != null ? property.getValue() : SVNPropertyValue.create("");
                    byte[] propBytes = SVNPropertyValue.getPropertyAsBytes(propertyValue);                   
                    byte[] bytes = SVNCommandUtil.runEditor(getSVNEnvironment(), getSVNEnvironment().getEditorCommand(), propBytes, "svn-prop");
                    SVNPropertyValue newPropertyValue = SVNPropertyValue.create(propertyName, bytes);
                    if (newPropertyValue != null && !newPropertyValue.equals(propertyValue)) {
                        checkBooleanProperty(propertyName, newPropertyValue);
                        client.doSetProperty(target.getFile(), propertyName, newPropertyValue, 
                                getSVNEnvironment().isForce(), SVNDepth.EMPTY, this, null);
                        String message = "Set new value for property ''{0}'' on ''{1}''";
                        String path = SVNCommandUtil.getLocalPath(targetName);
                        message = MessageFormat.format(message, new Object[]{propertyName, path});
                        getSVNEnvironment().getOut().println(message);
                    } else {
                        String message = "No changes to property ''{0}'' on ''{1}''";
                        String path = SVNCommandUtil.getLocalPath(targetName);
                        message = MessageFormat.format(message, new Object[]{propertyName, path});
                        getSVNEnvironment().getOut().println(message);
                    }
                } else {
                    SVNPropertyData property = client.doGetProperty(target.getURL(), propertyName, 
                            SVNRevision.UNDEFINED, SVNRevision.HEAD);
                    SVNPropertyValue propertyValue = property != null ? property.getValue() : SVNPropertyValue.create("");
                    byte[] propBytes = SVNPropertyValue.getPropertyAsBytes(propertyValue);                                       
                    byte[] bytes = SVNCommandUtil.runEditor(getSVNEnvironment(), getSVNEnvironment().getEditorCommand(), propBytes, "svn-prop");
                    SVNPropertyValue newPropertyValue = SVNPropertyValue.create(propertyName, bytes);
                    if (newPropertyValue != null && !newPropertyValue.equals(propertyValue)) {
                        checkBooleanProperty(propertyName, newPropertyValue);
                        client.setCommitHandler(getSVNEnvironment());
                        SVNCommitInfo info = client.doSetProperty(target.getURL(), propertyName,
                                newPropertyValue, SVNRevision.HEAD, getSVNEnvironment().getMessage(),
                                getSVNEnvironment().getRevisionProperties(), getSVNEnvironment().isForce(),
                                this);
                        String message = "Set new value for property ''{0}'' on ''{1}''";
                        message = MessageFormat.format(message, new Object[]{propertyName, targetName});
                        getSVNEnvironment().getOut().println(message);
                        if (!getSVNEnvironment().isQuiet()) {
                            getSVNEnvironment().printCommitInfo(info);
                        }
                    } else {
                        String message = "No changes to property ''{0}'' on ''{1}''";
                        message = MessageFormat.format(message, new Object[]{propertyName, targetName});
                        getSVNEnvironment().getOut().println(message);
                    }
                }
                clearCollectedProperties();
            }
        }
    }
}
