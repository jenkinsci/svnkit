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

import java.util.Collection;
import java.util.LinkedList;

import org.tmatesoft.svn.cli2.AbstractSVNCommand;


/**
 * @version 1.1.2
 * @author  TMate Software Ltd.
 */
public abstract class SVNCommand extends AbstractSVNCommand {

    private Collection myGlobalOptions;

    protected SVNCommand(String name, String[] aliases) {
        super(name, aliases);
    }
    
    public boolean acceptsRevisionRange() {
        return false;
    }

    public boolean isCommitter() {
        return false;
    }
    
    public String getFileAmbigousErrorMessage() {
        return "Log message file is a versioned file; use '--force-log' to override";
    }

    public String getMessageAmbigousErrorMessage() {
        return "The log message is a path name (was -F intended?); use '--force-log' to override";
    }

    protected SVNCommandEnvironment getSVNEnvironment() {
        return (SVNCommandEnvironment) getEnvironment();
    }
    
    protected String getResourceBundleName() {
        return "org.tmatesoft.svn.cli2.svn.commands";
    }

    public Collection getGlobalOptions() {
        if (myGlobalOptions == null) {
            myGlobalOptions = new LinkedList();
            myGlobalOptions.add(SVNOption.USERNAME);
            myGlobalOptions.add(SVNOption.PASSWORD);
            myGlobalOptions.add(SVNOption.NO_AUTH_CACHE);
            myGlobalOptions.add(SVNOption.NON_INTERACTIVE);
            myGlobalOptions.add(SVNOption.CONFIG_DIR);
        }
        return myGlobalOptions;
    }
}
