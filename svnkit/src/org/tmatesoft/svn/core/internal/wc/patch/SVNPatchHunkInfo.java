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
package org.tmatesoft.svn.core.internal.wc.patch;


/**
 * @version 1.3
 * @author  TMate Software Ltd.
 */
public class SVNPatchHunkInfo {

    public static SVNPatchHunkInfo getHunkInfo(SVMPatchTarget target, SVNPatchHunk hunk, int fuzz) {
        return null;
    }

    private SVNPatchHunk hunk;

    public boolean isRejected() {
        return false;
    }
    
    public SVNPatchHunk getHunk() {
        return hunk;
    }

    public int getMatchedLine() {
        return 0;
    }

    public int getFuzz() {
        return 0;
    }

}
