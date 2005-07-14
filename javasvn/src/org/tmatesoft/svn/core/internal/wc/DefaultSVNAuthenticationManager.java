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
package org.tmatesoft.svn.core.internal.wc;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import org.tmatesoft.svn.core.auth.ISVNAuthenticationManager;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationProvider;
import org.tmatesoft.svn.core.auth.ISVNProxyManager;
import org.tmatesoft.svn.core.auth.ISVNSSLManager;
import org.tmatesoft.svn.core.auth.SVNAuthentication;
import org.tmatesoft.svn.core.auth.SVNPasswordAuthentication;
import org.tmatesoft.svn.core.auth.SVNSSHAuthentication;
import org.tmatesoft.svn.core.io.SVNAuthenticationException;
import org.tmatesoft.svn.core.io.SVNCancelException;
import org.tmatesoft.svn.core.io.SVNException;

/**
 * @version 1.0
 * @author  TMate Software Ltd.
 */
public class DefaultSVNAuthenticationManager implements ISVNAuthenticationManager {

    private File myConfigDirectory;
    private boolean myIsStoreAuth;
    private ISVNAuthenticationProvider[] myProviders;
    
    private SVNAuthentication myPreviousAuthentication;
    private String myPreviousErrorMessage;

    public DefaultSVNAuthenticationManager(File configDirectory, boolean storeAuth, String userName, String password) {
        myConfigDirectory = configDirectory;
        myIsStoreAuth = storeAuth;
        
        myProviders = new ISVNAuthenticationProvider[4];
        if (userName != null) {
            // 'default' provider.
            password = password == null ? "" : password;
            ISVNAuthenticationProvider dumbProvider = new DumbAuthenticationProvider(userName, password, myIsStoreAuth);
            myProviders[0] = dumbProvider;
        }
        // runtime provider.
        ISVNAuthenticationProvider cacheProvider = new CacheAuthenticationProvider(new HashMap());
        myProviders[1] = cacheProvider;
        // disk storage providers
    }

    public void setAuthenticationProvider(ISVNAuthenticationProvider provider) {
        // add provider to list
        myProviders[3] = provider; 
    }

    public ISVNProxyManager getProxyManager(String url) {
        return null;
    }

    public ISVNSSLManager getSSLManager(String url) {
        return null;
    }

    public SVNAuthentication getFirstAuthentication(String kind, String realm) throws SVNException {
        myPreviousAuthentication = null;
        myPreviousErrorMessage = null;
        // iterate over providers and ask for auth till it is found.
        for (int i = 0; i < myProviders.length; i++) {
            if (myProviders[i] == null) {
                continue;
            }
            SVNAuthentication auth = myProviders[i].requestClientAuthentication(kind, null, realm, null, null, myIsStoreAuth);
            if (auth != null) {
                myPreviousAuthentication = auth;
                return auth;
            }
            if (i == 3) {
                throw new SVNCancelException();
            }
        }
        throw new SVNAuthenticationException("svn: Authentication required for '" + realm + "'");
    }

    public SVNAuthentication getNextAuthentication(String kind, String realm) throws SVNException {
        if (myProviders[3] == null) {
            throw new SVNAuthenticationException("svn: Authentication required for '" + realm + "'");
        }
        SVNAuthentication auth = myProviders[3].requestClientAuthentication(kind, null, realm, myPreviousErrorMessage, myPreviousAuthentication, myIsStoreAuth);
        if (auth == null) {
            throw new SVNCancelException();
        }
        return auth;
    }

    public void acknowledgeAuthentication(boolean accepted, String kind, String realm, String errorMessage, SVNAuthentication authentication) {
        if (!accepted) {
            myPreviousErrorMessage = errorMessage;
            myPreviousAuthentication = authentication;
            return;
        }
        if (myIsStoreAuth && authentication.isStorageAllowed()) {
        }
        ((CacheAuthenticationProvider) myProviders[1]).saveAuthentication(authentication, realm);
    }
    
    private static class DumbAuthenticationProvider implements ISVNAuthenticationProvider {
        
        private String myUserName;
        private String myPassword;
        private boolean myIsStore;
        
        public DumbAuthenticationProvider(String userName, String password, boolean store) {
            myUserName = userName;
            myPassword = password;
            myIsStore = store;
        }
        public SVNAuthentication requestClientAuthentication(String kind, String url, String realm, String errorMessage, SVNAuthentication previousAuth, boolean authMayBeStored) {
            if (previousAuth == null) {
                if (ISVNAuthenticationManager.SSH.equals(kind)) {
                    return new SVNSSHAuthentication(myUserName, myPassword, myIsStore);
                } else if (ISVNAuthenticationManager.PASSWORD.equals(kind)) {
                    return new SVNPasswordAuthentication(myUserName, myPassword, myIsStore);
                }
            }
            return null;
        }
        public int acceptServerAuthentication(String url, Object serverAuth, ISVNAuthenticationManager manager, boolean resultMayBeStored) {
            return ACCEPTED;
        }
    }

    private static class CacheAuthenticationProvider implements ISVNAuthenticationProvider {        
        
        private Map myStorage;

        // one map per realm
        // map: kind->map (realm->single auth (last valid), so only getFirst will work).
        public CacheAuthenticationProvider(Map cachedStorage) {
            myStorage = cachedStorage;
            if (myStorage == null) {
                myStorage = new HashMap();
            }
        }

        public SVNAuthentication requestClientAuthentication(String kind, String url, String realm, String errorMessage, SVNAuthentication previousAuth, boolean authMayBeStored) {
            // get next after prev for select kind and realm.
            if (previousAuth != null) {
                return null;
            }
            Map kindMap = (Map) myStorage.get(kind);
            if (kindMap == null) {
                return null;
            }
            return (SVNAuthentication) kindMap.get(realm);
        }
        
        public void saveAuthentication(SVNAuthentication auth, String realm) {
            if (auth == null || realm == null) {
                return;
            }
            String kind = auth instanceof SVNSSHAuthentication ? ISVNAuthenticationManager.SSH : ISVNAuthenticationManager.PASSWORD;
            Map kindMap = (Map) myStorage.get(kind);
            if (kindMap == null) {
                kindMap = new HashMap();
                myStorage.put(kind, kindMap);
            }
            kindMap.put(realm, auth);
        }
        
        public int acceptServerAuthentication(String url, Object serverAuth, ISVNAuthenticationManager manager, boolean resultMayBeStored) {
            return ACCEPTED;
        }
    }

}
