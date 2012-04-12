/*
 * ====================================================================
 * Copyright (c) 2004-2012 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html.
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.cli;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.security.cert.X509Certificate;
import java.text.MessageFormat;
import java.util.HashMap;
import java.util.Map;

import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationManager;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationProvider;
import org.tmatesoft.svn.core.auth.SVNAuthentication;
import org.tmatesoft.svn.core.auth.SVNPasswordAuthentication;
import org.tmatesoft.svn.core.auth.SVNSSHAuthentication;
import org.tmatesoft.svn.core.auth.SVNSSLAuthentication;
import org.tmatesoft.svn.core.auth.SVNUserNameAuthentication;
import org.tmatesoft.svn.core.internal.util.SVNSSLUtil;
import org.tmatesoft.svn.core.internal.wc.ISVNAuthStoreHandler;
import org.tmatesoft.svn.core.internal.wc.ISVNGnomeKeyringPasswordProvider;
import org.tmatesoft.svn.core.internal.wc.ISVNSSLPasspharsePromptSupport;
import org.tmatesoft.svn.core.wc.SVNWCUtil;


/**
 * @version 1.0
 * @author  TMate Software Ltd.
 */
public class SVNConsoleAuthenticationProvider implements ISVNAuthenticationProvider, ISVNSSLPasspharsePromptSupport, ISVNAuthStoreHandler, ISVNGnomeKeyringPasswordProvider {
    
    private static final String OUR_PASSPHRASE_PROMPT_TEXT = "-----------------------------------------------------------------------\n" +
                                                             "ATTENTION!  Your passphrase for client certificate:\n" +
                                                             "\n" +
                                                             "   {0}\n" +
                                                             "\n" +
                                                             "can only be stored to disk unencrypted!  You are advised to configure\n" + 
                                                             "your system so that Subversion can store passphrase encrypted, if\n" +
                                                             "possible.  See the documentation for details.\n" + 
                                                             "\n" + 
                                                             "You can avoid future appearances of this warning by setting the value\n" +
                                                             "of the ''store-ssl-client-cert-pp-plaintext'' option to either ''yes'' or\n" +
                                                             "''no'' in ''{1}''.\n" +
                                                             "-----------------------------------------------------------------------\n";        
    private static final String OUR_PASSWORD_PROMPT_TEXT =   "-----------------------------------------------------------------------\n" +
                                                             "ATTENTION!  Your password for authentication realm:\n" +
                                                             "\n" +
                                                             "   {0}\n" + 
                                                             "\n" + 
                                                             "can only be stored to disk unencrypted!  You are advised to configure\n" + 
                                                             "your system so that Subversion can store passwords encrypted, if\n" + 
                                                             "possible.  See the documentation for details.\n" +
                                                             "\n" + 
                                                             "You can avoid future appearances of this warning by setting the value\n" +
                                                             "of the ''store-plaintext-passwords'' option to either ''yes'' or ''no'' in\n" +
                                                             "''{1}''.\n" +
                                                             "-----------------------------------------------------------------------\n";

    private static final String OUR_HOST_KEY_PROMPT_TEXT =   "The ''{0}'' server''s key fingerprint is:\n{1}\n";
    
    private static final String OUR_PASSWORD_PROMPT_STRING = "Store password unencrypted (yes/no)? ";
    private static final String OUR_PASSPHRASE_PROMPT_STRING = "Store passphrase unencrypted (yes/no)? ";
    private static final int MAX_PROMPT_COUNT = 3;
    private Map myRequestsCount = new HashMap();
    private boolean myIsTrustServerCertificate;
    
    public SVNConsoleAuthenticationProvider(boolean trustServerCertificate) {
        myIsTrustServerCertificate = trustServerCertificate;
    }
    
