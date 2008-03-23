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

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.tmatesoft.svn.cli2.SVNCommandUtil;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.util.SVNXMLUtil;
import org.tmatesoft.svn.core.internal.wc.SVNPath;
import org.tmatesoft.svn.core.wc.SVNPropertyData;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNWCClient;


/**
 * @version 1.1.2
 * @author  TMate Software Ltd.
 */
public class SVNPropListCommand extends SVNPropertiesCommand {

    public SVNPropListCommand() {
        super("proplist", new String[] {"plist", "pl"});
    }

    protected Collection createSupportedOptions() {
        Collection options = new ArrayList();
        options.add(SVNOption.VERBOSE);
        options.add(SVNOption.RECURSIVE);
        options.add(SVNOption.DEPTH);
        options.add(SVNOption.REVISION);
        options.add(SVNOption.QUIET);
        options.add(SVNOption.REVPROP);
        options.add(SVNOption.XML);
        options.add(SVNOption.CHANGELIST);
        return options;
    }

    public void run() throws SVNException {
        Collection targets = new ArrayList(); 
        targets = getSVNEnvironment().combineTargets(targets, true);
        if (targets.isEmpty()) {
            targets.add("");
        }

        if (getSVNEnvironment().isRevprop()) {
            SVNURL url = getRevpropURL(getSVNEnvironment().getStartRevision(), targets);
            SVNWCClient wcClient = getSVNEnvironment().getClientManager().getWCClient();
            long rev = wcClient.doGetRevisionProperty(url, null, getSVNEnvironment().getStartRevision(), this);
            Map revisionPropertiesMap = getRevisionProperties();
            List revisionProperties = (List) revisionPropertiesMap.get(new Long(rev));
            if (revisionProperties == null) {
                revisionProperties = Collections.EMPTY_LIST;
            }
            if (getSVNEnvironment().isXML()) {
                printXMLHeader("properties");
                StringBuffer buffer = openXMLTag("revprops", SVNXMLUtil.XML_STYLE_NORMAL, "rev", Long.toString(rev), null);
                for (Iterator props = revisionProperties.iterator(); props.hasNext();) {
                    SVNPropertyData property = (SVNPropertyData) props.next();
                    buffer = addXMLProp(property, buffer);
                }
                buffer = closeXMLTag("revprops", buffer);
                getSVNEnvironment().getOut().print(buffer);
                printXMLFooter("properties");
            } else {
                getSVNEnvironment().getOut().println("Unversioned properties on revision " + rev + ":");
                for (Iterator props = revisionProperties.iterator(); props.hasNext();) {
                    SVNPropertyData property = (SVNPropertyData) props.next();
                    getSVNEnvironment().getOut().print("  " + property.getName());
                    if (getSVNEnvironment().isVerbose()) {
                        getSVNEnvironment().getOut().print(" : ");
                        if (property.getValue().isString()){
                            getSVNEnvironment().getOut().print(property.getValue().getString());
                        } else {
                            try {
                                getSVNEnvironment().getOut().write(property.getValue().getBytes());
                            } catch (IOException e) {

                            }
                        }
                    }
                    getSVNEnvironment().getOut().println();
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
            
            Collection changeLists = getSVNEnvironment().getChangelistsCollection();
            SVNWCClient client = getSVNEnvironment().getClientManager().getWCClient();
            for (Iterator ts = targets.iterator(); ts.hasNext();) {
                String targetPath = (String) ts.next();
                SVNPath target = new SVNPath(targetPath, true);
                SVNRevision pegRevision = target.getPegRevision();
                try {
                    if (target.isURL()) {
                        client.doGetProperty(target.getURL(), null, pegRevision, getSVNEnvironment().getStartRevision(), depth, this);
                    } else {
                        client.doGetPropertyList(target.getFile(), null, pegRevision, 
                                getSVNEnvironment().getStartRevision(), depth, this, changeLists);
                    }
                } catch (SVNException e) {
                    getSVNEnvironment().handleWarning(e.getErrorMessage(), 
                            new SVNErrorCode[] {SVNErrorCode.UNVERSIONED_RESOURCE, SVNErrorCode.ENTRY_NOT_FOUND},
                            getSVNEnvironment().isQuiet());
                }
                if (!getSVNEnvironment().isXML()) {
                    printCollectedProperties(target.isURL());
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
    
    protected void printCollectedProperties(boolean isURL) {
        Map map = isURL ? getURLProperties() : getPathProperties();
        for (Iterator keys = map.keySet().iterator(); keys.hasNext();) {
            Object key = keys.next();
            List props = (List) map.get(key);
            if (!getSVNEnvironment().isQuiet()) {
                getSVNEnvironment().getOut().print("Properties on '");
                if (isURL) {
                    getSVNEnvironment().getOut().print(key);
                } else {
                    String path = SVNCommandUtil.getLocalPath(getSVNEnvironment().getRelativePath((File) key));
                    getSVNEnvironment().getOut().print(path);
                }
                getSVNEnvironment().getOut().println("':");
            }
            for (Iterator plist = props.iterator(); plist.hasNext();) {
                SVNPropertyData property = (SVNPropertyData) plist.next();
                getSVNEnvironment().getOut().print("  " + property.getName());
                if (getSVNEnvironment().isVerbose()) {
                    getSVNEnvironment().getOut().print(" : ");
                    printProperty(property.getValue());
                }
                getSVNEnvironment().getOut().println();
            }
        }
    }

    //TODO: in future replace all calls to this method within SVNProplistCommand with 
    //calls to SVNXMLCommand.printXMLPropHash()
    protected void printCollectedPropertiesXML(boolean isURL) {
        Map map = isURL ? getURLProperties() : getPathProperties();

        for (Iterator keys = map.keySet().iterator(); keys.hasNext();) {
            Object key = keys.next();
            List props = (List) map.get(key);
            String target = key.toString();
            if (!isURL) {
                target = SVNCommandUtil.getLocalPath(getSVNEnvironment().getRelativePath((File) key));
            } 
            StringBuffer buffer = openXMLTag("target", SVNXMLUtil.XML_STYLE_NORMAL, "path", target, null);
            for (Iterator plist = props.iterator(); plist.hasNext();) {
                SVNPropertyData property = (SVNPropertyData) plist.next();
                buffer = addXMLProp(property, buffer);
            }
            buffer = closeXMLTag("target", buffer);
            getSVNEnvironment().getOut().print(buffer);
        }
    }
}
