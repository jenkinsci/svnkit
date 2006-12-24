/*
 * ====================================================================
 * Copyright (c) 2004-2006 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */

package org.tmatesoft.svn.cli;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.Set;

import org.tmatesoft.svn.core.SVNException;

/**
 * @version 1.1.0
 * @author  TMate Software Ltd.
 */
public abstract class SVNArgument {

    public static final SVNArgument PASSWORD = createStringArgument(new String[] { "--password" });
    public static final SVNArgument USERNAME = createStringArgument(new String[] { "--username" });
    public static final SVNArgument CONFIG_DIR = createStringArgument(new String[] { "--config-dir" });

    public static final SVNArgument NON_RECURSIVE = createUnaryArgument(new String[] { "--non-recursive", "-N" });
	public static final SVNArgument NO_AUTO_PROPS = createUnaryArgument(new String[] { "--no-auto-props"});
    public static final SVNArgument AUTO_PROPS = createUnaryArgument(new String[] { "--auto-props"});
    public static final SVNArgument IGNORE_ANCESTRY = createUnaryArgument(new String[] {"--ignore-ancestry"});
    public static final SVNArgument REV_PROP = createUnaryArgument(new String[] { "--revprop"} );
    public static final SVNArgument RECURSIVE = createUnaryArgument(new String[] { "--recursive", "-R" });
    public static final SVNArgument VERBOSE = createUnaryArgument(new String[] { "--verbose", "-v" });
    public static final SVNArgument NO_DIFF_DELETED = createUnaryArgument(new String[] {"--no-diff-deleted"});
    public static final SVNArgument NO_DIFF_ADDED = createUnaryArgument(new String[] {"--no-diff-added"});
    public static final SVNArgument DIFF_COPY_FROM = createUnaryArgument(new String[] {"--diff-copy-from"});
    public static final SVNArgument USE_ANCESTRY = createUnaryArgument(new String[] {"--notice-ancestry"});
    public static final SVNArgument QUIET = createUnaryArgument(new String[] { "--quiet", "-q" });
    public static final SVNArgument SHOW_UPDATES = createUnaryArgument(new String[] { "--show-updates", "-u" });
    public static final SVNArgument NO_IGNORE = createUnaryArgument(new String[] { "--no-ignore" });
    public static final SVNArgument MESSAGE = createStringArgument(new String[] { "--message", "-m" });
    public static final SVNArgument REVISION = createStringArgument(new String[] { "--revision", "-r" });
    public static final SVNArgument OLD = createStringArgument(new String[] {"--old"});
    public static final SVNArgument NEW = createStringArgument(new String[] {"--new"});
    public static final SVNArgument NO_AUTH_CACHE = createUnaryArgument(new String[] {"--no-auth-cache"});
    public static final SVNArgument FORCE = createUnaryArgument(new String[] { "--force" });
    public static final SVNArgument FORCE_LOG = createUnaryArgument(new String[] { "--force-log" });
    public static final SVNArgument FILE = createStringArgument(new String[] { "-F" });
    public static final SVNArgument EDITOR_CMD = createStringArgument(new String[] { "--editor-cmd" });
    public static final SVNArgument STRICT = createUnaryArgument(new String[] { "--strict" });
    public static final SVNArgument STOP_ON_COPY = createUnaryArgument(new String[] { "--stop-on-copy" });
    public static final SVNArgument NO_UNLOCK = createUnaryArgument(new String[] { "--no-unlock" });
    public static final SVNArgument RELOCATE = createUnaryArgument(new String[] { "--relocate" });
    public static final SVNArgument EOL_STYLE = createStringArgument(new String[] { "--native-eol" });
    public static final SVNArgument DRY_RUN = createUnaryArgument(new String[] { "--dry-run" });
    public static final SVNArgument INCREMENTAL = createUnaryArgument(new String[] { "--incremental" });
    public static final SVNArgument XML = createUnaryArgument(new String[] { "--xml" });
    public static final SVNArgument LIMIT = createStringArgument(new String[] { "--limit" });
    public static final SVNArgument NON_INTERACTIVE = createUnaryArgument(new String[] { "--non-interactive" });
    public static final SVNArgument CHANGE = createStringArgument(new String[] { "--change", "-c" });
    public static final SVNArgument SUMMARIZE = createUnaryArgument(new String[] { "--summarize" });