    public int acceptServerAuthentication(SVNURL url, String realm, Object certificate, boolean resultMayBeStored) {
        
        if (certificate instanceof byte[]) {
            StringBuffer prompt = new StringBuffer(OUR_HOST_KEY_PROMPT_TEXT);
            if (resultMayBeStored) {
                prompt.append("If you trust this host, enter ''p'' to add the key to the SVN cache and carry on connecting.\n");
                prompt.append("If you want to carry on connecting just once, without adding the key to the cache, type ''t''.");
                prompt.append("If you do not trust this host, type ''R'' to abandon the connection.");
                prompt.append("\n(R)eject, accept (t)emporarily or accept (p)ermanently? "); 
            } else {
                prompt.append("If you want to carry on connecting just once, without adding the key to the cache, type ''t''.");
                prompt.append("If you do not trust this host, type ''R'' to abandon the connection.");
                prompt.append("\n(R)eject or accept (t)emporarily? "); 
            }
            System.err.print(MessageFormat.format(prompt.toString(), new Object[] {url.getHost(), SVNSSLUtil.getFingerprint((byte[]) certificate, "MD5")}));
            System.err.flush();
            while(true) {
                String line = readLine();
                if (line == null) {
                    return ISVNAuthenticationProvider.REJECTED;
                }
                if (line.length() < 1) {
                    continue;
                }
                char ch = line.charAt(0);
                if (ch == 'R' || ch == 'r') {
                    return ISVNAuthenticationProvider.REJECTED;
                } else if (ch == 't' || ch == 'T') {
                    return ISVNAuthenticationProvider.ACCEPTED_TEMPORARY;
                } else if (resultMayBeStored && (ch == 'p' || ch == 'P')) {
                    return ISVNAuthenticationProvider.ACCEPTED;
                }
            }
        }
        
        if (myIsTrustServerCertificate) {
            return ISVNAuthenticationProvider.ACCEPTED_TEMPORARY;
        }
        
        if (!(certificate instanceof X509Certificate)) {
            return ISVNAuthenticationProvider.ACCEPTED_TEMPORARY;
        }
        String hostName = url.getHost();
        X509Certificate cert = (X509Certificate) certificate;
        StringBuffer prompt = SVNSSLUtil.getServerCertificatePrompt(cert, realm, hostName);
        if (resultMayBeStored) {
            prompt.append("\n(R)eject, accept (t)emporarily or accept (p)ermanently? "); 
        } else {
            prompt.append("\n(R)eject or accept (t)emporarily? "); 
        }
        System.err.print(prompt.toString());
        System.err.flush();
        while(true) {
            String line = readLine();
            if (line == null) {
                return ISVNAuthenticationProvider.REJECTED;
            }
            if (line.length() < 1) {
                continue;
            }
            char ch = line.charAt(0);
            if (ch == 'R' || ch == 'r') {
                return ISVNAuthenticationProvider.REJECTED;
            } else if (ch == 't' || ch == 'T') {
                return ISVNAuthenticationProvider.ACCEPTED_TEMPORARY;
            } else if (resultMayBeStored && (ch == 'p' || ch == 'P')) {
                return ISVNAuthenticationProvider.ACCEPTED;
            }
        }
    }

