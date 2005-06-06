/*
 * Created on 06.06.2005
 */
package org.tmatesoft.svn.core.wc;

import java.io.File;

import org.tmatesoft.svn.core.io.SVNException;

public interface ISVNPropertyHandler {
    
    public void handleProperty(File path, SVNPropertyData property) throws SVNException;

    public void handleProperty(String url, SVNPropertyData property) throws SVNException;
}
