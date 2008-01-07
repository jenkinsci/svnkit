/*
 * ====================================================================
 * Copyright (c) 2004-2007 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.core.wc;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

import org.tmatesoft.svn.core.ISVNCanceller;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationManager;
import org.tmatesoft.svn.core.io.ISVNConnectionListener;
import org.tmatesoft.svn.core.io.ISVNSession;
import org.tmatesoft.svn.core.io.ISVNTunnelProvider;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.io.SVNRepositoryFactory;
import org.tmatesoft.svn.util.ISVNDebugLog;
import org.tmatesoft.svn.util.SVNDebugLog;


/**
 * The <b>DefaultSVNRepositoryPool</b> class is a default implementation of 
 * the <b>ISVNRepositoryPool</b> interface. 
 * 
 * <p>
 * It creates <b>SVNRepository</b> objects that may be stored in a common
 * pool and reused later. The objects common pool may be shared by different
 * threads, but each thread can retrieve only those objects, that have been
 * created within that thread. So, <b>DefaultSVNRepositoryPool</b> is thread-safe.
 * An objects pool may be global during runtime, or it may be private - one separate 
 * pool per one <b>DefaultSVNRepositoryPool</b> object. Also there's a possibility to
 * have a <b>DefaultSVNRepositoryPool</b> object with the pool feature
 * disabled (<b>SVNRepository</b> objects instantiated by such a creator are never
 * cached). 
 * 
 * <p>
 * <b>DefaultSVNRepositoryPool</b> caches one <b>SVNRepository</b> object per one url 
 * protocol (per one thread), that is the number of protocols used equals to
 * the number of objects cached per one thread (if all objects are created as reusable).
 * 
 * <p>
 * Also <b>DefaultSVNRepositoryPool</b> is able to create <b>SVNRepository</b> objects
 * that use a single socket connection (i.e. don't close a connection after every repository
 * access operation but reuse a single one). 
 * 
 * @version 1.1.1
 * @author  TMate Software Ltd.
 */
public class DefaultSVNRepositoryPool implements ISVNRepositoryPool, ISVNSession, ISVNConnectionListener {
    
    /**
     * Defines a common shared objects pool. All objects that will be 
     * created by different threads will be stored in this common pool.
     * 
     * @deprecated  
     */
    public static final int RUNTIME_POOL = 1;
    
    /**
     * Defines a private pool. All objects that will be created by 
     * different threads will be stored only within this pool object.
     * This allows to have more than one separate pools.
     *  
     * @deprecated  
     */
    public static final int INSTANCE_POOL = 2;
    
    /**
     * Defines a without-pool configuration. Objects that are created
     * by this <b>DefaultSVNRepositoryPool</b> object are not cached,
     * the pool feature is disabled.
     * 
     * @deprecated
     */
    public static final int NO_POOL = 4;

    private static final long DEFAULT_IDLE_TIMEOUT = 60*1000;
    private static Timer ourTimer = new Timer(true);
    
    private ISVNAuthenticationManager myAuthManager;
    private ISVNTunnelProvider myTunnelProvider;
    private ISVNDebugLog myDebugLog;
    private Map myPool;
    private long myTimeout;
    private Map myInactiveRepositories = new HashMap();
    private Timer myTimer;

    private boolean myIsKeepConnection;
    
    /**
     * Constructs a <b>DefaultSVNRepositoryPool</b> instance
     * that represents {@link #RUNTIME_POOL} objects pool. 
     * <b>SVNRepository</b> objects created by this instance will
     * use a single socket connection.
     * 
     * @param authManager      an authentication driver
     * @param tunnelProvider   a tunnel provider
     */
    public DefaultSVNRepositoryPool(ISVNAuthenticationManager authManager, ISVNTunnelProvider tunnelProvider) {
        this(authManager, tunnelProvider, DEFAULT_IDLE_TIMEOUT, true);
    }

    /**
     * Constructs a <b>DefaultSVNRepositoryPool</b> instance
     * that represents {@link #RUNTIME_POOL} objects pool. 
     * <b>SVNRepository</b> objects created by this instance will
     * use a single socket connection.
     * 
     * @param authManager      an authentication driver
     * @param tunnelProvider   a tunnel provider
     * @param timeout          inactivity timeout after which open connections should be closed 
     * @param keepConnection   whether to left connection open 
     */
    public DefaultSVNRepositoryPool(ISVNAuthenticationManager authManager, ISVNTunnelProvider tunnelProvider, long timeout, boolean keepConnection) {
        myAuthManager = authManager;
        myTunnelProvider = tunnelProvider;
        myDebugLog = SVNDebugLog.getDefaultLog();
        myTimeout = timeout > 0 ? timeout : DEFAULT_IDLE_TIMEOUT;
        myIsKeepConnection = keepConnection;
        myTimeout = timeout;
        if (myIsKeepConnection) {
            myTimer = ourTimer;
            ourTimer.schedule(new TimeoutTask(), 10000);
        }
    }
    
