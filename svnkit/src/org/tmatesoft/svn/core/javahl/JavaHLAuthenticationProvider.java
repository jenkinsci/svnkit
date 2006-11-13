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
package org.tmatesoft.svn.core.javahl;

import java.io.File;
import java.security.MessageDigest;
import java.security.cert.X509Certificate;

import org.tigris.subversion.javahl.PromptUserPassword;
import org.tigris.subversion.javahl.PromptUserPassword2;
import org.tigris.subversion.javahl.PromptUserPassword3;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNURL;
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
class JavaHLAuthenticationProvider implements ISVNAuthenticationProvider {
    
    private PromptUserPassword myPrompt;
    
    public JavaHLAuthenticationProvider(PromptUserPassword prompt){
        myPrompt = prompt;
    }

    public SVNAuthentication requestClientAuthentication(String kind, SVNURL url, String realm, SVNErrorMessage errorMessage, SVNAuthentication previousAuth, boolean authMayBeStored) {
        if (ISVNAuthenticationManager.SSH.equals(kind) && myPrompt instanceof PromptUserPasswordSSH) {
            PromptUserPasswordSSH prompt4 = (PromptUserPasswordSSH) myPrompt;
            String userName = previousAuth != null && previousAuth.getUserName() != null ? previousAuth.getUserName() : getUserName(null, url);
            int port = url != null ? url.getPort() : -1;
            if (prompt4.promptSSH(realm, userName, port, authMayBeStored)) {
                String password = prompt4.getPassword();
                String keyPath = prompt4.getSSHPrivateKeyPath();
                String passphrase = prompt4.getSSHPrivateKeyPassphrase();
                userName = getUserName(prompt4.getUsername(), url);
                if ("".equals(passphrase)) {
                    passphrase = null;
                }
                port = prompt4.getSSHPort();
                if (port < 0 && url != null) {
                    port = url.getPort();
                }
                if (port < 0) {
                    port = 22;
                }
                boolean save = prompt4.userAllowedSave();
                if (keyPath != null && !"".equals(keyPath)) {
                    return new SVNSSHAuthentication(userName, new File(keyPath), passphrase, port, save);
                } else if (password != null){
                    return new SVNSSHAuthentication(userName, password, port, save);
                }
            }
            return null;                        
        } else if (ISVNAuthenticationManager.SSL.equals(kind) && myPrompt instanceof PromptUserPasswordSSL) {
            PromptUserPasswordSSL prompt4 = (PromptUserPasswordSSL) myPrompt;
            if (prompt4.promptSSL(realm, authMayBeStored)) {
                String cert = prompt4.getSSLClientCertPath();
                String password = prompt4.getSSLClientCertPassword();
                if (cert != null) {
                    if ("".equals(password)) {
                        password = null;
                    }
                    boolean save = prompt4.userAllowedSave();
                    return new SVNSSLAuthentication(new File(cert), password, save);
                }
            }
            return null;                        
        }
        if (ISVNAuthenticationManager.SSH.equals(kind) && previousAuth == null) {
            // use configuration file here? but it was already used once...
            String keyPath = System.getProperty("svnkit.ssh2.key", System.getProperty("javasvn.ssh2.key"));
            String userName = getUserName(System.getProperty("svnkit.ssh2.username", System.getProperty("javasvn.ssh2.username")), url);
            String passPhrase = System.getProperty("svnkit.ssh2.passphrase", System.getProperty("javasvn.ssh2.passphrase"));
            if (userName == null) {
                return null;
            }
            if (keyPath != null && previousAuth == null) {
                // use port number from configuration file?
                return new SVNSSHAuthentication(userName, new File(keyPath), passPhrase, -1, true);
            }
            // try to get password for ssh from the user.
        } else if(ISVNAuthenticationManager.USERNAME.equals(kind)) {
            String userName = previousAuth != null && previousAuth.getUserName() != null ? previousAuth.getUserName() : getUserName(null, url);
            if (myPrompt instanceof PromptUserPasswordUser) {
                PromptUserPasswordUser prompt3 = (PromptUserPasswordUser) myPrompt;
                if (prompt3.promptUser(realm, userName, authMayBeStored))  {
                    return new SVNUserNameAuthentication(prompt3.getUsername(), prompt3.userAllowedSave());
                }
                return null;
            } else if (myPrompt instanceof PromptUserPassword3) {
                PromptUserPassword3 prompt3 = (PromptUserPassword3) myPrompt;
                if (prompt3.prompt(realm, userName, authMayBeStored))  {
                    return new SVNUserNameAuthentication(prompt3.getUsername(), prompt3.userAllowedSave());
                }
                return null;
            } 
            if (myPrompt.prompt(realm, userName)) {
                return new SVNUserNameAuthentication(myPrompt.getUsername(), false);
            }
            return null;            
        } else if(!ISVNAuthenticationManager.PASSWORD.equals(kind)){
            return null;
        }
        String userName = previousAuth != null && previousAuth.getUserName() != null ? previousAuth.getUserName() : getUserName(null, url);
        if (myPrompt instanceof PromptUserPassword3) {
            PromptUserPassword3 prompt3 = (PromptUserPassword3) myPrompt;
            if(prompt3.prompt(realm, userName, authMayBeStored)){
                if (ISVNAuthenticationManager.SSH.equals(kind)) {
                    // use default port number from configuration file (should be in previous auth).
                    int portNumber = (previousAuth instanceof SVNSSHAuthentication) ? ((SVNSSHAuthentication) previousAuth).getPortNumber() : -1;
                    return new SVNSSHAuthentication(prompt3.getUsername(), prompt3.getPassword(), portNumber, prompt3.userAllowedSave());
                } 
                return new SVNPasswordAuthentication(prompt3.getUsername(), prompt3.getPassword(), prompt3.userAllowedSave());
            }
        }else{
            if(myPrompt.prompt(realm, userName)){
                if (ISVNAuthenticationManager.SSH.equals(kind)) {
                    return new SVNSSHAuthentication(userName, myPrompt.getPassword(), -1, true);
                } 
                return new SVNPasswordAuthentication(myPrompt.getUsername(), myPrompt.getPassword(), true);
            }
        }
        return null;
    }

