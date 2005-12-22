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

package org.tmatesoft.svn.cli;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import org.tmatesoft.svn.core.SVNException;

/**
 * @author TMate Software Ltd.
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

    public static SVNArgument findArgument(String name) {
        for (Iterator arguments = ourArguments.iterator(); arguments.hasNext();) {
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

    private static Set ourArguments;

    static {
        ourArguments = new HashSet();
        ourArguments.add(SVNArgument.PASSWORD);
        ourArguments.add(SVNArgument.USERNAME);
        ourArguments.add(SVNArgument.CONFIG_DIR);

        ourArguments.add(SVNArgument.NON_RECURSIVE);
        ourArguments.add(SVNArgument.RECURSIVE);
        ourArguments.add(SVNArgument.VERBOSE);
        ourArguments.add(SVNArgument.QUIET);
        ourArguments.add(SVNArgument.SHOW_UPDATES);
        ourArguments.add(SVNArgument.NO_IGNORE);
        ourArguments.add(SVNArgument.MESSAGE);
        ourArguments.add(SVNArgument.REVISION);
        ourArguments.add(SVNArgument.FORCE);
        ourArguments.add(SVNArgument.FORCE_LOG);
        ourArguments.add(SVNArgument.FILE);
        ourArguments.add(SVNArgument.EDITOR_CMD);
        ourArguments.add(SVNArgument.STRICT);
        ourArguments.add(SVNArgument.NO_UNLOCK);
        ourArguments.add(SVNArgument.NO_AUTH_CACHE);
        ourArguments.add(SVNArgument.RELOCATE);
        ourArguments.add(SVNArgument.EOL_STYLE);
        ourArguments.add(SVNArgument.NO_DIFF_DELETED);
        ourArguments.add(SVNArgument.USE_ANCESTRY);
        ourArguments.add(SVNArgument.OLD);
        ourArguments.add(SVNArgument.NEW);
        ourArguments.add(SVNArgument.DRY_RUN);
        ourArguments.add(SVNArgument.IGNORE_ANCESTRY);
        ourArguments.add(SVNArgument.NO_AUTO_PROPS);
        ourArguments.add(SVNArgument.AUTO_PROPS);
        ourArguments.add(SVNArgument.REV_PROP);
        ourArguments.add(SVNArgument.INCREMENTAL);
        ourArguments.add(SVNArgument.XML);
        ourArguments.add(SVNArgument.LIMIT);
        ourArguments.add(SVNArgument.STOP_ON_COPY);
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