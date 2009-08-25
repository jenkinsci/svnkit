/*
 * ====================================================================
 * Copyright (c) 2004-2009 TMate Software Ltd.  All rights reserved.
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
import java.util.Iterator;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;

import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationManager;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationProvider;
import org.tmatesoft.svn.core.auth.SVNAuthentication;
import org.tmatesoft.svn.core.auth.SVNPasswordAuthentication;
import org.tmatesoft.svn.core.auth.SVNSSHAuthentication;
import org.tmatesoft.svn.core.auth.SVNSSLAuthentication;
import org.tmatesoft.svn.core.auth.SVNUserNameAuthentication;
import org.tmatesoft.svn.core.auth.SVNAuthAttempt;
import org.tmatesoft.svn.core.auth.PullIterator;
import org.tmatesoft.svn.core.auth.SVNAuthAttempt.FlattenIterator;
import org.tmatesoft.svn.core.internal.util.SVNSSLUtil;

import org.tigris.subversion.javahl.PromptUserPassword;
import org.tigris.subversion.javahl.PromptUserPassword2;
import org.tigris.subversion.javahl.PromptUserPassword3;

/**
 * @version 1.3
 * @author  TMate Software Ltd.
 */
class JavaHLAuthenticationProvider implements ISVNAuthenticationProvider {
    
    private static final String ADAPTER_DEFAULT_PROMPT_CLASS = 
        "org.tigris.subversion.svnclientadapter.javahl.AbstractJhlClientAdapter$DefaultPromptUserPassword";
    private PromptUserPassword myPrompt;
    
    public JavaHLAuthenticationProvider(PromptUserPassword prompt){
        myPrompt = prompt;
    }

