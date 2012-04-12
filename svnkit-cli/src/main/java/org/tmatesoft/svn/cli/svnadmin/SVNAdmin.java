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

import org.tmatesoft.svn.cli.AbstractSVNCommand;
import org.tmatesoft.svn.cli.AbstractSVNCommandEnvironment;
import org.tmatesoft.svn.cli.AbstractSVNLauncher;
import org.tmatesoft.svn.cli.SVNCommandLine;


/**
 * @version 1.3
 * @author  TMate Software Ltd.
 */
public class SVNAdmin extends AbstractSVNLauncher {

    public static void main(String[] args) {
        new SVNAdmin().run(args);
    }

    protected AbstractSVNCommandEnvironment createCommandEnvironment() {
        return new SVNAdminCommandEnvironment(getProgramName(), System.out, System.err, System.in);
    }

    protected String getProgramName() {
        return "jsvnadmin";
    }

    protected boolean needArgs() {
        return true;
    }

    protected boolean needCommand() {
        return true;
    }

    protected void registerCommands() {
        AbstractSVNCommand.registerCommand(new SVNAdminHelpCommand());
        AbstractSVNCommand.registerCommand(new SVNAdminCreateCommand());
        AbstractSVNCommand.registerCommand(new SVNAdminDumpCommand());
        AbstractSVNCommand.registerCommand(new SVNAdminListLocksCommand());
        AbstractSVNCommand.registerCommand(new SVNAdminListTransactionsCommand());
        AbstractSVNCommand.registerCommand(new SVNAdminLoadCommand());
        AbstractSVNCommand.registerCommand(new SVNAdminRemoveLocksCommand());
        AbstractSVNCommand.registerCommand(new SVNAdminRemoveTransactionsCommand());
        AbstractSVNCommand.registerCommand(new SVNAdminSetLogCommand());
        AbstractSVNCommand.registerCommand(new SVNAdminSetRevPropCommand());
        AbstractSVNCommand.registerCommand(new SVNAdminVerifyCommand());
        AbstractSVNCommand.registerCommand(new SVNAdminRecoverCommand());
        AbstractSVNCommand.registerCommand(new SVNAdminUpgradeCommand());
        AbstractSVNCommand.registerCommand(new SVNAdminSetUUIDCommand());
        AbstractSVNCommand.registerCommand(new SVNAdminHotCopyCommand());
        AbstractSVNCommand.registerCommand(new SVNAdminPackCommand());
    }

    protected void registerOptions() {
        SVNCommandLine.registerOption(SVNAdminOption.HELP);
        SVNCommandLine.registerOption(SVNAdminOption.QUESTION);
        SVNCommandLine.registerOption(SVNAdminOption.VERSION);
        SVNCommandLine.registerOption(SVNAdminOption.REVISION);
        SVNCommandLine.registerOption(SVNAdminOption.INCREMENTAL);
        SVNCommandLine.registerOption(SVNAdminOption.DELTAS);
        SVNCommandLine.registerOption(SVNAdminOption.BYPASS_HOOKS);
        SVNCommandLine.registerOption(SVNAdminOption.QUIET);
        SVNCommandLine.registerOption(SVNAdminOption.IGNORE_UUID);
        SVNCommandLine.registerOption(SVNAdminOption.FORCE_UUID);
        SVNCommandLine.registerOption(SVNAdminOption.PARENT_DIR);
        SVNCommandLine.registerOption(SVNAdminOption.FS_TYPE);
        SVNCommandLine.registerOption(SVNAdminOption.BDB_TXN_NOSYNC);
        SVNCommandLine.registerOption(SVNAdminOption.BDB_LOG_KEEP);

        SVNCommandLine.registerOption(SVNAdminOption.CONFIG_DIR);
        SVNCommandLine.registerOption(SVNAdminOption.CLEAN_LOGS);
        SVNCommandLine.registerOption(SVNAdminOption.USE_PRE_COMMIT_HOOK);
        SVNCommandLine.registerOption(SVNAdminOption.USE_POST_COMMIT_HOOK);
        SVNCommandLine.registerOption(SVNAdminOption.USE_PRE_REVPROP_CHANGE_HOOK);
        SVNCommandLine.registerOption(SVNAdminOption.USE_POST_REVPROP_CHANGE_HOOK);
        SVNCommandLine.registerOption(SVNAdminOption.WAIT);
        SVNCommandLine.registerOption(SVNAdminOption.PRE_14_COMPATIBLE);
        SVNCommandLine.registerOption(SVNAdminOption.PRE_15_COMPATIBLE);
    }
}
