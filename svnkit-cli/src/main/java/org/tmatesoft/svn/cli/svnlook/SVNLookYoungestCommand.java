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
package org.tmatesoft.svn.cli.svnlook;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

import org.tmatesoft.svn.core.SVNException;


/**
 * @version 1.3
 * @author  TMate Software Ltd.
 */
public class SVNLookYoungestCommand extends SVNLookCommand {

    protected SVNLookYoungestCommand() {
        super("youngest", null);
    }

    protected Collection createSupportedOptions() {
        List options = new LinkedList();
        return options;
    }

    public void run() throws SVNException {
        long revision = getSVNLookEnvironment().getRepository().getLatestRevision();
        getEnvironment().getOut().println(revision);
    }

}
