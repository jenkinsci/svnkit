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

import java.util.Collection;
import java.util.Iterator;

import org.tmatesoft.svn.core.internal.io.dav.DAVElement;
import org.xml.sax.Attributes;


/**
 * @author TMate Software Ltd.
 * @version 1.1.2
 */
public interface IDAVRequest {

    public void setRootElement(DAVElement rootElement);

    public DAVElement getRootElement();

    public Collection getElements();

    public void add(DAVElement element);

    public void put(DAVElement element, String cdata);

    public void put(DAVElement element, Attributes attrs);

    public String getFirstValue(DAVElement element);

    public Collection getValues(DAVElement element);

    public String getAttributeValue(DAVElement element, String attributeName);

    public String getAttributeValue(DAVElement element, String attributeNamespace, String attributeName);

    public Iterator entryIterator();

    public boolean isEmpty();

    public int size();

    interface Entry {

        public DAVElement getElement();

        public String getFirstValue();

        public Collection getValues();

        public String getAttributeValue(String attributeName);

    }

}
