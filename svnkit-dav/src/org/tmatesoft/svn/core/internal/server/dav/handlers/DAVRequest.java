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
import java.util.Map;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.io.dav.DAVElement;
import org.tmatesoft.svn.core.internal.io.dav.handlers.BasicDAVHandler;
import org.xml.sax.Attributes;

/**
 * @author TMate Software Ltd.
 * @version 1.1.2
 */
public abstract class DAVRequest extends BasicDAVHandler {

    private DAVElement myRootElement;
    private Map myProperties;

    public DAVRequest() {
        init();
    }

    protected Map getProperties() {
        if (myProperties == null) {
            myProperties = new HashMap();
        }
        return myProperties;
    }

    protected void setRootElement(DAVElement rootElement) {
        myRootElement = rootElement;
    }

    protected DAVElement getRootElement() {
        return myRootElement;
    }

    protected void startElement(DAVElement parent, DAVElement element, Attributes attrs) throws SVNException {
        if (parent == null) {
            setRootElement(element);
        } else if (parent == getRootElement()) {
            put(getProperties(), element, attrs);
        } else {
            DAVElementProperty parentProperty = (DAVElementProperty) getProperties().get(parent);
            if (parentProperty == null) {
                invalidXML();
            } else {
                parentProperty.addChild(element, attrs);
            }
        }
    }

    private void put(Map properties, DAVElement element, Attributes attrs) {
        DAVElementProperty elementProperty = (DAVElementProperty) properties.get(element);
        boolean hasSuchElement = true;
        if (elementProperty == null) {
            elementProperty = new DAVElementProperty();
            hasSuchElement = false;
        }
        elementProperty.setAttributes(attrs);
        if (!hasSuchElement) {
            properties.put(element, elementProperty);
        }
    }

    protected void endElement(DAVElement parent, DAVElement element, StringBuffer cdata) throws SVNException {
        if (parent == null) {
            setRootElement(element);
        } else if (parent == getRootElement()) {
            put(getProperties(), element, cdata.toString());
        } else {
            DAVElementProperty parentProperty = (DAVElementProperty) getProperties().get(parent);
            if (parentProperty == null) {
                invalidXML();
            } else {
                parentProperty.addChild(element, cdata.toString());
            }
        }
    }

    private void put(Map properties, DAVElement element, String cdata) throws SVNException {
        DAVElementProperty elementProperty = (DAVElementProperty) properties.get(element);
        boolean hasSuchElement = true;
        if (elementProperty == null) {
            elementProperty = new DAVElementProperty();
            hasSuchElement = false;
        }
        elementProperty.addValue(cdata);
        if (!hasSuchElement) {
            properties.put(element, elementProperty);
        }
    }

    protected class DAVElementProperty {

        ArrayList myValues;
        Attributes myAttributes;
        Map myChildren;

        private void addValue(String cdata) throws SVNException {
            if (myChildren != null) {
                invalidXML();
            } else if (myValues == null) {
                myValues = new ArrayList();
            }
            myValues.add(cdata);
        }

        protected String getFirstValue() {
            if (myValues != null) {
                return (String) myValues.get(0);
            }
            return null;
        }

        protected Collection getValues() {
            return myValues;
        }

        protected String getAttributeValue(String name) {
            if (myAttributes != null) {
                return myAttributes.getValue(name);
            }
            return null;
        }

        protected String getAttributeValue(String namespace, String name) {
            return myAttributes.getValue(namespace, name);
        }

        private void setAttributes(Attributes attributes) {
            myAttributes = attributes;
        }

        protected Map getChildren() {
            return myChildren;
        }

        private void addChild(DAVElement element, Attributes attrs) throws SVNException {
            if (myValues != null) {
                invalidXML();
            } else if (myChildren == null) {
                myChildren = new HashMap();
            }
            put(myChildren, element, attrs);
        }

        private void addChild(DAVElement element, String cdata) throws SVNException {
            if (myValues != null) {
                invalidXML();
            } else if (myChildren == null) {
                myChildren = new HashMap();
            }
            put(myChildren, element, cdata);
        }
    }
}
