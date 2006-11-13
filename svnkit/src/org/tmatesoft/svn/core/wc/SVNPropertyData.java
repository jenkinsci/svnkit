/*
 * ====================================================================
 * Copyright (c) 2004-2006 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.core.wc;

import org.tmatesoft.svn.core.SVNProperty;

/**
 * <b>SVNPropertyData</b> is a wrapper for both versioned and unversioned
 * properties. This class represents the pair: property name - property value.
 * Property managing methods of the <b>SVNWCClient</b> class use 
 * <b>SVNPropertyData</b> to wrap properties and dispatch them to 
 * <b>handleProperty()</b> methods of <b>ISVNPropertyHandler</b> for processing
 * or simply return that 'properties object' as a target.
 * 
 * @version 1.1.0
 * @author  TMate Software Ltd.
 * @see     ISVNPropertyHandler
 * @see     SVNWCClient
 */
public class SVNPropertyData {

    private String myValue;

    private String myName;
    
    /**
     * Constructs an <b>SVNPropertyData</b> given a property name and its
     * value. 
     * 
     * @param name  a property name
     * @param data  a property value
     */
    public SVNPropertyData(String name, String data) {
        myName = name;
        myValue = data;
        if (myValue != null && SVNProperty.isSVNProperty(myName)) {
            myValue = myValue.replaceAll("\n", System.getProperty("line.separator"));
        }
    }
    
    /**
     * Gets the name of the property represented by this 
     * <b>SVNPropertyData</b> object. 
     * 
     * @return  a property name
     */
    public String getName() {
        return myName;
    }
    
    /**
     * Gets the value of the property represented by this 
     * <b>SVNPropertyData</b> object.
     *  
     * @return  a property value
     */
    public String getValue() {
        return myValue;
    }

}
