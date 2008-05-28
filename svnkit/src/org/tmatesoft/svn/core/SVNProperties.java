/*
 * ====================================================================
 * Copyright (c) 2004-2007 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.core;

import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.tmatesoft.svn.core.internal.util.SVNHashMap;


/**
 * @author TMate Software Ltd.
 * @version 1.1.1
 */
public class SVNProperties {

    private Map myProperties;
    
    public static SVNProperties wrap(Map map) {
        if (map == null) {
            return new SVNProperties();
        }
        return new SVNProperties(map);
    }

    public SVNProperties() {
        myProperties = new SVNHashMap();
    }

    public SVNProperties(SVNProperties properties) {
        myProperties = new SVNHashMap(properties.myProperties);
    }

    private SVNProperties(Map properties) {
        myProperties = properties;
    }

    public void put(String propertyName, SVNPropertyValue propertyValue) {
        myProperties.put(propertyName, propertyValue);
    }

    public void put(String propertyName, String propertyValue) {
        myProperties.put(propertyName, SVNPropertyValue.create(propertyValue));
    }

    public void put(String propertyName, byte[] propertyValue) {
        myProperties.put(propertyName, SVNPropertyValue.create(propertyName, propertyValue));
    }

    public String getStringValue(String propertyName) {
        SVNPropertyValue value = (SVNPropertyValue) myProperties.get(propertyName);
        return value == null ? null : value.getString();
    }

    public byte[] getBinaryValue(String propertyName) {
        SVNPropertyValue value = (SVNPropertyValue) myProperties.get(propertyName);
        return value == null ? null : value.getBytes();
    }

    public SVNPropertyValue getSVNPropertyValue(String propertyName) {
        return (SVNPropertyValue) myProperties.get(propertyName);        
    }

    public SVNPropertyValue remove(String propertyName) {
        return (SVNPropertyValue) myProperties.remove(propertyName);
    }

    public void putAll(SVNProperties properties) {
        myProperties.putAll(properties.myProperties);
    }

    public boolean isEmpty() {
        return myProperties.isEmpty();
    }

    public void clear() {
        myProperties.clear();
    }

    public void removeNullValues() {
        for (Iterator iterator = myProperties.keySet().iterator(); iterator.hasNext();) {
            String name = (String) iterator.next();
            if (myProperties.get(name) == null) {
                iterator.remove();
            }
        }
    }

    public int size() {
        return myProperties.size();
    }

    public boolean containsName(String propertyName) {
        return myProperties.containsKey(propertyName);
    }

    public Set nameSet() {
        return myProperties.keySet();
    }

    public boolean containsValue(SVNPropertyValue value){
        return myProperties.containsValue(value);        
    }

    public Collection values(){
        return myProperties.values();
    }
    
    public SVNProperties getRegularProperties() {
        SVNProperties result = new SVNProperties();
        for (Iterator propNamesIter = nameSet().iterator(); propNamesIter.hasNext();) {
            String propName = (String) propNamesIter.next();
            if (SVNProperty.isRegularProperty(propName)) {
                result.put(propName, getSVNPropertyValue(propName));
            }
        }
        return result;
    }

    public SVNProperties compareTo(SVNProperties properties) {
        SVNProperties result = new SVNProperties();
        if (isEmpty()) {
            result.putAll(properties);
            return result;
        }
        
        Collection props1 = nameSet();
        Collection props2 = properties.nameSet();
        
        // missed in props2.
        Collection tmp = new TreeSet(props1);
        tmp.removeAll(props2);
        for (Iterator props = tmp.iterator(); props.hasNext();) {
            String missing = (String) props.next();
            result.put(missing, (byte[]) null);
        }

        // added in props2.
        tmp = new TreeSet(props2);
        tmp.removeAll(props1);

        for (Iterator props = tmp.iterator(); props.hasNext();) {
            String added = (String) props.next();
            result.put(added, properties.getSVNPropertyValue(added));
        }

        // changed in props2
        tmp = new TreeSet(props2);
        tmp.retainAll(props1);
        
        for (Iterator props = tmp.iterator(); props.hasNext();) {
            String changed = (String) props.next();
            SVNPropertyValue value1 = getSVNPropertyValue(changed);
            SVNPropertyValue value2 = properties.getSVNPropertyValue(changed);
            if (!value1.equals(value2)) {
                result.put(changed, value2);
            }
        }
        return result;
    }

}
