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
import java.util.Arrays;


/**
 * @author TMate Software Ltd.
 * @version 1.1.1
 */
public class SVNPropertyValue {

    private String myValue;
    private byte[] myData;

    public static SVNPropertyValue create(String propertyName, byte[] data, int offset, int length) {
        if (data == null) {
            return null;
        }
        if (SVNProperty.isSVNProperty(propertyName)) {
            String value = null;
            try {
                value = new String(data, offset, length, "UTF-8");
            } catch (UnsupportedEncodingException e) {
                value = new String(data, offset, length);
            }
            return new SVNPropertyValue(value);
        }
        return new SVNPropertyValue(data, offset, length);
    }

    public static SVNPropertyValue create(String propertyName, byte[] data) {
        if (data == null) {
            return null;
        }
        return create(propertyName, data, 0, data.length);
    }

    public static SVNPropertyValue create(String propertyValue) {
        if (propertyValue == null){
            return null;            
        }
        return new SVNPropertyValue(propertyValue);
    }

    public static byte[] getPropertyAsBytes(SVNPropertyValue value) {
        if (value == null) {
            return null;
        }
        if (value.isString()) {
            try {
                return value.getString().getBytes("UTF-8");
            } catch (UnsupportedEncodingException e) {
                return value.getString().getBytes();
            }
        }
        return value.getBytes();
    }

    public static String getPropertyAsString(SVNPropertyValue value) {
        if (value == null) {
            return null;
        }
        if (value.isBinary()) {
            try {
                return new String(value.getBytes(), "UTF-8");
            } catch (UnsupportedEncodingException e) {
                return new String(value.getBytes());
            }
        }
        return value.getString();
    }

    private SVNPropertyValue(byte[] data, int offset, int length) {
        myData = new byte[length];
        System.arraycopy(data, offset, myData, 0, length);
    }

    private SVNPropertyValue(String propertyValue) {
        myValue = propertyValue;
    }

    public boolean isBinary() {
        return myData != null;
    }

    public byte[] getBytes() {
        return myData;
    }

    public boolean isString() {
        return myValue != null;
    }

    public String getString() {
        return myValue;
    }

    public String toString() {
        if (isBinary()) {
            return "property is binary";
        }
        return getString();
    }

    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }

        if (obj instanceof SVNPropertyValue) {
            SVNPropertyValue value = (SVNPropertyValue) obj;
            if (isString()) {
                return myValue.equals(value.getString());
            } else if (isBinary()) {
                return Arrays.equals(myData, getPropertyAsBytes(value));
            }
        }
        return false;
    }

    public int hashCode() {
        if (myValue != null) {
            return myValue.hashCode();
        }
        if (myData != null) {
            return myData.hashCode();
        }
        return super.hashCode();
    }
}