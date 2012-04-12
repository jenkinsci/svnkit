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

import java.text.MessageFormat;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;

import org.tmatesoft.svn.cli.AbstractSVNCommand;
import org.tmatesoft.svn.cli.SVNCommandUtil;
import org.tmatesoft.svn.core.SVNException;


/**
 * @version 1.3
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
        "Available subcommands:";
    
    private static final String GENERIC_HELP_FOOTER =             
        "SVNKit is a pure Java (TM) version of Subversion - a tool for version control.\n" +
        "For additional information, see http://svnkit.com/\n";

    private static final String VERSION_HELP_FOOTER =
        "\nThe following repository access (RA) modules are available:\n\n" +
        "* org.tmatesoft.svn.core.internal.io.dav : Module for accessing a repository via WebDAV protocol.\n" +
        "  - handles 'http' scheme\n" +
        "  - handles 'https' scheme\n" +
        "* org.tmatesoft.svn.core.internal.io.svn: Module for accessing a repository using the svn network protocol.\n" + 
        "  - handles 'svn' scheme\n" +
        "* org.tmatesoft.svn.core.internal.io.fs: Module for accessing a repository on local disk.\n" +
        "  - handles 'file' scheme (only FSFS repositories are supported)\n";

    public SVNHelpCommand() {
        super("help", new String[] {"?", "h"});
    }

    protected Collection createSupportedOptions() {
        Collection options = new LinkedList();
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
                    getSVNEnvironment().getErr().println("\"" + commandName + "\": unknown command.\n");
                    continue;
                }
                String help = SVNCommandUtil.getCommandHelp(command, getEnvironment().getProgramName(), true);
                getSVNEnvironment().getOut().println(help);
                getSVNEnvironment().getOut().println();
            }
        } else if (getSVNEnvironment().isVersion()) {
            String version = SVNCommandUtil.getVersion(getEnvironment(), getSVNEnvironment().isQuiet());
            getEnvironment().getOut().println(version);
            if (!getSVNEnvironment().isQuiet()) {
                getEnvironment().getOut().println(VERSION_HELP_FOOTER);
            }
        } else if (getSVNEnvironment().getArguments().isEmpty()) {
            String help = SVNCommandUtil.getGenericHelp(getEnvironment().getProgramName(), GENERIC_HELP_HEADER, GENERIC_HELP_FOOTER, null);
            getSVNEnvironment().getOut().print(help);
        } else {
            String message = MessageFormat.format("Type ''{0} help'' for usage.", new Object[] {getSVNEnvironment().getProgramName()});
            getSVNEnvironment().getOut().println(message);
        }
    }
}
