/*
 * ====================================================================
 * Copyright (c) 2004-2008 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html.
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.cli.svndumpfilter;

import java.text.MessageFormat;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;

import org.tmatesoft.svn.cli.AbstractSVNCommand;
import org.tmatesoft.svn.cli.SVNCommandUtil;
import org.tmatesoft.svn.core.SVNException;


/**
 * @version 1.1.2
 * @author  TMate Software Ltd.
 */
public class SVNDumpFilterHelpCommand extends SVNDumpFilterCommand {
    private static final String GENERIC_HELP_HEADER = 
        "general usage: {0} SUBCOMMAND [ARGS & OPTIONS ...]\n" +
        "Type ''{0} help <subcommand>'' for help on a specific subcommand.\n" +
        "Type ''{0} --version'' to see the program version.\n" +
        "\n" + 
        "Available subcommands:\n";

    public SVNDumpFilterHelpCommand() {
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
                String help = SVNCommandUtil.getCommandHelp(command);
                getEnvironment().getOut().println(help);
            }
        } else if (getSVNDumpFilterEnvironment().isVersion()) {
            String version = SVNCommandUtil.getVersion(getEnvironment(), getSVNDumpFilterEnvironment().isQuiet());
            getEnvironment().getOut().println(version);
        } else if (getEnvironment().getArguments().isEmpty()) {
            String help = SVNCommandUtil.getGenericHelp(getEnvironment().getProgramName(), GENERIC_HELP_HEADER, 
                    null);
            getEnvironment().getOut().print(help);
        } else {
            String message = MessageFormat.format("Type ''{0} help'' for usage.", new Object[] { 
                    getEnvironment().getProgramName() });
            getEnvironment().getOut().println(message);
        }
    }

}