    /**
     * Constructs a <b>DefaultSVNRepositoryPool</b> instance.
     * 
     * @param authManager         an authentication driver
     * @param tunnelProvider      a tunnel provider  
     * @param keepConnections     if <span class="javakeyword">true</span>
     *                            then <b>SVNRepository</b> objects will keep 
     *                            a single connection for accessing a repository,
     *                            if <span class="javakeyword">false</span> - open 
     *                            a new connection per each repository access operation
     * @param poolMode            a mode of this object represented by
     *                            one of the constant fields of <b>DefaultSVNRepositoryPool</b>
     * @deprecated
     */
    public DefaultSVNRepositoryPool(ISVNAuthenticationManager authManager, ISVNTunnelProvider tunnelProvider, boolean keepConnections, int poolMode) {
        this(authManager, tunnelProvider);
    }
    
    /**
     * Creates a new <b>SVNRepository</b> driver object.
     * if <code>mayReuse</code> is <span class="javakeyword">true</span> 
     * and the mode of this <b>DefaultSVNRepositoryPool</b> object is not 
     * {@link #NO_POOL} then first tries to find the <b>SVNRepository</b>
     * object in the pool for the given protocol. If the object is not found,
     * creates a new one for that protocol, caches it in the pool and returns
     * back. 
     * 
     * <p>
     * <b>NOTE:</b> be careful when simultaneously using several <b>SVNRepository</b>
     * drivers for the same protocol - since there can be only one driver object in
     * the pool per a protocol, creating two objects for the same protocol
     * with <code>mayReuse</code> set to <span class="javakeyword">true</span>, 
     * actually returns the same single object stored in the thread pool. 
     * 
     * @param url             a repository location for which a driver
     *                        is to be created
     * @param mayReuse        if <span class="javakeyword">true</span> then
     *                        <b>SVNRepository</b> object is reusable 
     * @return                a new <b>SVNRepository</b> driver object
     * @throws SVNException   
     * @see                   org.tmatesoft.svn.core.io.SVNRepository                                    
     * 
     */
    public synchronized SVNRepository createRepository(SVNURL url, boolean mayReuse) throws SVNException {
        if (myIsKeepConnection && myTimer == null && ourTimer != null) {
            myTimer = ourTimer;
            ourTimer.schedule(new TimeoutTask(), 10000);
        }
        
        SVNRepository repos = null;
        Map pool = getPool();
        if (!mayReuse || pool == null) {            
            repos = SVNRepositoryFactory.create(url, this);
            repos.setAuthenticationManager(myAuthManager);
            repos.setTunnelProvider(myTunnelProvider);
            repos.setDebugLog(myDebugLog);
            return repos;
        }
        
        repos = (SVNRepository) pool.get(url.getProtocol());
        if (repos != null) {
            repos.setLocation(url, false);
        } else {
            repos = SVNRepositoryFactory.create(url, this);
            // add listener.
            if (myIsKeepConnection) {
                repos.addConnectionListener(this);
            }
            pool.put(url.getProtocol(), repos);
        }         
        repos.setAuthenticationManager(myAuthManager);
        repos.setTunnelProvider(myTunnelProvider);
        repos.setDebugLog(myDebugLog);
        return repos;
    }
    
    public void setAuthenticationManager(ISVNAuthenticationManager authManager) {
        myAuthManager = authManager;
        Map pool = getPool();
        for (Iterator protocols = pool.keySet().iterator(); protocols.hasNext();) {
            String key = (String) protocols.next();
            SVNRepository repository = (SVNRepository) pool.get(key);
            repository.setAuthenticationManager(myAuthManager);
        }
    }
    
    /**
     * Says if the given <b>SVNRepository</b> driver object should
     * keep a connection opened. If this object was created with
     * <code>keepConnections</code> set to <span class="javakeyword">true</span>
     * and if <code>repository</code> is not created for the 
     * <span class="javastring">"svn+ssh"</span> protocol (since for this protocol there's
     * no extra need to keep a connection opened - it remains opened), this
     * method returns <span class="javakeyword">true</span>.
     *  
     * @param  repository  an <b>SVNRepository</b> driver 
     * @return             <span class="javakeyword">true</span> if 
     *                     the driver should keep a connection
     */
    public boolean keepConnection(SVNRepository repository) {
        return myIsKeepConnection;
    }
    
