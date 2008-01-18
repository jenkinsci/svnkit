/*
 * ====================================================================
 * Copyright (c) 2004-2007 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html.
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.cli2.svn;

import java.util.Collection;

import org.tmatesoft.svn.cli2.AbstractSVNOption;


/**
 * @version 1.1.2
 * @author  TMate Software Ltd.
 */
public class SVNOption extends AbstractSVNOption {

    public static final SVNOption VERBOSE = new SVNOption("verbose", "v");
    public static final SVNOption UPDATE = new SVNOption("show-updates", "u");
    public static final SVNOption NON_RECURSIVE = new SVNOption("non-recursive", "N");
    public static final SVNOption DEPTH = new SVNOption("depth", false);
    public static final SVNOption QUIET = new SVNOption("quiet", "q");
    public static final SVNOption NO_IGNORE = new SVNOption("no-ignore");
    public static final SVNOption INCREMENTAL = new SVNOption("incremental");
    public static final SVNOption XML = new SVNOption("xml");
    public static final SVNOption CONFIG_DIR = new SVNOption("config-dir", false);
    public static final SVNOption IGNORE_EXTERNALS = new SVNOption("ignore-externals");
    public static final SVNOption CHANGELIST = new SVNOption("changelist", false);
    public static final SVNOption HELP = new SVNOption("help", "h");
    public static final SVNOption QUESTION = new SVNOption(null, "?");
    public static final SVNOption VERSION = new SVNOption("version");

    public static final SVNOption RECURSIVE = new SVNOption("recursive", "R");
    public static final SVNOption REVISION = new SVNOption("revision", "r", false);
    public static final SVNOption CHANGE = new SVNOption("change", "c", false);
    public static final SVNOption REVPROP = new SVNOption("revprop");
    public static final SVNOption STRICT = new SVNOption("strict");

    public static final SVNOption FILE = new SVNOption("file", "F", false);
    public static final SVNOption ENCODING = new SVNOption("encoding", false);
    public static final SVNOption TARGETS = new SVNOption("targets", false);
    public static final SVNOption FORCE = new SVNOption("force");
    public static final SVNOption FORCE_LOG = new SVNOption("force-log");
    public static final SVNOption MESSAGE = new SVNOption("message", "m", false);
    public static final SVNOption WITH_REVPROP = new SVNOption("with-revprop", false);
    public static final SVNOption EDITOR_CMD = new SVNOption("editor-cmd", false);

    public static final SVNOption NO_UNLOCK = new SVNOption("no-unlock");
    public static final SVNOption DRY_RUN = new SVNOption("dry-run");
    public static final SVNOption RECORD_ONLY = new SVNOption("record-only");
    public static final SVNOption USE_MERGE_HISTORY = new SVNOption("use-merge-history", "g");
    public static final SVNOption EXTENSIONS = new SVNOption("extensions", "x", false);
    public static final SVNOption IGNORE_ANCESTRY = new SVNOption("ignore-ancestry");
    public static final SVNOption NATIVE_EOL = new SVNOption("native-eol", false);
    public static final SVNOption RELOCATE = new SVNOption("relocate");
    public static final SVNOption AUTOPROPS = new SVNOption("auto-props");
    public static final SVNOption NO_AUTOPROPS = new SVNOption("no-auto-props");
    public static final SVNOption KEEP_CHANGELIST = new SVNOption("keep-changelist");
    public static final SVNOption PARENTS = new SVNOption("parents");
    public static final SVNOption KEEP_LOCAL = new SVNOption("keep-local");
    public static final SVNOption ACCEPT = new SVNOption("accept", false);
    public static final SVNOption REMOVE = new SVNOption("remove");
    public static final SVNOption FROM_SOURCE = new SVNOption("from-source", false);

    public static final SVNOption OLD = new SVNOption("old", false);
    public static final SVNOption NEW = new SVNOption("new", false);
    public static final SVNOption SUMMARIZE = new SVNOption("summarize");
    public static final SVNOption NOTICE_ANCESTRY = new SVNOption("notice-ancestry");
    public static final SVNOption NO_DIFF_DELETED = new SVNOption("no-diff-deleted");
    public static final SVNOption STOP_ON_COPY = new SVNOption("stop-on-copy");
    public static final SVNOption LIMIT = new SVNOption("limit", "l", false);
    public static final SVNOption WITH_ALL_REVPROPS = new SVNOption("with-all-revprops");
    
    // auth options.
    public static final SVNOption USERNAME = new SVNOption("username", false);
    public static final SVNOption PASSWORD = new SVNOption("password", false);
    public static final SVNOption NO_AUTH_CACHE = new SVNOption("no-auth-cache");
    public static final SVNOption NON_INTERACTIVE = new SVNOption("non-interactive");
    
    public static Collection addAuthOptions(Collection target) {
        if (target != null) {
            target.add(USERNAME);
            target.add(PASSWORD);
            target.add(NO_AUTH_CACHE);
            target.add(NON_INTERACTIVE);
        }
        return target;
    }
    
    public static Collection addLogMessageOptions(Collection target) {
        if (target != null) {
            target.add(MESSAGE);
            target.add(FILE);
            target.add(FORCE_LOG);
            target.add(EDITOR_CMD);
            target.add(ENCODING);
            target.add(WITH_REVPROP);
        }
        return target;
    }
    
    private SVNOption(String name) {
        this(name, null, true);
    }

    private SVNOption(String name, boolean unary) {
        this(name, null, unary);
    }

    private SVNOption(String name, String alias) {
        this(name, alias, true);
    }

    private SVNOption(String name, String alias, boolean unary) {
        super(name, alias, unary);
    }

    protected String getResourceBundleName() {
        return "org.tmatesoft.svn.cli2.svn.options";
    }
}
