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

import org.tmatesoft.svn.core.internal.util.SVNEncodingUtil;


/**
 * @author TMate Software Ltd.
 * @version 1.1.1
 */
public class SVNPropertyValue {

    private String myName;
    private String myValue;
    private byte[] myData;

    public static SVNPropertyValue create(String propertyName, byte[] data, int offset, int length) {
        return new SVNPropertyValue(propertyName, data, offset, length);
    }

    public static SVNPropertyValue create(String propertyName, byte[] data) {
        return new SVNPropertyValue(propertyName, data);
    }

    public static SVNPropertyValue create(String propertyName, String propertyValue) {
        return new SVNPropertyValue(propertyName, propertyValue);
    }

    private SVNPropertyValue(String propertyName, byte[] data, int offset, int length) {
        myName = propertyName;
        if (data != null) {
            if (SVNProperty.isSVNProperty(myName)) {
                try {
                    myValue = new String(data, offset, length, "UTF-8");
                } catch (UnsupportedEncodingException e) {
                    myValue = new String(data, offset, length);
                }
            } else {
                myData = new byte[length];
                System.arraycopy(data, offset, myData, 0, length);
            }
        }
    }

    private SVNPropertyValue(String propertyName, byte[] data) {
        this(propertyName, data, 0, data == null ? -1 : data.length);
    }

    private SVNPropertyValue(String propertyName, String propertyValue) {
        myName = propertyName;
        myValue = propertyValue;
    }

    public static byte[] getPropertyAsBytes(SVNPropertyValue value) {
        if (value == null || value.hasNullValue()) {
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
        if (value == null || value.hasNullValue()) {
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

    public String getName() {
        return myName;
    }

    public SVNPropertyValue changePropertyName(String newName) {
        if (myName.equals(newName)) {
            return this;
        }
        if (isBinary()) {
            return create(newName, getBytes());
        }
        return create(newName, getString());
    }

    public byte[] getBytes() {
        return myData;
    }

    public String getString() {
        return myValue;
    }

    public boolean isBinary() {
        return myData != null;
    }

    public boolean isString() {
        return myValue != null;
    }

    public boolean hasNullValue() {
        return !isBinary() && !isString();
    }

    public boolean isXMLSafe() {
        if (isBinary()) {
            return false;
        }
        return SVNEncodingUtil.isXMLSafe(getString());
    }

    public String toString() {
        if (isBinary()) {
            return "property is binary";
        }
        return getString();
    }

    //TODO: compare properties names
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