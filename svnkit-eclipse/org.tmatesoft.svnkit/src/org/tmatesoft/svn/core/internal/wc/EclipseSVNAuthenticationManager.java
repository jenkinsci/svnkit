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
package org.tmatesoft.svn.core.internal.wc;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.Platform;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationManager;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationProvider;
import org.tmatesoft.svn.core.auth.SVNAuthentication;
import org.tmatesoft.svn.core.auth.SVNPasswordAuthentication;
import org.tmatesoft.svn.core.auth.SVNSSHAuthentication;
import org.tmatesoft.svn.core.auth.SVNSSLAuthentication;
import org.tmatesoft.svn.core.auth.SVNUserNameAuthentication;

/**
 * @version 1.1.0
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

    public EclipseSVNAuthenticationManager(File configDirectory, boolean storeAuth, String userName, String password, File keyFile, String passphrase) {
        super(configDirectory, storeAuth, userName, password, keyFile, passphrase);
    }

    protected ISVNAuthenticationProvider createCacheAuthenticationProvider(File authDir) {
        return new KeyringAuthenticationProvider();
    }

    protected ISVNAuthenticationProvider createDefaultAuthenticationProvider(String userName, String password, File privateKey, String passphrase, boolean allowSave) {
        return new ISVNAuthenticationProvider() {
            public SVNAuthentication requestClientAuthentication(String kind, SVNURL url, String realm, SVNErrorMessage errorMessage, SVNAuthentication previousAuth, boolean authMayBeStored) {
                return null;
            }
            public int acceptServerAuthentication(SVNURL url, String realm, Object certificate, boolean resultMayBeStored) {
                return ACCEPTED;
            }
        };
    }
    
    static class KeyringAuthenticationProvider implements ISVNAuthenticationProvider, IPersistentAuthenticationProvider {

        public SVNAuthentication requestClientAuthentication(String kind, SVNURL url, String realm, SVNErrorMessage errorMessage, SVNAuthentication previousAuth, boolean authMayBeStored) {
            // get from key-ring, use realm.
            realm = realm == null ? DEFAULT_URL.toString() : realm;
            Map info = Platform.getAuthorizationInfo(DEFAULT_URL, realm, kind);
            // convert info to SVNAuthentication.
            if (info != null && ISVNAuthenticationManager.SSL.equals(kind)) {
                String path = (String) info.get("cert");
                String password = (String) info.get("password");
                if (path != null) {
                    return new SVNSSLAuthentication(new File(path), password, authMayBeStored);
                }
            } else if (info != null && !info.isEmpty() && info.get("username") != null) {
                if (ISVNAuthenticationManager.PASSWORD.equals(kind)) {
                    return new SVNPasswordAuthentication((String) info.get("username"), (String) info.get("password"), authMayBeStored);
                } else if (ISVNAuthenticationManager.SSH.equals(kind)) {
                    int port = url.hasPort() ? url.getPort() : -1;
                    if (port < 0 && info.get("port") != null) {
                        port = Integer.parseInt((String) info.get("port"));
                    }
                    if (port < 0) {
                        // will give us default port.
                        port = url.getPort();
                    }
                    if (info.get("key") != null) {
                        File keyPath = new File((String) info.get("key"));
                        return new SVNSSHAuthentication((String) info.get("username"), keyPath, (String) info.get("passphrase"), port, authMayBeStored);
                    } else if (info.get("password") != null) {
                        return new SVNSSHAuthentication((String) info.get("username"), (String) info.get("password"), port, authMayBeStored);
                    }
                } else if (ISVNAuthenticationManager.USERNAME.equals(kind)) {
                    return new SVNUserNameAuthentication((String) info.get("username"), authMayBeStored);
                }
            }
            return null;
        }

        public int acceptServerAuthentication(SVNURL url, String realm, Object certificate, boolean resultMayBeStored) {
            return ACCEPTED_TEMPORARY;
        }

        public void saveAuthentication(SVNAuthentication auth, String kind, String realm) {
            if (!(auth instanceof SVNSSLAuthentication) && (auth.getUserName() == null || "".equals(auth.getUserName()))) {
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
            } else if (auth instanceof SVNSSLAuthentication) {
                SVNSSLAuthentication sslAuth = (SVNSSLAuthentication) auth;
                File path = sslAuth.getCertificateFile();
                String password = sslAuth.getPassword();
                if (path != null) {
                    info.put("cert", path.getAbsolutePath());
                    if (password != null && !"".equals(password)) {
                        info.put("password", password);
                    }
                }
            }
            try {
                Platform.addAuthorizationInfo(DEFAULT_URL, realm, kind, info);
            } catch (CoreException e) {
            }
        }
    
    }

}
