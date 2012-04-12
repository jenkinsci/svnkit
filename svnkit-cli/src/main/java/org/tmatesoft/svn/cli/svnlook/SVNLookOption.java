/*
 * ====================================================================
 * Copyright (c) 2004-2012 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html.
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.cli.svnlook;

import org.tmatesoft.svn.cli.AbstractSVNOption;


/**
 * @version 1.3
 * @author  TMate Software Ltd.
 */
public class SVNLookOption extends AbstractSVNOption {
    
    public static final SVNLookOption HELP = new SVNLookOption("help", "h", true);
    public static final SVNLookOption QUESTION = new SVNLookOption("?", null, true);
    public static final SVNLookOption VERSION = new SVNLookOption("version", null, true);
    public static final SVNLookOption COPY_INFO = new SVNLookOption("copy-info", null, true);
    public static final SVNLookOption DIFF_COPY_FROM = new SVNLookOption("diff-copy-from", null, true);
    public static final SVNLookOption FULL_PATHS = new SVNLookOption("full-paths", null, true);
    public static final SVNLookOption LIMIT = new SVNLookOption("limit", "l", false);
    public static final SVNLookOption NO_DIFF_ADDED = new SVNLookOption("no-diff-added", null, true);
    public static final SVNLookOption NO_DIFF_DELETED = new SVNLookOption("no-diff-deleted", null, true);
    public static final SVNLookOption NON_RECURSIVE = new SVNLookOption("non-recursive", "N", true);
    public static final SVNLookOption REVISION = new SVNLookOption("revision", "r", false);
    public static final SVNLookOption REVPROP = new SVNLookOption("revprop", null, true);
    public static final SVNLookOption SHOW_IDS = new SVNLookOption("show-ids", null, true);
    public static final SVNLookOption TRANSACTION = new SVNLookOption("transaction", "t", false);
    public static final SVNLookOption VERBOSE = new SVNLookOption("verbose", "v", true);
    public static final SVNLookOption XML = new SVNLookOption("xml", null, true);
    public static final SVNLookOption EXTENSIONS = new SVNLookOption("extensions", "x", false);

    private SVNLookOption(String name, String alias, boolean unary) {
        super(name, alias, unary);
    }

    protected String getResourceBundleName() {
        return "org.tmatesoft.svn.cli.svnlook.options";
    }
}
