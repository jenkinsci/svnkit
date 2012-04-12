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
package org.tmatesoft.svn.cli.svnsync;

import org.tmatesoft.svn.cli.AbstractSVNOption;


/**
 * @version 1.3
 * @author  TMate Software Ltd.
 */
public class SVNSyncOption extends AbstractSVNOption {
    
    public static final SVNSyncOption HELP = new SVNSyncOption("help", "h", true);
    public static final SVNSyncOption QUESTION = new SVNSyncOption("?", null, true);
    public static final SVNSyncOption VERSION = new SVNSyncOption("version", null, true);
    public static final SVNSyncOption CONFIG_DIR = new SVNSyncOption("config-dir", null, false);
    public static final SVNSyncOption SYNC_PASSWORD = new SVNSyncOption("sync-password", null, false);
    public static final SVNSyncOption SYNC_USERNAME = new SVNSyncOption("sync-username", null, false);
    public static final SVNSyncOption SOURCE_PASSWORD = new SVNSyncOption("source-password", null, false);
    public static final SVNSyncOption SOURCE_USERNAME = new SVNSyncOption("source-username", null, false);
    public static final SVNSyncOption USERNAME = new SVNSyncOption("username", null, false);
    public static final SVNSyncOption PASSWORD = new SVNSyncOption("password", null, false);
    public static final SVNSyncOption NO_AUTH_CACHE = new SVNSyncOption("no-auth-cache", null, true);
    public static final SVNSyncOption NON_INTERACTIVE = new SVNSyncOption("non-interactive", null, true);
    public static final SVNSyncOption QUIET = new SVNSyncOption("quiet", "q", true);
    public static final SVNSyncOption TRUST_SERVER_CERT = new SVNSyncOption("trust-server-cert", null, true);

    private SVNSyncOption(String name, String alias, boolean unary) {
        super(name, alias, unary);
    }

    protected String getResourceBundleName() {
        return "org.tmatesoft.svn.cli.svnsync.options";
    }
}
