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

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.tmatesoft.svn.cli2.SVNCommandTarget;
import org.tmatesoft.svn.cli2.SVNCommandUtil;
import org.tmatesoft.svn.cli2.SVNOption;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.util.SVNEncodingUtil;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.wc.SVNChangelistClient;
import org.tmatesoft.svn.core.wc.SVNPropertyData;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNWCClient;


/**
 * @version 1.1.2
 * @author  TMate Software Ltd.
 */
public class SVNPropGetCommand extends SVNPropertiesCommand {

    public SVNPropGetCommand() {
        super("propget", new String[] {"pget", "pg"});
    }

    protected Collection createSupportedOptions() {
        Collection options = new ArrayList();
        options.add(SVNOption.RECURSIVE);
        options.add(SVNOption.DEPTH);
        options.add(SVNOption.REVISION);
        options.add(SVNOption.REVPROP);
        options.add(SVNOption.STRICT);
        options = SVNOption.addAuthOptions(options);
        options.add(SVNOption.CONFIG_DIR);
        options.add(SVNOption.XML);
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
        targets = getEnvironment().combineTargets(targets);
        if (targets.isEmpty()) {
            targets.add("");
        }

        if (getEnvironment().isRevprop()) {
            SVNURL url = getRevpropURL(getEnvironment().getStartRevision(), targets);
            SVNWCClient wcClient = getEnvironment().getClientManager().getWCClient();
            long rev = wcClient.doGetRevisionProperty(url, propertyName, getEnvironment().getStartRevision(), this);
            SVNPropertyData propertyValue = getRevisionProperty(rev);
            if (propertyValue != null) {
                if (getEnvironment().isXML()) {
                    printXMLHeader("properties");
                    StringBuffer buffer = openXMLTag("revprops", XML_STYLE_NORMAL, "rev", Long.toString(rev), null);
                    buffer = openXMLTag("property", XML_STYLE_PROTECT_PCDATA, "name", SVNEncodingUtil.xmlEncodeAttr(propertyName), buffer);
                    buffer.append(SVNEncodingUtil.xmlEncodeCDATA(propertyValue.getValue()));
                    buffer = closeXMLTag("property", buffer);
                    buffer = closeXMLTag("revprops", buffer);
                    getEnvironment().getOut().print(buffer);
                    printXMLFooter("properties");
                } else {
                    getEnvironment().getOut().print(propertyValue.getValue());
                    if (!getEnvironment().isStrict()) {
                        getEnvironment().getOut().println();
                    }
                }
            }
            clearCollectedProperties();
        } else {
            if (getEnvironment().isXML()) {
                printXMLHeader("properties");
            }
            SVNDepth depth = getEnvironment().getDepth();
            if (depth == SVNDepth.UNKNOWN) {
                depth = SVNDepth.EMPTY;
            }
            SVNWCClient client = getEnvironment().getClientManager().getWCClient();
            for (Iterator ts = targets.iterator(); ts.hasNext();) {
                String targetPath = (String) ts.next();
                SVNCommandTarget target = new SVNCommandTarget(targetPath, true);
                SVNRevision pegRevision = target.getPegRevision();
                boolean printFileNames = false;
                if (target.isURL()) {
                    client.doGetProperty(target.getURL(), propertyName, pegRevision, getEnvironment().getStartRevision(), depth.isRecursive(), this);
                    printFileNames = !getEnvironment().isStrict() && (depth.isRecursive() || targets.size() > 1 || getURLProperties().size() > 1); 
                } else {
                    client.doGetProperty(target.getFile(), propertyName, pegRevision, getEnvironment().getStartRevision(), depth.isRecursive(), this);
                    printFileNames = !getEnvironment().isStrict() && (depth.isRecursive() || targets.size() > 1 || getPathProperties().size() > 1); 
                }
                if (!getEnvironment().isXML()) {
                    printCollectedProperties(printFileNames, target.isURL());
                } else {
                    printCollectedPropertiesXML(target.isURL());
                }
                clearCollectedProperties();
            }
            if (getEnvironment().isXML()) {
                printXMLFooter("properties");
            }
        }
    }
    
    protected void printCollectedProperties(boolean printFileName, boolean isURL) {
        Map map = isURL ? getURLProperties() : getPathProperties();
        for (Iterator keys = map.keySet().iterator(); keys.hasNext();) {
            Object key = keys.next();
            List props = (List) map.get(key);
            if (printFileName) {
                if (isURL) {
                    getEnvironment().getOut().print(key);
                } else {
                    String path = SVNCommandUtil.getLocalPath(getEnvironment().getRelativePath((File) key));
                    getEnvironment().getOut().print(path);
                }
                getEnvironment().getOut().print(" - ");
            }
            SVNPropertyData property = (SVNPropertyData) props.get(0);
            getEnvironment().getOut().print(property.getValue());
            if (!getEnvironment().isStrict()) {
                getEnvironment().getOut().println();
            }
        }
    }

    protected void printCollectedPropertiesXML(boolean isURL) {
        Map map = isURL ? getURLProperties() : getPathProperties();

        for (Iterator keys = map.keySet().iterator(); keys.hasNext();) {
            Object key = keys.next();
            List props = (List) map.get(key);
            String target = key.toString();
            if (!isURL) {
                target = SVNCommandUtil.getLocalPath(getEnvironment().getRelativePath((File) key));
            } 
            SVNPropertyData property = (SVNPropertyData) props.get(0);
            StringBuffer buffer = openXMLTag("target", XML_STYLE_NORMAL, "path", target, null);
            buffer = openXMLTag("property", XML_STYLE_PROTECT_PCDATA, "name", property.getName(), buffer);
            buffer.append(SVNEncodingUtil.xmlEncodeCDATA(property.getValue()));
            buffer = closeXMLTag("property", buffer);
            buffer = closeXMLTag("target", buffer);
            getEnvironment().getOut().print(buffer);
        }
    }
}
