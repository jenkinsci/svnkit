/*
 * Created on 26.05.2005
 */
package org.tmatesoft.svn.core.internal.wc;

import org.tmatesoft.svn.core.io.SVNException;

public interface ISVNCrawler {
    
    public void visitDirectory(SVNWCAccess owner, SVNDirectory dir) throws SVNException;

}
