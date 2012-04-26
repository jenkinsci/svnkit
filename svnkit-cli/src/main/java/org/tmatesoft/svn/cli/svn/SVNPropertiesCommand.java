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
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.tmatesoft.svn.cli.SVNCommandUtil;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.SVNPropertyValue;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.wc.ISVNPropertyHandler;
import org.tmatesoft.svn.core.wc.SVNPropertyData;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.util.SVNLogType;


/**
 * @author TMate Software Ltd.
 * @version 1.3
 */
public abstract class SVNPropertiesCommand extends SVNXMLCommand implements ISVNPropertyHandler {

    private Map myRevisionProperties;
    private Map myURLProperties;
    private Map myPathProperties;

    protected SVNPropertiesCommand(String name, String[] aliases) {
        super(name, aliases);
        clearCollectedProperties();
    }

    protected void clearCollectedProperties() {
        myRevisionProperties = new LinkedHashMap();
        myURLProperties = new LinkedHashMap();
        myPathProperties = new LinkedHashMap();
    }

    protected SVNURL getRevpropURL(SVNRevision revision, Collection targets) throws SVNException {
        if (revision != SVNRevision.HEAD && revision.getDate() == null && revision.getNumber() < 0) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CL_ARG_PARSING_ERROR,
                    "Must specify revision as a number, a date or 'HEAD' when operating on revision property");
            SVNErrorManager.error(err, SVNLogType.CLIENT);
        }
        if (targets.size() != 1) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CL_ARG_PARSING_ERROR,
                    "Wrong number of targets specified");
            SVNErrorManager.error(err, SVNLogType.CLIENT);
        }
        String target = (String) targets.iterator().next();
        return getSVNEnvironment().getURLFromTarget(target);
    }

    protected String checkRevPropTarget(SVNRevision revision, Collection targets) throws SVNException {
        if (revision != SVNRevision.HEAD && revision.getDate() == null && revision.getNumber() < 0) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CL_ARG_PARSING_ERROR,
                    "Must specify revision as a number, a date or 'HEAD' when operating on revision property");
            SVNErrorManager.error(err, SVNLogType.CLIENT);
        }
        if (targets.size() != 1) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CL_ARG_PARSING_ERROR,
                    "Wrong number of targets specified");
            SVNErrorManager.error(err, SVNLogType.CLIENT);
        }
        return (String) targets.iterator().next();
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

    protected SVNPropertyData getRevisionProperty(long revision) {
        Object key = new Long(revision);
        if (myRevisionProperties.containsKey(key)) {
            return (SVNPropertyData) ((List) myRevisionProperties.get(new Long(revision))).get(0);
        }
        return null;
    }

    protected SVNPropertyData getPathProperty(File path) {
        if (myPathProperties.containsKey(path)) {
            return (SVNPropertyData) ((List) myPathProperties.get(path)).get(0);
        }
        return null;
    }

    protected SVNPropertyData getURLProperty(SVNURL url) {
        if (myURLProperties.containsKey(url)) {
            return (SVNPropertyData) ((List) myURLProperties.get(url)).get(0);
        }
        return null;
    }

    protected Map getURLProperties() {
        return myURLProperties;
    }

    protected Map getPathProperties() {
        return myPathProperties;
    }

    protected Map getRevisionProperties() {
        return myRevisionProperties;
    }

    protected void printProplist(List props) {
        for (Iterator plist = props.iterator(); plist.hasNext();) {
            SVNPropertyData property = (SVNPropertyData) plist.next();
            getSVNEnvironment().getOut().println("  " + property.getName());
            if (getSVNEnvironment().isVerbose()) {
                printProperty(property.getValue(), true);
            }
        }
    }
    
    protected void printProperty(SVNPropertyValue value, boolean isPropListLike) {
        if (value.isString()) {
            String stringValue = value.getString();
            if (isPropListLike) {
                stringValue += '\n';
                String[] lines = SVNCommandUtil.breakToLines(stringValue);
                for (int i = 0; lines != null && i < lines.length; i++) {
                    String line = lines[i];
                    getSVNEnvironment().getOut().print("    ");
                    getSVNEnvironment().getOut().print(line);
                }
            } else {
                getSVNEnvironment().getOut().print(stringValue);
            }
        } else {
            try {
                if (isPropListLike) {
                    //indent here
                    getSVNEnvironment().getOut().print("    ");
                }
                getSVNEnvironment().getOut().write(value.getBytes());
                if (isPropListLike) {
                    getSVNEnvironment().getOut().println();
                }
            } catch (IOException e) {
            }
        }
    }

    protected void checkBooleanProperty(String name, SVNPropertyValue value) throws SVNException {
        if (!SVNProperty.isBooleanProperty(name)) {
            return;
        }
        String stringValue = value.getString().trim();
        if ("".equals(stringValue) || "off".equalsIgnoreCase(stringValue) || "no".equalsIgnoreCase(stringValue) || "false".equalsIgnoreCase(stringValue)) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.BAD_PROPERTY_VALUE, "To turn off the {0} property, use ''svn propdel'';\n" +
                    "setting the property to ''{1}'' will not turn it off.", new Object[]{name, value});
            getSVNEnvironment().handleWarning(err, new SVNErrorCode[]{SVNErrorCode.BAD_PROPERTY_VALUE},
                    getSVNEnvironment().isQuiet());
        }
    }
}
