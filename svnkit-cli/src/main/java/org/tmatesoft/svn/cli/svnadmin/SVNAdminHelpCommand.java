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
package org.tmatesoft.svn.cli.svnadmin;

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
public class SVNAdminHelpCommand extends SVNAdminCommand {

    private static final String GENERIC_HELP_HEADER = 
        "general usage: {0} SUBCOMMAND REPOS_PATH  [ARGS & OPTIONS ...]\n" +
        "Type ''{0} help <subcommand>'' for help on a specific subcommand.\n" +
        "Type ''{0} --version'' to see the program version and FS modules.\n" +
        "\n" + 
        "Available subcommands:";

    private static final String VERSION_HELP_FOOTER =
        "\nThe following repository back-end (FS) modules are available:\n\n" +
        "* fs_fs : Module for working with a plain file (FSFS) repository.";

    public SVNAdminHelpCommand() {
        super("help", new String[] {"?", "h"});
    }

    protected Collection createSupportedOptions() {
        return new LinkedList();
    }

    public void run() throws SVNException {
        if (!getEnvironment().getArguments().isEmpty()) {
            for (Iterator commands = getEnvironment().getArguments().iterator(); commands.hasNext();) {
                String commandName = (String) commands.next();
                AbstractSVNCommand command = AbstractSVNCommand.getCommand(commandName);
                if (command == null) {
                    getEnvironment().getErr().println("\"" + commandName + "\": unknown command.\n");
                    continue;
                }
                getEnvironment().getProgramName();
                String help = SVNCommandUtil.getCommandHelp(command, getEnvironment().getProgramName(), true);
                getEnvironment().getOut().println(help);
            }
        } else if (getSVNAdminEnvironment().isVersion()) {
            String version = SVNCommandUtil.getVersion(getEnvironment(), getSVNAdminEnvironment().isQuiet());
            getEnvironment().getOut().println(version);
            if (!getSVNAdminEnvironment().isQuiet()) {
                getEnvironment().getOut().println(VERSION_HELP_FOOTER);
            }
        } else if (getEnvironment().getArguments().isEmpty()) {
            String help = SVNCommandUtil.getGenericHelp(getEnvironment().getProgramName(), GENERIC_HELP_HEADER, null, null);
            getEnvironment().getOut().print(help);
        } else {
            String message = MessageFormat.format("Type ''{0} help'' for usage.", new Object[] {getEnvironment().getProgramName()});
            getEnvironment().getOut().println(message);
        }
    }

}
