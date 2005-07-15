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

import org.tmatesoft.svn.core.auth.ISVNAuthenticationManager;
import org.tmatesoft.svn.core.internal.io.svn.SVNRepositoryFactoryImpl;
import org.tmatesoft.svn.core.io.SVNException;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.io.SVNRepositoryLocation;

/**
 * @version 1.0
 * @author  TMate Software Ltd.
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

    public SVNRepository createRepository(String url) throws SVNException {
        SVNRepositoryLocation location = SVNRepositoryLocation.parseURL(url);
        SVNRepository repository = SVNRepositoryFactoryImpl.create(location);
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
