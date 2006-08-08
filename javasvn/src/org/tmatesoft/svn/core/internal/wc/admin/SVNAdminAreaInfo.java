/*
 * ====================================================================
 * Copyright (c) 2004-2006 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://tmate.org/svn/license.html.
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.core.internal.wc.admin;


/**
 * @version 1.0
 * @author  TMate Software Ltd.
 */
public class SVNAdminAreaInfo {
    
    private String myTargetName;
    private SVNAdminArea myTarget;
    private SVNAdminArea myAnchor;

    protected SVNAdminAreaInfo(SVNAdminArea anchor, SVNAdminArea target, String targetName) {
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

}
