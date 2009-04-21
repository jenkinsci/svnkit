/*
 * ====================================================================
 * Copyright (c) 2004-2009 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.core.wc;

/**
 * @author TMate Software Ltd.
 * @version 1.2.0
 */
public class SVNEditorAction {

    public static SVNEditorAction ADD = new SVNEditorAction("add"); 
    public static SVNEditorAction MODIFY = new SVNEditorAction("modify");
    public static SVNEditorAction DELETE = new SVNEditorAction("delete");

    private String myName;

    private SVNEditorAction(String name) {
        myName = name;
    }

    public String toString() {
        return myName;
    }
}
