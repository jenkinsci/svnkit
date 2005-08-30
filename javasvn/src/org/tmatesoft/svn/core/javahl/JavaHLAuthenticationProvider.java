/*
 * Created on 25.06.2005
 */
package org.tmatesoft.svn.core.javahl;

import java.io.File;
import java.security.MessageDigest;
import java.security.cert.X509Certificate;

import org.tigris.subversion.javahl.PromptUserPassword;
import org.tigris.subversion.javahl.PromptUserPassword2;
import org.tigris.subversion.javahl.PromptUserPassword3;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationManager;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationProvider;
import org.tmatesoft.svn.core.auth.SVNAuthentication;
import org.tmatesoft.svn.core.auth.SVNPasswordAuthentication;
import org.tmatesoft.svn.core.auth.SVNSSHAuthentication;

class JavaHLAuthenticationProvider implements ISVNAuthenticationProvider {
    
    private PromptUserPassword myPrompt;
    
    public JavaHLAuthenticationProvider(PromptUserPassword prompt){
        myPrompt = prompt;
    }

    public SVNAuthentication requestClientAuthentication(String kind, SVNURL url, String realm, String errorMessage, SVNAuthentication previousAuth, boolean authMayBeStored) {
        if (ISVNAuthenticationManager.SSH.equals(kind) && myPrompt instanceof PromptUserPassword4) {
            PromptUserPassword4 prompt4 = (PromptUserPassword4) myPrompt;
            String userName = previousAuth != null && previousAuth.getUserName() != null ? previousAuth.getUserName() : System.getProperty("user.name");
            int port = url.getPort();
            if (prompt4.promptSSH(realm, userName, port, authMayBeStored)) {
                String password = prompt4.getPassword();
                String keyPath = prompt4.getSSHPrivateKeyPath();
                String passphrase = prompt4.getSSHPrivateKeyPassphrase();
                if ("".equals(passphrase)) {
                    passphrase = null;
                }
                port = prompt4.getSSHPort() >= 0 ? prompt4.getSSHPort() : url.getPort();
                if (port < 0) {
                    port = 22;
                }
                boolean save = prompt4.userAllowedSave();
                if (keyPath != null && !"".equals(keyPath)) {
                    return new SVNSSHAuthentication(userName, new File(keyPath), passphrase, port, save);
                } else if (password != null && !"".equals(password)){
                    return new SVNSSHAuthentication(userName, password, port, save);
                }
            }
            return null;
                        
        }
        if (ISVNAuthenticationManager.SSH.equals(kind) && previousAuth == null) {
            // use configuration file here? but it was already used once...
            String keyPath = System.getProperty("javasvn.ssh2.key");
            String userName = System.getProperty("javasvn.ssh2.username");
            if (userName == null) {
                userName = System.getProperty("user.name");
            }
            String passPhrase = System.getProperty("javasvn.ssh2.passphrase");
            if (userName == null) {
                return null;
            }
            if (keyPath != null && previousAuth == null) {
                // use port number from configuration file?
                return new SVNSSHAuthentication(userName, new File(keyPath), passPhrase, -1, true);
            }
            // try to get password for ssh from the user.
        } else if(!ISVNAuthenticationManager.PASSWORD.equals(kind)){
            return null;
        }
        String userName = previousAuth != null && previousAuth.getUserName() != null ? previousAuth.getUserName() : System.getProperty("user.name");
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

}
