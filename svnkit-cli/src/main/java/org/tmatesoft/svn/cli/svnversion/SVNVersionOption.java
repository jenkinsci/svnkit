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
package org.tmatesoft.svn.cli.svnversion;

import org.tmatesoft.svn.cli.AbstractSVNOption;


/**
 * @version 1.3
 * @author  TMate Software Ltd.
 */
public class SVNVersionOption extends AbstractSVNOption {
    
    public static final SVNVersionOption NO_NEWLINE = new SVNVersionOption("no-newline", "n", true);
    public static final SVNVersionOption COMMITTED = new SVNVersionOption("committed", "c", true);
    public static final SVNVersionOption HELP = new SVNVersionOption("help", "h", true);
    public static final SVNVersionOption VERSION = new SVNVersionOption("version", null, true);

    private SVNVersionOption(String name, String alias, boolean unary) {
        super(name, alias, unary);
    }

    protected String getResourceBundleName() {
        return "org.tmatesoft.svn.cli.svnversion.options";
    }

}
