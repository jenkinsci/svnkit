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
import java.security.MessageDigest;
import java.security.cert.X509Certificate;
import java.util.Date;

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
        int failures = getServerCertificateFailures(cert, hostName);
        String prompt = "Error validating server certificate for '" + realm + "':\n";
        if ((failures & 8) != 0) {
            prompt += " - The certificate is not issued by a trusted authority. Use the\n" +
                      "   fingerprint to validate the certificate manually!\n";
        }
        if ((failures & 4) != 0) {
            prompt += " - The certificate hostname does not match.\n";
        }
        if ((failures & 2) != 0) {
            prompt += " - The certificate has expired.\n";
        }
        if ((failures & 1) != 0) {
            prompt += " - The certificate is not yet valid.\n";
        }
        prompt += getServerCertificateInfo(cert);
        if (resultMayBeStored) {
            prompt += "(R)eject, accept (t)emporarily or accept (p)ermanently? "; 
        } else {
            prompt += "(R)eject or accept (t)emporarily? "; 
        }
        System.out.print(prompt);
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
            String name = null;
            while(name == null) {
                name = prompt(!"file".equals(url.getProtocol()) ? 
                    "Author name [" + System.getProperty("user.name") + "]:" : 
                    "Username [" + System.getProperty("user.name") + "]:");
                if ("".equals(name) || name == null) {
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
      info.append("Certificate information:");
      info.append('\n');
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
      info.append('\n');
      return info.toString();
  }

  private static int getServerCertificateFailures(X509Certificate cert, String realHostName) {
      int mask = 8;
      Date time = new Date(System.currentTimeMillis());
      if (time.before(cert.getNotBefore())) {
          mask |= 1;
      }
      if (time.after(cert.getNotAfter())) {
          mask |= 2;
      }
      String hostName = cert.getSubjectDN().getName();
      int index = hostName.indexOf("CN=") + 3;
      if (index >= 0) {
          hostName = hostName.substring(index);
          if (hostName.indexOf(' ') >= 0) {
              hostName = hostName.substring(0, hostName.indexOf(' '));
          }
          if (hostName.indexOf(',') >= 0) {
              hostName = hostName.substring(0, hostName.indexOf(','));
          }
      }
      if (realHostName != null && !realHostName.equals(hostName)) {
          mask |= 4;
      }
      return mask;
  }


}
