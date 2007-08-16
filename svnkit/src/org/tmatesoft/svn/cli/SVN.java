/*
 * ====================================================================
 * Copyright (c) 2004-2007 TMate Software Ltd.  All rights reserved.
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
import java.util.HashSet;
import java.util.Set;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationManager;
import org.tmatesoft.svn.core.internal.io.dav.DAVRepositoryFactory;
import org.tmatesoft.svn.core.internal.io.fs.FSRepositoryFactory;
import org.tmatesoft.svn.core.internal.io.svn.SVNRepositoryFactoryImpl;
import org.tmatesoft.svn.core.internal.wc.SVNFileType;
import org.tmatesoft.svn.core.wc.SVNStatus;
import org.tmatesoft.svn.core.wc.SVNStatusClient;
import org.tmatesoft.svn.core.wc.SVNStatusType;
import org.tmatesoft.svn.util.SVNDebugLog;

/**
 * @version 1.1.1
 * @author  TMate Software Ltd.
 */
public class SVN {
    private static Set ourArguments;
    private static Set newCommands;

    static {
        ourArguments = new HashSet();
        ourArguments.add(SVNArgument.PASSWORD);
        ourArguments.add(SVNArgument.USERNAME);
        ourArguments.add(SVNArgument.CONFIG_DIR);

        ourArguments.add(SVNArgument.NON_RECURSIVE);
        ourArguments.add(SVNArgument.RECURSIVE);
        ourArguments.add(SVNArgument.VERBOSE);
        ourArguments.add(SVNArgument.QUIET);
        ourArguments.add(SVNArgument.SHOW_UPDATES);
        ourArguments.add(SVNArgument.NO_IGNORE);
        ourArguments.add(SVNArgument.MESSAGE);
        ourArguments.add(SVNArgument.REVISION);
        ourArguments.add(SVNArgument.FORCE);
        ourArguments.add(SVNArgument.FORCE_LOG);
        ourArguments.add(SVNArgument.FILE);
        ourArguments.add(SVNArgument.EDITOR_CMD);
        ourArguments.add(SVNArgument.STRICT);
        ourArguments.add(SVNArgument.NO_UNLOCK);
        ourArguments.add(SVNArgument.NO_AUTH_CACHE);
        ourArguments.add(SVNArgument.RELOCATE);
        ourArguments.add(SVNArgument.EOL_STYLE);
        ourArguments.add(SVNArgument.NO_DIFF_DELETED);
        ourArguments.add(SVNArgument.USE_ANCESTRY);
        ourArguments.add(SVNArgument.OLD);
        ourArguments.add(SVNArgument.NEW);
        ourArguments.add(SVNArgument.DRY_RUN);
        ourArguments.add(SVNArgument.IGNORE_ANCESTRY);
        ourArguments.add(SVNArgument.NO_AUTO_PROPS);
        ourArguments.add(SVNArgument.AUTO_PROPS);
        ourArguments.add(SVNArgument.REV_PROP);
        ourArguments.add(SVNArgument.INCREMENTAL);
        ourArguments.add(SVNArgument.XML);
        ourArguments.add(SVNArgument.LIMIT);
        ourArguments.add(SVNArgument.STOP_ON_COPY);
        ourArguments.add(SVNArgument.NON_INTERACTIVE);
        ourArguments.add(SVNArgument.CHANGE);
        ourArguments.add(SVNArgument.SUMMARIZE);
        ourArguments.add(SVNArgument.EXTENSIONS);
        ourArguments.add(SVNArgument.IGNORE_ALL_WS);
        ourArguments.add(SVNArgument.IGNORE_EOL_STYLE);
        ourArguments.add(SVNArgument.IGNORE_WS_CHANGE);
        ourArguments.add(SVNArgument.KEEP_LOCAL);
        ourArguments.add(SVNArgument.DEPTH);
        ourArguments.add(SVNArgument.WITH_REVPROP);
        ourArguments.add(SVNArgument.ENCODING);
        ourArguments.add(SVNArgument.PARENTS);
        ourArguments.add(SVNArgument.CHANGELIST);
        ourArguments.add(SVNArgument.KEEP_CHANGELIST);
        ourArguments.add(SVNArgument.REMOVE);
        ourArguments.add(SVNArgument.ACCEPT);
        ourArguments.add(SVNArgument.USE_MERGE_INFO);
        ourArguments.add(SVNArgument.IGNORE_EXTERNALS);
        ourArguments.add(SVNArgument.RECORD_ONLY);

        newCommands = new HashSet();
        newCommands.add("status");
        newCommands.add("stat");
        newCommands.add("st");
        newCommands.add("propedit");
        newCommands.add("pedit");
        newCommands.add("pe");
        newCommands.add("propset");
        newCommands.add("pset");
        newCommands.add("ps");
        newCommands.add("propdel");
        newCommands.add("pdel");
        newCommands.add("pd");
        newCommands.add("proplist");
        newCommands.add("plist");
        newCommands.add("pl");
        newCommands.add("propgrt");
        newCommands.add("pget");
        newCommands.add("pg");
        newCommands.add("merge");
        newCommands.add("update");
        newCommands.add("up");
        newCommands.add("checkout");
        newCommands.add("co");
        newCommands.add("export");
        newCommands.add("switch");
        newCommands.add("sw");
        newCommands.add("help");
        newCommands.add("h");
        newCommands.add("?");
        newCommands.add("import");
        newCommands.add("commit");
        newCommands.add("ci");
        newCommands.add("mkdir");
        newCommands.add("add");
        newCommands.add("delete");
        newCommands.add("del");
        newCommands.add("remove");
        newCommands.add("rm");
        newCommands.add("revert");
        newCommands.add("lock");
        newCommands.add("unlock");
        newCommands.add("resolved");
        newCommands.add("cleanup");
        newCommands.add("move");
        newCommands.add("mv");
        newCommands.add("rename");
        newCommands.add("ren");
        newCommands.add("copy");
        newCommands.add("cp");
        newCommands.add("changelist");
        newCommands.add("cl");
        newCommands.add("cat");
        newCommands.add("blame");
        newCommands.add("praise");
        newCommands.add("annotate");
        newCommands.add("ann");
        newCommands.add("diff");
        newCommands.add("di");
        newCommands.add("log");
        newCommands.add("list");
        newCommands.add("ls");
        newCommands.add("info");
    }

    public static void main(String[] args) {
        if (args == null || args.length < 1) {
            System.err.println("usage: jsvn commandName commandArguments");
            System.exit(1);
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
            if (newCommands.contains(commandName.toLowerCase().trim())) {
                org.tmatesoft.svn.cli2.svn.SVN.main(args);
                return;
            }
            SVNCommand command = SVNCommand.getCommand(commandName);


            if (command != null) {
                if (SVNCommand.isForceLogCommand(commandName) && !commandLine.hasArgument(SVNArgument.FORCE_LOG)) {
                    if (commandLine.hasArgument(SVNArgument.FILE)) {
                        File file = new File((String) commandLine.getArgumentValue(SVNArgument.FILE));
                        // check if it is a versioned file.
                        SVNStatusClient stClient = new SVNStatusClient((ISVNAuthenticationManager) null, null);
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
                boolean isSuccess = true;
                try {
                    command.run(System.out, System.err);
                } catch (SVNException e) {
                    System.err.println(e.getMessage());
                    SVNDebugLog.getDefaultLog().info(e);
                    isSuccess = false;
                } finally {
                    if (command.getClientManager() != null) {
                        command.getClientManager().shutdownConnections(true);
                    }
                }
                if (!isSuccess) {
                    System.exit(1);
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
