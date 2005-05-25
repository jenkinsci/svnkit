/*
 * Created on 17.05.2005
 */
package org.tmatesoft.svn.core.wc;

import org.tmatesoft.svn.core.io.SVNCancelException;

public interface ISVNEventListener {
    
    public static final double UNKNOWN = -1;
    
    public void svnEvent(SVNEvent event, double progress);
    
    public void checkCancelled() throws SVNCancelException;

}
