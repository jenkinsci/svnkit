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
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.tigris.subversion.javahl.PropertyData;
import org.tmatesoft.svn.cli2.SVNCommandTarget;
import org.tmatesoft.svn.cli2.SVNCommandUtil;
import org.tmatesoft.svn.cli2.SVNOption;
import org.tmatesoft.svn.cli2.SVNXMLCommand;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.util.SVNEncodingUtil;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.wc.ISVNPropertyHandler;
import org.tmatesoft.svn.core.wc.SVNChangelistClient;
import org.tmatesoft.svn.core.wc.SVNPropertyData;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNWCClient;


/**
 * @version 1.1.2
 * @author  TMate Software Ltd.
 */
public class SVNPropGetCommand extends SVNXMLCommand implements ISVNPropertyHandler {

    private Map myRevisionProperties;
    private Map myURLProperties;
    private Map myPathProperties;

    public SVNPropGetCommand() {
        super("propget", new String[] {"pget", "pg"});
        clearCollectedProperties();
    }
    
    protected void clearCollectedProperties() {
        myRevisionProperties = new LinkedHashMap();
        myURLProperties = new LinkedHashMap();
        myPathProperties = new LinkedHashMap();
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
            getEnvironment().setCurrentTarget(new SVNCommandTarget(""));
            SVNChangelistClient changelistClient = getEnvironment().getClientManager().getChangelistClient();
            changelistClient.getChangelist(getEnvironment().getCurrentTargetFile(), getEnvironment().getChangelist(), targets);
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
            PropertyData propertyValue = getRevisionProperty(rev);
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
                    getEnvironment().setCurrentTarget(target);
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
                    String path = SVNCommandUtil.getLocalPath(getEnvironment().getCurrentTargetRelativePath((File) key));
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
                target = SVNCommandUtil.getLocalPath(getEnvironment().getCurrentTargetRelativePath((File) key));
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
    
    protected SVNURL getRevpropURL(SVNRevision revision, Collection targets) throws SVNException {
        if (revision != SVNRevision.HEAD && revision.getDate() == null && revision.getNumber() < 0) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CL_ARG_PARSING_ERROR, 
                    "Must specify revision as a number, a date or 'HEAD' when operating on revision property");
            SVNErrorManager.error(err);
        }
        if (targets.size() != 1) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CL_ARG_PARSING_ERROR, 
                "Wrong number of targets specified");
            SVNErrorManager.error(err);
        }
        String target = (String) targets.iterator().next();
        return getEnvironment().getURLFromTarget(target);
    }

    public void handleProperty(File path, SVNPropertyData property) throws SVNException {
        if (!myPathProperties.containsKey(path)) {
            myPathProperties.put(path, new LinkedList());
        }
        ((Collection) myPathProperties.get(path)).add(property);
    }

    public void handleProperty(SVNURL url, SVNPropertyData property) throws SVNException {
        if (!myURLProperties.containsKey(url)) {
            myURLProperties.put(url, new LinkedList());
        }
        ((Collection) myURLProperties.get(url)).add(property);
    }

    public void handleProperty(long revision, SVNPropertyData property) throws SVNException {
        Object key = new Long(revision);
        if (!myRevisionProperties.containsKey(key)) {
            myRevisionProperties.put(key, new LinkedList());
        }
        ((Collection) myRevisionProperties.get(new Long(revision))).add(property);
    }
    
    protected PropertyData getRevisionProperty(long revision) {
        Object key = new Long(revision);
        if (myRevisionProperties.containsKey(key)) {
            return (PropertyData) ((List) myRevisionProperties.get(new Long(revision))).get(0);
        }
        return null;
    }

    protected Map getURLProperties() {
        return myURLProperties;
    }


    protected Map getPathProperties() {
        return myPathProperties;
    }
}
