/*
 * Created on 10.05.2005
 */
package org.tmatesoft.svn.core.internal.ws.log;

interface ISVNLogBaton {
    
    public int getLevel();
    
    public ISVNAdminArea getAdminArea();
}