    public SVNAuthentication requestClientAuthentication(String kind, SVNURL url, String realm, SVNErrorMessage errorMessage, SVNAuthentication previousAuth, boolean authMayBeStored) {
        Integer requestsCount = (Integer) myRequestsCount.get(kind + "$" + url + "$" + realm);
        if (requestsCount == null) {
            myRequestsCount.put(kind + "$" + url + "$" + realm, new Integer(1));
        } else if (requestsCount.intValue() == MAX_PROMPT_COUNT) {
            // no more than three requests per realm
            return null;
        } else {
            myRequestsCount.put(kind + "$" + url + "$" + realm, new Integer(requestsCount.intValue() + 1));
        }
        
        if (ISVNAuthenticationManager.PASSWORD.equals(kind)) {
            String name = null;
            String defaultUserName = null;
            if (previousAuth != null) {
                if (previousAuth.isPartial()) {
                    name = previousAuth.getUserName();
                } else if (previousAuth.getUserName() != null) {
                    defaultUserName = previousAuth.getUserName();
                }
            }

            printRealm(realm);

            if (name == null) {
                String promptString = defaultUserName == null  ? "Username" : "Username [" + defaultUserName + "]";
                name = prompt(promptString);
                if ("".equals(name) && defaultUserName != null) {
                    name = defaultUserName;
                }
            }
            if (name == null) {
                return null;
            }
            String password = promptPassword("Password for '" + name + "'");
            if (password == null) {
                return null;
            }
            return new SVNPasswordAuthentication(name, password, authMayBeStored, url, false);
        } else if (ISVNAuthenticationManager.SSH.equals(kind)) {
            String name = null;
            String defaultUserName = null;
            String defaultPassword = null;
            String defaultPassphrase = null;
            File defaultPrivateKeyFile = null;
            int defaultPort = -1;

            if (url.getUserInfo() != null && !"".equals(url.getUserInfo())) {
                defaultUserName = url.getUserInfo();
            }
            
            if (previousAuth != null && previousAuth instanceof SVNSSHAuthentication) {
                SVNSSHAuthentication sshPreviousAuth = (SVNSSHAuthentication) previousAuth;
                defaultUserName = defaultUserName == null ? sshPreviousAuth.getUserName() : defaultUserName;

                defaultPassword = sshPreviousAuth.getPassword();
                defaultPassphrase = sshPreviousAuth.getPassphrase();
                defaultPrivateKeyFile = sshPreviousAuth.getPrivateKeyFile();
                defaultPort = sshPreviousAuth.getPortNumber();
            }
            
            printRealm(realm);

            name = prompt(defaultUserName == null ? "Username" : "Username [" + defaultUserName + "]");
            if ("".equals(name) && defaultUserName != null) {
                name = defaultUserName;
            }
            
            if (name == null) {
                return null;
            }

            String passwordPrompt = null;
            if (defaultPassword != null) {
                passwordPrompt = "Password for '" + url.getHost() + "' (leave blank if you are going to use private key) [";
                for (int i = 0; i < defaultPassword.length(); i++) {
                    passwordPrompt += "*";
                }
                passwordPrompt += "]";
            } else {
                passwordPrompt = "Password for '" + url.getHost() + "' (leave blank if you are going to use private key)";
            }
            
            String password = promptPassword(passwordPrompt);
            if (password == null) {
                return null;
            } else if ("".equals(password)) {
                if (defaultPassword != null) {
                    password = defaultPassword;
                } else {
                    password = null;
                }
            }

            String keyFilePath = null;
            File keyFile = null;
            String passphrase = null;
            if (password == null) {
                while(keyFilePath == null) {
                    String privateKeyFilePrompt = null;
                    if (defaultPrivateKeyFile != null) {
                        privateKeyFilePrompt = "Private key for '" + url.getHost() + "' (OpenSSH format) [" + defaultPrivateKeyFile.getAbsolutePath() + "]";
                    } else {
                        privateKeyFilePrompt = "Private key for '" + url.getHost() + "' (OpenSSH format)"; 
                    }

                    keyFilePath = prompt(privateKeyFilePrompt);
                    if ("".equals(keyFilePath)) {
                        if (defaultPrivateKeyFile != null) {
                            if (!defaultPrivateKeyFile.isFile() || !defaultPrivateKeyFile.canRead()) {
                                defaultPrivateKeyFile = null;
                                keyFilePath = null;
                                keyFile = null;
                                continue;
                            }
                            keyFile = defaultPrivateKeyFile;
                            keyFilePath = keyFile.getAbsolutePath();
                        }
                        continue;
                    }

                    if (keyFilePath == null) {
                        return null;
                    }
                    
                    keyFile = new File(keyFilePath);
                    if (!keyFile.isFile() || !keyFile.canRead()) {
                        keyFilePath = null;
                        keyFile = null;
                        continue;
                    }
                }
                
                String passphrasePrompt = null;
                if (defaultPassphrase != null) {
                    passphrasePrompt = "Private key passphrase [";
                    for (int i = 0; i < defaultPassphrase.length(); i++) {
                        passphrasePrompt += "*";
                    }
                    passphrasePrompt += "]";
                } else {
                    passphrasePrompt = "Private key passphrase [none]";
                }
                
                passphrase = promptPassword(passphrasePrompt);
                if ("".equals(passphrase)) {
                    if (defaultPassphrase != null) {
                        passphrase = defaultPassphrase;
                    } else {
                        passphrase = null;
                    }
                } else if (passphrase == null) {
                    return null;
                }
            }

            int port = defaultPort > 0 ? defaultPort : 22;
            String portValue = prompt("Port number for '" + url.getHost() + "' [" + port + "]");
            if (portValue == null) {
                return null;
            }
            if (!"".equals(portValue)) {
                try {
                    port = Integer.parseInt(portValue);
                } catch (NumberFormatException e) {}
            }
            if (password != null) {
                return new SVNSSHAuthentication(name, password, port, authMayBeStored, url, false);
            } else if (keyFile != null) {
                return new SVNSSHAuthentication(name, keyFile, passphrase, port, authMayBeStored, url, false);
            }
        } else if (ISVNAuthenticationManager.USERNAME.equals(kind)) {
            String name = System.getProperty("user.name");
            if (name != null && "".equals(name.trim())) {
                name = null;
            }
            if (name != null) {
                return new SVNUserNameAuthentication(name, authMayBeStored, url, false);
            }
            printRealm(realm);
            name = prompt(!"file".equals(url.getProtocol()) ? 
                "Author name [" + System.getProperty("user.name") + "]" : 
                "Username [" + System.getProperty("user.name") + "]");
            if (name == null) {
                return null;
            }
            if ("".equals(name.trim())) {
                name = System.getProperty("user.name");
            }            
            return new SVNUserNameAuthentication(name, authMayBeStored, url, false);
        } else if (ISVNAuthenticationManager.SSL.equals(kind)) {
            if (SVNSSLAuthentication.isCertificatePath(realm)) {
                String passphrase = promptPassword("Passphrase for '" + realm + "'");
                if (passphrase == null) {
                    return null;
                }
                return new SVNPasswordAuthentication("", passphrase, authMayBeStored, url, false);
            }
            boolean isMSCAPI = false;
            printRealm(realm);
            String path = null;
            while(path == null) {
                path = prompt("Client certificate filename or 'MSCAPI'");
                if ("".equals(path)) {
                    continue;
                }
                if (path == null) {
                    return null;
                }
                if (path.startsWith(SVNSSLAuthentication.MSCAPI)) {
                    isMSCAPI = true;
                } else {
                    File file = new File(path);
                    if (!file.isFile() || !file.canRead()) {
                        path = null;
                        continue;
                    }
                }
            }
            if (isMSCAPI) {
                String alias = promptPassword("MSCAPI certificate alias");
                if (alias == null) {
                    return null;
                } else if ("".equals(alias)) {
                    alias = null;
                }
                return new SVNSSLAuthentication(SVNSSLAuthentication.MSCAPI, alias, authMayBeStored, url, false);
            }
            SVNSSLAuthentication sslAuth = new SVNSSLAuthentication(new File(path), null, authMayBeStored, url, false);
            sslAuth.setCertificatePath(path);
            return sslAuth;
        }
        return null;
    }

