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

import org.tmatesoft.svn.core.internal.wc.ISVNExtendedMergeCallback;

/**
 * <b>SVNEditorAction</b> is used in extended merge operations to provide to 
 * {@link ISVNExtendedMergeCallback} editor actions.
 * 
 * @author TMate Software Ltd.
 * @version 1.3
 * @since   1.3
 */
public class SVNEditorAction {

    /**
     * Add action.
     * 
     * @since 1.3
     */
    public static SVNEditorAction ADD = new SVNEditorAction("add"); 
    
    /**
     * Modify action.
     * 
     * @since 1.3
     */
    public static SVNEditorAction MODIFY = new SVNEditorAction("modify");
    
    /**
     * Delete action.
     * 
     * @since 1.3 
     */
    public static SVNEditorAction DELETE = new SVNEditorAction("delete");

    private String myName;

    private SVNEditorAction(String name) {
        myName = name;
    }

    /**
     * Returns a string representation of this object.
     * @return  string representation
     * @since   1.3
     */
    public String toString() {
        return myName;
    }
}
