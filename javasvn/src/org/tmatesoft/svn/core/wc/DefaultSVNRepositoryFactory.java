/*
 * ====================================================================
 * Copyright (c) 2004 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://tmate.org/svn/license.html.
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.core.wc;

import java.util.Map;
import java.util.WeakHashMap;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationManager;
import org.tmatesoft.svn.core.io.ISVNSession;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.io.SVNRepositoryFactory;


/**
 * @version 1.0
 * @author  TMate Software Ltd.
 */
public class DefaultSVNRepositoryFactory implements ISVNRepositoryFactory, ISVNSession {
    
    public static final int RUNTIME_POOL = 1;
    public static final int INSTANCE_POOL = 2;
    public static final int NO_POOL = 4;
    
    private ISVNAuthenticationManager myAuthManager;
    private boolean myIsKeepConnections;
    private int myPoolMode;
    private Map myPool;
    private static Map ourPool;

    public DefaultSVNRepositoryFactory(ISVNAuthenticationManager authManager) {
        this(authManager, true, RUNTIME_POOL);
    }

    public DefaultSVNRepositoryFactory(ISVNAuthenticationManager authManager, boolean keepConnections, int poolMode) {
        myAuthManager = authManager;
        myIsKeepConnections = keepConnections;
        myPoolMode = poolMode;
    }

    public synchronized SVNRepository createRepository(SVNURL url, boolean mayReuse) throws SVNException {
        SVNRepository repos = null;
        if (!mayReuse || myPoolMode == NO_POOL) {            
            repos = SVNRepositoryFactory.create(url, this);
            repos.setAuthenticationManager(myAuthManager);
            return repos;
        }
        Map pool = null;
        if (myPoolMode == INSTANCE_POOL) {
            pool = myPool;
        } else if (myPoolMode == RUNTIME_POOL){
            pool = ourPool;
        }
        if (pool == null) {
            pool = new WeakHashMap();
        }
        if (pool.containsKey(Thread.currentThread())) {
            repos = (SVNRepository) pool.get(Thread.currentThread());
            repos.setLocation(url, false);
        } 
        if (repos == null) {
            repos = SVNRepositoryFactory.create(url, this);
            pool.put(Thread.currentThread(), repos);
        } 
        repos.setAuthenticationManager(myAuthManager);
        return repos;
    }

    public boolean keepConnection() {
        return myIsKeepConnections;
    }

    // no caching in this class
    public void saveCommitMessage(SVNRepository repository, long revision, String message) {
    }

    public String getCommitMessage(SVNRepository repository, long revision) {
        return null;
    }

    public boolean hasCommitMessage(SVNRepository repository, long revision) {
        return false;
    }
}
