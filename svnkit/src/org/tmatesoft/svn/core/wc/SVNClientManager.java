/*
 * ====================================================================
 * Copyright (c) 2004-2006 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.core.wc;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationManager;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.io.SVNRepositoryFactory;
import org.tmatesoft.svn.core.wc.admin.SVNAdminClient;
import org.tmatesoft.svn.core.wc.admin.SVNLookClient;
import org.tmatesoft.svn.util.ISVNDebugLog;
import org.tmatesoft.svn.util.SVNDebugLog;

/**
 * The <b>SVNClientManager</b> class is used to manage <b>SVN</b>*<b>Client</b> 
 * objects as well as for providing them to a user what makes the user's work
 * easier and his code - pretty clear and flexible.
 * 
 * <p> 
 * When you don't have special needs to create, keep and manage 
 * separate <b>SVN</b>*<b>Client</b> objects by yourself, you should
 * use <b>SVNClientManager</b> that takes care of all that work for you.
 * These are some of advantages of using <b>SVNClientManager</b>:
 * <ol>
 * <li>If you instantiate an <b>SVN</b>*<b>Client</b> object by yourself 
 * you need to provide a run-time configuration driver - {@link ISVNOptions} - 
 * as well as an authentication and network layers driver - 
 * {@link org.tmatesoft.svn.core.auth.ISVNAuthenticationManager}. When
 * using an <b>SVNClientManager</b> you have multiple choices to provide
 * and use those drivers:
 * <pre class="javacode">
 *     <span class="javacomment">//1.default options and authentication drivers to use</span>
 *     SVNClientManager clientManager = SVNClientManager.newInstance();
 *     
 *     ...
 *     
 *     <span class="javacomment">//2.provided options and default authentication drivers to use</span>
 *     ISVNOptions myOptions;
 *     ...
 *     SVNClientManager clientManager = SVNClientManager.newInstance(myOptions);
 *     
 *     ...
 *     
 *     <span class="javacomment">//3.provided options and authentication drivers to use</span>
 *     ISVNOptions myOptions;
 *     ISVNAuthenticationManager myAuthManager;
 *     ...
 *     SVNClientManager clientManager = SVNClientManager.newInstance(myOptions, myAuthManager);
 *     
 *     ...
 *     
 *     <span class="javacomment">//4.provided options driver and user's credentials to make</span> 
 *     <span class="javacomment">//a default authentication driver use them</span> 
 *     ISVNOptions myOptions;
 *     ...
 *     SVNClientManager 
 *         clientManager = SVNClientManager.newInstance(myOptions, <span class="javastring">"name"</span>, <span class="javastring">"passw"</span>);
 *     </pre><br />
 * Having instantiated an <b>SVNClientManager</b> in one of these ways, all 
 * the <b>SVN</b>*<b>Client</b> objects it will provide you will share those
 * drivers, so you don't need to code much to provide the same drivers to each
 * <b>SVN</b>*<b>Client</b> instance by yourself.
 * <li>With <b>SVNClientManager</b> you don't need to create and keep your
 * <b>SVN</b>*<b>Client</b> objects by youself - <b>SVNClientManager</b> will
 * do all the work for you, so this will certainly bring down your efforts
 * on coding and your code will be clearer and more flexible. All you need is
 * to create an <b>SVNClientManager</b> instance.
 * <li>Actually every <b>SVN</b>*<b>Client</b> object is instantiated only at
 * the moment of the first call to an appropriate <b>SVNClientManager</b>'s 
 * <code>get</code> method:
 * <pre class="javacode">
 *     SVNClientManager clientManager;
 *     ...
 *     <span class="javacomment">//an update client will be created only at that moment when you</span> 
 *     <span class="javacomment">//first call this method for getting your update client, but if you</span>
 *     <span class="javacomment">//have already called it once before, then the method will return</span>
 *     <span class="javacomment">//that update client object instantiated in previous... so, it's</span>
 *     <span class="javacomment">//quite cheap, you see..</span> 
 *     SVNUpdateClient updateClient = clientManager.getUpdateClient();</pre><br />
 * <li>You can provide a single event handler that will be used by all 
 * <b>SVN</b>*<b>Client</b> objects provided by <b>SVNClientManager</b>:
 * <pre class="javacode">
 * <span class="javakeyword">import</span> org.tmatesoft.svn.core.wc.ISVNEventHandler;
 *     
 *     ...
 *     
 *     ISVNEventHandler commonEventHandler;
 *     SVNClientManager clientManager = SVNClientManager.newInstance();
 *     ...
 *     <span class="javacomment">//will be used by all SVN*Client objects</span>
 *     <span class="javacomment">//obtained from your client manager</span>
 *     clientManager.setEventHandler(commonEventHandler);
 * </pre>
 * <li>
 * </ol>
 * 
 * @version 1.1.0
 * @author  TMate Software Ltd.
 * @see     ISVNEventHandler
 * @see     <a target="_top" href="http://svnkit.com/kb/examples/">Examples</a>
 */
