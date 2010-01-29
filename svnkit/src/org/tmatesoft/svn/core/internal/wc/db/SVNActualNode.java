/*
 * ====================================================================
 * Copyright (c) 2004-2010 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html.
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.core.internal.wc.db;

import org.tmatesoft.svn.core.SVNProperties;
import org.tmatesoft.svn.core.wc.SVNChangelistClient;


/**
 * @version 1.3
 * @author  TMate Software Ltd.
 */
public class SVNActualNode {

    private long myWCId;
    private String myLocalRelPath;
    private String myParentRelPath;
    private SVNProperties myProperties;
    private String myConflictOld;
    private String myConflictNew;
    private String myConflictWorking;
    private String myPropReject;
    private String myChangeList;
    private String myTreeConflictData;
    
    public static SVNActualNode maybeCreateActualNode(SVNActualNode actualNode) {
        if (actualNode != null) {
            return actualNode;
        }
        return new SVNActualNode();
    }

    
    public long getWCId() {
        return myWCId;
    }

    
    public void setWCId(long wCId) {
        myWCId = wCId;
    }

    
    public String getLocalRelPath() {
        return myLocalRelPath;
    }

    
    public void setLocalRelPath(String localRelPath) {
        myLocalRelPath = localRelPath;
    }

    
    public String getParentRelPath() {
        return myParentRelPath;
    }

    
    public void setParentRelPath(String parentRelPath) {
        myParentRelPath = parentRelPath;
    }

    
    public SVNProperties getProperties() {
        return myProperties;
    }

    
    public void setProperties(SVNProperties properties) {
        myProperties = properties;
    }

    
    public String getConflictOld() {
        return myConflictOld;
    }

    
    public void setConflictOld(String conflictOld) {
        myConflictOld = conflictOld;
    }

    
    public String getConflictNew() {
        return myConflictNew;
    }

    
    public void setConflictNew(String conflictNew) {
        myConflictNew = conflictNew;
    }

    
    public String getConflictWorking() {
        return myConflictWorking;
    }

    
    public void setConflictWorking(String conflictWorking) {
        myConflictWorking = conflictWorking;
    }

    
    public String getPropReject() {
        return myPropReject;
    }

    
    public void setPropReject(String propReject) {
        myPropReject = propReject;
    }

    
    public String getChangeList() {
        return myChangeList;
    }

    public void setChangeList(String changeList) {
        myChangeList = changeList;
    }
    
    public String getTreeConflictData() {
        return myTreeConflictData;
    }
    
    public void setTreeConflictData(String treeConflictData) {
        myTreeConflictData = treeConflictData;
    }
}
