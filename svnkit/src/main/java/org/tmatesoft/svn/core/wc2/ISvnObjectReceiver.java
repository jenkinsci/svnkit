package org.tmatesoft.svn.core.wc2;

import org.tmatesoft.svn.core.SVNException;

public interface ISvnObjectReceiver<T> {
    
    public void receive(SvnTarget target, T object) throws SVNException;

}