public class SVNClientManager implements ISVNRepositoryPool {
    
    private ISVNOptions myOptions;
    
    private SVNCommitClient myCommitClient;
    private SVNCopyClient myCopyClient;
    private SVNDiffClient myDiffClient;
    private SVNLogClient myLogClient;
    private SVNMoveClient myMoveClient;
    private SVNStatusClient myStatusClient;
    private SVNUpdateClient myUpdateClient;
    private SVNWCClient myWCClient;
    private SVNAdminClient myAdminClient;
    private SVNLookClient myLookClient;
    
    private ISVNEventHandler myEventHandler;
    private ISVNRepositoryPool myRepositoryPool;
    private ISVNDebugLog myDebugLog;

    private SVNClientManager(ISVNOptions options, ISVNRepositoryPool repositoryPool) {
        myOptions = options;
        if (myOptions == null) {
            myOptions = SVNWCUtil.createDefaultOptions(true);
        }
        myRepositoryPool = repositoryPool;
    }

    private SVNClientManager(ISVNOptions options, final ISVNAuthenticationManager authManager) {
        this(options, new DefaultSVNRepositoryPool(authManager == null ? SVNWCUtil.createDefaultAuthenticationManager() : authManager, options));
    }
    
    /**
     * Creates a new instance of this class using default {@link ISVNOptions}
     * and {@link org.tmatesoft.svn.core.auth.ISVNAuthenticationManager} drivers. 
     * That means this <b>SVNClientManager</b> will use the SVN's default run-time 
     * configuration area.  
     * 
     * @return a new <b>SVNClientManager</b> instance
     */
    public static SVNClientManager newInstance() {
        return new SVNClientManager(null, (ISVNAuthenticationManager) null);
    }

    /**
     * Creates a new instance of this class using the provided {@link ISVNOptions}
     * and default {@link org.tmatesoft.svn.core.auth.ISVNAuthenticationManager} drivers. 
     * That means this <b>SVNClientManager</b> will use the caller's configuration options
     * (which correspond to options found in the default SVN's <i>config</i>
     * file) and the default SVN's <i>servers</i> configuration and auth storage.  
     * 
     * @param  options  a config driver
     * @return          a new <b>SVNClientManager</b> instance
     */
    public static SVNClientManager newInstance(ISVNOptions options) {
        return new SVNClientManager(options, (ISVNAuthenticationManager) null);
    }

    /**
     * Creates a new instance of this class using the provided {@link ISVNOptions}
     * and {@link org.tmatesoft.svn.core.auth.ISVNAuthenticationManager} drivers. 
     * That means this <b>SVNClientManager</b> will use the caller's configuration options
     * (which correspond to options found in the default SVN's <i>config</i>
     * file) as well as authentication credentials and servers options (similar to
     * options found in the default SVN's <i>servers</i>).   
     *
     * @param  options     a config driver
     * @param  authManager an authentication driver
     * @return             a new <b>SVNClientManager</b> instance
     */
    public static SVNClientManager newInstance(ISVNOptions options, ISVNAuthenticationManager authManager) {
        return new SVNClientManager(options, authManager);
    }
    
    /**
     * Creates a new instance of this class using the provided
     * config driver and creator of of <b>SVNRepository</b> objects. 
     * 
     * @param  options         a config driver
     * @param  repositoryPool  a creator of <b>SVNRepository</b> objects
     * @return                 a new <b>SVNClientManager</b> instance
     */
    public static SVNClientManager newInstance(ISVNOptions options, ISVNRepositoryPool repositoryPool) {
        return new SVNClientManager(options, repositoryPool);
    }

