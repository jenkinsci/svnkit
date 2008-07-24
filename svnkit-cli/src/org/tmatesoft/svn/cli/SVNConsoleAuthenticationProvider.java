/*
 * ====================================================================
 * Copyright (c) 2004-2007 TMate Software Ltd.  All rights reserved.
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
import java.security.cert.X509Certificate;

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
        System.out.print(prompt.toString());
        System.out.flush();
        int r = -1;
        while(true) {
            try {
                r = System.in.read();
                if (r < 0) {
                    return ISVNAuthenticationProvider.REJECTED;
                }
                char ch = (char) (r & 0xFF);
                if (ch == 'R' || ch == 'r') {
                    return ISVNAuthenticationProvider.REJECTED;
                } else if (ch == 't' || ch == 'T') {
                    return ISVNAuthenticationProvider.ACCEPTED_TEMPORARY;
                } else if (resultMayBeStored && (ch == 'p' || ch == 'P')) {
                    return ISVNAuthenticationProvider.ACCEPTED;
                }
            } catch (IOException e) {
                return ISVNAuthenticationProvider.REJECTED;
            }
        }
    }

    public SVNAuthentication requestClientAuthentication(String kind, SVNURL url, String realm, SVNErrorMessage errorMessage, SVNAuthentication previousAuth, boolean authMayBeStored) {
        if (ISVNAuthenticationManager.PASSWORD.equals(kind)) {
            String name = null;
            printRealm(realm);
            while(name == null) {
                name = prompt("Username");
                if ("".equals(name)) {
                    name = null;
                }
            }
            String password = prompt("Password for '" + name + "'");
            if (password == null) {
                password = "";
            }
            return new SVNPasswordAuthentication(name, password, authMayBeStored);
        } else if (ISVNAuthenticationManager.SSH.equals(kind)) {
            String name = null;
            printRealm(realm);
            while(name == null) {
                name = prompt("Username");
                if ("".equals(name)) {
                    name = null;
                }
            }
            String password = prompt("Password for '" + url.getHost() + "' (leave blank if you are going to use private key)");
            if ("".equals(password)) {
                password = null;
            }
            String keyFile = null;
            String passphrase = null;
            if (password == null) {
                while(keyFile == null) {
                    keyFile = prompt("Private key for '" + url.getHost() + "' (OpenSSH format)");
                    if ("".equals(keyFile)) {
                        name = null;
                    }
                    File file = new File(keyFile);
                    if (!file.isFile() && !file.canRead()) {
                        continue;
                    }
                    passphrase = prompt("Private key passphrase [none]");
                    if ("".equals(passphrase)) {
                        passphrase = null;
                    }
                }
            }
            int port = 22;
            String portValue = prompt("Port number for '" + url.getHost() + "' [22]");
            if (portValue != null && !"".equals(portValue)) {
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
            printRealm(realm);
            String name = System.getProperty("user.name");
            if (name != null && "".equals(name.trim())) {
                name = null;
            }
            if (name != null) {
                return new SVNUserNameAuthentication(name, authMayBeStored);
            }
            printRealm(realm);
            while(name == null) {
                name = prompt(!"file".equals(url.getProtocol()) ? 
                    "Author name [" + System.getProperty("user.name") + "]" : 
                    "Username [" + System.getProperty("user.name") + "]");
                if (name == null || "".equals(name.trim())) {
                    name = System.getProperty("user.name");
                }
            }
            return new SVNUserNameAuthentication(name, authMayBeStored);
        } else if (ISVNAuthenticationManager.SSL.equals(kind)) {
            printRealm(realm);
            String path = null;
            while(path == null) {
                path = prompt("Client certificate filename");
                if ("".equals(path)) {
                    path = null;
                }
            }
            String password = prompt("Passphrase for '" + realm + "'");
            if (password == null) {
                password = "";
            }
            return new SVNSSLAuthentication(new File(path), password, authMayBeStored);
        }
        return null;
    }

    private static void printRealm(String realm) {
        if (realm != null) {
            System.out.println("Authentication realm: " + realm);
            System.out.flush();
        }
    }
    
    private static String prompt(String label) {
        System.out.print(label + ": ");
        System.out.flush();
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
        try {
            return reader.readLine();
        } catch (IOException e) {
            return null;
        }
    }
}
