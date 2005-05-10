/*
 * Created on 10.05.2005
 */
package org.tmatesoft.svn.core.internal.ws.log;

interface ISVNEntries {
    
    public void setProperty(String name, String propertyName, String value);
    
    public String getProperty(String name, String propertyName);
    
    public void remove(String name);
    
    public void add(String name);
}
