/*
 * Created on 12.05.2005
 */
package org.tmatesoft.svn.core.internal.ws.log;

public interface ISVNOptions {
    
    public boolean isUseCommitTime();
    
    public boolean isIgnored(String name);    
}