    /**
     * Creates a new instance of this class using the provided {@link ISVNOptions}
     * driver and user's credentials to make a default implementation of
     * {@link org.tmatesoft.svn.core.auth.ISVNAuthenticationManager} use them. 
     * That means this <b>SVNClientManager</b> will use the caller's configuration options
     * (which correspond to options found in the default SVN's <i>config</i>
     * file), the default SVN's <i>servers</i> configuration and the caller's
     * credentials.
     * 
     * @param  options     a config driver
     * @param  userName    a user account name
     * @param  password    a user password 
     * @return             a new <b>SVNClientManager</b> instance
     */
    public static SVNClientManager newInstance(ISVNOptions options, String userName, String password) {
        boolean storeAuth = options == null ? true : options.isAuthStorageEnabled();
        ISVNAuthenticationManager authManager = SVNWCUtil.createDefaultAuthenticationManager(null, userName, password, storeAuth);
        return new SVNClientManager(options, authManager);
    }
    
    /**
     * Creates a low-level SVN protocol driver to directly work with
     * a repository. 
     * 
     * <p>
     * The driver created will be set a default {@link org.tmatesoft.svn.core.auth.ISVNAuthenticationManager} 
     * manager.
     * 
     * <p>
     * Used by <b>SVN</b>*<b>Client</b> objects (managed by this 
     * <b>SVNClientManager</b>) to access a repository when needed.
     * 
     * @param  url           a repository location to establish a 
     *                       connection with (will be the root directory
     *                       for the working session)
     * @param  mayReuse      if <span class="javakeyword">true</span> then tries
     *                       first tries to find a reusable driver or creates a new 
     *                       reusable one
     * @return               a low-level API driver for direct interacting
     *                       with a repository
     * @throws SVNException
     */
    public SVNRepository createRepository(SVNURL url, boolean mayReuse) throws SVNException {
        if (myRepositoryPool != null) {
            return myRepositoryPool.createRepository(url, mayReuse);
        }
        SVNRepository repository = SVNRepositoryFactory.create(url);
        repository.setAuthenticationManager(SVNWCUtil.createDefaultAuthenticationManager());
        repository.setDebugLog(getDebugLog());
        return repository;
    }

    public void shutdownConnections(boolean shutdownAll) {
        if (myRepositoryPool != null) {
            myRepositoryPool.shutdownConnections(shutdownAll);
            
        }
    }
    
    /**
     * Returns the run-time configuration options driver
     * which kept by this object.
     * 
     * @return  a run-time options driver
     */
    public ISVNOptions getOptions() {
        return myOptions;
    }
    
    /**
     * Sets an event handler to all <b>SVN</b>*<b>Client</b> objects 
     * created and kept by this <b>SVNClientManager</b>.
     *   
     * <p>
     * The provided event handler will be set only to only those objects
     * that have been already created (<b>SVN</b>*<b>Client</b> objects are
     * instantiated by an <b>SVNClientManager</b> at the moment of the 
     * first call to a <code>get*Client()</code> method). So, the handler
     * won't be set for those ones that have never been requested. However
     * as they are first requested (and thus created) the handler will be 
     * set to them, too, since <b>SVNClientManager</b> is still keeping the handler.
     * 
     * @param handler an event handler
     */
    public void setEventHandler(ISVNEventHandler handler) {
        myEventHandler = handler;
        if (myCommitClient != null) {
            myCommitClient.setEventHandler(handler);
        }
        if (myCopyClient != null) {
            myCopyClient.setEventHandler(handler);
        }
        if (myDiffClient != null) {
            myDiffClient.setEventHandler(handler);
        }
        if (myLogClient != null) {
            myLogClient.setEventHandler(handler);
        }
        if (myMoveClient != null) {
            myMoveClient.setEventHandler(handler);
        }
        if (myStatusClient != null) {
            myStatusClient.setEventHandler(handler);
        }
        if (myUpdateClient != null) {
            myUpdateClient.setEventHandler(handler);
        }
        if (myWCClient != null) {
            myWCClient.setEventHandler(handler);
        }
        if (myAdminClient != null) {
            myAdminClient.setEventHandler(handler);
        }
        if (myLookClient != null) {
            myLookClient.setEventHandler(handler);
        }
    }
    
