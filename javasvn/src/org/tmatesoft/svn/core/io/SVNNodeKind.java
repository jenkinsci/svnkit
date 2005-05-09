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
 * <p>
 * The final class <code>SVNNodeKind</code> incapsulates the kind of a versioned node
 * stored in the Subversion repository. This can be:
 * <ol>
 * <li>a directory - the node is a directory
 * <li>a file      - the node is a file
 * <li>none        - the versioned node is absent (does not exist)
 * <li>unknown     - the node kind can not be recognized
 * </ol>
 * <code>SVNNodeKind</code> items are used to describe directory
 * entry type.
 * </p> 
 * @version 1.0
 * @author TMate Software Ltd.
 * @see SVNDirEntry
 */
public final class SVNNodeKind {
    /**
     * Defines the none node kind 
     */
    public static final SVNNodeKind NONE = new SVNNodeKind();
    /**
     * Defines the file node kind
     */
    public static final SVNNodeKind FILE = new SVNNodeKind();
    /**
     * Defines the directory node kind
     */
    public static final SVNNodeKind DIR = new SVNNodeKind();
    /**
     * Defines the unknown node kind
     */
    public static final SVNNodeKind UNKNOWN = new SVNNodeKind();
    /**
     * Default constructor.
     */
    private SVNNodeKind() {}
    /**
     * Parses the passed string and finds out the node kind. For instance,
     * parseKind("dir") will return <code>SVNNodeKind.DIR</code>.
     * @param kind a node kind as a string
     * @return node kind as <code>SVNNodeKind</code>. If the exact node kind is not known
     * <code>SVNNodeKind.UNKNOWN</code> is returned.
     */
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
    /**
     * <p>
     * Represents the current <code>SVNNodeKind</code> object as a string.
     * </p>
     * @return string representation of the node kind.
     */
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
