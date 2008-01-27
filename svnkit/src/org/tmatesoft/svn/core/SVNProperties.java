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

import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;


/**
 * @author TMate Software Ltd.
 * @version 1.1.1
 */
public class SVNProperties {

    public static final SVNProperties EMPTY_PROPERTIES = new SVNProperties(Collections.EMPTY_MAP);

    private Map myProperties;

    public SVNProperties() {
        myProperties = new HashMap();
    }

    public SVNProperties(SVNProperties properties) {
        myProperties = new HashMap(properties.myProperties);
    }

    private SVNProperties(Map properties) {
        myProperties = properties;
    }

    public void put(String propertyName, SVNPropertyValue propertyValue) {
        myProperties.put(propertyName, propertyValue);
    }

    public void put(String propertyName, String propertyValue) {
        myProperties.put(propertyName, propertyValue);
    }

    public void put(String propertyName, byte[] propertyValue) {
        myProperties.put(propertyName, propertyValue);
    }

    public String getStringValue(String propertyName) {
        Object value = myProperties.get(propertyName);
        if (value == null) {
            return null;
        }
        if (value instanceof String) {
            return (String) value;
        }
        if (value instanceof byte[]) {
            SVNPropertyValue propertyValue = SVNPropertyValue.create(propertyName, (byte[]) value);
            myProperties.put(propertyName, propertyValue);
            return propertyValue.getString();
        }
        if (value instanceof SVNPropertyValue) {
            SVNPropertyValue propertyValue = (SVNPropertyValue) value;
            return propertyValue.getString();
        }
        return null;
    }

    public byte[] getBinaryValue(String propertyName) {
        Object value = myProperties.get(propertyName);
        if (value == null || value instanceof String) {
            return null;
        }
        if (value instanceof byte[]) {
            return (byte[]) value;
        }
        if (value instanceof SVNPropertyValue) {
            SVNPropertyValue propertyValue = (SVNPropertyValue) value;
            return propertyValue.getBytes();
        }
        return null;
    }

    public SVNPropertyValue getSVNPropertyValue(String propertyName) {
        Object value = myProperties.get(propertyName);
        if (value == null) {
            return null;
        }
        if (value instanceof SVNPropertyValue) {
            return (SVNPropertyValue) value;
        }
        if (value instanceof String) {
            SVNPropertyValue propertyValue = SVNPropertyValue.create((String) value);
            myProperties.put(propertyName, propertyValue);
            return propertyValue;
        }
        if (value instanceof byte[]) {
            SVNPropertyValue propertyValue = SVNPropertyValue.create(propertyName, (byte[]) value);
            myProperties.put(propertyName, propertyValue);
            return propertyValue;
        }
        return null;
    }

    public Object remove(String propertyName) {
        return myProperties.remove(propertyName);
    }

    public void putAll(SVNProperties properties) {
        myProperties.putAll(properties.myProperties);
    }

    public void copyValue(SVNProperties properties, String propertyName) {
        myProperties.put(propertyName, properties.myProperties.get(propertyName));
    }

    public boolean isEmpty() {
        return myProperties.isEmpty();
    }

    public void clear() {
        myProperties.clear();
    }

    public boolean hasNullValues() {
        if (myProperties.isEmpty()) {
            return false;
        }
        return myProperties.containsValue(null);
    }

    public boolean hasNotNullValues() {
        if (isEmpty()) {
            return false;
        }
        if (!hasNullValues()) {
            return true;
        }
        for (Iterator entries = myProperties.entrySet().iterator(); entries.hasNext();) {
            Map.Entry entry = (Map.Entry) entries.next();
            if (entry.getValue() != null) {
                return true;
            }
        }
        return false;
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
}
