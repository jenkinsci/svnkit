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

    private boolean myIsStorageEnabled;

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
        myIsStorageEnabled = true;
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

    public boolean isStorageAllowed() {
        return myIsStorageEnabled;
    }

    public void setStorageAllowed(boolean allowed) {
        myIsStorageEnabled = allowed;
    }

    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        final SVNAuthentication that = (SVNAuthentication) o;

        if (myProxyPort != that.myProxyPort) return false;
        if (myHttpsClientCertFile != null ? !myHttpsClientCertFile.equals(that.myHttpsClientCertFile) : that.myHttpsClientCertFile != null) return false;
        if (!myKind.equals(that.myKind)) return false;
        if (myPassphrase != null ? !myPassphrase.equals(that.myPassphrase) : that.myPassphrase != null) return false;
        if (myPassword != null ? !myPassword.equals(that.myPassword) : that.myPassword != null) return false;
        if (myProxyHost != null ? !myProxyHost.equals(that.myProxyHost) : that.myProxyHost != null) return false;
        if (myProxyPassword != null ? !myProxyPassword.equals(that.myProxyPassword) : that.myProxyPassword != null) return false;
        if (myProxyUserName != null ? !myProxyUserName.equals(that.myProxyUserName) : that.myProxyUserName != null) return false;
        if (myRealm != null ? !myRealm.equals(that.myRealm) : that.myRealm != null) return false;
        if (mySSHKeyFile != null ? !mySSHKeyFile.equals(that.mySSHKeyFile) : that.mySSHKeyFile != null) return false;
        if (myUserName != null ? !myUserName.equals(that.myUserName) : that.myUserName != null) return false;

        return true;
    }

    public int hashCode() {
        int result;
        result = myKind.hashCode();
        result = 29 * result + (myRealm != null ? myRealm.hashCode() : 0);
        result = 29 * result + (myUserName != null ? myUserName.hashCode() : 0);
        result = 29 * result + (myHttpsClientCertFile != null ? myHttpsClientCertFile.hashCode() : 0);
        result = 29 * result + (mySSHKeyFile != null ? mySSHKeyFile.hashCode() : 0);
        result = 29 * result + (myPassword != null ? myPassword.hashCode() : 0);
        result = 29 * result + (myProxyHost != null ? myProxyHost.hashCode() : 0);
        result = 29 * result + myProxyPort;
        result = 29 * result + (myProxyPassword != null ? myProxyPassword.hashCode() : 0);
        result = 29 * result + (myProxyUserName != null ? myProxyUserName.hashCode() : 0);
        result = 29 * result + (myPassphrase != null ? myPassphrase.hashCode() : 0);
        return result;
    }
}
