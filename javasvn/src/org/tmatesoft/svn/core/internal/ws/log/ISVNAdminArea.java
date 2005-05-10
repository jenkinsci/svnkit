/*
 * Created on 10.05.2005
 */
package org.tmatesoft.svn.core.internal.ws.log;

import java.io.File;
import java.io.IOException;

public interface ISVNAdminArea {
    
    public void apply(ISVNAdminArea copy);
    
    public void delete();
    
    public void dispose();
    
    public void lock() throws IOException;
    
    public boolean isLocked();
    
    public void unlock();
    
    public ISVNLogHandler getLogHandler();
    
    public ISVNEntries getEntries();
    
    public void runLog(ISVNLogHandler handler);
    
    public String[] getChildNames();
    
    
    public ISVNAdminArea getChild(String name);
    
    public ISVNAdminArea getWorkingCopy();

    public ISVNAdminArea getParent();
    
    public ISVNAdminArea getOwner();

    public File getTextFile(String name);

    public File getBaseFile(String name);

    public File getTmpBaseFile(String name);
}
