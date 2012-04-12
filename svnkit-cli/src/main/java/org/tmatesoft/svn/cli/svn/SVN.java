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
package org.tmatesoft.svn.cli.svn;

import org.tmatesoft.svn.cli.AbstractSVNCommand;
import org.tmatesoft.svn.cli.AbstractSVNCommandEnvironment;
import org.tmatesoft.svn.cli.AbstractSVNLauncher;
import org.tmatesoft.svn.cli.SVNCommandLine;

/**
 * @version 1.3
 * @author  TMate Software Ltd.
 */
public class SVN extends AbstractSVNLauncher {

    public static void main(String[] args) {
        new SVN().run(args);
    }

    protected String getProgramName() {
        return "jsvn";
    }

    protected AbstractSVNCommandEnvironment createCommandEnvironment() {
        return new SVNCommandEnvironment(getProgramName(), System.out, System.err, System.in);
    }
    
    protected void registerCommands() {
        AbstractSVNCommand.registerCommand(new SVNAddCommand());
        AbstractSVNCommand.registerCommand(new SVNBlameCommand());
        AbstractSVNCommand.registerCommand(new SVNCatCommand());
        AbstractSVNCommand.registerCommand(new SVNChangeListCommand());
        AbstractSVNCommand.registerCommand(new SVNCheckoutCommand());
        AbstractSVNCommand.registerCommand(new SVNCleanupCommand());
        AbstractSVNCommand.registerCommand(new SVNCommitCommand());
        AbstractSVNCommand.registerCommand(new SVNCopyCommand());
        AbstractSVNCommand.registerCommand(new SVNDeleteCommand());
        AbstractSVNCommand.registerCommand(new SVNDiffCommand());
        AbstractSVNCommand.registerCommand(new SVNExportCommand());
        AbstractSVNCommand.registerCommand(new SVNHelpCommand());
        AbstractSVNCommand.registerCommand(new SVNImportCommand());
        AbstractSVNCommand.registerCommand(new SVNInfoCommand());
        AbstractSVNCommand.registerCommand(new SVNListCommand());
        AbstractSVNCommand.registerCommand(new SVNLockCommand());
        AbstractSVNCommand.registerCommand(new SVNLogCommand());
        AbstractSVNCommand.registerCommand(new SVNMergeCommand());
        AbstractSVNCommand.registerCommand(new SVNMkDirCommand());
        AbstractSVNCommand.registerCommand(new SVNMoveCommand());
        AbstractSVNCommand.registerCommand(new SVNPropDelCommand());
        AbstractSVNCommand.registerCommand(new SVNPropEditCommand());
        AbstractSVNCommand.registerCommand(new SVNPropGetCommand());
        AbstractSVNCommand.registerCommand(new SVNPropListCommand());
        AbstractSVNCommand.registerCommand(new SVNPropSetCommand());
        AbstractSVNCommand.registerCommand(new SVNResolveCommand());
        AbstractSVNCommand.registerCommand(new SVNResolvedCommand());
        AbstractSVNCommand.registerCommand(new SVNStatusCommand());
        AbstractSVNCommand.registerCommand(new SVNSwitchCommand());
        AbstractSVNCommand.registerCommand(new SVNRevertCommand());
        AbstractSVNCommand.registerCommand(new SVNUnLockCommand());
        AbstractSVNCommand.registerCommand(new SVNUpdateCommand());
        AbstractSVNCommand.registerCommand(new SVNMergeInfoCommand());
        AbstractSVNCommand.registerCommand(new SVNPatchCommand());
        AbstractSVNCommand.registerCommand(new SVNUpgradeCommand());
    }

