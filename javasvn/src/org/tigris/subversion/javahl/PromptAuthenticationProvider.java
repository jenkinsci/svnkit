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
        return new SVNAuthentication(kind, realm, myPrompt.getUsername(), myPrompt.getPassword());
    }

    public int acceptServerAuthentication(SVNAuthentication authentication,
            ISVNAuthenticationManager manager) {
        return ISVNAuthenticationProvider.ACCEPTED;
    }

}
