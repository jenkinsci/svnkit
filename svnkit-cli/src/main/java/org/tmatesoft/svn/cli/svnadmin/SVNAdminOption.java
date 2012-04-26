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
package org.tmatesoft.svn.cli.svnadmin;

import org.tmatesoft.svn.cli.AbstractSVNOption;


/**
 * @version 1.3
 * @author  TMate Software Ltd.
 */
public class SVNAdminOption extends AbstractSVNOption {

    public static final SVNAdminOption HELP = new SVNAdminOption("help", "h");
    public static final SVNAdminOption QUESTION = new SVNAdminOption(null, "?");
    public static final SVNAdminOption VERSION = new SVNAdminOption("version");
    public static final SVNAdminOption REVISION = new SVNAdminOption("revision", "r", false);
    public static final SVNAdminOption INCREMENTAL = new SVNAdminOption("incremental");
    public static final SVNAdminOption DELTAS = new SVNAdminOption("deltas");
    public static final SVNAdminOption BYPASS_HOOKS = new SVNAdminOption("bypass-hooks");
    public static final SVNAdminOption QUIET = new SVNAdminOption("quiet", "q");
    public static final SVNAdminOption IGNORE_UUID = new SVNAdminOption("ignore-uuid");
    public static final SVNAdminOption FORCE_UUID = new SVNAdminOption("force-uuid");
    public static final SVNAdminOption PARENT_DIR = new SVNAdminOption("parent-dir", null, false);
    public static final SVNAdminOption FS_TYPE = new SVNAdminOption("fs-type", null, false);
    public static final SVNAdminOption BDB_TXN_NOSYNC = new SVNAdminOption("bdb-txn-nosync");
    public static final SVNAdminOption BDB_LOG_KEEP = new SVNAdminOption("bdb-log-keep");

    public static final SVNAdminOption CONFIG_DIR = new SVNAdminOption("config-dir", null, false);
    public static final SVNAdminOption CLEAN_LOGS = new SVNAdminOption("clean-logs");
    public static final SVNAdminOption USE_PRE_COMMIT_HOOK = new SVNAdminOption("use-pre-commit-hook");
    public static final SVNAdminOption USE_POST_COMMIT_HOOK = new SVNAdminOption("use-post-commit-hook");
    public static final SVNAdminOption USE_PRE_REVPROP_CHANGE_HOOK = new SVNAdminOption("use-pre-revprop-change-hook");
    public static final SVNAdminOption USE_POST_REVPROP_CHANGE_HOOK = new SVNAdminOption("use-post-revprop-change-hook");
    public static final SVNAdminOption WAIT = new SVNAdminOption("wait");
    public static final SVNAdminOption PRE_14_COMPATIBLE = new SVNAdminOption("pre-1.4-compatible");
    public static final SVNAdminOption PRE_15_COMPATIBLE = new SVNAdminOption("pre-1.5-compatible");
    public static final SVNAdminOption PRE_16_COMPATIBLE = new SVNAdminOption("pre-1.6-compatible");
    public static final SVNAdminOption PRE_17_COMPATIBLE = new SVNAdminOption("pre-1.7-compatible");
    public static final SVNAdminOption WITH_17_COMPATIBLE = new SVNAdminOption("with-1.7-compatible");

    private SVNAdminOption(String name) {
        this(name, null, true);
    }

    private SVNAdminOption(String name, String alias) {
        this(name, alias, true);
    }

    private SVNAdminOption(String name, String alias, boolean unary) {
        super(name, alias, unary);
    }

    protected String getResourceBundleName() {
        return "org.tmatesoft.svn.cli.svnadmin.options";
    }
}