    public boolean canStorePlainTextPasswords(String realm, SVNAuthentication auth) throws SVNException {
        return isPlainTextAllowed(realm, OUR_PASSWORD_PROMPT_TEXT, OUR_PASSWORD_PROMPT_STRING);
    }

    public boolean canStorePlainTextPassphrases(String realm, SVNAuthentication auth) throws SVNException {
        return isPlainTextAllowed(realm, OUR_PASSPHRASE_PROMPT_TEXT, OUR_PASSPHRASE_PROMPT_STRING);
    }

    private boolean isPlainTextAllowed(String realm, String promptText, String promptString) {
        File configPath = new File(SVNWCUtil.getDefaultConfigurationDirectory(), "servers");
        String formattedMessage = MessageFormat.format(promptText, new Object[] { realm, configPath.getAbsolutePath() });

        System.err.print(formattedMessage);
        while (true) {
            System.err.print(promptString);
            System.err.flush();
            String answer = readLine();
            
            if ("yes".equalsIgnoreCase(answer)) {
                return true; 
            } else if ("no".equalsIgnoreCase(answer)) {
                return false;
            } 
            promptString = "Please type 'yes' or 'no': ";
        }
    }
    
    private static void printRealm(String realm) {
        if (realm != null) {
            System.err.println("Authentication realm: " + realm);
            System.err.flush();
        }
    }
    
    private static String prompt(String label) {
        System.err.print(label + ": ");
        System.err.flush();
        return readLine();
    }
    
    private static String promptPassword(String label) {
        System.err.print(label + ": ");
        System.err.flush();
        Class systemClass = System.class;
        try {
            // try to use System.console().readPassword - since JDK 1.6
            Method consoleMethod = systemClass.getMethod("console", new Class[0]);
            if (consoleMethod != null) {
                Object consoleObject = consoleMethod.invoke(null, new Object[0]);
                if (consoleObject != null) {
                    Class consoleClass = consoleObject.getClass();
                    Method readPasswordMethod = consoleClass.getMethod("readPassword", new Class[0]);
                    if (readPasswordMethod != null) {
                        Object password = readPasswordMethod.invoke(consoleObject, new Object[0]);
                        if (password == null) {
                            return null;
                        } else if (password instanceof char[]) {
                            return new String((char[]) password);
                        }
                    }
                }
            }
        } catch (SecurityException e) {
        } catch (NoSuchMethodException e) {
        } catch (IllegalArgumentException e) {
        } catch (IllegalAccessException e) {
        } catch (InvocationTargetException e) {
        }        
        return readLine();
    }

    private static String readLine() {
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
        try {
            return reader.readLine();
        } catch (IOException e) {
            return null;
        }
    }

    public boolean isSSLPassphrasePromtSupported() {
        return true;
    }

    public String getKeyringPassword(String keyringName) throws SVNException {
        return promptPassword("Password for '" + keyringName + "' GNOME keyring");
    }
}
