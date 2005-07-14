/*
 * Created on 25.06.2005
 */
package org.tigris.subversion.javahl;

import java.io.File;

import org.tmatesoft.svn.core.auth.ISVNAuthenticationManager;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationProvider;
import org.tmatesoft.svn.core.auth.SVNAuthentication;
import org.tmatesoft.svn.core.auth.SVNPasswordAuthentication;
import org.tmatesoft.svn.core.auth.SVNSSHAuthentication;

public class PromptAuthenticationProvider implements ISVNAuthenticationProvider {
    
    PromptUserPassword myPrompt;
    
    public PromptAuthenticationProvider(PromptUserPassword prompt){
        myPrompt = prompt;
    }

    public SVNAuthentication requestClientAuthentication(String kind, String url, String realm, String errorMessage, SVNAuthentication previousAuth, boolean authMayBeStored) {
        if (ISVNAuthenticationManager.SSH.equals(kind) && previousAuth == null) {
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
                // use system-wide ssh auth.
                return new SVNSSHAuthentication(userName, new File(keyPath), passPhrase, true);
            }
            // try to get password for ssh from the user.
        } else if(!ISVNAuthenticationManager.PASSWORD.equals(kind)){
            return null;
        }
        String userName = previousAuth != null && previousAuth.getUserName() != null ? previousAuth.getUserName() : System.getProperty("user.name");
        if (myPrompt instanceof PromptUserPassword3) {
            PromptUserPassword3 prompt3 = (PromptUserPassword3) myPrompt;
            if(prompt3.prompt(realm, userName, authMayBeStored)){
                return new SVNPasswordAuthentication(prompt3.getUsername(), prompt3.getPassword(), prompt3.userAllowedSave());
            }
        }else{
            if(myPrompt.prompt(realm, userName)){
                return new SVNPasswordAuthentication(myPrompt.getUsername(), myPrompt.getPassword(), true);
            }
        }
        return null;
    }

    public int acceptServerAuthentication(String url, Object serverAuth, ISVNAuthenticationManager manager, boolean resultMayBeStored) {
        return 0;
    }

}
