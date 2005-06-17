/*
 * Created on 17.06.2005
 */
package org.tigris.subversion.javahl;

import java.io.File;

import org.tmatesoft.svn.core.io.SVNException;
import org.tmatesoft.svn.core.wc.ISVNPropertyHandler;
import org.tmatesoft.svn.core.wc.SVNPropertyData;


class SVNPropertyRetriever implements ISVNPropertyHandler{
    
    private PropertyData myData = null;
    private SVNClient myClient;
    
    public SVNPropertyRetriever(SVNClient client){
        myClient = client;
    }

    public void handleProperty(File path, SVNPropertyData property) throws SVNException {
        myData = new PropertyData(myClient, path.getAbsolutePath(), property.getName(), property.getValue(), property.getValue().getBytes());
    }

    public void handleProperty(String url, SVNPropertyData property) throws SVNException {
        myData = new PropertyData(myClient, url, property.getName(), property.getValue(), property.getValue().getBytes());
    }
    
    public PropertyData getPropertyData(){
        if(myData.getValue() == null){
            return null;
        }
        return myData;
    }
    
}