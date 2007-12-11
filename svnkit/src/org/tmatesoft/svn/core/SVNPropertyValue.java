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
import org.tmatesoft.svn.core.internal.util.SVNEncodingUtil;


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
            } else if (myData != null) {
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

    public boolean isXMLSafe(String encoding) {
        if (isBinary()) {
            return false;
        }
        if (encoding != null) {
            return SVNEncodingUtil.isXMLSafe(getString(encoding));
        }
        return SVNEncodingUtil.isXMLSafe(getString(encoding));
    }

    public byte[] getBytes(String encoding) {
        if (myData == null) {
            if (myValue != null) {
                if (encoding == null) {
                    myData = myValue.getBytes();
                } else {
                    try {
                        myData = myValue.getBytes(encoding);
                    } catch (UnsupportedEncodingException e) {
                        myData = myValue.getBytes();
                    }
                }
            }
        }
        return myData;
    }

    public byte[] getBytes() {
        return getBytes("UTF-8");
    }

    public String getString(String encoding) {
        if (myValue == null) {
            if (!isBinary() && myData != null) {
                try {
                    myValue = new String(myData, encoding);
                } catch (UnsupportedEncodingException e) {
                    myValue = new String(myData);
                }
            }
        }
        return myValue;
    }

    public String getString() {
        return getString("UTF-8");
    }

    public String toString() {
        if (isBinary()) {
            return "property is binary";
        }
        return getString();
    }

    public SVNPropertyValue trim() {
        if (!isBinary()) {
            if (myValue != null) {
                return new SVNPropertyValue(myValue.trim());
            }
        }
        return this;
    }

    public SVNPropertyValue replace(char oldChar, char newChar) {
        if (!isBinary()) {
            if (getString() != null) {
                return new SVNPropertyValue(getString().replace(oldChar, newChar));
            }
        }
        return this;
    }

    public SVNPropertyValue replaceAll(String regex, String replacement) {
        if (!isBinary()) {
            if (getString() != null) {
                return new SVNPropertyValue(getString().replaceAll(regex, replacement));
            }
        }
        return this;
    }

    public boolean endsWith(String suffix) {
        if (!isBinary()) {
            if (getString() != null) {
                return getString().endsWith(suffix);
            }
        }
        return false;
    }

    public SVNPropertyValue append(String str) {
        if (!isBinary()) {
            if (getString() != null) {
                return new SVNPropertyValue(getString().concat(str));
            }
        }
        return this;
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
        if (myValue != null) {
            return myValue.hashCode();
        }
        if (myData != null) {
            return myData.hashCode();
        }
        return super.hashCode();
    }
}