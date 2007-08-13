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
package org.tmatesoft.svn.cli2;

import org.tmatesoft.svn.cli2.command.SVNBlameCommand;
import org.tmatesoft.svn.cli2.command.SVNCatCommand;
import org.tmatesoft.svn.cli2.command.SVNChangeListCommand;
import org.tmatesoft.svn.cli2.command.SVNCopyCommand;
import org.tmatesoft.svn.cli2.command.SVNCleanupCommand;
import org.tmatesoft.svn.cli2.command.SVNAddCommand;
import org.tmatesoft.svn.cli2.command.SVNCheckoutCommand;
import org.tmatesoft.svn.cli2.command.SVNCommitCommand;
import org.tmatesoft.svn.cli2.command.SVNDeleteCommand;
import org.tmatesoft.svn.cli2.command.SVNDiffCommand;
import org.tmatesoft.svn.cli2.command.SVNExportCommand;
import org.tmatesoft.svn.cli2.command.SVNHelpCommand;
import org.tmatesoft.svn.cli2.command.SVNImportCommand;
import org.tmatesoft.svn.cli2.command.SVNLockCommand;
import org.tmatesoft.svn.cli2.command.SVNMergeCommand;
import org.tmatesoft.svn.cli2.command.SVNMkDirCommand;
import org.tmatesoft.svn.cli2.command.SVNMoveCommand;
import org.tmatesoft.svn.cli2.command.SVNPropDelCommand;
import org.tmatesoft.svn.cli2.command.SVNPropEditCommand;
import org.tmatesoft.svn.cli2.command.SVNPropGetCommand;
import org.tmatesoft.svn.cli2.command.SVNPropListCommand;
import org.tmatesoft.svn.cli2.command.SVNPropSetCommand;
import org.tmatesoft.svn.cli2.command.SVNResolvedCommand;
import org.tmatesoft.svn.cli2.command.SVNRevertCommand;
import org.tmatesoft.svn.cli2.command.SVNStatusCommand;
import org.tmatesoft.svn.cli2.command.SVNSwitchCommand;
import org.tmatesoft.svn.cli2.command.SVNUnLockCommand;
import org.tmatesoft.svn.cli2.command.SVNUpdateCommand;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.io.dav.DAVRepositoryFactory;
import org.tmatesoft.svn.core.internal.io.fs.FSRepositoryFactory;
import org.tmatesoft.svn.core.internal.io.svn.SVNRepositoryFactoryImpl;

/**
 * @version 1.1.2
 * @author  TMate Software Ltd.
 */
public class SVN {
    
    static {
        new SVNAddCommand();
        new SVNBlameCommand();
        new SVNCatCommand();
        new SVNChangeListCommand();
        new SVNCheckoutCommand();
        new SVNCleanupCommand();
        new SVNCommitCommand();
        new SVNCopyCommand();
        new SVNDeleteCommand();
        new SVNDiffCommand();
        new SVNExportCommand();
        new SVNHelpCommand();
        new SVNImportCommand();
        new SVNLockCommand();
        new SVNMergeCommand();
        new SVNMkDirCommand();
        new SVNMoveCommand();
        new SVNPropDelCommand();
        new SVNPropEditCommand();
        new SVNPropGetCommand();
        new SVNPropListCommand();
        new SVNPropSetCommand();
        new SVNResolvedCommand();
        new SVNStatusCommand();
        new SVNSwitchCommand();
        new SVNRevertCommand();
        new SVNUnLockCommand();
        new SVNUpdateCommand();
    }

    public static void main(String[] args) {
        if (args == null || args.length < 1) {
            SVNHelpCommand.printBasicUsage("jsvn");
            failure();
        }
        initRA();
        SVNCommandLine commandLine = new SVNCommandLine();
        try {
            commandLine.init(args);
        } catch (SVNException e) {
            handleError(e);
            SVNHelpCommand.printBasicUsage("jsvn");
            failure();
        }
        SVNCommandEnvironment env = new SVNCommandEnvironment(System.out, System.err, System.in);
        
        try {
            env.init(commandLine);
        } catch (SVNException e) {
            handleError(e);
            SVNHelpCommand.printBasicUsage("jsvn");
            failure();
        }

        env.initClientManager();
        if (!env.run()) {
            failure();
        }
        success();
    }
    
    private static void initRA() {
        DAVRepositoryFactory.setup();
        SVNRepositoryFactoryImpl.setup();
        FSRepositoryFactory.setup();
    }

    private static void handleError(SVNException e) {
        System.err.println(e.getMessage());
    }

    public static void failure() {
        System.exit(1);
    }

    public static void success() {
        System.exit(0);
    }

}
