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

import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

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
        Map pool = getPool();
        if (!mayReuse || pool == null) {            
            repos = SVNRepositoryFactory.create(url, this);
            repos.setAuthenticationManager(myAuthManager);
            return repos;
        }
        
        repos = retriveRepository(pool, url.getProtocol());
        if (repos != null) {
            repos.setLocation(url, false);
        } else {
            repos = SVNRepositoryFactory.create(url, this);
            saveRepository(pool, repos, url.getProtocol());
        }         
        repos.setAuthenticationManager(myAuthManager);
        
        return repos;
    }

    public boolean keepConnection(SVNRepository repository) {
        return myIsKeepConnections && !"svn+ssh".equals(repository.getLocation().getProtocol());
    }
    
    public synchronized void shutdownConnections(boolean shutdownAll) {
        Map pool = null;
        if (myPoolMode == INSTANCE_POOL) {
            pool = myPool;
        } else if (myPoolMode == RUNTIME_POOL){
            pool = ourPool;
        }
        if (pool != null) {
            clearPool(pool, shutdownAll);
        }
    }
    
    private Map getPool() {
        switch (myPoolMode) {
            case INSTANCE_POOL:
                if (myPool == null) {
                    myPool = new HashMap();
                }
                return myPool;
            case RUNTIME_POOL:
                if (ourPool == null) {
                    ourPool = new HashMap();
                }
                return ourPool;
            default:
        }
        return null;
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
    
    private static void clearPool(Map pool, boolean force) {
        for (Iterator references = pool.keySet().iterator(); references.hasNext();) {
            WeakReference reference = (WeakReference) references.next();
            if (force || reference.get() == null) {
                Map repositoriesMap = (Map) pool.get(reference);
                for (Iterator repos = repositoriesMap.values().iterator(); repos.hasNext();) {
                    SVNRepository repo = (SVNRepository) repos.next();
                    try {
                        repo.closeSession();
                    } catch (SVNException e) {
                    }
                    repos.remove();
                }
                references.remove();
            }
        }
    }
    
    private static SVNRepository retriveRepository(Map pool, String protocol) {
        clearPool(pool, false);
        for (Iterator references = pool.keySet().iterator(); references.hasNext();) {
            WeakReference reference = (WeakReference) references.next();
            if (reference.get() == Thread.currentThread()) {
                Map repositoriesMap = (Map) pool.get(reference);
                if (repositoriesMap.containsKey(protocol)) {
                    return (SVNRepository) repositoriesMap.get(protocol);
                }
                return null;
            } 
        }
        return null;
    }

    private static void saveRepository(Map pool, SVNRepository repository, String protocol) {
        clearPool(pool, false);
        for (Iterator references = pool.keySet().iterator(); references.hasNext();) {
            WeakReference reference = (WeakReference) references.next();
            if (reference.get() == Thread.currentThread()) {
                Map repositoriesMap = (Map) pool.get(reference);
                repositoriesMap.put(protocol, repository);
                return;
            } 
        }
        Map map = new HashMap();
        map.put(protocol, repository);
        pool.put(new WeakReference(Thread.currentThread()), map);
    }
}
