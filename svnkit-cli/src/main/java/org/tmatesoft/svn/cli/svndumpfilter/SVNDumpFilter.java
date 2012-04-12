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

import org.tmatesoft.svn.cli.AbstractSVNCommand;
import org.tmatesoft.svn.cli.AbstractSVNCommandEnvironment;
import org.tmatesoft.svn.cli.AbstractSVNLauncher;
import org.tmatesoft.svn.cli.SVNCommandLine;


/**
 * @version 1.3
 * @author  TMate Software Ltd.
 */
public class SVNDumpFilter extends AbstractSVNLauncher {

    public static void main(String[] args) {
        new SVNDumpFilter().run(args);
    }
    
    protected AbstractSVNCommandEnvironment createCommandEnvironment() {
        return new SVNDumpFilterCommandEnvironment(getProgramName(), System.out, System.err, System.in);
    }

    protected String getProgramName() {
        return "jsvndumpfilter";
    }

    protected boolean needArgs() {
        return true;
    }

    protected boolean needCommand() {
        return true;
    }

    protected void registerCommands() {
        AbstractSVNCommand.registerCommand(new SVNDumpFilterHelpCommand());
        AbstractSVNCommand.registerCommand(new SVNDumpFilterExcludeCommand());
        AbstractSVNCommand.registerCommand(new SVNDumpFilterIncludeCommand());
    }

    protected void registerOptions() {
        SVNCommandLine.registerOption(SVNDumpFilterOption.HELP);
        SVNCommandLine.registerOption(SVNDumpFilterOption.QUESTION);
        SVNCommandLine.registerOption(SVNDumpFilterOption.QUIET);
        SVNCommandLine.registerOption(SVNDumpFilterOption.VERSION);
        SVNCommandLine.registerOption(SVNDumpFilterOption.DROP_EMPTY_REVISIONS);
        SVNCommandLine.registerOption(SVNDumpFilterOption.PRESERVE_REVISION_PROPERTIES);
        SVNCommandLine.registerOption(SVNDumpFilterOption.RENUMBER_REVISIONS);
        SVNCommandLine.registerOption(SVNDumpFilterOption.SKIP_MISSING_MERGE_SOURCES);
        SVNCommandLine.registerOption(SVNDumpFilterOption.TARGETS);
    }

}