    /**
     * Returns an instance of the {@link SVNCommitClient} class. 
     * 
     * <p>
     * If it's the first time this method is being called the object is
     * created, initialized and then returned. Further calls to this
     * method will get the same object instantiated at that moment of 
     * the first call. <b>SVNClientManager</b> does not reinstantiate
     * its <b>SVN</b>*<b>Client</b> objects. 
     * 
     * @return an <b>SVNCommitClient</b> instance
     */
    public SVNCommitClient getCommitClient() {
        if (myCommitClient == null) {
            myCommitClient = new SVNCommitClient(this, myOptions);
            myCommitClient.setEventHandler(myEventHandler);
            myCommitClient.setDebugLog(getDebugLog());
        }
        return myCommitClient;
    }

    /**
     * Returns an instance of the {@link SVNAdminClient} class. 
     * 
     * <p>
     * If it's the first time this method is being called the object is
     * created, initialized and then returned. Further calls to this
     * method will get the same object instantiated at that moment of 
     * the first call. <b>SVNClientManager</b> does not reinstantiate
     * its <b>SVN</b>*<b>Client</b> objects. 
     * 
     * @return an <b>SVNAdminClient</b> instance
     */
    public SVNAdminClient getAdminClient() {
        if (myAdminClient == null) {
            myAdminClient = new SVNAdminClient(this, myOptions);
            myAdminClient.setEventHandler(myEventHandler);
            myAdminClient.setDebugLog(getDebugLog());
        }
        return myAdminClient;
    }

    public SVNLookClient getLookClient() {
        if (myLookClient == null) {
            myLookClient = new SVNLookClient(this, myOptions);
            myLookClient.setEventHandler(myEventHandler);
            myLookClient.setDebugLog(getDebugLog());
        }
        return myLookClient;
    }
    
    /**
     * Returns an instance of the {@link SVNCopyClient} class. 
     * 
     * <p>
     * If it's the first time this method is being called the object is
     * created, initialized and then returned. Further calls to this
     * method will get the same object instantiated at that moment of 
     * the first call. <b>SVNClientManager</b> does not reinstantiate
     * its <b>SVN</b>*<b>Client</b> objects. 
     * 
     * @return an <b>SVNCopyClient</b> instance
     */
    public SVNCopyClient getCopyClient() {
        if (myCopyClient == null) {
            myCopyClient = new SVNCopyClient(this, myOptions);
            myCopyClient.setEventHandler(myEventHandler);
            myCopyClient.setDebugLog(getDebugLog());
        }
        return myCopyClient;
    }

    /**
     * Returns an instance of the {@link SVNDiffClient} class. 
     * 
     * <p>
     * If it's the first time this method is being called the object is
     * created, initialized and then returned. Further calls to this
     * method will get the same object instantiated at that moment of 
     * the first call. <b>SVNClientManager</b> does not reinstantiate
     * its <b>SVN</b>*<b>Client</b> objects. 
     * 
     * @return an <b>SVNDiffClient</b> instance
     */
    public SVNDiffClient getDiffClient() {
        if (myDiffClient == null) {
            myDiffClient = new SVNDiffClient(this, myOptions);
            myDiffClient.setEventHandler(myEventHandler);
            myDiffClient.setDebugLog(getDebugLog());
        }
        return myDiffClient;
    }

    /**
     * Returns an instance of the {@link SVNLogClient} class. 
     * 
     * <p>
     * If it's the first time this method is being called the object is
     * created, initialized and then returned. Further calls to this
     * method will get the same object instantiated at that moment of 
     * the first call. <b>SVNClientManager</b> does not reinstantiate
     * its <b>SVN</b>*<b>Client</b> objects. 
     * 
     * @return an <b>SVNLogClient</b> instance
     */
    public SVNLogClient getLogClient() {
        if (myLogClient == null) {
            myLogClient = new SVNLogClient(this, myOptions);
            myLogClient.setEventHandler(myEventHandler);
            myLogClient.setDebugLog(getDebugLog());
        }
        return myLogClient;
    }

    /**
     * Returns an instance of the {@link SVNMoveClient} class. 
     * 
     * <p>
     * If it's the first time this method is being called the object is
     * created, initialized and then returned. Further calls to this
     * method will get the same object instantiated at that moment of 
     * the first call. <b>SVNClientManager</b> does not reinstantiate
     * its <b>SVN</b>*<b>Client</b> objects. 
     * 
     * @return an <b>SVNMoveClient</b> instance
     */
    public SVNMoveClient getMoveClient() {
        if (myMoveClient == null) {
            myMoveClient = new SVNMoveClient(this, myOptions);
            myMoveClient.setEventHandler(myEventHandler);
            myMoveClient.setDebugLog(getDebugLog());
        }
        return myMoveClient;
    }