    public int acceptServerAuthentication(SVNURL url, String realm, Object serverAuth,  boolean resultMayBeStored) {
        if (serverAuth != null && myPrompt instanceof PromptUserPassword2) {
            PromptUserPassword2 sslPrompt = (PromptUserPassword2) myPrompt;
            serverAuth = serverAuth instanceof X509Certificate ? 
                    getServerCertificateInfo((X509Certificate) serverAuth) : serverAuth;
            if (serverAuth == null) {
                serverAuth = "";
            }
            return sslPrompt.askTrustSSLServer(serverAuth.toString(), resultMayBeStored);
        }
        return ACCEPTED;
    }

    private static String getFingerprint(X509Certificate cert) {
          StringBuffer s = new StringBuffer();
          try  {
             MessageDigest md = MessageDigest.getInstance("SHA1");
             md.update(cert.getEncoded());
             byte[] digest = md.digest();
             for (int i= 0; i < digest.length; i++)  {
                if (i != 0) {
                    s.append(':');
                }
                int b = digest[i] & 0xFF;
                String hex = Integer.toHexString(b);
                if (hex.length() == 1) {
                    s.append('0');
                }
                s.append(hex.toLowerCase());
             }
          } catch (Exception e)  {
          } 
          return s.toString();
       }

    private static String getServerCertificateInfo(X509Certificate cert) {
        StringBuffer info = new StringBuffer();
        info.append(" - Subject: ");
        info.append(cert.getSubjectDN().getName());
        info.append('\n');
        info.append(" - Valid: ");
        info.append("from " + cert.getNotBefore() + " until " + cert.getNotAfter());
        info.append('\n');
        info.append(" - Issuer: ");
        info.append(cert.getIssuerDN().getName());
        info.append('\n');
        info.append(" - Fingerprint: ");
        info.append(getFingerprint(cert));
        return info.toString();
    }
    
    private static String getUserName(String userName, SVNURL url) {
        if (userName == null || "".equals(userName.trim())) {
            userName = url != null ? url.getUserInfo() : null;
        }
        if (userName == null || "".equals(userName.trim())) {
            userName = System.getProperty("user.name");
        }
        return userName;
    }

}
