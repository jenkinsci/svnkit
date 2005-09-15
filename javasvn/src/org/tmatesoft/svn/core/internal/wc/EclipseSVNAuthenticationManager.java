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
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.Platform;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationManager;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationProvider;
import org.tmatesoft.svn.core.auth.SVNAuthentication;
import org.tmatesoft.svn.core.auth.SVNPasswordAuthentication;
import org.tmatesoft.svn.core.auth.SVNSSHAuthentication;
import org.tmatesoft.svn.util.SVNDebugLog;

/**
 * @version 1.0
 * @author  TMate Software Ltd.
 */
public class EclipseSVNAuthenticationManager extends DefaultSVNAuthenticationManager {

    private static URL DEFAULT_URL;
    
    static {
        try {
            DEFAULT_URL = new URL("http://tmate.org/svn/");
        } catch (MalformedURLException e) {            
        }
    }

    public EclipseSVNAuthenticationManager(File configDirectory, boolean storeAuth, String userName, String password) {
        super(configDirectory, storeAuth, userName, password);
    }

    protected ISVNAuthenticationProvider createCacheAuthenticationProvider(File authDir) {
        return new KeyringAuthenticationProvider();
    }
    
    static class KeyringAuthenticationProvider implements ISVNAuthenticationProvider, IPersistentAuthenticationProvider {

        public SVNAuthentication requestClientAuthentication(String kind, SVNURL url, String realm, String errorMessage, SVNAuthentication previousAuth, boolean authMayBeStored) {
            // get from key-ring, use realm.
            realm = realm == null ? DEFAULT_URL.toString() : realm;
            Map info = Platform.getAuthorizationInfo(DEFAULT_URL, realm, kind);
            // convert info to SVNAuthentication.
            if (info != null && !info.isEmpty() && info.get("username") != null) {
                if (ISVNAuthenticationManager.PASSWORD.equals(kind)) {
                    return new SVNPasswordAuthentication((String) info.get("username"), (String) info.get("password"), authMayBeStored);
                } else if (ISVNAuthenticationManager.SSH.equals(kind)) {
                    int port = url.getPort();
                    if (port < 0 && info.get("port") != null) {
                        port = Integer.parseInt((String) info.get("port"));
                    }
                    if (port < 0) {
                        port = 22;
                    }
                    if (info.get("key") != null) {
                        File keyPath = new File((String) info.get("key"));
                        return new SVNSSHAuthentication((String) info.get("username"), keyPath, (String) info.get("passphrase"), port, authMayBeStored);
                    } else if (info.get("password") != null) {
                        return new SVNSSHAuthentication((String) info.get("username"), (String) info.get("password"), port, authMayBeStored);
                    }
                }
            }
            return null;
        }

        public int acceptServerAuthentication(SVNURL url, String realm, Object certificate, boolean resultMayBeStored) {
            return ACCEPTED_TEMPORARY;
        }

        public void saveAuthentication(SVNAuthentication auth, String kind, String realm) {
            SVNDebugLog.logInfo("saving password");
            if (auth.getUserName() == null || "".equals(auth.getUserName())) {
                return;
            }
            realm = realm == null ? DEFAULT_URL.toString() : realm;
            Map info = new HashMap();
            // convert info to SVNAuthentication.
            info.put("username", auth.getUserName());
            if (auth instanceof SVNPasswordAuthentication) {
                info.put("password", ((SVNPasswordAuthentication) auth).getPassword());
            } else if (auth instanceof SVNSSHAuthentication) {
                SVNSSHAuthentication sshAuth = (SVNSSHAuthentication) auth;
                if (sshAuth.getPrivateKeyFile() != null) {
                    info.put("key", sshAuth.getPrivateKeyFile().getAbsolutePath());
                    if (sshAuth.getPassphrase() != null) {
                        info.put("passphrase", sshAuth.getPassphrase());
                    }
                } else if (sshAuth.getPassword() != null) {
                    info.put("password", sshAuth.getPassword());
                }
                if (sshAuth.getPortNumber() >= 0) {
                    info.put("port", Integer.toString(sshAuth.getPortNumber()));
                }
            }
            try {
                Platform.addAuthorizationInfo(DEFAULT_URL, realm, kind, info);
            } catch (CoreException e) {
            }
        }
    
    }
}
