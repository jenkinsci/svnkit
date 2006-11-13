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
package org.tmatesoft.svn.core.internal.wc.admin;

import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeSet;

import org.tmatesoft.svn.core.SVNException;

/**
 * @version 1.1.0
 * @author  TMate Software Ltd.
 */
public abstract class SVNVersionedProperties {
    private Map myProperties;
    private boolean myIsModified;
    
    protected SVNVersionedProperties(Map props) {
        myProperties = props;
        myIsModified = false;
    }
    
    public abstract boolean containsProperty(String name) throws SVNException;
    
    public abstract String getPropertyValue(String name) throws SVNException;

    public boolean isModified() {
        return myIsModified;
    }
    
    protected void setModified(boolean modified) {
        myIsModified = modified;
    }
    
    public boolean isEmpty() throws SVNException {
        Map props = loadProperties();
        return props == null || props.isEmpty();
    }
    
    public Collection getPropertyNames(Collection target) throws SVNException {
        Map props = loadProperties();

        target = target == null ? new TreeSet() : target;
        if (isEmpty()) {
            return target;
        }
        for (Iterator names = props.keySet().iterator(); names.hasNext();) {
            target.add(names.next());
        }
        return target;
    }

    public void setPropertyValue(String name, String value) throws SVNException {
        Map props = loadProperties();
        if (value != null) {
            props.put(name, value);
        } else {
            props.remove(name);
        }
        myIsModified = true;
    }

    public SVNVersionedProperties compareTo(SVNVersionedProperties properties) throws SVNException {
        Map result = new HashMap();
        if (isEmpty()) {
            result.putAll(properties.asMap());
            return wrap(result);
        }
        
        Collection props1 = getPropertyNames(null);
        Collection props2 = properties.getPropertyNames(null);
        
        // missed in props2.
        Collection tmp = new TreeSet(props1);
        tmp.removeAll(props2);
        for (Iterator props = tmp.iterator(); props.hasNext();) {
            String missing = (String) props.next();
            result.put(missing, null);
        }

        // added in props2.
        tmp = new TreeSet(props2);
        tmp.removeAll(props1);

        for (Iterator props = tmp.iterator(); props.hasNext();) {
            String added = (String) props.next();
            result.put(added, properties.getPropertyValue(added));
        }

        // changed in props2
        props2.retainAll(props1);
        for (Iterator props = props2.iterator(); props.hasNext();) {
            String changed = (String) props.next();
            String value1 = getPropertyValue(changed);
            String value2 = properties.getPropertyValue(changed);
            if (!value1.equals(value2)) {
                result.put(changed, value2);
            }
        }
        return wrap(result);
    }
    
    public void copyTo(SVNVersionedProperties destination) throws SVNException {
        Map props = loadProperties();
        if (isEmpty()) {
            destination.removeAll();
        } else {
            destination.put(props);
        }
    }
    
    public void removeAll() throws SVNException {
        Map props = loadProperties();
        if (!isEmpty()) {
            props.clear();
            myIsModified = true;
        }
    }
    
    public boolean equals(SVNVersionedProperties props) throws SVNException {
        return compareTo(props).isEmpty();
    }
    
    public Map asMap() throws SVNException {
        Map props = loadProperties() != null ? new HashMap(loadProperties()) : new HashMap();
        return props;
    }
    
    protected void put(Map props) throws SVNException {
        Map thisProps = loadProperties(); 
        thisProps.clear();
        thisProps.putAll(props);
        myIsModified = true;
    }

    protected Map getPropertiesMap() {
        return myProperties;
    }
    
    protected void setPropertiesMap(Map props) {
        myProperties = props;
    }
    
    protected abstract SVNVersionedProperties wrap(Map properties);

    protected abstract Map loadProperties() throws SVNException;

}
