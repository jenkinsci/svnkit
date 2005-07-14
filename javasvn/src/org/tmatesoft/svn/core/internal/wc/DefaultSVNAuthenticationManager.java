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

    private boolean myIsStoreAuth;
    private ISVNAuthenticationProvider[] myProviders;
    
    private SVNAuthentication myPreviousAuthentication;
    private String myPreviousErrorMessage;

    public DefaultSVNAuthenticationManager(File configDirectory, boolean storeAuth, String userName, String password) {
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
        myProviders[2] = new PersistentAuthenticationProvider(new File(configDirectory, "auth"));
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
        if (myIsStoreAuth && authentication.isStorageAllowed() && myProviders[2] != null) {
            ((PersistentAuthenticationProvider) myProviders[2]).saveAuthentication(authentication, kind, realm);
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
    
    private static class PersistentAuthenticationProvider implements ISVNAuthenticationProvider {
        
        private File myDirectory;

        public PersistentAuthenticationProvider(File directory) {
            myDirectory = directory;
        }

        public SVNAuthentication requestClientAuthentication(String kind, String url, String realm, String errorMessage, SVNAuthentication previousAuth, boolean authMayBeStored) {
            File dir = new File(myDirectory, kind);
            if (!dir.isDirectory()) {
                return null;
            }
            String fileName = SVNFileUtil.computeChecksum(realm);
            File authFile = new File(dir, fileName);
            if (authFile.exists()) {
                SVNProperties props = new SVNProperties(authFile, "");
                try {
                    String storedRealm = props.getPropertyValue("svn:realmstring");
                    if (storedRealm == null || !storedRealm.equals(realm)) {
                        return null;
                    }
                    if ("wincrypt".equals(props.getPropertyValue("passtype"))) {
                        return null;
                    }
                    String password = props.getPropertyValue("password");
                    String userName = props.getPropertyValue("username");
                    String path = props.getPropertyValue("key");
                    String passphrase = props.getPropertyValue("passphrase");
                    if (ISVNAuthenticationManager.PASSWORD.equals(kind)) {
                        return new SVNPasswordAuthentication(userName, password, true);
                    } else if (ISVNAuthenticationManager.SSH.equals(kind)) {
                        if (path != null) {
                            return new SVNSSHAuthentication(userName, new File(path), passphrase, authMayBeStored);
                        } else if (password != null) {
                            return new SVNSSHAuthentication(userName, password, authMayBeStored);
                        }                    
                    }
                } catch (SVNException e) {
                    //
                }
            }
            return null;
        }
        
        public void saveAuthentication(SVNAuthentication auth, String kind, String realm) {
            File dir = new File(myDirectory, kind);
            if (!dir.exists()) {
                dir.mkdirs();
            }
            if (!dir.isDirectory()) {
                return;
            }
            // get file name for auth and store password.
            String fileName = SVNFileUtil.computeChecksum(realm);
            File authFile = new File(dir, fileName);

            SVNProperties props = new SVNProperties(authFile, "");
            props.delete();
            
            try {
                props.setPropertyValue("svn:realmstring", realm);
                props.setPropertyValue("username", auth.getUserName());
                if (ISVNAuthenticationManager.PASSWORD.equals(kind)) {
                    SVNPasswordAuthentication passwordAuth = (SVNPasswordAuthentication) auth;
                    props.setPropertyValue("password", passwordAuth.getPassword());
                } else if (ISVNAuthenticationManager.SSH.equals(kind)) {
                    SVNSSHAuthentication sshAuth = (SVNSSHAuthentication) auth;
                    props.setPropertyValue("password", sshAuth.getPassword());
                    if (sshAuth.getPrivateKeyFile() != null) { 
                        String path = SVNPathUtil.validateFilePath(sshAuth.getPrivateKeyFile().getAbsolutePath());
                        props.setPropertyValue("passphrase", sshAuth.getPassphrase());
                        props.setPropertyValue("key", path);
                    }
                }
                SVNFileUtil.setReadonly(props.getFile(), false);
            } catch (SVNException e) {
                props.getFile().delete();
            }
        }
        

        public int acceptServerAuthentication(String url, Object serverAuth, ISVNAuthenticationManager manager, boolean resultMayBeStored) {
            return ACCEPTED;
        }
        
    }

}
