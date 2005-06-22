package org.tmatesoft.svn.core.wc;

import java.io.File;

/**
 * Created by IntelliJ IDEA.
 * User: alex
 * Date: 22.06.2005
 * Time: 18:22:21
 * To change this template use File | Settings | File Templates.
 */
    // object that stores credentials info
public class SVNAuthentication {

    private String myKind;
    private String myRealm;
    private String myUserName;
    private File myHttpsClientCertFile;
    private File mySSHKeyFile;
    private String myPassword;

    private String myProxyHost;
    private int myProxyPort;
    private String myProxyPassword;
    private String myProxyUserName;
    private String myPassphrase;

    // password ot ssh2
    public SVNAuthentication(String kind, String realm, String username,
                             String password) {
        this(kind,realm,username);
        myPassword = password;
    }

    // user name
    public SVNAuthentication(String kind, String realm, String username) {
        this(kind, realm);
        myUserName = username;
    }

    public SVNAuthentication(String kind, String realm, String proxyHost, int proxyPort, String userName, String password) {
        this(kind, realm);
        myProxyHost = proxyHost;
        myProxyPort = proxyPort;
        myProxyPassword = password;
        myProxyUserName = userName;
    }

    // ssh2
    public SVNAuthentication(String kind, String realm, String username, String password, File sshKeyFile, String passphrase) {
        this(kind, realm, username);
        mySSHKeyFile = sshKeyFile;
        myPassphrase = passphrase;
        myPassword = password;
    }

    // https client cert.
    public SVNAuthentication(String kind, String realm, File httpsClientCertFile) {
        this(kind, realm);
        myHttpsClientCertFile = httpsClientCertFile;
    }

    private SVNAuthentication(String kind, String realm) {
        myKind = kind;
        myRealm = realm;
    }

    public String getKind() {
        return myKind;
    }

    public String getRealm() {
        return myRealm;
    }

    public String getUserName() {
        return myUserName;
    }

    public File getHttpsClientCertFile() {
        return myHttpsClientCertFile;
    }

    public File getSSHKeyFile() {
        return mySSHKeyFile;
    }

    public String getPassword() {
        return myPassword;
    }

    public String getProxyHost() {
        return myProxyHost;
    }

    public int getProxyPort() {
        return myProxyPort;
    }

    public String getProxyPassword() {
        return myProxyPassword;
    }

    public String getProxyUserName() {
        return myProxyUserName;
    }

    public String getPassphrase() {
        return myPassphrase;
    }
}
