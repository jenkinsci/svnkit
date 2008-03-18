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
import java.io.UnsupportedEncodingException;

import org.tmatesoft.svn.cli2.SVNCommandUtil;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNPropertyValue;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.wc.SVNPath;
import org.tmatesoft.svn.core.internal.wc.SVNPropertiesManager;
import org.tmatesoft.svn.core.wc.SVNChangelistClient;
import org.tmatesoft.svn.core.wc.SVNPropertyData;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNWCClient;


/**
 * @author TMate Software Ltd.
 * @version 1.1.2
 */
public class SVNPropSetCommand extends SVNPropertiesCommand {

    public SVNPropSetCommand() {
        super("propset", new String[]{"pset", "ps"});
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
        options.add(SVNOption.FORCE);
        options.add(SVNOption.CHANGELIST);

        return options;
    }

    public void run() throws SVNException {
        String propertyName = getSVNEnvironment().popArgument();
        if (propertyName == null) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CL_INSUFFICIENT_ARGS);
            SVNErrorManager.error(err);
        }
        if (!SVNPropertiesManager.isValidPropertyName(propertyName)) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CLIENT_PROPERTY_NAME,
                    "''{0}'' is not a valid Subversion property name", propertyName);
            SVNErrorManager.error(err);
        }

        String encoding = null;
        if (SVNPropertiesManager.propNeedsTranslation(propertyName)) {
            encoding = getSVNEnvironment().getEncoding();
            if (encoding == null) {
                encoding = "UTF-8";
            }
        } else {
            if (getSVNEnvironment().getEncoding() != null) {
                SVNErrorMessage errorMessage = SVNErrorMessage.create(SVNErrorCode.UNSUPPORTED_FEATURE,
                        "Bad encoding option: prop value not stored as UTF8");
                SVNErrorManager.error(errorMessage);
            }
        }

        SVNPropertyValue propertyValue = null;
        if (encoding != null) {
            if (getSVNEnvironment().getFileData() != null) {
                String stringValue = null;
                try {
                    stringValue = new String(getSVNEnvironment().getFileData(), encoding);
                } catch (UnsupportedEncodingException e) {
                    stringValue = new String(getSVNEnvironment().getFileData());
                }
                propertyValue = SVNPropertyValue.create(stringValue);
            } else {
                propertyValue = SVNPropertyValue.create(getSVNEnvironment().popArgument());
            }
        } else {
            if (getSVNEnvironment().getFileData() != null) {
                propertyValue = SVNPropertyValue.create(propertyName, getSVNEnvironment().getFileData());
            } else {
                propertyValue = SVNPropertyValue.create(propertyName, getSVNEnvironment().popArgument().getBytes());
            }
        }

        Collection targets = new ArrayList();
        if (getSVNEnvironment().getChangelist() != null) {
            SVNPath target = new SVNPath("");
            SVNChangelistClient changelistClient = getSVNEnvironment().getClientManager().getChangelistClient();
            changelistClient.getChangelist(target.getFile(), getSVNEnvironment().getChangelist(), targets);
            if (targets.isEmpty()) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CL_ARG_PARSING_ERROR, "no such changelist ''{0}''", getSVNEnvironment().getChangelist());
                SVNErrorManager.error(err);
            }
        }
        if (getSVNEnvironment().getTargets() != null) {
            targets.addAll(getSVNEnvironment().getTargets());
        }
        targets = getSVNEnvironment().combineTargets(targets, true);

        if (getSVNEnvironment().isRevprop()) {
            if (targets.isEmpty()) {
                targets.add("");
            }
            SVNURL revPropURL = getRevpropURL(getSVNEnvironment().getStartRevision(), targets);
            getSVNEnvironment().getClientManager().getWCClient().doSetRevisionProperty(revPropURL, getSVNEnvironment().getStartRevision(),
                    propertyName, propertyValue, getSVNEnvironment().isForce(), this);
        } else if (getSVNEnvironment().getStartRevision() != SVNRevision.UNDEFINED) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CL_ARG_PARSING_ERROR,
                    "Cannot specify revision for setting versioned property ''{0}''", propertyName);
            SVNErrorManager.error(err);
        } else {
            SVNDepth depth = getSVNEnvironment().getDepth();
            if (depth == SVNDepth.UNKNOWN) {
                depth = SVNDepth.EMPTY;
            }
            if (targets.isEmpty()) {
                if (getSVNEnvironment().getFileData() == null) {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CL_INSUFFICIENT_ARGS,
                            "Explicit target required (''{0}'' interpreted as prop value)", propertyValue);
                    SVNErrorManager.error(err);
                } else {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CL_INSUFFICIENT_ARGS, "Explicit target argument required");
                    SVNErrorManager.error(err);
                }
            }
            SVNWCClient client = getSVNEnvironment().getClientManager().getWCClient();
            for (Iterator ts = targets.iterator(); ts.hasNext();) {
                String targetName = (String) ts.next();
                SVNPath target = new SVNPath(targetName);
                if (target.isFile()) {
                    boolean success = true;
                    try {
                        if (target.isFile()) {
                            client.doSetProperty(target.getFile(), propertyName, propertyValue,
                                    getSVNEnvironment().isForce(), depth, this);
                        } else {
                            client.setCommitHandler(getSVNEnvironment());
                            client.doSetProperty(target.getURL(), propertyName, propertyValue, SVNRevision.HEAD, getSVNEnvironment().getMessage(),
                                    getSVNEnvironment().getRevisionProperties(), getSVNEnvironment().isForce(), depth, this);
                        }
                    } catch (SVNException e) {
                        success = getSVNEnvironment().handleWarning(e.getErrorMessage(),
                                new SVNErrorCode[]{SVNErrorCode.UNVERSIONED_RESOURCE, SVNErrorCode.ENTRY_NOT_FOUND},
                                getSVNEnvironment().isQuiet());
                    }
                    clearCollectedProperties();
                    if (!getSVNEnvironment().isQuiet()) {
                        checkBooleanProperty(propertyName, propertyValue);
                        if (success) {
                            String path = SVNCommandUtil.getLocalPath(targetName);
                            String message = depth.isRecursive() ?
                                    "property ''{0}'' set (recursively) on ''{1}''" :
                                    "property ''{0}'' set on ''{1}''";
                            message = MessageFormat.format(message, new Object[]{propertyName, path});
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
            String message = "property ''{0}'' set on repository revision {1}";
            message = MessageFormat.format(message, new Object[]{property.getName(), new Long(revision)});
            getSVNEnvironment().getOut().println(message);
        }
    }


}