    protected void registerOptions() {
        SVNCommandLine.registerOption(SVNOption.VERBOSE);
        SVNCommandLine.registerOption(SVNOption.UPDATE);
        SVNCommandLine.registerOption(SVNOption.NON_RECURSIVE);
        SVNCommandLine.registerOption(SVNOption.DEPTH);
        SVNCommandLine.registerOption(SVNOption.SET_DEPTH);
        SVNCommandLine.registerOption(SVNOption.QUIET);
        SVNCommandLine.registerOption(SVNOption.NO_IGNORE);
        SVNCommandLine.registerOption(SVNOption.INCREMENTAL);
        SVNCommandLine.registerOption(SVNOption.XML);
        SVNCommandLine.registerOption(SVNOption.CONFIG_DIR);
        SVNCommandLine.registerOption(SVNOption.IGNORE_EXTERNALS);
        SVNCommandLine.registerOption(SVNOption.IGNORE_KEYWORDS);
        SVNCommandLine.registerOption(SVNOption.CHANGELIST);
        SVNCommandLine.registerOption(SVNOption.HELP);
        SVNCommandLine.registerOption(SVNOption.QUESTION);
        SVNCommandLine.registerOption(SVNOption.VERSION);

        SVNCommandLine.registerOption(SVNOption.RECURSIVE);
        SVNCommandLine.registerOption(SVNOption.REVISION);
        SVNCommandLine.registerOption(SVNOption.CHANGE);
        SVNCommandLine.registerOption(SVNOption.REVPROP);
        SVNCommandLine.registerOption(SVNOption.STRICT);

        SVNCommandLine.registerOption(SVNOption.FILE);
        SVNCommandLine.registerOption(SVNOption.ENCODING);
        SVNCommandLine.registerOption(SVNOption.TARGETS);
        SVNCommandLine.registerOption(SVNOption.FORCE);
        SVNCommandLine.registerOption(SVNOption.FORCE_LOG);
        SVNCommandLine.registerOption(SVNOption.MESSAGE);
        SVNCommandLine.registerOption(SVNOption.WITH_REVPROP);
        SVNCommandLine.registerOption(SVNOption.EDITOR_CMD);

        SVNCommandLine.registerOption(SVNOption.NO_UNLOCK);
        SVNCommandLine.registerOption(SVNOption.DRY_RUN);
        SVNCommandLine.registerOption(SVNOption.RECORD_ONLY);
        SVNCommandLine.registerOption(SVNOption.USE_MERGE_HISTORY);
        SVNCommandLine.registerOption(SVNOption.EXTENSIONS);
        SVNCommandLine.registerOption(SVNOption.IGNORE_ANCESTRY);
        SVNCommandLine.registerOption(SVNOption.SHOW_COPIES_AS_ADDS);
        SVNCommandLine.registerOption(SVNOption.NATIVE_EOL);
        SVNCommandLine.registerOption(SVNOption.RELOCATE);
        SVNCommandLine.registerOption(SVNOption.AUTOPROPS);
        SVNCommandLine.registerOption(SVNOption.NO_AUTOPROPS);
        SVNCommandLine.registerOption(SVNOption.KEEP_CHANGELISTS);
        SVNCommandLine.registerOption(SVNOption.PARENTS);
        SVNCommandLine.registerOption(SVNOption.KEEP_LOCAL);
        SVNCommandLine.registerOption(SVNOption.ACCEPT);
        SVNCommandLine.registerOption(SVNOption.REMOVE);

        SVNCommandLine.registerOption(SVNOption.DIFF);
        SVNCommandLine.registerOption(SVNOption.OLD);
        SVNCommandLine.registerOption(SVNOption.NEW);
        SVNCommandLine.registerOption(SVNOption.SUMMARIZE);
        SVNCommandLine.registerOption(SVNOption.NOTICE_ANCESTRY);
        SVNCommandLine.registerOption(SVNOption.NO_DIFF_DELETED);
        SVNCommandLine.registerOption(SVNOption.STOP_ON_COPY);
        SVNCommandLine.registerOption(SVNOption.LIMIT);
        SVNCommandLine.registerOption(SVNOption.AUTHOR_OF_INTEREST);
        SVNCommandLine.registerOption(SVNOption.REGULAR_EXPRESSION);
        SVNCommandLine.registerOption(SVNOption.GIT_DIFF_FORMAT);

        SVNCommandLine.registerOption(SVNOption.USERNAME);
        SVNCommandLine.registerOption(SVNOption.PASSWORD);
        SVNCommandLine.registerOption(SVNOption.NO_AUTH_CACHE);
        SVNCommandLine.registerOption(SVNOption.NON_INTERACTIVE);
        SVNCommandLine.registerOption(SVNOption.WITH_ALL_REVPROPS);
        SVNCommandLine.registerOption(SVNOption.SHOW_REVS);
        SVNCommandLine.registerOption(SVNOption.REINTEGRATE);
        SVNCommandLine.registerOption(SVNOption.ALLOW_MIXED_REVISIONS);
        SVNCommandLine.registerOption(SVNOption.DIFF_CMD);
        SVNCommandLine.registerOption(SVNOption.TRUST_SERVER_CERT);
        SVNCommandLine.registerOption(SVNOption.CONFIG_OPTION);

        SVNCommandLine.registerOption(SVNOption.STRIP);
        
    }

    protected boolean needArgs() {
        return true;
    }

    protected boolean needCommand() {
        return true;
    }
}
