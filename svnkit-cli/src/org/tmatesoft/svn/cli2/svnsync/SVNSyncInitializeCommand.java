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
import java.util.LinkedList;

import org.tmatesoft.svn.core.SVNException;


/**
 * @version 1.1.2
 * @author  TMate Software Ltd.
 */
public class SVNSyncInitializeCommand extends SVNSyncCommand {

    public SVNSyncInitializeCommand() {
        super("initialize", new String[] { "init" });
    }
    
    protected Collection createSupportedOptions() {
        LinkedList options = new LinkedList();
        options.add(SVNSyncOption.NON_INTERACTIVE);
        options.add(SVNSyncOption.NO_AUTH_CACHE);
        options.add(SVNSyncOption.USERNAME);
        options.add(SVNSyncOption.PASSWORD);
        options.add(SVNSyncOption.SOURCE_USERNAME);
        options.add(SVNSyncOption.SOURCE_PASSWORD);
        options.add(SVNSyncOption.SYNC_USERNAME);
        options.add(SVNSyncOption.SYNC_PASSWORD);
        options.add(SVNSyncOption.CONFIG_DIR);
        options.add(SVNSyncOption.QUIET);
        return options;
    }

    public void run() throws SVNException {
    }

}
