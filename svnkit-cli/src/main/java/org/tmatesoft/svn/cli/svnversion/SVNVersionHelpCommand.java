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
package org.tmatesoft.svn.cli.svnversion;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.tmatesoft.svn.cli.AbstractSVNCommand;
import org.tmatesoft.svn.cli.SVNCommandUtil;
import org.tmatesoft.svn.core.SVNException;


/**
 * @version 1.3
 * @author  TMate Software Ltd.
 */
public class SVNVersionHelpCommand extends AbstractSVNCommand {

    public SVNVersionHelpCommand() {
        super("help", null);
    }

    protected Collection createSupportedOptions() {
        List options = new ArrayList();
        options.add(SVNVersionOption.VERSION);
        options.add(SVNVersionOption.HELP);
        return options;
    }

    public Collection getGlobalOptions() {
        return Collections.EMPTY_LIST;
    }
    
    protected SVNVersionCommandEnvironment getSVNVersionEnvironment() {
        return (SVNVersionCommandEnvironment) getEnvironment();
    }

    protected String getResourceBundleName() {
        return "org.tmatesoft.svn.cli.svnversion.commands";
    }

    public void run() throws SVNException {
        if (getSVNVersionEnvironment().isHelp()) {
            String help = SVNCommandUtil.getCommandHelp(AbstractSVNCommand.getCommand(""), getEnvironment().getProgramName(), true);
            getEnvironment().getOut().println(help);
        } else if (getSVNVersionEnvironment().isVersion()) {
            String help = SVNCommandUtil.getVersion(getEnvironment(), false);
            getEnvironment().getOut().println(help);
        } else {
            getEnvironment().getOut().println("Type '" + getEnvironment().getProgramName() + " --help' for usage.");
        }
    }

}
