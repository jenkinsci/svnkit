/*
 * Created on 12.05.2005
 */
package org.tmatesoft.svn.core.internal.ws.log;

import java.io.InputStream;
import java.io.OutputStream;

public interface ISVNEntry {
    
    public String getPropertyValue(String name);
    
    public void getPropertyValue(String name, OutputStream value);
    
    public void setPropertyValue(String name, String value);
    
    public void setPropertyValue(String name, InputStream value);
    
    public long getRevision();
    
    public long getCommitedRevision();
    
    public boolean isMissing();
    
    public boolean isFile();
    
    public boolean isDirectory();
    
    public boolean isObstructed();
    
    public boolean isAdded();
    
    public boolean isDeleted();
    
    public boolean isReplaced();
}
