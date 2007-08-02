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

import org.tmatesoft.svn.cli2.SVNCommand;
import org.tmatesoft.svn.cli2.SVNOption;
import org.tmatesoft.svn.core.SVNException;


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
        System.out.println(MessageFormat.format("Type ''{0} help'' for usage.", new Object[] {programName}));
    }

    public void run() throws SVNException {
    }


}