    /**
     * Returns an instance of the {@link SVNStatusClient} class. 
     * 
     * <p>
     * If it's the first time this method is being called the object is
     * created, initialized and then returned. Further calls to this
     * method will get the same object instantiated at that moment of 
     * the first call. <b>SVNClientManager</b> does not reinstantiate
     * its <b>SVN</b>*<b>Client</b> objects. 
     * 
     * @return an <b>SVNStatusClient</b> instance
     */
    public SVNStatusClient getStatusClient() {
        if (myStatusClient == null) {
            myStatusClient = new SVNStatusClient(this, myOptions);
            myStatusClient.setEventHandler(myEventHandler);
            myStatusClient.setDebugLog(getDebugLog());
        }
        return myStatusClient;
    }

    /**
     * Returns an instance of the {@link SVNUpdateClient} class. 
     * 
     * <p>
     * If it's the first time this method is being called the object is
     * created, initialized and then returned. Further calls to this
     * method will get the same object instantiated at that moment of 
     * the first call. <b>SVNClientManager</b> does not reinstantiate
     * its <b>SVN</b>*<b>Client</b> objects. 
     * 
     * @return an <b>SVNUpdateClient</b> instance
     */
    public SVNUpdateClient getUpdateClient() {
        if (myUpdateClient == null) {
            myUpdateClient = new SVNUpdateClient(this, myOptions);
            myUpdateClient.setEventHandler(myEventHandler);
            myUpdateClient.setDebugLog(getDebugLog());
        }
        return myUpdateClient;
    }

    /**
     * Returns an instance of the {@link SVNWCClient} class. 
     * 
     * <p>
     * If it's the first time this method is being called the object is
     * created, initialized and then returned. Further calls to this
     * method will get the same object instantiated at that moment of 
     * the first call. <b>SVNClientManager</b> does not reinstantiate
     * its <b>SVN</b>*<b>Client</b> objects. 
     * 
     * @return an <b>SVNWCClient</b> instance
     */
    public SVNWCClient getWCClient() {
        if (myWCClient == null) {
            myWCClient = new SVNWCClient(this, myOptions);
            myWCClient.setEventHandler(myEventHandler);
            myWCClient.setDebugLog(getDebugLog());
        }
        return myWCClient;
    }
    
    /**
     * Returns the debug logger currently in use.  
     * 
     * <p>
     * If no debug logger has been specified by the time this call occurs, 
     * a default one (returned by <code>org.tmatesoft.svn.util.SVNDebugLog.getDefaultLog()</code>) 
     * will be created and used.
     * 
     * @return a debug logger
     */
    public ISVNDebugLog getDebugLog() {
        if (myDebugLog == null) {
            return SVNDebugLog.getDefaultLog();
        }
        return myDebugLog;
    }

    /**
     * Sets a logger to write debug log information to. Sets this same logger
     * object to all <b>SVN</b>*<b>Client</b> objects instantiated by this 
     * moment. 
     * 
     * @param log a debug logger
     */
    public void setDebugLog(ISVNDebugLog log) {
        myDebugLog = log;
        if (myCommitClient != null) {
            myCommitClient.setDebugLog(log);
        }
        if (myCopyClient != null) {
            myCopyClient.setDebugLog(log);
        }
        if (myDiffClient != null) {
            myDiffClient.setDebugLog(log);
        }
        if (myLogClient != null) {
            myLogClient.setDebugLog(log);
        }
        if (myMoveClient != null) {
            myMoveClient.setDebugLog(log);
        }
        if (myStatusClient != null) {
            myStatusClient.setDebugLog(log);
        }
        if (myUpdateClient != null) {
            myUpdateClient.setDebugLog(log);
        }
        if (myWCClient != null) {
            myWCClient.setDebugLog(log);
        }
        if (myAdminClient != null) {
            myAdminClient.setDebugLog(log);
        }
        if (myLookClient != null) {
            myLookClient.setDebugLog(log);
        }
    }
}