    /**
     * Closes connections of cached <b>SVNRepository</b> objects. 
     * 
     * @param shutdownAll if <span class="javakeyword">true</span> - closes
     *                    connections of all the cached objects, otherwise only
     *                    connections of those cached objects which owner threads
     *                    have already disposed
     * @see               org.tmatesoft.svn.core.io.SVNRepository                                    
     */
    public synchronized void shutdownConnections(boolean shutdownAll) {
        dispose();
    }
    
    public void dispose() {
        synchronized (myInactiveRepositories) {
            myInactiveRepositories.clear();
            myTimer = null;
        }
        
        Map pool = getPool();
        for (Iterator protocols = pool.keySet().iterator(); protocols.hasNext();) {
            String key = (String) protocols.next();
            SVNRepository repository = (SVNRepository) pool.get(key);
            repository.closeSession();
        }
        myPool = null;
    }
    
    private Map getPool() {
        if (myPool == null) {
            myPool = new HashMap();
        }
        return myPool;
    }

    // no caching in this class
    /**
     * Does nothing.
     * 
     * @param repository  an <b>SVNRepository</b> driver (to distinguish
     *                    that repository for which this message is actual)
     * @param revision    a revision number
     * @param message     the commit message for <code>revision</code>
     */
    public void saveCommitMessage(SVNRepository repository, long revision, String message) {
    }
    
    /**
     * Returns <span class="javakeyword">null</span>.
     * 
     * @param repository  an <b>SVNRepository</b> driver (to distinguish
     *                    that repository for which a commit message is requested)
     * @param revision    a revision number
     * @return            the commit message for <code>revision</code>
     */
    public String getCommitMessage(SVNRepository repository, long revision) {
        return null;
    }
    
    /**
     * Returns <span class="javakeyword">false</span>.
     * 
     * @param repository  an <b>SVNRepository</b> driver (to distinguish
     *                    that repository for which a commit message is requested)
     * @param revision    a revision number
     * @return            <span class="javakeyword">true</span> if the cache
     *                    has got a message for the given repository and revision,
     *                    <span class="javakeyword">false</span> otherwise 
     */
    public boolean hasCommitMessage(SVNRepository repository, long revision) {
        return false;
    }

    public void connectionClosed(final SVNRepository repository) {
        // start inactivity timer.
        synchronized (myInactiveRepositories) {
            myInactiveRepositories.put(repository, new Long(System.currentTimeMillis()));
            // schedule timeout cleanup.
        }
    }

    public void connectionOpened(SVNRepository repository) {
        synchronized (myInactiveRepositories) {
            myInactiveRepositories.remove(repository);
        }
    }
    
    private long getTimeout() {
        return myTimeout;
    }

    public void setCanceller(ISVNCanceller canceller) {
        Map pool = getPool();
        for (Iterator protocols = pool.keySet().iterator(); protocols.hasNext();) {
            String key = (String) protocols.next();
            SVNRepository repository = (SVNRepository) pool.get(key);
            repository.setCanceller(canceller);
        }
    }

    public void setDebugLog(ISVNDebugLog log) {
        myDebugLog = log == null ? SVNDebugLog.getDefaultLog() : log;
        Map pool = getPool();
        for (Iterator protocols = pool.keySet().iterator(); protocols.hasNext();) {
            String key = (String) protocols.next();
            SVNRepository repository = (SVNRepository) pool.get(key);
            repository.setDebugLog(log);
        }
    }
    
    private class TimeoutTask extends TimerTask {
        public void run() {
            boolean scheduled = false;
            try {
                long currentTime = System.currentTimeMillis();
                synchronized (myInactiveRepositories) {
                    for (Iterator repositories = myInactiveRepositories.keySet().iterator(); repositories.hasNext();) {
                        SVNRepository repos = (SVNRepository) repositories.next();
                        long time = ((Long) myInactiveRepositories.get(repos)).longValue();
                        if (currentTime - time >= getTimeout()) {
                            repositories.remove();
                            repos.closeSession();
                        }
                    }
                    if (myTimer != null) {
                        myTimer.schedule(new TimeoutTask(), 10000);
                        scheduled = true;
                    }
                }
            } catch (Throwable th) {
                SVNDebugLog.getDefaultLog().error(th);
                if (!scheduled && myTimer != null) {
                    myTimer.schedule(new TimeoutTask(), 10000);
                    scheduled = true;
                }
            }
        }
    }
}
