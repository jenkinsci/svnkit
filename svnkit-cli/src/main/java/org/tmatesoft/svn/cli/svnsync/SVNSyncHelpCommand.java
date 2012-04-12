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
package org.tmatesoft.svn.cli.svnsync;

import java.text.MessageFormat;
import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedList;

import org.tmatesoft.svn.cli.AbstractSVNCommand;
import org.tmatesoft.svn.cli.SVNCommandUtil;
import org.tmatesoft.svn.core.SVNException;


/**
 * @version 1.3
 * @author  TMate Software Ltd.
 */
public class SVNSyncHelpCommand extends SVNSyncCommand {
    private static final String GENERIC_HELP_HEADER = 
        "general usage: {0} SUBCOMMAND DEST_URL  [ARGS & OPTIONS ...]\n" +
        "Type ''{0} help <subcommand>'' for help on a specific subcommand.\n" +
        "Type ''{0} --version'' to see the program version and RA modules.\n" +
        "\n" +
        "Available subcommands:";
    
    private static final String VERSION_HELP_FOOTER =
        "\nThe following repository access (RA) modules are available:\n\n" +
        "* org.tmatesoft.svn.core.internal.io.dav : Module for accessing a repository via WebDAV protocol.\n" +
        "  - handles 'http' scheme\n" +
        "  - handles 'https' scheme\n" +
        "* org.tmatesoft.svn.core.internal.io.svn: Module for accessing a repository using the svn network protocol.\n" + 
        "  - handles 'svn' scheme\n" +
        "* org.tmatesoft.svn.core.internal.io.fs: Module for accessing a repository on local disk.\n" +
        "  - handles 'file' scheme (only FSFS repositories are supported)\n";

    public SVNSyncHelpCommand() {
        super("help", new String[] {"?", "h"}, 2);
    }
    
    protected Collection createSupportedOptions() {
        return new LinkedList();
    }

    public void run() throws SVNException {
        if (!getSVNSyncEnvironment().getArguments().isEmpty()) {
            for (Iterator commands = getSVNSyncEnvironment().getArguments().iterator(); commands.hasNext();) {
                String commandName = (String) commands.next();
                AbstractSVNCommand command = AbstractSVNCommand.getCommand(commandName);
                if (command == null) {
                    getSVNSyncEnvironment().getErr().println("\"" + commandName + "\": unknown command.\n");
                    continue;
                }
                String help = SVNCommandUtil.getCommandHelp(command, getEnvironment().getProgramName(), true);
                getSVNSyncEnvironment().getOut().println(help);
            }
        } else if (getSVNSyncEnvironment().isVersion()) {
            String version = SVNCommandUtil.getVersion(getEnvironment(), getSVNSyncEnvironment().isQuiet());
            getEnvironment().getOut().println(version);
            getEnvironment().getOut().println(VERSION_HELP_FOOTER);
        } else if (getSVNSyncEnvironment().getArguments().isEmpty()) {

            Comparator commandComparator = new Comparator() {
                public int compare(Object o1, Object o2) {
                    AbstractSVNCommand c1 = (AbstractSVNCommand) o1;
                    AbstractSVNCommand c2 = (AbstractSVNCommand) o2;
                    if (c1 instanceof SVNSyncCommand && c2 instanceof SVNSyncCommand) {
                        SVNSyncCommand syncCommand1 = (SVNSyncCommand) c1;
                        SVNSyncCommand syncCommand2 = (SVNSyncCommand) c2;
                        if (syncCommand1.getOutputPriority() != syncCommand2.getOutputPriority()) {
                            return syncCommand1.getOutputPriority() < syncCommand2.getOutputPriority() ? -1 : 1;
                        }
                    }
                    return c1.getName().compareTo(c2.getName());
                }
            };

            String help = SVNCommandUtil.getGenericHelp(getEnvironment().getProgramName(), GENERIC_HELP_HEADER, 
                    null, commandComparator);
            getSVNSyncEnvironment().getOut().print(help);
        } else {
            String message = MessageFormat.format("Type ''{0} help'' for usage.", new Object[] { 
                    getSVNSyncEnvironment().getProgramName() });
            getSVNSyncEnvironment().getOut().println(message);
        }
    }

}
