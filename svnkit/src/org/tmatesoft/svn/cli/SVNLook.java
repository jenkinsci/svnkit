/*
 * ====================================================================
 * Copyright (c) 2004-2006 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html.
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.cli;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.io.dav.DAVRepositoryFactory;
import org.tmatesoft.svn.core.internal.io.fs.FSRepositoryFactory;
import org.tmatesoft.svn.core.internal.io.svn.SVNRepositoryFactoryImpl;
import org.tmatesoft.svn.util.SVNDebugLog;


/**
 * @version 1.1
 * @author  TMate Software Ltd.
 */
public class SVNLook {
    private static Set ourArguments;
    private static Map ourCommands;

    static {
        ourArguments = new HashSet();
        ourArguments.add(SVNArgument.REVISION);
        ourArguments.add(SVNArgument.TRANSACTION);
        ourArguments.add(SVNArgument.COPY_INFO);
        ourArguments.add(SVNArgument.NO_DIFF_DELETED);
        ourArguments.add(SVNArgument.NO_DIFF_ADDED);
        ourArguments.add(SVNArgument.DIFF_COPY_FROM);
        ourArguments.add(SVNArgument.SHOW_IDS);
        ourArguments.add(SVNArgument.REV_PROP);
        ourArguments.add(SVNArgument.VERBOSE);
        ourArguments.add(SVNArgument.FULL_PATHS);
        
//        Locale.setDefault(Locale.ENGLISH);
        ourCommands = new HashMap();
        ourCommands.put(new String[] { "author"}, "org.tmatesoft.svn.cli.command.SVNLookAuthorCommand");
        ourCommands.put(new String[] { "cat" }, "org.tmatesoft.svn.cli.command.SVNLookCatCommand");
        ourCommands.put(new String[] { "changed" }, "org.tmatesoft.svn.cli.command.SVNLookChangedCommand");
        ourCommands.put(new String[] { "date" }, "org.tmatesoft.svn.cli.command.SVNLookDateCommand");
        ourCommands.put(new String[] { "diff" }, "org.tmatesoft.svn.cli.command.SVNLookDiffCommand");
        ourCommands.put(new String[] { "dirs-changed" }, "org.tmatesoft.svn.cli.command.SVNLookDirsChangedCommand");
        ourCommands.put(new String[] { "history" }, "org.tmatesoft.svn.cli.command.SVNLookHistoryCommand");
        ourCommands.put(new String[] { "info" }, "org.tmatesoft.svn.cli.command.SVNLookInfoCommand");
        ourCommands.put(new String[] { "lock" }, "org.tmatesoft.svn.cli.command.SVNLookLockCommand");
        ourCommands.put(new String[] { "log" }, "org.tmatesoft.svn.cli.command.SVNLookLogCommand");
        ourCommands.put(new String[] { "propget", "pget", "pg" }, "org.tmatesoft.svn.cli.command.SVNLookPropgetCommand");
        ourCommands.put(new String[] { "proplist", "plist", "pl" }, "org.tmatesoft.svn.cli.command.SVNLookProplistCommand");
        ourCommands.put(new String[] { "tree" }, "org.tmatesoft.svn.cli.command.SVNLookTreeCommand");
        ourCommands.put(new String[] { "uuid" }, "org.tmatesoft.svn.cli.command.SVNLookUUIDCommand");
        ourCommands.put(new String[] { "youngest" }, "org.tmatesoft.svn.cli.command.SVNLookYoungestCommand");
    }

    public static void main(String[] args) {
        if (args == null || args.length < 1) {
            System.err.println("general usage: jsvnlook SUBCOMMAND REPOS_PATH [ARGS & OPTIONS ...]");
            System.exit(0);
        }

        StringBuffer commandLineString = new StringBuffer();
        for(int i = 0; i < args.length; i++) {
            commandLineString.append(args[i] + (i < args.length - 1 ? " " : ""));
        }

        SVNCommandLine commandLine = null;
        try {
            try {
                commandLine = new SVNCommandLine(args, ourArguments);
            } catch (SVNException e) {
                SVNDebugLog.getDefaultLog().info(e);
                System.err.println(e.getMessage());
                System.exit(1);
            }
            String commandName = commandLine.getCommandName();
            SVNCommand command = SVNCommand.getCommand(commandName, ourCommands);
            
    
            if (command != null) {
                DAVRepositoryFactory.setup();
                SVNRepositoryFactoryImpl.setup();
                FSRepositoryFactory.setup();
    
                command.setCommandLine(commandLine);
                try {
                    command.run(System.out, System.err);
                } catch (SVNException e) {
                    System.err.println(e.getMessage());
                    SVNDebugLog.getDefaultLog().info(e);
                } finally {
                    if (command.getClientManager() != null) {
                        command.getClientManager().shutdownConnections(true);
                    }
                }
            } else {
                System.err.println("error: unknown command name '" + commandName + "'");
                System.exit(1);
            }
        } catch (Throwable th) {
            SVNDebugLog.getDefaultLog().info(th);
            System.exit(-1);
        }   
        System.exit(0);
    }

}
