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

import java.io.File;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.io.dav.DAVRepositoryFactory;
import org.tmatesoft.svn.core.internal.io.fs.FSRepositoryFactory;
import org.tmatesoft.svn.core.internal.io.svn.SVNRepositoryFactoryImpl;
import org.tmatesoft.svn.core.internal.wc.SVNFileType;
import org.tmatesoft.svn.core.wc.SVNStatus;
import org.tmatesoft.svn.core.wc.SVNStatusClient;
import org.tmatesoft.svn.core.wc.SVNStatusType;
import org.tmatesoft.svn.util.SVNDebugLog;

/**
 * @version 1.1.0
 * @author  TMate Software Ltd.
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
                SVNDebugLog.getDefaultLog().info(e);
                System.err.println(e.getMessage());
                System.exit(1);
            }
            String commandName = commandLine.getCommandName();
            SVNCommand command = SVNCommand.getCommand(commandName);
            
    
            if (command != null) {
                if (SVNCommand.isForceLogCommand(commandName) && !commandLine.hasArgument(SVNArgument.FORCE_LOG)) {
                    if (commandLine.hasArgument(SVNArgument.FILE)) {
                        File file = new File((String) commandLine.getArgumentValue(SVNArgument.FILE));
                        // check if it is a versioned file.
                        SVNStatusClient stClient = new SVNStatusClient(null, null);
                        try {
                            SVNStatus status = stClient.doStatus(file.getAbsoluteFile(), false);
                            if (status != null && status.getContentsStatus() != SVNStatusType.STATUS_UNVERSIONED &&
                                    status.getContentsStatus() != SVNStatusType.STATUS_IGNORED &&
                                    status.getContentsStatus() != SVNStatusType.STATUS_EXTERNAL) {
                                if ("lock".equals(commandName)) {
                                    System.err.println("svn: Lock comment file is a versioned file; use '--force-log' to override");
                                } else {
                                    System.err.println("svn: Log message file is a versioned file; use '--force-log' to override");
                                }
                                System.exit(1);
                            }
                        } catch (SVNException e) {}
                    } 
                    if (commandLine.hasArgument(SVNArgument.MESSAGE)) {
                        File file = new File((String) commandLine.getArgumentValue(SVNArgument.MESSAGE));
                        if (SVNFileType.getType(file) != SVNFileType.NONE) {
                            if ("lock".equals(commandName)) {
                                System.err.println("svn: The lock comment is a path name (was -F intended?); use '--force-log' to override");
                            } else {
                                System.err.println("svn: The log message is a path name (was -F intended?); use '--force-log' to override");
                            }
                            System.exit(1);
                        }
                    }
                }
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
