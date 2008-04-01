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
package org.tmatesoft.svn.cli2.svnlook;

import java.util.Collection;
import java.util.LinkedList;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.io.SVNRepositoryFactory;


/**
 * @version 1.1.2
 * @author  TMate Software Ltd.
 */
public class SVNLookUUIDCommand extends SVNLookCommand {

    protected SVNLookUUIDCommand() {
        super("uuid", null);
    }

    protected Collection createSupportedOptions() {
        return new LinkedList();
    }

    public void run() throws SVNException {
        SVNURL url = SVNURL.fromFile(getLocalRepository());
        SVNRepository repository = SVNRepositoryFactory.create(url);
        
        repository.setCanceller(getEnvironment());
        String uuid = repository.getRepositoryUUID(true);
        getEnvironment().getOut().println(uuid);
    }

}
