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
package org.tmatesoft.svn.core.internal.wc.admin;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.tmatesoft.svn.core.internal.util.SVNHashMap;
import java.util.Map;

import org.tmatesoft.svn.core.SVNDepth;


/**
 * @version 1.1.1
 * @author  TMate Software Ltd.
 */
public class SVNAdminAreaInfo {
    
    private String myTargetName;
    private SVNAdminArea myTarget;
    private SVNAdminArea myAnchor;
    private SVNWCAccess myAccess;
    
    private Map myNewExternals;
    private Map myOldExternals;
    private Map myDepths;
    private Set myIncompleteEntries;

    public SVNAdminAreaInfo(SVNWCAccess access, SVNAdminArea anchor, SVNAdminArea target, String targetName) {
        myAccess = access;
        myAnchor = anchor;
        myTarget = target;
        myTargetName = targetName;
    }
    
    public SVNAdminArea getAnchor() {
        return myAnchor;
    }
    
    public SVNAdminArea getTarget() {
        return myTarget;
    }
    
    public String getTargetName() {
        return myTargetName;
    }

    public SVNWCAccess getWCAccess() {
        return myAccess;
    }
    
    public void addOldExternal(String path, String oldValue) {
        if (myOldExternals == null) {
            myOldExternals = new SVNHashMap();
        }
        myOldExternals.put(path, oldValue);
    }

    public void addNewExternal(String path, String newValue) {
        if (myNewExternals == null) {
            myNewExternals = new SVNHashMap();
        }
        myNewExternals.put(path, newValue);
    }

    public void addExternal(String path, String oldValue, String newValue) {
        addNewExternal(path, newValue);
        addOldExternal(path, oldValue);
    }
    
    public void addDepth(String path, SVNDepth depth) {
        if (myDepths == null) {
            myDepths = new SVNHashMap();
        }
        myDepths.put(path, depth);
    }
    
    public void removeDepth(String path) {
        if (myDepths != null) {
            myDepths.remove(path);
        }
    }

    public void removeExternal(String path) {
        if (myNewExternals != null) {
            myNewExternals.remove(path);
        }
        if (myOldExternals != null) {
            myOldExternals.remove(path);
        }
    }
    
    public Map getNewExternals() {
        return myNewExternals == null ? Collections.EMPTY_MAP : myNewExternals;
    }

    public Map getOldExternals() {
        return myOldExternals == null ? Collections.EMPTY_MAP : myOldExternals;
    }

    public Map getDepths() {
        return myDepths == null ? Collections.EMPTY_MAP : myDepths;
    }
    
    public void addIncompleteEntry(String path) {
        if (myIncompleteEntries == null) {
            myIncompleteEntries = new HashSet();
        }
        myIncompleteEntries.add(path);
    }
    
    public boolean isIncomplete(String path) {
        return myIncompleteEntries != null && myIncompleteEntries.contains(path);
    }
}
