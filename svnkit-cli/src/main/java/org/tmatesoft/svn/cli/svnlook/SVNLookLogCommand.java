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
import org.tmatesoft.svn.core.SVNProperties;
import org.tmatesoft.svn.core.SVNPropertyValue;
import org.tmatesoft.svn.core.SVNRevisionProperty;


/**
 * @version 1.3
 * @author  TMate Software Ltd.
 */
public class SVNLookLogCommand extends SVNLookCommand {

    protected SVNLookLogCommand() {
        super("log", null);
    }

    protected Collection createSupportedOptions() {
        List options = new LinkedList();
        options.add(SVNLookOption.REVISION);
        options.add(SVNLookOption.TRANSACTION);
        return options;
    }

    public void run() throws SVNException {
        SVNProperties props = getProperties();
        SVNPropertyValue value = props.getSVNPropertyValue(SVNRevisionProperty.LOG);
        if (value != null && value.getString() != null) {
            getEnvironment().getOut().print(value.getString());
        }
        getEnvironment().getOut().println();
    }

}
