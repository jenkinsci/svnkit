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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.io.InputStream;

import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;


/**
 * @author TMate Software Ltd.
 * @version 1.1.1
 */
public class SVNPropertyValue {

    public static final SVNPropertyValue TRUE = new SVNPropertyValue(Boolean.TRUE.toString());
    public static final SVNPropertyValue FALSE = new SVNPropertyValue(Boolean.FALSE.toString());

    private String myValue;
    private byte[] myData;
    private Boolean myIsBinary;

    public SVNPropertyValue(byte[] data, int offset, int length) {
        myData = new byte[length];
        System.arraycopy(data, offset, myData, 0, length);
    }

    public SVNPropertyValue(byte[] data) {
        this(data, 0, data.length);
    }

    public SVNPropertyValue(String value) {
        myValue = value;
    }

    public boolean isBinary() {
        if (myIsBinary == null) {
            if (myValue != null) {
                myIsBinary = Boolean.FALSE;
            } else if (myData != null){
                try {
                    InputStream is = new ByteArrayInputStream(myData);
                    myIsBinary = Boolean.valueOf(SVNProperty.isBinaryMimeType(SVNFileUtil.detectMimeType(is)));
                } catch (IOException e) {
                    myIsBinary = Boolean.TRUE;
                }
            } else {
                myIsBinary = Boolean.FALSE;
            }
        }
        return myIsBinary.booleanValue();
    }

    public byte[] getBytes() {
        if (myData == null) {
            if (myValue != null) {
                try {
                    myData = myValue.getBytes("UTF-8");
                } catch (UnsupportedEncodingException e) {
                    myData = myValue.getBytes();
                }
            }
        }
        return myData;
    }

    public String getString() {
        if (myValue == null) {
            if (!isBinary() && myData != null) {
                try {
                    myValue = new String(myData, "UTF-8");
                } catch (UnsupportedEncodingException e) {
                    myValue = new String(myData);
                }
            }
        }
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
            if (myValue != null) {
                return myValue.equals(value.myValue);
            } else if (myData != null) {
                return myData.equals(value.myData);
            }
        }
        return false;
    }

    public int hashCode() {
        if (myValue != null){
            return myValue.hashCode();
        }
        if (myData != null){
            return myData.hashCode();            
        }
        return super.hashCode();
    }
}