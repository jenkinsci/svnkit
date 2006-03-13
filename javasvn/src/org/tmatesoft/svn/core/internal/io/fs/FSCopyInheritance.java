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
package org.tmatesoft.svn.core.internal.io.fs;


/**
 * @version 1.0
 * @author  TMate Software Ltd.
 */
public class FSCopyInheritance {
    private FSCopyIDInheritanceStyle myStyle;
    private String myCopySourcePath;
    
    public FSCopyInheritance(FSCopyIDInheritanceStyle style, String path) {
        myStyle = style;
        myCopySourcePath = path;
    }

    public String getCopySourcePath() {
        return myCopySourcePath;
    }
    
    public FSCopyIDInheritanceStyle getStyle() {
        return myStyle;
    }

    public void setCopySourcePath(String copySourcePath) {
        myCopySourcePath = copySourcePath;
    }
    
    public void setStyle(FSCopyIDInheritanceStyle style) {
        myStyle = style;
    }
    
}
