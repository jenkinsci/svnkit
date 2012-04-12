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

import org.tmatesoft.svn.cli.AbstractSVNCommand;
import org.tmatesoft.svn.cli.AbstractSVNCommandEnvironment;
import org.tmatesoft.svn.cli.AbstractSVNLauncher;
import org.tmatesoft.svn.cli.SVNCommandLine;


/**
 * @version 1.3
 * @author  TMate Software Ltd.
 */
public class SVNVersion extends AbstractSVNLauncher {

    public static void main(String[] args) {
        new SVNVersion().run(args);
    }

    protected AbstractSVNCommandEnvironment createCommandEnvironment() {
        return new SVNVersionCommandEnvironment(getProgramName(), System.out, System.err, System.in);
    }

    protected String getProgramName() {
        return "jsvnversion";
    }

    protected void registerCommands() {
        AbstractSVNCommand.registerCommand(new SVNVersionCommand());
        AbstractSVNCommand.registerCommand(new SVNVersionHelpCommand());
    }

    protected void registerOptions() {
        SVNCommandLine.registerOption(SVNVersionOption.COMMITTED);
        SVNCommandLine.registerOption(SVNVersionOption.NO_NEWLINE);
        SVNCommandLine.registerOption(SVNVersionOption.HELP);
        SVNCommandLine.registerOption(SVNVersionOption.VERSION);
    }

    protected boolean needArgs() {
        return false;
    }

    protected boolean needCommand() {
        return false;
    }

}
