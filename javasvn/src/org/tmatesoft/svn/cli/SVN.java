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


import org.tmatesoft.svn.core.internal.io.dav.DAVRepositoryFactory;
import org.tmatesoft.svn.core.internal.io.svn.SVNRepositoryFactoryImpl;
import org.tmatesoft.svn.core.internal.ws.fs.FSEntryFactory;
import org.tmatesoft.svn.core.io.SVNException;
import org.tmatesoft.svn.util.DebugLog;

/**
 * @author TMate Software Ltd.
 */
public class SVN {
    
    public static void main(String[] args) {
        if (args == null || args.length < 1) {
            DebugLog.log("invliad arguments!");
            System.err.println("usage: svn commandName commandArguments");
            System.exit(0);
        }

        StringBuffer commandLineString = new StringBuffer();
        for(int i = 0; i < args.length; i++) {
            commandLineString.append(args[i] + (i < args.length - 1 ? " " : ""));
        }
        DebugLog.log("command line: " + commandLineString.toString());
        
        SVNCommandLine commandLine = null;
        try {
            try {
                commandLine = new SVNCommandLine(args);
            } catch (SVNException e) {
                DebugLog.error(e);
                System.err.println("error: " + e.getMessage());
                System.exit(1);
            }
            String commandName = commandLine.getCommandName();
            DebugLog.log("COMMAND NAME: " + commandName + " ========================================== ");
            SVNCommand command = SVNCommand.getCommand(commandName);
            DebugLog.log("command: " + command);
    
            if (command != null) {
                DAVRepositoryFactory.setup();
                SVNRepositoryFactoryImpl.setup();
                FSEntryFactory.setup();
    
                command.setCommandLine(commandLine);
                try {
                    command.run(System.out, System.err);
                } catch (SVNException e) {
                    System.err.println(e.getMessage());
                    DebugLog.error(e);
                }
            } else {
                System.err.println("error: unknown command name '" + commandName + "'");
                System.exit(1);
            }
        } catch (Throwable th) {
            DebugLog.error(th);
            System.exit(-1);
        }
        System.exit(0);
    }
 }
