package org.tmatesoft.svn.core.javahl17;

import java.io.File;
import java.security.cert.X509Certificate;
import java.text.MessageFormat;

import org.apache.subversion.javahl.callback.UserPasswordCallback;
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

class JavaHLAuthenticationProvider implements ISVNAuthenticationProvider, ISVNSSLPasspharsePromptSupport {

    //TODO: what is the constant about?
    private static final String ADAPTER_DEFAULT_PROMPT_CLASS =
        "org.tigris.subversion.svnclientadapter.javahl.AbstractJhlClientAdapter$DefaultPromptUserPassword";
    private UserPasswordCallback prompt;

    public JavaHLAuthenticationProvider(UserPasswordCallback prompt){
        this.prompt = prompt;
    }

    public SVNAuthentication requestClientAuthentication(String kind, SVNURL url, String realm, SVNErrorMessage errorMessage, SVNAuthentication previousAuth, boolean authMayBeStored) {
        if (ISVNAuthenticationManager.SSH.equals(kind) && prompt instanceof UserPasswordSSHCallback) {
            UserPasswordSSHCallback prompt4 = (UserPasswordSSHCallback) prompt;
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
        } else if (ISVNAuthenticationManager.SSL.equals(kind) && SVNSSLAuthentication.isCertificatePath(realm)) {
            String passphrase = prompt.askQuestion(realm, "SSL Certificate Passphrase", authMayBeStored);
            if (passphrase != null) {
                return new SVNPasswordAuthentication("", passphrase, prompt.userAllowedSave(), url, false);
            }
        } else if (ISVNAuthenticationManager.SSL.equals(kind) && !SVNSSLAuthentication.isCertificatePath(realm) && (prompt instanceof UserPasswordSSLCallback)) {
            UserPasswordSSLCallback prompt4 = (UserPasswordSSLCallback) prompt;
            if (prompt4.promptSSL(realm, authMayBeStored)) {
                String cert = prompt4.getSSLClientCertPath();
                String password = prompt4.getSSLClientCertPassword();
                if (cert != null) {
                    if ("".equals(password)) {
                        password = null;
                    }
                    boolean save = prompt.userAllowedSave();
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
            if (prompt.prompt(realm, userName, authMayBeStored)) {
                return new SVNUserNameAuthentication(prompt.getUsername(), prompt.userAllowedSave(), url, false);
            }
            return getDefaultUserNameCredentials(userName);
//TODO: prompt.prompt(String, String) is not used
//            if (prompt.prompt(realm, userName)) {
//                return new SVNUserNameAuthentication(prompt.getUsername(), false, url, false);
//            }
//            return getDefaultUserNameCredentials(userName);
        } else if(!ISVNAuthenticationManager.PASSWORD.equals(kind)){
            return null;
        }
        String userName = previousAuth != null && previousAuth.getUserName() != null ? previousAuth.getUserName() : getUserName(null, url);
//        if (prompt instanceof PromptUserPassword3) {
            if(prompt.prompt(realm, userName, authMayBeStored)){
                if (ISVNAuthenticationManager.SSH.equals(kind)) {
                    // use default port number from configuration file (should be in previous auth).
                    int portNumber = (previousAuth instanceof SVNSSHAuthentication) ? ((SVNSSHAuthentication) previousAuth).getPortNumber() : -1;
                    return new SVNSSHAuthentication(prompt.getUsername(), prompt.getPassword(), portNumber, prompt.userAllowedSave(), url, false);
                }
                return new SVNPasswordAuthentication(prompt.getUsername(), prompt.getPassword(), prompt.userAllowedSave(), url, false);
            }
//TODO: prompt.prompt(String, String) is not used
//        }else{
//            if(prompt.prompt(realm, userName)){
//                if (ISVNAuthenticationManager.SSH.equals(kind)) {
//                    return new SVNSSHAuthentication(userName, prompt.getPassword(), -1, true, url, false);
//                }
//                return new SVNPasswordAuthentication(prompt.getUsername(), prompt.getPassword(), true, url, false);
//            }
//        }
        return null;
    }

    private SVNAuthentication getDefaultUserNameCredentials(String userName) {
        if (ADAPTER_DEFAULT_PROMPT_CLASS.equals(prompt.getClass().getName())) {
            // return default username, despite prompt was 'cancelled'.
            return new SVNUserNameAuthentication(userName, false, null, false);
        }
        return null;
    }

    public int acceptServerAuthentication(SVNURL url, String realm, Object serverAuth,  boolean resultMayBeStored) {
        if (serverAuth instanceof X509Certificate) {
            serverAuth = serverAuth instanceof X509Certificate ?
                    SVNSSLUtil.getServerCertificatePrompt((X509Certificate) serverAuth, realm, url.getHost()) : serverAuth;
            if (serverAuth == null) {
                serverAuth = "Unsupported certificate type '" + (serverAuth != null ? serverAuth.getClass().getName() : "null") + "'";
            }
            return prompt.askTrustSSLServer(serverAuth.toString(), resultMayBeStored);
        } else if (prompt != null && serverAuth instanceof byte[]) {
            String prompt = "The ''{0}'' server''s key fingerprint is:\n{1}\n" +
            		"If you trust this host, select ''Yes'' to add the key to the SVN cache and carry on connecting.\n" +
            		"If you do not trust this host, select ''No'' to abandon the connection.";
            prompt = MessageFormat.format(prompt, new Object[]{url.getHost(), SVNSSLUtil.getFingerprint((byte[]) serverAuth, "MD5")});
            if (!this.prompt.askYesNo(realm, prompt, false)) {
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
