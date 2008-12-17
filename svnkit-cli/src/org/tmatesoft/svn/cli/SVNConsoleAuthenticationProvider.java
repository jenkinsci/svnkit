/*
 * ====================================================================
 * Copyright (c) 2004-2008 TMate Software Ltd.  All rights reserved.
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
import java.util.HashMap;
import java.util.Map;

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


/**
 * @version 1.0
 * @author  TMate Software Ltd.
 */
public class SVNConsoleAuthenticationProvider implements ISVNAuthenticationProvider {
    
    private static final int MAX_PROMPT_COUNT = 3;
    private Map myRequestsCount = new HashMap();
    

    public int acceptServerAuthentication(SVNURL url, String realm, Object certificate, boolean resultMayBeStored) {
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
            printRealm(realm);
            name = prompt("Username");
            if (name == null) {
                return null;
            }
            String password = promptPassword("Password for '" + name + "'");
            if (password == null) {
                return null;
            }
            return new SVNPasswordAuthentication(name, password, authMayBeStored);
        } else if (ISVNAuthenticationManager.SSH.equals(kind)) {
            String name = null;
            printRealm(realm);
            name = prompt("Username");
            if (name == null) {
                return null;
            }
            String password = promptPassword("Password for '" + url.getHost() + "' (leave blank if you are going to use private key)");
            if (password == null) {
                return null;
            } else if ("".equals(password)) {
                password = null;
            }
            String keyFile = null;
            String passphrase = null;
            if (password == null) {
                while(keyFile == null) {
                    keyFile = prompt("Private key for '" + url.getHost() + "' (OpenSSH format)");
                    if ("".equals(keyFile)) {
                        continue;
                    }
                    if (keyFile == null) {
                        return null;
                    }
                    File file = new File(keyFile);
                    if (!file.isFile() || !file.canRead()) {
                        keyFile = null;
                        continue;
                    }
                }
                passphrase = promptPassword("Private key passphrase [none]");
                if ("".equals(passphrase)) {
                    passphrase = null;
                } else if (passphrase == null) {
                    return null;
                }
            }
            int port = 22;
            String portValue = prompt("Port number for '" + url.getHost() + "' [22]");
            if (portValue == null) {
                return null;
            }
            if (!"".equals(portValue)) {
                try {
                    port = Integer.parseInt(portValue);
                } catch (NumberFormatException e) {}
            }
            if (password != null) {
                return new SVNSSHAuthentication(name, password, port, authMayBeStored);
            } else if (keyFile != null) {
                return new SVNSSHAuthentication(name, new File(keyFile), passphrase, port, authMayBeStored);
            }
        } else if (ISVNAuthenticationManager.USERNAME.equals(kind)) {
            String name = System.getProperty("user.name");
            if (name != null && "".equals(name.trim())) {
                name = null;
            }
            if (name != null) {
                return new SVNUserNameAuthentication(name, authMayBeStored);
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
            return new SVNUserNameAuthentication(name, authMayBeStored);
        } else if (ISVNAuthenticationManager.SSL.equals(kind)) {
            printRealm(realm);
            String path = null;
            while(path == null) {
                path = prompt("Client certificate filename");
                if ("".equals(path)) {
                    continue;
                }
                if (path == null) {
                    return null;
                }
                File file = new File(path);
                if (!file.isFile() || !file.canRead()) {
                    path = null;
                    continue;
                }
            }
            String password = promptPassword("Passphrase for '" + realm + "'");
            if (password == null) {
                return null;
            } else if ("".equals(password)) {
                password = null;
            }
            return new SVNSSLAuthentication(new File(path), password, authMayBeStored);
        }
        return null;
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
}
