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

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationManager;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.io.SVNRepositoryFactory;

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
 * <li>To instantiate an <b>SVN</b>*<b>Client</b> object you need
 * to provide a run-time configuration driver - {@link ISVNOptions} - 
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
 *     SVNClientManager clientManager = SVNClientManager.newInstance(myOptions, <span class="javastring">"name"</span>, <span class="javastring">"passw"</span>);
 *     </pre><br />
 * Having instantiated an <b>SVNClientManager</b> in one of these ways, all 
 * the <b>SVN</b>*<b>Client</b> objects it will provide you will share those
 * drivers, so you don't need to code much to provide the same drivers to each
 * <b>SVN</b>*<b>Client</b> instance by yourself.
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
 * <b>SVN</b>*<b>Client</b> objects provided by <b>SVNClientManager</b>.
 * <li>
 * </ol>
 * 
 * 
 * 
 * @version 1.0
 * @author  TMate Software Ltd.
 * 
 * @see     <a target="_top" href="http://tmate.org/svn/kb/examples/">Examples</a>
 */
public class SVNClientManager implements ISVNRepositoryFactory {
    
    private ISVNOptions myOptions;
    private ISVNAuthenticationManager myAuthenticationManager;
    
    private SVNCommitClient myCommitClient;
    private SVNCopyClient myCopyClient;
    private SVNDiffClient myDiffClient;
    private SVNLogClient myLogClient;
    private SVNMoveClient myMoveClient;
    private SVNStatusClient myStatusClient;
    private SVNUpdateClient myUpdateClient;
    private SVNWCClient myWCClient;
    
    private ISVNEventHandler myEventHandler;

    private SVNClientManager(ISVNOptions options, ISVNAuthenticationManager authManager) {
        myOptions = options;
        if (myOptions == null) {
            myOptions = SVNWCUtil.createDefaultOptions(true);
        }
        myAuthenticationManager = authManager;
        if (myAuthenticationManager == null) {
            myAuthenticationManager = SVNWCUtil.createDefaultAuthenticationManager();
        }
    }
    
    public static SVNClientManager newInstance() {
        return new SVNClientManager(null, null);
    }

    public static SVNClientManager newInstance(ISVNOptions options) {
        return new SVNClientManager(options, null);
    }
    
    public static SVNClientManager newInstance(ISVNOptions options, ISVNAuthenticationManager authManager) {
        return new SVNClientManager(options, authManager);
    }

    public static SVNClientManager newInstance(ISVNOptions options, String userName, String password) {
        boolean storeAuth = options == null ? true : options.isAuthStorageEnabled();
        ISVNAuthenticationManager authManager = SVNWCUtil.createDefaultAuthenticationManager(null, userName, password, storeAuth);
        return new SVNClientManager(options, authManager);
    }

    public SVNRepository createRepository(SVNURL url) throws SVNException {
        SVNRepository repository = SVNRepositoryFactory.create(url);
        repository.setAuthenticationManager(myAuthenticationManager);
        return repository;
    }

    public ISVNOptions getOptions() {
        return myOptions;
    }
    
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
    }

    public SVNCommitClient getCommitClient() {
        if (myCommitClient == null) {
            myCommitClient = new SVNCommitClient(this, myOptions);
            myCommitClient.setEventHandler(myEventHandler);
        }
        return myCommitClient;
    }

    public SVNCopyClient getCopyClient() {
        if (myCopyClient == null) {
            myCopyClient = new SVNCopyClient(this, myOptions);
            myCopyClient.setEventHandler(myEventHandler);
        }
        return myCopyClient;
    }

    public SVNDiffClient getDiffClient() {
        if (myDiffClient == null) {
            myDiffClient = new SVNDiffClient(this, myOptions);
            myDiffClient.setEventHandler(myEventHandler);
        }
        return myDiffClient;
    }

    public SVNLogClient getLogClient() {
        if (myLogClient == null) {
            myLogClient = new SVNLogClient(this, myOptions);
            myLogClient.setEventHandler(myEventHandler);
        }
        return myLogClient;
    }

    public SVNMoveClient getMoveClient() {
        if (myMoveClient == null) {
            myMoveClient = new SVNMoveClient(this, myOptions);
            myMoveClient.setEventHandler(myEventHandler);
        }
        return myMoveClient;
    }

    public SVNStatusClient getStatusClient() {
        if (myStatusClient == null) {
            myStatusClient = new SVNStatusClient(this, myOptions);
            myStatusClient.setEventHandler(myEventHandler);
        }
        return myStatusClient;
    }

    public SVNUpdateClient getUpdateClient() {
        if (myUpdateClient == null) {
            myUpdateClient = new SVNUpdateClient(this, myOptions);
            myUpdateClient.setEventHandler(myEventHandler);
        }
        return myUpdateClient;
    }

    public SVNWCClient getWCClient() {
        if (myWCClient == null) {
            myWCClient = new SVNWCClient(this, myOptions);
            myWCClient.setEventHandler(myEventHandler);
        }
        return myWCClient;
    }

}
