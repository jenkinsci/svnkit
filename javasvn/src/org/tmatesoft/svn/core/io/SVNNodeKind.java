/*
 * ====================================================================
 * Copyright (c) 2004 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://tmate.org/svn/license.html.
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */

package org.tmatesoft.svn.core.io;

/**
 * @author Alexander Kitaev
 */
public final class SVNNodeKind {
    
    public static final SVNNodeKind NONE = new SVNNodeKind();
    public static final SVNNodeKind FILE = new SVNNodeKind();
    public static final SVNNodeKind DIR = new SVNNodeKind();
    public static final SVNNodeKind UNKNOWN = new SVNNodeKind();
    
    private SVNNodeKind() {}
    
    public static SVNNodeKind parseKind(String kind) {
        if ("file".equals(kind)) {
            return FILE;
        } else if ("dir".equals(kind)) {
            return DIR;
        } else if ("none".equals(kind) || kind == null) {
            return NONE;
        }
        return UNKNOWN;
    }
    
    public String toString() {
        if (this == NONE) {
            return "<none>";
        } else if (this == FILE) {
            return "<file>";
        } else if (this == DIR) {
            return "<dir>";
        }
        return "<unknown>";
    }

}
