/*
 * ====================================================================
 * Copyright (c) 2004 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://tmate.org/svn/license.html.
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */

package org.tmatesoft.svn.core.internal.io.dav;

import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;


/**
 * @version 1.0
 * @author  TMate Software Ltd.
 */
public class DAVResponse {
    
    private String myHref;
    private DAVStatus myStatus;
    private Map myProperties;
    
    public String getHref() {
        return myHref;
    }
    public DAVStatus getStatus() {
        return myStatus;
    }
    public Object getPropertyValue(DAVElement property) {
        if (myProperties == null) {
            return null;
        }
        return myProperties.get(property);        
    }
    public Object putPropertyValue(DAVElement property, Object value) {
        if (myProperties == null) { 
            myProperties = new HashMap();
        }
        return myProperties.put(property, value);        
    }
    public Iterator properties() {
        if (myProperties == null) {
            return Collections.EMPTY_LIST.iterator();
        }
        return myProperties.keySet().iterator();
    }
    public void setStatus(DAVStatus status) {
        myStatus = status;
    }
    public void setHref(String href) {
        myHref = href;
    }
}
