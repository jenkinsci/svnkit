/*
 * Created on 25.06.2005
 */
package org.tigris.subversion.javahl;

import org.tmatesoft.svn.core.wc.ISVNAuthenticationManager;
import org.tmatesoft.svn.core.wc.ISVNAuthenticationProvider;
import org.tmatesoft.svn.core.wc.SVNAuthentication;

public class PromptAuthenticationProvider implements ISVNAuthenticationProvider {
    
    PromptUserPassword myPrompt;
    
    public PromptAuthenticationProvider(PromptUserPassword prompt){
        myPrompt = prompt;
    }

    public SVNAuthentication requestClientAuthentication(String kind,
            String realm, String userName, ISVNAuthenticationManager manager) {
        if(myPrompt.prompt(realm, userName)){
            SVNAuthentication auth = new SVNAuthentication(kind, realm, myPrompt.getUsername(), myPrompt.getPassword());
            if (myPrompt instanceof PromptUserPassword3) {
                PromptUserPassword3 prompt3 = (PromptUserPassword3) myPrompt;
                auth.setStorageAllowed(prompt3.userAllowedSave());
            }
            return auth;
        }
        return null;
    }

    public int acceptServerAuthentication(SVNAuthentication authentication,
            ISVNAuthenticationManager manager) {
        return ISVNAuthenticationProvider.ACCEPTED;
    }

}
