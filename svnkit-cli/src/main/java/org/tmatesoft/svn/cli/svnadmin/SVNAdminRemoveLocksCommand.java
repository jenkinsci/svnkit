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
package org.tmatesoft.svn.cli.svnadmin;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNLock;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.auth.BasicAuthenticationManager;
import org.tmatesoft.svn.core.internal.util.SVNHashMap;
import org.tmatesoft.svn.core.io.ISVNLockHandler;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.io.SVNRepositoryFactory;


/**
 * @version 1.3
 * @author  TMate Software Ltd.
 */
public class SVNAdminRemoveLocksCommand extends SVNAdminCommand implements ISVNLockHandler {

    public SVNAdminRemoveLocksCommand() {
        super("rmlocks", null);
    }

    protected Collection createSupportedOptions() {
        return new ArrayList();
    }

    public void run() throws SVNException {
        List targets = getEnvironment().combineTargets(null, false);
        if (!targets.isEmpty()) {
            targets.remove(0);
        }
        String[] locks = (String[]) targets.toArray(new String[targets.size()]);

        SVNRepository repository = SVNRepositoryFactory.create(SVNURL.fromFile(getLocalRepository()));
        String userName = System.getProperty("user.name", "administrator");
        repository.setAuthenticationManager(new BasicAuthenticationManager(userName, ""));
        repository.setCanceller(getEnvironment());
        
        for (int i = 0; i < locks.length; i++) {
            String lockPath = locks[i];
            try {
                SVNLock lock = repository.getLock(lockPath);
                if (lock == null) {
                    getEnvironment().getOut().println("Path '" + lockPath + "' isn't locked.");
                    continue;
                }
                Map pathToToken = new SVNHashMap();
                pathToToken.put(lockPath, lock.getID());
                repository.unlock(pathToToken, true, this);
            } catch (SVNException e) {
                getEnvironment().handleError(e.getErrorMessage());
            }
        }
    }

    public void handleLock(String path, SVNLock lock, SVNErrorMessage error) throws SVNException {
    }

    public void handleUnlock(String path, SVNLock lock, SVNErrorMessage error) throws SVNException {
        if (error != null) {
            getEnvironment().handleError(error);
        } else {
            getEnvironment().getOut().println("Removed lock on '" + path + "'.");
        }
    }
}
