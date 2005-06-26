/*
 * Created on 25.06.2005
 */
package org.tigris.subversion.javahl;

import java.io.File;

import org.tmatesoft.svn.core.wc.ISVNAuthenticationManager;
import org.tmatesoft.svn.core.wc.ISVNAuthenticationProvider;
import org.tmatesoft.svn.core.wc.SVNAuthentication;

public class PromptAuthenticationProvider implements ISVNAuthenticationProvider {
    
    PromptUserPassword myPrompt;
    
    public PromptAuthenticationProvider(PromptUserPassword prompt){
        myPrompt = prompt;
    }

    public SVNAuthentication requestClientAuthentication(String kind,
            String realm, SVNAuthentication previousAuth, ISVNAuthenticationManager manager) {
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
                SVNAuthentication auth = new SVNAuthentication(kind, realm, userName, null, new File(keyPath), passPhrase);
                auth.setStorageAllowed(true);
                return auth;
            }
            // try to get password for ssh from the user.
        } else if(!ISVNAuthenticationManager.PASSWORD.equals(kind)){
            return null;
        }
        String userName = previousAuth != null && previousAuth.getUserName() != null ? previousAuth.getUserName() : System.getProperty("user.name");
        if (myPrompt instanceof PromptUserPassword3) {
            PromptUserPassword3 prompt3 = (PromptUserPassword3) myPrompt;
            if(prompt3.prompt(realm, userName, manager.isAuthStorageEnabled())){
                SVNAuthentication auth = new SVNAuthentication(kind, realm,
                        prompt3.getUsername(), prompt3.getPassword());
                auth.setStorageAllowed(prompt3.userAllowedSave());
                return auth;
            }
        }else{
            if(myPrompt.prompt(realm, userName)){
                return new SVNAuthentication(kind, realm, myPrompt.getUsername(), myPrompt.getPassword());
            }
        }
        return null;
    }

    public int acceptServerAuthentication(SVNAuthentication authentication,
            ISVNAuthenticationManager manager) {
        return ISVNAuthenticationProvider.ACCEPTED;
    }

}
