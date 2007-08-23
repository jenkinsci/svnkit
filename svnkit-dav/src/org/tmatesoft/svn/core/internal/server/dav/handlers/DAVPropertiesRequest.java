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
import java.util.Iterator;

import org.tmatesoft.svn.core.internal.io.dav.DAVElement;
import org.xml.sax.Attributes;

/**
 * @author TMate Software Ltd.
 * @version 1.1.2
 */
public class DAVPropertiesRequest implements IDAVRequest {

    DAVElement myRootElement;
    Collection myDAVElements;

    public void setRootElement(DAVElement rootElement) {
        myRootElement = rootElement;
    }

    public DAVElement getRootElement() {
        return myRootElement;
    }

    public Collection getElements() {
        if (myDAVElements == null) {
            myDAVElements = new ArrayList();
        }
        return myDAVElements;
    }

    public void add(DAVElement element) {
        getElements().add(element);
    }

    public void put(DAVElement element, String cdata) {
        add(element);
    }

    public void put(DAVElement element, Attributes attrs) {
        add(element);
    }

    public String getFirstValue(DAVElement element) {
        return null;
    }

    public Collection getValues(DAVElement element) {
        return null;
    }

    public String getAttributeValue(DAVElement element, String attributeName) {
        return null;
    }


    public String getAttributeValue(DAVElement element, String attributeNamespace, String attributeName) {
        return null;
    }

    public Iterator entryIterator() {
        return new Iterator() {

            private Iterator myIterator = getElements().iterator();

            public void remove() {
                myIterator.remove();
            }

            public boolean hasNext() {
                return myIterator.hasNext();
            }

            public Object next() {
                DAVElement nextElement = (DAVElement) myIterator.next();
                return new Entry(nextElement);
            }

        };
    }

    public boolean isEmpty() {
        return getElements().isEmpty();
    }

    public int size() {
        return getElements().size();
    }

    public class Entry implements IDAVRequest.Entry {

        DAVElement myDAVElement;

        private Entry(DAVElement element) {
            myDAVElement = element;
        }

        public DAVElement getElement() {
            return myDAVElement;
        }

        public String getFirstValue() {
            return null;
        }

        public Collection getValues() {
            return null;
        }

        public String getAttributeValue(String attributeName) {
            return null;
        }
    }

}
