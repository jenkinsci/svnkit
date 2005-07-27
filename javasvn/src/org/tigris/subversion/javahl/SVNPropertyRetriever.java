/*
 * Created on 17.06.2005
 */
package org.tigris.subversion.javahl;

import java.io.File;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
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

    public void handleProperty(SVNURL url, SVNPropertyData property) throws SVNException {
        myData = new PropertyData(myClient, url.toString(), property.getName(), property.getValue(), property.getValue().getBytes());
    }

    public void handleProperty(long revision, SVNPropertyData property) throws SVNException {
        myData = new PropertyData(myClient, null, property.getName(), property.getValue(), property.getValue().getBytes());
    }
    
    public PropertyData getPropertyData(){
        if(myData == null){
            return null;
        }
        if(myData.getValue() == null){
            return null;
        }
        return myData;
    }
    
}