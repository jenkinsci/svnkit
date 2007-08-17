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
            String version = Version.getMajorVersion() + "." + Version.getMinorVersion() + "." + Version.getMicroVersion();
            String revNumber = Version.getRevisionNumber() < 0 ? "SNAPSHOT" : Long.toString(Version.getRevisionNumber());
            String message = MessageFormat.format(getEnvironment().getProgramName() + ", version {0}\n", new String[] {version + " (r" + revNumber + ")"});
            if (getSVNEnvironment().isQuiet()) {
                message = version;
            }
            getSVNEnvironment().getOut().println(message);
            if (!getSVNEnvironment().isQuiet()) {
                message = 
                    "Copyright (C) 2004-2007 TMate Software.\n" +
                    "SVNKit is open source (GPL) software, see http://svnkit.com/ for more information.\n" +
                    "SVNKit is pure Java (TM) version of Subversion, see http://subversion.tigris.org/";
                getSVNEnvironment().getOut().println(message);
            }
        } else if (getSVNEnvironment().getArguments().isEmpty()) {
            getSVNEnvironment().getOut().print(SVNCommandUtil.getGenericHelp(getEnvironment().getProgramName()));
        } else {
            String message = MessageFormat.format("Type ''{0} help'' for usage.", new String[] {getSVNEnvironment().getProgramName()});
            getSVNEnvironment().getOut().println(message);
        }
    }
}