    public static final SVNArgument EXTENSIONS = createUnaryArgument(new String[] { "-x", "--extensions" });
    public static final SVNArgument IGNORE_WS_CHANGE = createUnaryArgument(new String[] { "-b", "--ignore-space-change" });
    public static final SVNArgument IGNORE_ALL_WS = createUnaryArgument(new String[] { "-w", "--ignore-all-space" });
    public static final SVNArgument IGNORE_EOL_STYLE = createUnaryArgument(new String[] { "--ignore-eol-style" });

    public static final SVNArgument FS_TYPE = createStringArgument(new String[] { "--fs-type" });
    public static final SVNArgument PRE_14_COMPATIBLE = createUnaryArgument(new String[] { "--pre-1.4-compatible" });
    public static final SVNArgument BDB_TXN_NOSYNC = createUnaryArgument(new String[] { "--bdb-txn-nosync" });
    public static final SVNArgument BDB_LOG_KEEP = createUnaryArgument(new String[] { "--bdb-log-keep" });
    public static final SVNArgument DELTAS = createUnaryArgument(new String[] { "--deltas" });
    public static final SVNArgument IGNORE_UUID = createUnaryArgument(new String[] { "--ignore-uuid" });
    public static final SVNArgument FORCE_UUID = createUnaryArgument(new String[] { "--force-uuid" });
    public static final SVNArgument USE_PRECOMMIT_HOOK = createUnaryArgument(new String[] { "--use-pre-commit-hook" });
    public static final SVNArgument USE_POSTCOMMIT_HOOK = createUnaryArgument(new String[] { "--use-post-commit-hook" });
    public static final SVNArgument PARENT_DIR = createStringArgument(new String[] { "--parent-dir" });

    public static final SVNArgument TRANSACTION = createStringArgument(new String[] { "--transaction", "-t" });
    public static final SVNArgument COPY_INFO = createUnaryArgument(new String[] { "--copy-info" });
    public static final SVNArgument SHOW_IDS = createUnaryArgument(new String[] { "--show-ids" });
    public static final SVNArgument FULL_PATHS = createUnaryArgument(new String[] { "--full-paths" });
    
    
    public static SVNArgument findArgument(String name, Set validArguments) {
        for (Iterator arguments = validArguments.iterator(); arguments.hasNext();) {
            SVNArgument argument = (SVNArgument) arguments.next();
            for (Iterator names = argument.names(); names.hasNext();) {
                String argumentName = (String) names.next();
                if (argumentName.equals(name)) {
                    return argument;
                }
            }
        }
        return null;
    }

    private static SVNArgument createStringArgument(String[] names) {
        return new SVNStringArgument(names);
    }

    private static SVNArgument createUnaryArgument(String[] names) {
        return new SVNUnaryArgument(names);
    }

    private ArrayList myNames;

    private SVNArgument(String[] names) {
        myNames = new ArrayList();
        for (int i = 0; i < names.length; i++) {
            myNames.add(names[i]);
        }
    }

    public Iterator names() {
        return myNames.iterator();
    }

    public abstract boolean hasValue();

    public abstract Object parseValue(String value) throws SVNException;

    private static class SVNUnaryArgument extends SVNArgument {
        protected SVNUnaryArgument(String[] names) {
            super(names);
        }

        public boolean hasValue() {
            return false;
        }

        public Object parseValue(String value) throws SVNException {
            return null;
        }
    }

    private static class SVNStringArgument extends SVNArgument {
        protected SVNStringArgument(String[] names) {
            super(names);
        }

        public boolean hasValue() {
            return true;
        }

        public Object parseValue(String value) throws SVNException {
            return value;
        }
    }
}