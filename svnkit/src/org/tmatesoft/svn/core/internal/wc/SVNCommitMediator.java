/*
 * ====================================================================
 * Copyright (c) 2004-2007 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.core.internal.wc;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNProperties;
import org.tmatesoft.svn.core.SVNPropertyValue;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.util.SVNHashMap;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.internal.wc.admin.SVNAdminArea;
import org.tmatesoft.svn.core.internal.wc.admin.SVNEntry;
import org.tmatesoft.svn.core.internal.wc.admin.SVNVersionedProperties;
import org.tmatesoft.svn.core.internal.wc.admin.SVNWCAccess;
import org.tmatesoft.svn.core.io.ISVNWorkspaceMediator;
import org.tmatesoft.svn.core.wc.SVNCommitItem;


/**
 * @version 1.1.1
 * @author  TMate Software Ltd.
 */
public class SVNCommitMediator implements ISVNWorkspaceMediator {

    private Collection myTmpFiles;
    private Map myWCPropsMap;
    private Map myCommitItems;
    private File myBaseFile;
    private SVNWCAccess myBaseAccess;

    public SVNCommitMediator(Map commitItems) {
        myTmpFiles = new ArrayList();
        myWCPropsMap = new SVNHashMap();
        myCommitItems = commitItems;
    }
    
    public void setBaseURL(SVNURL baseURL) throws SVNException {
        computeBaseFile(baseURL);
    }
 
    public SVNProperties getWCProperties(SVNCommitItem item) {
        return (SVNProperties) myWCPropsMap.get(item);
    }

    public Collection getTmpFiles() {
        return myTmpFiles;
    }

    public SVNPropertyValue getWorkspaceProperty(String path, String name) throws SVNException {
        SVNCommitItem item = (SVNCommitItem) myCommitItems.get(path);
        if (item == null) {
            // get using base file!
            if (myBaseFile != null) {
                File file = new File(myBaseFile, path);
                try {
                    SVNAdminArea dir = myBaseAccess.probeTry(file, false, 0);
                    if (dir != null) {
                        String entry = "";
                        if (!file.equals(dir.getRoot())) {
                            entry = file.getName();
                        }
                        SVNVersionedProperties props = dir.getWCProperties(entry);
                        if (props != null) {
                            return props.getPropertyValue(name);
                        }
                    }
                } catch (SVNException e) {
                    return null;
                } 
            }
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
        if (wcProps != null) {
            return wcProps.getPropertyValue(name);
        }
        return null;    
    }

    public void setWorkspaceProperty(String path, String name, SVNPropertyValue value)
            throws SVNException {
        if (name == null) {
            return;
        }
        SVNCommitItem item = (SVNCommitItem) myCommitItems.get(path);
        if (!myWCPropsMap.containsKey(item)) {
            myWCPropsMap.put(item, new SVNProperties());
        }

        ((SVNProperties) myWCPropsMap.get(item)).put(name, value);
    }
    
    private void computeBaseFile(SVNURL baseURL) throws SVNException {
        SVNWCAccess wcAccess = null;
        for(Iterator items = myCommitItems.values().iterator(); items.hasNext();) {
            SVNCommitItem commitItem = (SVNCommitItem) items.next();
            SVNWCAccess itemAccess = commitItem.getWCAccess();
            if (wcAccess == null) {
                wcAccess = itemAccess;
            } else if (wcAccess != itemAccess) {
                return;
            }
        }
        if (wcAccess != null) {
            SVNAdminArea[] areas = wcAccess.getAdminAreas();
            for (int i = 0; i < areas.length; i++) {
                SVNAdminArea dir = areas[i];
                SVNEntry rootEntry = dir.getEntry("", false);
                if (rootEntry != null && baseURL.equals(rootEntry.getSVNURL())) {
                    myBaseFile = dir.getRoot();
                    myBaseAccess = dir.getWCAccess();
                    return;
                }
            }
        }
    }
}
