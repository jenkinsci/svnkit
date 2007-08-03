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
package org.tmatesoft.svn.cli2.command;

import java.text.MessageFormat;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;

import org.tmatesoft.svn.cli2.SVNCommand;
import org.tmatesoft.svn.cli2.SVNCommandUtil;
import org.tmatesoft.svn.cli2.SVNOption;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.util.Version;


/**
 * @version 1.1.2
 * @author  TMate Software Ltd.
 */
public class SVNHelpCommand extends SVNCommand {

    public SVNHelpCommand() {
        super("help", new String[] {"?", "h"});
    }

    protected Collection createSupportedOptions() {
        Collection options = new HashSet();
        options.add(SVNOption.CONFIG_DIR);
        return options;
    }
    
    public static void printBasicUsage(String programName) {
        System.err.println(MessageFormat.format("Type ''{0} help'' for usage.", new Object[] {programName}));
    }

    public void run() throws SVNException {
        if (!getEnvironment().getArguments().isEmpty()) {
            for (Iterator commands = getEnvironment().getArguments().iterator(); commands.hasNext();) {
                String commandName = (String) commands.next();
                SVNCommand command = SVNCommand.getCommand(commandName);
                if (command == null) {
                    getEnvironment().getErr().println("svn: \"" + commandName + "\": unknown command.\n");
                    continue;
                }
                String help = getCommandHelp(command);
                getEnvironment().getOut().println(help);
            }
        } else if (getEnvironment().isVersion()) {
            String version = Version.getMajorVersion() + "." + Version.getMinorVersion() + "." + Version.getMicroVersion();
            String revNumber = Version.getRevisionNumber() < 0 ? "SNAPSHOT" : Long.toString(Version.getRevisionNumber());
            String message = MessageFormat.format("SVNKit, version {0}\n", new String[] {version + " (r" + revNumber + ")"});
            if (getEnvironment().isQuiet()) {
                message = version;
            }
            getEnvironment().getOut().println(message);
            if (!getEnvironment().isQuiet()) {
                message = 
                    "Copyright (C) 2004-2007 TMate Software.\n" +
                    "SVNKit is open source (GPL) software, see http://svnkit.com/ for more information.\n" +
                    "SVNKit is pure Java (TM) version of Subversion, see http://subversion.tigris.org/";
                getEnvironment().getOut().println(message);
            }
        } else if (getEnvironment().getArguments().isEmpty()) {
            getEnvironment().getOut().print(getGenericHelp());
        } else {
            String message = MessageFormat.format("Type ''{0} help'' for usage.", new String[] {getEnvironment().getProgramName()});
            getEnvironment().getOut().println(message);
        }
    }

    private String getCommandHelp(SVNCommand command) {
        StringBuffer help = new StringBuffer();
        help.append(command.getName());
        if (command.getAliases().length > 0) {
            help.append(" (");
            for (int i = 0; i < command.getAliases().length; i++) {
                help.append(command.getAliases()[i]);
                if (i + 1 < command.getAliases().length) {
                    help.append(", ");
                }
            }
            help.append(")");
        }
        help.append(": ");
        help.append(command.getDescription());
        help.append("\n");
        if (!command.getSupportedOptions().isEmpty()) {
            help.append("\nValid Options:\n");
            for (Iterator options = command.getSupportedOptions().iterator(); options.hasNext();) {
                SVNOption option = (SVNOption) options.next();
                help.append("  ");
                String optionDesc = null;
                if (option.getAlias() != null) {
                    optionDesc = "-" + option.getAlias() + " [--" + option.getName() + "]";
                } else {
                    optionDesc = "--" + option.getName();
                }
                help.append(SVNCommandUtil.formatString(optionDesc, 24, true));
                help.append(" : ");
                help.append(option.getDescription());
                help.append("\n");
            }
        }
        return help.toString();
    }

    private String getGenericHelp() {
        String header = 
            "usage: {0} <subcommand> [options] [args]\n" +
            "Subversion command-line client, version {1}.\n" +
            "Type ''{0} help <subcommand>'' for help on a specific subcommand.\n" +
            "Type ''{0} --version'' to see the program version and RA modules\n" +
            "  or ''{0} --version --quiet'' to see just the version number.\n" +
            "\n" +
            "Most subcommands take file and/or directory arguments, recursing\n" +
            "on the directories.  If no arguments are supplied to such a\n" +
            "command, it recurses on the current directory (inclusive) by default.\n" +
            "\n" +
            "Available subcommands:\n";
        String footer = 
            "SVNKit is a pure Java (TM) version of Subversion - a tool for version control.\n" +
            "For additional information, see http://svnkit.com/\n";
        String version = Version.getMajorVersion() + "." + Version.getMinorVersion() + "." + Version.getMicroVersion();
        header = MessageFormat.format(header, new Object[] {getEnvironment().getProgramName(), version});

        StringBuffer help = new StringBuffer();
        help.append(header);
        for (Iterator commands = SVNCommand.availableCommands(); commands.hasNext();) {
            SVNCommand command = (SVNCommand) commands.next();
            help.append("\n   ");
            help.append(command.getName());
            if (command.getAliases().length > 0) {
                help.append(" (");
                for (int i = 0; i < command.getAliases().length; i++) {
                    help.append(command.getAliases()[i]);
                    if (i + 1 < command.getAliases().length) {
                        help.append(", ");
                    }
                }
                help.append(")");
            }
        }
        help.append("\n\n");
        help.append(footer);
        return help.toString();
    }
}
