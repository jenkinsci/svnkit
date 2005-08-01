/*
 * Created on 17.06.2005
 */
package org.tmatesoft.svn.core.javahl;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;

import org.tigris.subversion.javahl.PropertyData;
import org.tigris.subversion.javahl.SVNClient;
import org.tigris.subversion.javahl.JavaHLObjectFactory;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.wc.ISVNPropertyHandler;
import org.tmatesoft.svn.core.wc.SVNPropertyData;


class JavaHLPropertyHandler implements ISVNPropertyHandler{
    
    private PropertyData myData = null;
    private SVNClient myOwner;
    private Collection myAllData;
    
    public JavaHLPropertyHandler(SVNClient owner){
        myOwner = owner;
        myAllData = new ArrayList();
    }

    public void handleProperty(File path, SVNPropertyData property) throws SVNException {
        myData = JavaHLObjectFactory.createPropertyData(myOwner, path.getAbsolutePath(), property.getName(), property.getValue(), property.getValue().getBytes());
        myAllData.add(myData);
    }

    public void handleProperty(SVNURL url, SVNPropertyData property) throws SVNException {
        myData = JavaHLObjectFactory.createPropertyData(myOwner, url.toString(), property.getName(), property.getValue(), property.getValue().getBytes());
        myAllData.add(myData);
    }

    public void handleProperty(long revision, SVNPropertyData property) throws SVNException {
        myData = JavaHLObjectFactory.createPropertyData(myOwner, null, property.getName(), property.getValue(), property.getValue().getBytes());
        myAllData.add(myData);
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
    
    public PropertyData[] getAllPropertyData() {
        return (PropertyData[]) myAllData.toArray(new PropertyData[myAllData.size()]);
    }
    
}