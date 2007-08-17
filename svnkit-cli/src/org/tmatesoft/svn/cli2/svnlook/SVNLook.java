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
package org.tmatesoft.svn.cli2.svnlook;

import org.tmatesoft.svn.cli2.AbstractSVNCommandEnvironment;
import org.tmatesoft.svn.cli2.AbstractSVNLauncher;
import org.tmatesoft.svn.cli2.SVNCommandLine;


/**
 * @version 1.1.2
 * @author  TMate Software Ltd.
 */
public class SVNLook extends AbstractSVNLauncher {

    public static void main(String[] args) {
        new SVNLook().run(args);
    }

    protected AbstractSVNCommandEnvironment createCommandEnvironment() {
        return new SVNLookCommandEnvironment(getProgramName(), System.out, System.err, System.in);
    }

    protected String getProgramName() {
        return "jsvnsync";
    }

    protected boolean needArgs() {
        return true;
    }

    protected boolean needCommand() {
        return true;
    }

    protected void registerCommands() {
    }

    protected void registerOptions() {
        SVNCommandLine.registerOption(SVNLookOption.HELP);
        SVNCommandLine.registerOption(SVNLookOption.QUESTION);
        SVNCommandLine.registerOption(SVNLookOption.VERSION);
        SVNCommandLine.registerOption(SVNLookOption.COPY_INFO);
        SVNCommandLine.registerOption(SVNLookOption.DIFF_COPY_FROM);
        SVNCommandLine.registerOption(SVNLookOption.FULL_PATHS);
        SVNCommandLine.registerOption(SVNLookOption.LIMIT);
        SVNCommandLine.registerOption(SVNLookOption.NO_DIFF_ADDED);
        SVNCommandLine.registerOption(SVNLookOption.NO_DIFF_DELETED);
        SVNCommandLine.registerOption(SVNLookOption.NON_RECURSIVE);
        SVNCommandLine.registerOption(SVNLookOption.REVISION);
        SVNCommandLine.registerOption(SVNLookOption.REVPROP);
        SVNCommandLine.registerOption(SVNLookOption.SHOW_IDS);
        SVNCommandLine.registerOption(SVNLookOption.TRANSACTION);
        SVNCommandLine.registerOption(SVNLookOption.VERBOSE);
    }
}