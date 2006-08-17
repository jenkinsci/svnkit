/*
 * ====================================================================
 * Copyright (c) 2004-2006 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://tmate.org/svn/license.html.
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.core.internal.wc.admin;

import java.util.Map;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNProperty;


public abstract class SVNProperties14 extends ISVNProperties {

    private SVNAdminArea14 myAdminArea;
    private String myEntryName;
    
    public SVNProperties14(Map props, SVNAdminArea14 adminArea, String entryName) {
        super(props);
        myAdminArea = adminArea;
        myEntryName = entryName;
    }
    
    public String getPropertyValue(String name) throws SVNException {
        SVNEntry entry = myAdminArea.getEntry(myEntryName, true);
        String[] cachableProps = entry.getCachableProperties(); 
        if (cachableProps != null && getIndex(cachableProps, name) >= 0) {
            String[] presentProps = entry.getPresentProperties();
            if (presentProps == null || getIndex(presentProps, name) < 0) {
                return null;
            }
            if (SVNProperty.isBooleanProperty(name)) {
                return SVNProperty.getValueOfBooleanProperty(name);
            }
        }
        
        Map props = loadProperties();
        if (!isEmpty()) {
            return (String)props.get(name); 
        }
        return null;
    }

    //TODO: this is not a good approach, however don't want to
    //sort the original array to use it with Arrays.binarySearch(), 
    //since there's a risk to lose the order elements are stored in
    //the array and written to the file. Maybe the storage order is 
    //important for somewhat somewhere... 
    private int getIndex(String[] array, String element) {
        if (array == null || element == null) {
            return -1;
        }
        for(int i = 0; i < array.length; i++){
            if (element.equals(array[i])) {
                return i;
            }
        }
        return -1;
    }

    protected ISVNProperties wrap(Map properties) {
        return new SVNProperties13(properties);
    }

}
