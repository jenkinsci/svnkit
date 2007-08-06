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
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.tmatesoft.svn.cli2.SVNXMLCommand;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.wc.ISVNPropertyHandler;
import org.tmatesoft.svn.core.wc.SVNPropertyData;
import org.tmatesoft.svn.core.wc.SVNRevision;


/**
 * @version 1.1.2
 * @author  TMate Software Ltd.
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
    
    protected SVNPropertyData getRevisionProperty(long revision) {
        Object key = new Long(revision);
        if (myRevisionProperties.containsKey(key)) {
            return (SVNPropertyData) ((List) myRevisionProperties.get(new Long(revision))).get(0);
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
}
