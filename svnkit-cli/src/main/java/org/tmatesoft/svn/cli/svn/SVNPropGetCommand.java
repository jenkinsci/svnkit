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
import java.io.IOException;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.tmatesoft.svn.cli.SVNCommandUtil;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.util.SVNXMLUtil;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.wc.SVNPath;
import org.tmatesoft.svn.core.internal.wc.SVNPropertiesManager;
import org.tmatesoft.svn.core.wc.SVNPropertyData;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNWCClient;
import org.tmatesoft.svn.util.SVNLogType;


/**
 * @version 1.3
 * @author  TMate Software Ltd.
 */
public class SVNPropGetCommand extends SVNPropertiesCommand {

    public SVNPropGetCommand() {
        super("propget", new String[] {"pget", "pg"});
    }

    protected Collection createSupportedOptions() {
        Collection options = new LinkedList();
        options.add(SVNOption.VERBOSE);
        options.add(SVNOption.RECURSIVE);
        options.add(SVNOption.DEPTH);
        options.add(SVNOption.REVISION);
        options.add(SVNOption.REVPROP);
        options.add(SVNOption.STRICT);
        options.add(SVNOption.XML);
        options.add(SVNOption.CHANGELIST);
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

        Collection targets = new LinkedList(); 
        targets = getSVNEnvironment().combineTargets(targets, true);
        if (targets.isEmpty()) {
            targets.add("");
        }

        if (getSVNEnvironment().isRevprop()) {
            SVNWCClient wcClient = getSVNEnvironment().getClientManager().getWCClient();
            String target = checkRevPropTarget(getSVNEnvironment().getStartRevision(), targets);            
            long rev;
            if (SVNCommandUtil.isURL(target)) {
                rev = wcClient.doGetRevisionProperty(SVNURL.parseURIEncoded(target), propertyName, getSVNEnvironment().getStartRevision(), this);
            } else {
                File targetPath = new SVNPath(target).getFile();
                rev = wcClient.doGetRevisionProperty(targetPath, propertyName, getSVNEnvironment().getStartRevision(), this);
            }
            SVNPropertyData propertyValue = getRevisionProperty(rev);
            
            if (propertyValue != null) {
                if (getSVNEnvironment().isXML()) {
                    printXMLHeader("properties");
                    StringBuffer buffer = openXMLTag("revprops", SVNXMLUtil.XML_STYLE_NORMAL, "rev", Long.toString(rev), null);
                    buffer = addXMLProp(propertyValue, buffer);
                    buffer = closeXMLTag("revprops", buffer);
                    getSVNEnvironment().getOut().print(buffer);
                    printXMLFooter("properties");
                } else {
                    if (propertyValue.getValue().isString()){
                        getSVNEnvironment().getOut().print(propertyValue.getValue());
                    } else {
                        try {
                            getSVNEnvironment().getOut().write(propertyValue.getValue().getBytes());
                        } catch (IOException e) {
                            //
                        }
                    }

                    if (!getSVNEnvironment().isStrict()) {
                        getSVNEnvironment().getOut().println();
                    }
                }
            }
            clearCollectedProperties();
        } else {
            if (getSVNEnvironment().isXML()) {
                printXMLHeader("properties");
            }
            SVNDepth depth = getSVNEnvironment().getDepth();
            if (depth == SVNDepth.UNKNOWN) {
                depth = SVNDepth.EMPTY;
            }
            
            boolean likeProplist = getSVNEnvironment().isVerbose() && !getSVNEnvironment().isStrict();
            Collection changeLists = getSVNEnvironment().getChangelistsCollection();
            SVNWCClient client = getSVNEnvironment().getClientManager().getWCClient();
            for (Iterator ts = targets.iterator(); ts.hasNext();) {
                String targetPath = (String) ts.next();
                SVNPath target = new SVNPath(targetPath, true);
                SVNRevision pegRevision = target.getPegRevision();
                boolean printFileNames = false;
                if (target.isURL()) {
                    client.doGetProperty(target.getURL(), propertyName, pegRevision, 
                            getSVNEnvironment().getStartRevision(), depth, this);
                    printFileNames = !getSVNEnvironment().isStrict() && (getSVNEnvironment().isVerbose() || 
                            depth.compareTo(SVNDepth.EMPTY) > 0 || targets.size() > 1 || getURLProperties().size() > 1); 
                } else {
                    client.doGetProperty(target.getFile(), propertyName, pegRevision, 
                            getSVNEnvironment().getStartRevision(), depth, this, changeLists);
                    printFileNames = !getSVNEnvironment().isStrict() && (getSVNEnvironment().isVerbose() || 
                            depth.compareTo(SVNDepth.EMPTY) > 0 || targets.size() > 1 || getPathProperties().size() > 1); 
                }
                if (!getSVNEnvironment().isXML()) {
                    printCollectedProperties(printFileNames, target.isURL(), likeProplist);
                } else {
                    printCollectedPropertiesXML(target.isURL());
                }
                clearCollectedProperties();
            }
            if (getSVNEnvironment().isXML()) {
                printXMLFooter("properties");
            }
        }
    }
    
    protected void printCollectedProperties(boolean printFileName, boolean isURL, boolean likePropList) {
        Map map = isURL ? getURLProperties() : getPathProperties();
        for (Iterator keys = map.keySet().iterator(); keys.hasNext();) {
            Object key = keys.next();
            List props = (List) map.get(key);
            if (printFileName) {
                if (isURL) {
                    if (likePropList) {
                        getSVNEnvironment().getOut().println("Properties on '" + key + "':");
                    } else {
                        getSVNEnvironment().getOut().print(key);
                        getSVNEnvironment().getOut().print(" - ");
                    }
                } else {
                    String path = SVNCommandUtil.getLocalPath(getSVNEnvironment().getRelativePath((File) key));
                    if (likePropList) {
                        getSVNEnvironment().getOut().println("Properties on '" + path + "':");
                    } else {
                        getSVNEnvironment().getOut().print(path);    
                        getSVNEnvironment().getOut().print(" - ");
                    }
                }
            }
            if (likePropList) {
                printProplist(props);
            } else {
                SVNPropertyData property = (SVNPropertyData) props.get(0);
                printProperty(property.getValue(), likePropList);
                if (!getSVNEnvironment().isStrict()) {
                    getSVNEnvironment().getOut().println();
                }
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
                target = SVNCommandUtil.getLocalPath(getSVNEnvironment().getRelativePath((File) key));
            } 
            SVNPropertyData property = (SVNPropertyData) props.get(0);
            StringBuffer buffer = openXMLTag("target", SVNXMLUtil.XML_STYLE_NORMAL, "path", target, null);
            buffer = addXMLProp(property, buffer);
            buffer = closeXMLTag("target", buffer);
            getSVNEnvironment().getOut().print(buffer);
        }
    }
}
