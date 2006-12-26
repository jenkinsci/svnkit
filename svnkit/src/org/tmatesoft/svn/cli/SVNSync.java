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
 * @version 1.1.0
 * @author  TMate Software Ltd.
 * @since   1.1
 */
public class SVNSync {
    private static Set ourArguments;
    private static Map ourCommands;

    static {
        ourArguments = new HashSet();
        ourArguments.add(SVNArgument.NON_INTERACTIVE);
        ourArguments.add(SVNArgument.NO_AUTH_CACHE);
        ourArguments.add(SVNArgument.PASSWORD);
        ourArguments.add(SVNArgument.USERNAME);
        ourArguments.add(SVNArgument.CONFIG_DIR);
        
//        Locale.setDefault(Locale.ENGLISH);
        ourCommands = new HashMap();
        ourCommands.put(new String[] { "initialize", "init" }, "org.tmatesoft.svn.cli.command.SVNSyncInitCommand");
        ourCommands.put(new String[] { "synchronize", "sync" }, "org.tmatesoft.svn.cli.command.SVNSyncSynchronizeCommand");
        ourCommands.put(new String[] { "copy-revprops" }, "org.tmatesoft.svn.cli.command.SVNSyncCopyRevpropsCommand");
    }

    public static void main(String[] args) {
        if (args == null || args.length < 1) {
            System.err.println("general usage: jsvnsync SUBCOMMAND DEST_URL  [ARGS & OPTIONS ...]\n");
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
