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

import org.tmatesoft.svn.core.io.SVNException;

/**
 * @author TMate Software Ltd.
 */
public abstract class SVNArgument {
    
    public static final SVNArgument PASSWORD = createStringArgument(new String[] {"--password"});
    public static final SVNArgument USERNAME = createStringArgument(new String[] {"--username"});
    public static final SVNArgument CONFIG_DIR = createStringArgument(new String[] {"--config-dir"});

    public static final SVNArgument NON_RECURSIVE = createUnaryArgument(new String[] {"--non-recursive", "-N"});
    public static final SVNArgument RECURSIVE = createUnaryArgument(new String[] {"--recursive", "-R"});
    public static final SVNArgument VERBOSE = createUnaryArgument(new String[] {"--verbose", "-v"});
    public static final SVNArgument QUIET = createUnaryArgument(new String[] {"--quiet", "-q"});
    public static final SVNArgument SHOW_UPDATES = createUnaryArgument(new String[] {"--show-updates", "-u"});
    public static final SVNArgument NO_IGNORE = createUnaryArgument(new String[] {"--no-ignore"});
    public static final SVNArgument MESSAGE = createStringArgument(new String[] {"--message", "-m"});
    public static final SVNArgument REVISION = createStringArgument(new String[] {"--revision", "-r"});
    
    public static SVNArgument findArgument(String name) {
        for(Iterator arguments = ourArguments.iterator(); arguments.hasNext();) {
            SVNArgument argument = (SVNArgument) arguments.next();
            for(Iterator names = argument.names(); names.hasNext();) {
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
        for(int i = 0; i < names.length; i++) {
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