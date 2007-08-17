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
package org.tmatesoft.svn.cli2.svn;

import java.text.MessageFormat;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;

import org.tmatesoft.svn.cli2.AbstractSVNCommand;
import org.tmatesoft.svn.cli2.SVNCommandUtil;
import org.tmatesoft.svn.core.SVNException;


/**
 * @version 1.1.2
 * @author  TMate Software Ltd.
 */
public class SVNHelpCommand extends SVNCommand {
    
    private static final String GENERIC_HELP_HEADER =             
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
    
    private static final String GENERIC_HELP_FOOTER =             
        "SVNKit is a pure Java (TM) version of Subversion - a tool for version control.\n" +
        "For additional information, see http://svnkit.com/\n";


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
        if (!getSVNEnvironment().getArguments().isEmpty()) {
            for (Iterator commands = getSVNEnvironment().getArguments().iterator(); commands.hasNext();) {
                String commandName = (String) commands.next();
                AbstractSVNCommand command = AbstractSVNCommand.getCommand(commandName);
                if (command == null) {
                    getSVNEnvironment().getErr().println("svn: \"" + commandName + "\": unknown command.\n");
                    continue;
                }
                String help = SVNCommandUtil.getCommandHelp(command);
                getSVNEnvironment().getOut().println(help);
            }
        } else if (getSVNEnvironment().isVersion()) {
            String version = SVNCommandUtil.getVersion(getEnvironment(), getSVNEnvironment().isQuiet());
            getEnvironment().getOut().println(version);
        } else if (getSVNEnvironment().getArguments().isEmpty()) {
            String help = SVNCommandUtil.getGenericHelp(getEnvironment().getProgramName(), GENERIC_HELP_HEADER, GENERIC_HELP_FOOTER);
            getSVNEnvironment().getOut().print(help);
        } else {
            String message = MessageFormat.format("Type ''{0} help'' for usage.", new String[] {getSVNEnvironment().getProgramName()});
            getSVNEnvironment().getOut().println(message);
        }
    }
}
