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
package org.tmatesoft.svn.cli.svndumpfilter;

import org.tmatesoft.svn.cli.AbstractSVNOption;


/**
 * @version 1.3
 * @author  TMate Software Ltd.
 */
public class SVNDumpFilterOption extends AbstractSVNOption {

    public static final SVNDumpFilterOption HELP = new SVNDumpFilterOption("help", "h", true);
    public static final SVNDumpFilterOption QUESTION = new SVNDumpFilterOption("?", null, true);
    public static final SVNDumpFilterOption VERSION = new SVNDumpFilterOption("version", null, true);
    public static final SVNDumpFilterOption QUIET = new SVNDumpFilterOption("quiet", "q", true);
    public static final SVNDumpFilterOption DROP_EMPTY_REVISIONS = new SVNDumpFilterOption("drop-empty-revs", null, true);
    public static final SVNDumpFilterOption RENUMBER_REVISIONS = new SVNDumpFilterOption("renumber-revs", null, true);
    public static final SVNDumpFilterOption SKIP_MISSING_MERGE_SOURCES = new SVNDumpFilterOption("skip-missing-merge-sources", null, true);
    public static final SVNDumpFilterOption PRESERVE_REVISION_PROPERTIES = new SVNDumpFilterOption("preserve-revprops", null, true);
    public static final SVNDumpFilterOption TARGETS = new SVNDumpFilterOption("targets", null, false);
    
    private SVNDumpFilterOption(String name, String alias, boolean unary) {
        super(name, alias, unary);
    }
    
    protected String getResourceBundleName() {
        return "org.tmatesoft.svn.cli.svndumpfilter.options";
    }

}
