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
package org.tmatesoft.svn.cli2.svnsync;

import java.util.Collection;

import org.tmatesoft.svn.core.SVNException;


/**
 * @version 1.1.2
 * @author  TMate Software Ltd.
 */
public class SVNSyncHelpCommand extends SVNSyncCommand {
    private static final String GENERIC_HELP_HEADER = 
        "general usage: {0} SUBCOMMAND DEST_URL  [ARGS & OPTIONS ...]\n" +
        "Type ''{0} help <subcommand>'' for help on a specific subcommand.\n" +
        "Type ''{0} --version'' to see the program version and RA modules.\n" +
        "\n" +
        "Available subcommands:\n";
    
    private static final String VERSION_HELP_FOOTER =
        "The following repository access (RA) modules are available:\n\n" +
        "* fs_fs : Module for working with a plain file (FSFS) repository.";

    public SVNSyncHelpCommand() {
        super("help", new String[] {"?", "h"});
    }
    
    protected Collection createSupportedOptions() {
        return null;
    }

    public void run() throws SVNException {
    }

}
