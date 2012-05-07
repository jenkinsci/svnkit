/*
 * ====================================================================
 * Copyright (c) 2004-2012 TMate Software Ltd.  All rights reserved.
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
import java.security.cert.X509Certificate;
import java.text.MessageFormat;

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
import org.tmatesoft.svn.core.internal.util.SVNSSLUtil;
import org.tmatesoft.svn.core.internal.wc.ISVNSSLPasspharsePromptSupport;

/**
 * @version 1.3
 * @author  TMate Software Ltd.
 */
class JavaHLAuthenticationProvider implements ISVNAuthenticationProvider, ISVNSSLPasspharsePromptSupport {

    private static final String ADAPTER_DEFAULT_PROMPT_CLASS =
        "org.tigris.subversion.svnclientadapter.javahl.AbstractJhlClientAdapter$DefaultPromptUserPassword";
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
                    return new SVNSSHAuthentication(userName, new File(keyPath), passphrase, port, save, url, false);
                } else if (password != null){
                    return new SVNSSHAuthentication(userName, password, port, save, url, false);
                }
            }
            return null;
        } else if (ISVNAuthenticationManager.SSL.equals(kind) && SVNSSLAuthentication.isCertificatePath(realm) && myPrompt instanceof PromptUserPassword3) {
            String passphrase = ((PromptUserPassword3) myPrompt).askQuestion(realm, "SSL Certificate Passphrase", authMayBeStored);
            if (passphrase != null) {
                return new SVNPasswordAuthentication("", passphrase, ((PromptUserPassword3) myPrompt).userAllowedSave(), url, false);
            }
        } else if (ISVNAuthenticationManager.SSL.equals(kind) && !SVNSSLAuthentication.isCertificatePath(realm) && myPrompt instanceof PromptUserPasswordSSL) {
            PromptUserPasswordSSL prompt4 = (PromptUserPasswordSSL) myPrompt;
            if (prompt4.promptSSL(realm, authMayBeStored)) {
                String cert = prompt4.getSSLClientCertPath();
                String password = prompt4.getSSLClientCertPassword();
                if (cert != null) {
                    if ("".equals(password)) {
                        password = null;
                    }
                    boolean save = prompt4.userAllowedSave();
                    if (cert.startsWith(SVNSSLAuthentication.MSCAPI)) {
                        String alias = null;
                        if (cert.lastIndexOf(';') > 0) {
                            alias = cert.substring(cert.lastIndexOf(';') + 1);
                        }
                        return new SVNSSLAuthentication(SVNSSLAuthentication.MSCAPI, alias, save, url, false);
                    }
                    SVNSSLAuthentication sslAuth = new SVNSSLAuthentication(new File(cert), password, save, url, false);
                    sslAuth.setCertificatePath(cert);
                    return sslAuth;
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
                return new SVNSSHAuthentication(userName, new File(keyPath), passPhrase, -1, true, url, false);
            }
            // try to get password for ssh from the user.
        } else if(ISVNAuthenticationManager.USERNAME.equals(kind)) {
            String userName = previousAuth != null && previousAuth.getUserName() != null ? previousAuth.getUserName() : getUserName(null, url);
            if (myPrompt instanceof PromptUserPasswordUser) {
                PromptUserPasswordUser prompt3 = (PromptUserPasswordUser) myPrompt;
                if (prompt3.promptUser(realm, userName, authMayBeStored))  {
                    return new SVNUserNameAuthentication(prompt3.getUsername(), prompt3.userAllowedSave(), url, false);
                }
                return getDefaultUserNameCredentials(userName);
            } else if (myPrompt instanceof PromptUserPassword3) {
                PromptUserPassword3 prompt3 = (PromptUserPassword3) myPrompt;
                if (prompt3.prompt(realm, userName, authMayBeStored))  {
                    return new SVNUserNameAuthentication(prompt3.getUsername(), prompt3.userAllowedSave(), url, false);
                }
                return getDefaultUserNameCredentials(userName);
            }
            if (myPrompt.prompt(realm, userName)) {
                return new SVNUserNameAuthentication(myPrompt.getUsername(), false, url, false);
            }
            return getDefaultUserNameCredentials(userName);
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
                    return new SVNSSHAuthentication(prompt3.getUsername(), prompt3.getPassword(), portNumber, prompt3.userAllowedSave(), url, false);
                }
                return new SVNPasswordAuthentication(prompt3.getUsername(), prompt3.getPassword(), prompt3.userAllowedSave(), url, false);
            }
        }else{
            if(myPrompt.prompt(realm, userName)){
                if (ISVNAuthenticationManager.SSH.equals(kind)) {
                    return new SVNSSHAuthentication(userName, myPrompt.getPassword(), -1, true, url, false);
                }
                return new SVNPasswordAuthentication(myPrompt.getUsername(), myPrompt.getPassword(), true, url, false);
            }
        }
        return null;
    }

    private SVNAuthentication getDefaultUserNameCredentials(String userName) {
        if (ADAPTER_DEFAULT_PROMPT_CLASS.equals(myPrompt.getClass().getName())) {
            // return default username, despite prompt was 'cancelled'.
            return new SVNUserNameAuthentication(userName, false, null, false);
        }
        return null;
    }

    public int acceptServerAuthentication(SVNURL url, String realm, Object serverAuth,  boolean resultMayBeStored) {
        if (myPrompt instanceof PromptUserPassword2 && serverAuth instanceof X509Certificate) {
            PromptUserPassword2 sslPrompt = (PromptUserPassword2) myPrompt;
            serverAuth = serverAuth instanceof X509Certificate ?
                    SVNSSLUtil.getServerCertificatePrompt((X509Certificate) serverAuth, realm, url.getHost()) : serverAuth;
            if (serverAuth == null) {
                serverAuth = "Unsupported certificate type '" + (serverAuth != null ? serverAuth.getClass().getName() : "null") + "'";
            }
            return sslPrompt.askTrustSSLServer(serverAuth.toString(), resultMayBeStored);
        } else if (myPrompt != null && serverAuth instanceof byte[]) {
            String prompt = "The ''{0}'' server''s key fingerprint is:\n{1}\n" +
            		"If you trust this host, select ''Yes'' to add the key to the SVN cache and carry on connecting.\n" +
            		"If you do not trust this host, select ''No'' to abandon the connection.";            
            prompt = MessageFormat.format(prompt, new Object[] {url.getHost(), SVNSSLUtil.getFingerprint((byte[]) serverAuth, "MD5")});
            if (!myPrompt.askYesNo(realm, prompt, false)) {
                return REJECTED;
            }                
        }
        return ACCEPTED;
    }

    public boolean isSSLPassphrasePromtSupported() {
        return true;
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
