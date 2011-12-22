package org.tmatesoft.svn.core.javahl17;

import org.apache.subversion.javahl.callback.UserPasswordCallback;

public interface UserPasswordSSLCallback extends UserPasswordCallback {

    public boolean promptSSL(String realm, boolean maySave);

    public String getSSLClientCertPath();

    public String getSSLClientCertPassword();
}
