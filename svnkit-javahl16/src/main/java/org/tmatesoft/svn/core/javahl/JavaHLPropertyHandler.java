/*
 * ====================================================================
 * Copyright (c) 2004-2012 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.core.javahl;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;

import org.tigris.subversion.javahl.JavaHLObjectFactory;
import org.tigris.subversion.javahl.PropertyData;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.wc.ISVNPropertyHandler;
import org.tmatesoft.svn.core.wc.SVNPropertyData;

/**
 * @version 1.3
 * @author  TMate Software Ltd.
 */
class JavaHLPropertyHandler implements ISVNPropertyHandler{
    
    private PropertyData myData = null;
    private Object myOwner;
    private Collection myAllData;
    
    public JavaHLPropertyHandler(Object owner){
        myOwner = owner;
        myAllData = new ArrayList();
    }

    public void handleProperty(File path, SVNPropertyData property) throws SVNException {
        myData = JavaHLObjectFactory.createPropertyData(myOwner, path.getAbsolutePath(), property.getName(), property.getValue());
        myAllData.add(myData);
    }

    public void handleProperty(SVNURL url, SVNPropertyData property) throws SVNException {
        myData = JavaHLObjectFactory.createPropertyData(myOwner, url.toString(), property.getName(),property.getValue());
        myAllData.add(myData);
    }

    public void handleProperty(long revision, SVNPropertyData property) throws SVNException {
        myData = JavaHLObjectFactory.createPropertyData(myOwner, null, property.getName(), property.getValue());
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