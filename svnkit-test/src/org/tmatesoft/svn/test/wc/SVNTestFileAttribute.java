/*
 * ====================================================================
 * Copyright (c) 2004-2008 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.test.wc;

import java.io.UnsupportedEncodingException;
import java.util.Arrays;
import java.util.Iterator;

import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNProperties;
import org.tmatesoft.svn.core.SVNPropertyValue;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.test.SVNTestErrorCode;
import org.tmatesoft.svn.util.SVNLogType;

/**
 * @author TMate Software Ltd.
 * @version 1.2.0
 */
public class SVNTestFileAttribute {

    public static final int ALL_ENTRIES = ~0;
    public static final int NONE = 0;

    public static final int PATH = 0x00001;
    public static final int CONTENT = 0x00002;
    public static final int FILE_TYPE = 0x00004;
    public static final int LINK_TARGET = 0x00008;
    public static final int VERSIONED_NODE_KIND = 0x00010;
    public static final int BASE_PROPERTIES = 0x00020;
    public static final int PROPERTIES = 0x00040;
    public static final int CONFLICTED = 0x00080;
    public static final int SCHEDULE = 0x00100;
    public static final int COPY_FROM_LOCATION = 0x00200;
    public static final int COPY_FROM_URL = 0x00400;
    public static final int VERSIONED = 0x00800;

    public static final String PATH_ATTRIBUTE = "path";
    public static final String CONTENT_ATTRIBUTE = "content";
    public static final String FILE_TYPE_ATTRIBUTE = "file type";
    public static final String LINK_TARGET_ATTRIBUTE = "link target";
    public static final String VERSIONED_NODE_KIND_ATTRIBUTE = "versioned node kind";
    public static final String BASE_PROPERTIES_ATTRIBUTE = "base properties";
    public static final String PROPERTIES_ATTRIBUTE = "properties";
    public static final String CONFLICTED_ATTRIBUTE = "conflicted";
    public static final String SCHEDULE_ATTRIBUTE = "schedule";
    public static final String COPY_FROM_LOCATION_ATTRIBUTE = "copy from location";
    public static final String COPY_FROM_REVISION_ATTRIBUTE = "copy from revision";
    public static final String VERSIONED_ATTRIBUTE = "versioned";

    public static String getAttributeAsString(String name, Object value) {
        if (value == null) {
            return "[NULL]";
        }
        if (CONTENT_ATTRIBUTE.equals(name)) {
            try {
                return new String((byte[]) value, "UTF-8");
            } catch (UnsupportedEncodingException e) {
                return new String((byte[]) value);
            }
        }
        if (PROPERTIES_ATTRIBUTE.equals(name) || BASE_PROPERTIES_ATTRIBUTE.equals(name)) {
            StringBuffer buffer = new StringBuffer();
            SVNProperties props = (SVNProperties) value;
            buffer.append("[ ");
            for (Iterator iterator = props.nameSet().iterator(); iterator.hasNext();) {
                String propName = (String) iterator.next();
                SVNPropertyValue propValue = props.getSVNPropertyValue(name);
                buffer.append(propName);
                buffer.append(" = ");
                buffer.append(SVNPropertyValue.getPropertyAsString(propValue));
                buffer.append("; ");
            }
            buffer.append(" ]");
            return buffer.toString();
        }

        return value.toString();
    }

    private String myName;
    private Object myValue;

    public SVNTestFileAttribute(String name, Object value) {
        myName = name;
        myValue = value;
    }

    public Object getValue() {
        return myValue;
    }

    public String getName() {
        return myName;
    }

    public boolean equals(SVNTestFileAttribute attribute) throws SVNException {
        if (attribute == null) {
            return false;
        }
        if (!getName().equals(attribute.getName())) {
            SVNErrorMessage error = SVNErrorMessage.create(SVNTestErrorCode.UNKNOWN, "attributes should have similar names to be compared");
            SVNErrorManager.error(error, SVNLogType.DEFAULT);
        }
        if (getValue() == null) {
            return attribute.getValue() == null;
        }
        if (CONTENT_ATTRIBUTE.equals(getName())) {
            return Arrays.equals((byte[]) getValue(), (byte[]) attribute.getValue());
        }
        return getValue().equals(attribute.getValue());
    }
}
