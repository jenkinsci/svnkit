/*
 * ====================================================================
 * Copyright (c) 2004-2006 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.core.internal.wc;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.internal.wc.admin.SVNAdminArea;
import org.tmatesoft.svn.core.internal.wc.admin.SVNVersionedProperties;
import org.tmatesoft.svn.core.internal.wc.admin.SVNWCAccess;
import org.tmatesoft.svn.core.io.ISVNWorkspaceMediator;
import org.tmatesoft.svn.core.wc.SVNCommitItem;

/**
 * @version 1.1.0
 * @author  TMate Software Ltd.
 */
public class SVNCommitMediator implements ISVNWorkspaceMediator {

    private Collection myTmpFiles;
    private Map myWCPropsMap;
    private Map myCommitItems;

    public SVNCommitMediator(Map commitItems) {
        myTmpFiles = new ArrayList();
        myWCPropsMap = new HashMap();
        myCommitItems = commitItems;
    }

    public Map getWCProperties(SVNCommitItem item) {
        return (Map) myWCPropsMap.get(item);
    }

    public Collection getTmpFiles() {
        return myTmpFiles;
    }

    public String getWorkspaceProperty(String path, String name) throws SVNException {
        SVNCommitItem item = (SVNCommitItem) myCommitItems.get(path);
        if (item == null) {
            return null;
        }
        SVNAdminArea dir;
        String target;
        SVNWCAccess wcAccess = item.getWCAccess();
        if (item.getKind() == SVNNodeKind.DIR) {
            dir = wcAccess.retrieve(item.getFile());
            target = "";
        } else {
            dir = wcAccess.retrieve(item.getFile().getParentFile());
            target = SVNPathUtil.tail(item.getPath());
        }
        SVNVersionedProperties wcProps = dir.getWCProperties(target);
        return wcProps.getPropertyValue(name);
    }

    public void setWorkspaceProperty(String path, String name, String value)
            throws SVNException {
        if (name == null) {
            return;
        }
        SVNCommitItem item = (SVNCommitItem) myCommitItems.get(path);
        if (!myWCPropsMap.containsKey(item)) {
            myWCPropsMap.put(item, new HashMap());
        }

        ((Map) myWCPropsMap.get(item)).put(name, value);
    }
}
