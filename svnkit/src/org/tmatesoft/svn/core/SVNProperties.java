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

import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import java.util.Map;
import java.util.Iterator;


/**
 * @author TMate Software Ltd.
 * @version 1.1.1
 */
public class SVNProperties {

    private Map myProperties;

    public SVNProperties() {
        myProperties = new HashMap();
    }

    public void put(String propertyName, String propertyValue) {
        myProperties.put(propertyName, propertyValue);
    }

    public void put(String propertyName, byte[] propertyValue) {
        myProperties.put(propertyName, propertyValue);
    }

    public void put(String propertyName, byte[] propertyValue, String encoding) {
        if (encoding == null) {
            myProperties.put(propertyName, propertyValue);
        } else {
            String stringValue;
            try {
                stringValue = new String(propertyValue, encoding);
            } catch (UnsupportedEncodingException e) {
                stringValue = new String(propertyValue);
            }
            myProperties.put(propertyName, stringValue);
        }
    }

    public String getStringValue(String propertyName, String encoding) {
        Object value = myProperties.get(propertyName);
        if (value == null) {
            return null;
        }
        if (value instanceof String) {
            return (String) value;
        }
        if (value instanceof byte[]) {
            byte[] bytes = (byte[]) value;
            String stringValue = null;
            try {
                stringValue = new String(bytes, encoding);
            } catch (UnsupportedEncodingException e) {
                stringValue = new String(bytes);
            }
            return stringValue;
        }
        return null;
    }

    public String getStringValue(String propertyName) {
        return getStringValue(propertyName, "UTF-8");
    }

    public byte[] getBinaryValue(String propertyName, String encoding){
        Object value = myProperties.get(propertyName);
        if (value == null) {
            return null;
        }
        if (value instanceof byte[]){
            return (byte[]) value;            
        }
        if (value instanceof String){
            String stringValue = (String) value;
            byte[] bytes = null;
            try {
                bytes = stringValue.getBytes(encoding);
            } catch (UnsupportedEncodingException e) {
                bytes = stringValue.getBytes();
            }
            return bytes;
        }
        return null;
    }

    public void putAllValues(SVNProperties properties){
        myProperties.putAll(properties.myProperties);        
    }

    public void putValue(SVNProperties properties, String propertyName){
        myProperties.put(propertyName, properties.myProperties.get(propertyName));
    }

    public byte[] getBinaryValue(String propertyName){
        return getBinaryValue(propertyName, "UTF-8");
    }

    public Iterator nameIterator() {
        return new Iterator() {

            Iterator keyIterator = myProperties.keySet().iterator();

            public void remove() {
                keyIterator.remove();
            }

            public boolean hasNext() {
                return keyIterator.hasNext();
            }

            public Object next() {
                return keyIterator.next();
            }
        };
    }

}
