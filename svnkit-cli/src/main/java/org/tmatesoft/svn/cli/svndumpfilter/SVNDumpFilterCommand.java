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
package org.tmatesoft.svn.cli.svndumpfilter;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;

import org.tmatesoft.svn.cli.AbstractSVNCommand;


/**
 * @version 1.3
 * @author  TMate Software Ltd.
 */
public abstract class SVNDumpFilterCommand extends AbstractSVNCommand {

    private int myOutputPriority;

    public SVNDumpFilterCommand(String name, String[] aliases, int outputPriority) {
        super(name, aliases);
        myOutputPriority = outputPriority;
    }
    
    public Collection getGlobalOptions() {
        return Collections.EMPTY_LIST;
    }

    protected Collection createSupportedOptions() {
        LinkedList options = new LinkedList();
        options.add(SVNDumpFilterOption.DROP_EMPTY_REVISIONS);
        options.add(SVNDumpFilterOption.RENUMBER_REVISIONS);
        options.add(SVNDumpFilterOption.SKIP_MISSING_MERGE_SOURCES);        
        options.add(SVNDumpFilterOption.TARGETS);
        options.add(SVNDumpFilterOption.PRESERVE_REVISION_PROPERTIES);        
        options.add(SVNDumpFilterOption.QUIET);
        return options;
    }

    protected String getResourceBundleName() {
        return "org.tmatesoft.svn.cli.svndumpfilter.commands";
    }

    protected SVNDumpFilterCommandEnvironment getSVNDumpFilterEnvironment() {
        return (SVNDumpFilterCommandEnvironment) getEnvironment();
    }

    public int getOutputPriority() {
        return myOutputPriority;
    }
}