    public Iterator<? extends SVNAuthAttempt> getAuthentications(final String kind, final String realm, final SVNURL url, final boolean authMayBeStored) {
        if (ISVNAuthenticationManager.SSH.equals(kind) && myPrompt instanceof PromptUserPasswordSSH) {
            final PromptUserPasswordSSH prompt4 = (PromptUserPasswordSSH) myPrompt;

            return SVNAuthAttempt.wrap(new PullIterator<SVNSSHAuthentication>() {
                SVNSSHAuthentication previousAuth;
                protected SVNSSHAuthentication fetch() {
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
                            return previousAuth=new SVNSSHAuthentication(userName, new File(keyPath), passphrase, port, save);
                        } else if (password != null){
                            return previousAuth=new SVNSSHAuthentication(userName, password, port, save);
                        }
                    }
                    return null;
                }
            });
        }
        if (ISVNAuthenticationManager.SSH.equals(kind)) {
            final List<Iterator<SVNAuthAttempt>> candidates = new ArrayList<Iterator<SVNAuthAttempt>>();

            // use configuration file here? but it was already used once...
            String keyPath = System.getProperty("svnkit.ssh2.key", System.getProperty("javasvn.ssh2.key"));
            String userName = getUserName(System.getProperty("svnkit.ssh2.username", System.getProperty("javasvn.ssh2.username")), url);
            String passPhrase = System.getProperty("svnkit.ssh2.passphrase", System.getProperty("javasvn.ssh2.passphrase"));
            if (userName!=null && keyPath != null) {
                // use port number from configuration file?
                candidates.add(Collections.singleton(new SVNAuthAttempt(
                    new SVNSSHAuthentication(userName, new File(keyPath), passPhrase, -1, true))).iterator());
            }

            candidates.add(SVNAuthAttempt.wrap(new PullIterator<SVNAuthentication>() {
                SVNSSHAuthentication previousAuth;

                protected SVNSSHAuthentication fetch() {
                    String userName = previousAuth != null && previousAuth.getUserName() != null ? previousAuth.getUserName() : getUserName(null, url);
                    if (myPrompt instanceof PromptUserPassword3) {
                        PromptUserPassword3 prompt3 = (PromptUserPassword3) myPrompt;
                        if (prompt3.prompt(realm, userName, authMayBeStored)) {
                            // use default port number from configuration file (should be in previous auth).
                            int portNumber = (previousAuth != null) ? previousAuth.getPortNumber() : -1;
                            return previousAuth = new SVNSSHAuthentication(prompt3.getUsername(), prompt3.getPassword(), portNumber, prompt3.userAllowedSave());
                        }
                    } else if (myPrompt.prompt(realm, userName)) {
                        return previousAuth = new SVNSSHAuthentication(userName, myPrompt.getPassword(), -1, true);
                    }
                    return null;
                }
            }));

            // return the union of both
            return new FlattenIterator<SVNAuthAttempt,Iterator<SVNAuthAttempt>>(candidates) {
                protected Iterator<SVNAuthAttempt> expand(Iterator<SVNAuthAttempt> item) {
                    return item;
                }
            };
        }

        if (ISVNAuthenticationManager.SSL.equals(kind) && myPrompt instanceof PromptUserPasswordSSL) {
            final PromptUserPasswordSSL prompt4 = (PromptUserPasswordSSL) myPrompt;

            return SVNAuthAttempt.wrap(new PullIterator<SVNSSLAuthentication>() {
                protected SVNSSLAuthentication fetch() {
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
            });
        }

        if(ISVNAuthenticationManager.USERNAME.equals(kind)) {
            return SVNAuthAttempt.wrap(new PullIterator<SVNAuthentication>() {
                SVNUserNameAuthentication previousAuth;
                protected SVNAuthentication fetch() {
                    String userName = previousAuth != null && previousAuth.getUserName() != null ? previousAuth.getUserName() : getUserName(null, url);
                    if (myPrompt instanceof PromptUserPasswordUser) {
                        PromptUserPasswordUser prompt3 = (PromptUserPasswordUser) myPrompt;
                        if (prompt3.promptUser(realm, userName, authMayBeStored))  {
                            return previousAuth=new SVNUserNameAuthentication(prompt3.getUsername(), prompt3.userAllowedSave());
                        }
                        return getDefaultUserNameCredentials(userName);
                    } else if (myPrompt instanceof PromptUserPassword3) {
                        PromptUserPassword3 prompt3 = (PromptUserPassword3) myPrompt;
                        if (prompt3.prompt(realm, userName, authMayBeStored))  {
                            return previousAuth=new SVNUserNameAuthentication(prompt3.getUsername(), prompt3.userAllowedSave());
                        }
                        return getDefaultUserNameCredentials(userName);
                    }
                    if (myPrompt.prompt(realm, userName)) {
                        return previousAuth=new SVNUserNameAuthentication(myPrompt.getUsername(), false);
                    }
                    return previousAuth=getDefaultUserNameCredentials(userName);
                }
            });
        }

        if(ISVNAuthenticationManager.PASSWORD.equals(kind)) {
            return SVNAuthAttempt.wrap(new PullIterator<SVNAuthentication>() {
                SVNPasswordAuthentication previousAuth;
                protected SVNPasswordAuthentication fetch() {
                    String userName = previousAuth != null && previousAuth.getUserName() != null ? previousAuth.getUserName() : getUserName(null, url);
                    if (myPrompt instanceof PromptUserPassword3) {
                        PromptUserPassword3 prompt3 = (PromptUserPassword3) myPrompt;
                        if(prompt3.prompt(realm, userName, authMayBeStored)){
                            return previousAuth=new SVNPasswordAuthentication(prompt3.getUsername(), prompt3.getPassword(), prompt3.userAllowedSave());
                        }
                    }else if(myPrompt.prompt(realm, userName)) {
                        return previousAuth=new SVNPasswordAuthentication(myPrompt.getUsername(), myPrompt.getPassword(), true);
                    }
                    return null;
                }
            });
        }

        return Collections.<SVNAuthAttempt>emptyList().iterator();
    }

    private SVNUserNameAuthentication getDefaultUserNameCredentials(String userName) {
        if (ADAPTER_DEFAULT_PROMPT_CLASS.equals(myPrompt.getClass().getName())) {
            // return default username, despite prompt was 'cancelled'.
            return new SVNUserNameAuthentication(userName, false);
        }
        return null;
    }

    public int acceptServerAuthentication(SVNURL url, String realm, Object serverAuth,  boolean resultMayBeStored) {
        if (serverAuth != null && myPrompt instanceof PromptUserPassword2) {
            PromptUserPassword2 sslPrompt = (PromptUserPassword2) myPrompt;
            serverAuth = serverAuth instanceof X509Certificate ? 
                    SVNSSLUtil.getServerCertificatePrompt((X509Certificate) serverAuth, realm, url.getHost()) : serverAuth;
            if (serverAuth == null) {
                serverAuth = "Unsupported certificate type '" + (serverAuth != null ? serverAuth.getClass().getName() : "null") + "'";
            }
            return sslPrompt.askTrustSSLServer(serverAuth.toString(), resultMayBeStored);
        }
        return ACCEPTED;
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
