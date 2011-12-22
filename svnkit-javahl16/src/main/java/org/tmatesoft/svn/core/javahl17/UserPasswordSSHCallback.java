package org.tmatesoft.svn.core.javahl17;

import org.apache.subversion.javahl.callback.UserPasswordCallback;

public interface UserPasswordSSHCallback extends UserPasswordCallback {

    public boolean promptSSH(String realm, String username, int sshPort, boolean maySave);

    public String getSSHPrivateKeyPath();

    public String getSSHPrivateKeyPassphrase();

    public int getSSHPort();
}
