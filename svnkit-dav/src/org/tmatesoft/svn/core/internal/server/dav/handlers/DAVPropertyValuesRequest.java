/*
 * ====================================================================
 * Copyright (c) 2004-2007 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html.
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.core.internal.server.dav.handlers;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.tmatesoft.svn.core.internal.io.dav.DAVElement;
import org.xml.sax.Attributes;

/**
 * @author TMate Software Ltd.
 * @version 1.1.2
 */
public class DAVPropertyValuesRequest implements IDAVRequest {

    private DAVElement myRootElement;
    private Map myProperties;

    private Map getProperties() {
        if (myProperties == null) {
            myProperties = new HashMap();
        }
        return myProperties;
    }

    public void setRootElement(DAVElement rootElement) {
        myRootElement = rootElement;
    }

    public DAVElement getRootElement() {
        return myRootElement;
    }

    public Collection getElements() {
        return getProperties().keySet();
    }

    public void add(DAVElement element) {
        getProperties().put(element, null);
    }

    public void addChild(DAVElement parent, DAVElement element) {
        if (parent == myRootElement) {
            getProperties().put(element, null);
        } else {

        }

    }

    public void put(DAVElement element, String cdata) {
        Object[] entryValues = (Object[]) getProperties().get(element);
        ArrayList elementValues;
        if (entryValues == null) {
            elementValues = new ArrayList();
            elementValues.add(cdata);
            entryValues = new Object[]{elementValues, null};
            getProperties().put(element, entryValues);
        } else if (entryValues[0] == null) {
            elementValues = new ArrayList();
            elementValues.add(cdata);
            entryValues[0] = elementValues;
        } else {
            elementValues = (ArrayList) entryValues[0];
            elementValues.add(cdata);
        }
    }

    public void put(DAVElement element, Attributes attrs) {
        Object[] entryValues = (Object[]) getProperties().get(element);
        if (entryValues == null) {
            entryValues = new Object[]{null, attrs};
            getProperties().put(element, entryValues);
        } else {
            entryValues[1] = attrs;
        }
    }

    public String getFirstValue(DAVElement element) {
        ArrayList values = (ArrayList) getValues(element);
        return (String) values.get(0);
    }

    public Collection getValues(DAVElement element) {
        Object[] entryValues = (Object[]) getProperties().get(element);
        return (ArrayList) entryValues[0];
    }

    public String getAttributeValue(DAVElement element, String attributeName) {
        Attributes attrs = getAttributes(element);
        return attrs.getValue(attributeName);
    }

    public String getAttributeValue(DAVElement element, String attributeNamespace, String attributeName) {
        Attributes attrs = getAttributes(element);
        return attrs.getValue(attributeNamespace, attributeName);
    }

    private Attributes getAttributes(DAVElement element) {
        Object[] entryValues = (Object[]) getProperties().get(element);
        return (Attributes) entryValues[1];
    }

    public Iterator entryIterator() {
        return new Iterator() {
            Iterator myIterator = getProperties().entrySet().iterator();

            public void remove() {
                myIterator.remove();
            }

            public boolean hasNext() {
                return myIterator.hasNext();
            }

            public Object next() {
                Map.Entry mapEntry = (Map.Entry) myIterator.next();
                DAVElement element = (DAVElement) mapEntry.getKey();
                Object[] entryValue = (Object[]) mapEntry.getValue();
                ArrayList values;
                Attributes attrs;
                if (entryValue == null) {
                    values = null;
                    attrs = null;
                } else {
                    values = (ArrayList) entryValue[0];
                    attrs = (Attributes) entryValue[1];
                }
                return new Entry(element, values, attrs);
            }
        };
    }

    public boolean isEmpty() {
        return getProperties().isEmpty();
    }

    public int size() {
        return getProperties().size();
    }

    public class Entry implements IDAVRequest.Entry {

        DAVElement myDAVElement;
        ArrayList myValues;
        Attributes myAttributes;


        private Entry(DAVElement DAVElement, ArrayList values, Attributes attributes) {
            myDAVElement = DAVElement;
            myValues = values;
            myAttributes = attributes;
        }

        public DAVElement getElement() {
            return myDAVElement;
        }

        public String getFirstValue() {
            if (myValues == null) {
                return null;
            }
            return (String) myValues.get(0);
        }

        public Collection getValues() {
            return myValues;
        }

        public String getAttributeValue(String attributeName) {
            if (myAttributes == null) {
                return null;
            }
            return myAttributes.getValue(attributeName);
        }
    }
}
