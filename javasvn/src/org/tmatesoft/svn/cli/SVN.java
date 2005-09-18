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


import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.io.dav.DAVRepositoryFactory;
import org.tmatesoft.svn.core.internal.io.svn.SVNRepositoryFactoryImpl;
import org.tmatesoft.svn.util.SVNDebugLog;

/**
 * @author TMate Software Ltd.
 */
public class SVN {
    
    public static void main(String[] args) {
        if (args == null || args.length < 1) {
            System.err.println("usage: svn commandName commandArguments");
            System.exit(0);
        }

        StringBuffer commandLineString = new StringBuffer();
        for(int i = 0; i < args.length; i++) {
            commandLineString.append(args[i] + (i < args.length - 1 ? " " : ""));
        }
        
        SVNCommandLine commandLine = null;
        try {
            try {
                commandLine = new SVNCommandLine(args);
            } catch (SVNException e) {
                SVNDebugLog.logInfo(e);
                System.err.println("error: " + e.getMessage());
                System.exit(1);
            }
            String commandName = commandLine.getCommandName();
            SVNCommand command = SVNCommand.getCommand(commandName);
    
            if (command != null) {
                DAVRepositoryFactory.setup();
                SVNRepositoryFactoryImpl.setup();
    
                command.setCommandLine(commandLine);
                try {
                    command.run(System.out, System.err);
                } catch (SVNException e) {
                    System.err.println(e.getMessage());
                    SVNDebugLog.logInfo(e);
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
            SVNDebugLog.logInfo(th);
            System.exit(-1);
        }   
        System.exit(0);
    }
 }
