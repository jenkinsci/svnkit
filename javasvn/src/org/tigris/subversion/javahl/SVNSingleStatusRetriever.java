/*
 * Created on 21.06.2005
 */
package org.tigris.subversion.javahl;

import org.tmatesoft.svn.core.wc.ISVNStatusHandler;
import org.tmatesoft.svn.core.wc.SVNStatus;

public class SVNSingleStatusRetriever implements ISVNStatusHandler {
    
    private SVNStatus myStatus = null;
    
    public void handleStatus(SVNStatus status) {
        myStatus = status;
    }
    
    public SVNStatus getStatus(){
        return myStatus;
    } 

}
