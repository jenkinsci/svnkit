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

import java.util.Collection;
import java.util.Collections;

import org.tmatesoft.svn.cli.AbstractSVNCommand;


/**
 * @version 1.3
 * @author  TMate Software Ltd.
 */
public abstract class SVNSyncCommand extends AbstractSVNCommand {

    private int myOutputPriority;

    public SVNSyncCommand(String name, String[] aliases, int outputPriority) {
        super(name, aliases);
        myOutputPriority = outputPriority;
    }

    public Collection getGlobalOptions() {
        return Collections.EMPTY_LIST;
    }

    protected String getResourceBundleName() {
        return "org.tmatesoft.svn.cli.svnsync.commands";
    }

    protected SVNSyncCommandEnvironment getSVNSyncEnvironment() {
        return (SVNSyncCommandEnvironment) getEnvironment();
    }

    public int getOutputPriority() {
        return myOutputPriority;
    }
}
