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

import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.io.dav.DAVElement;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.xml.sax.Attributes;

/**
 * @author TMate Software Ltd.
 * @version 1.1.2
 */
public abstract class DAVRequest {

    protected static final DAVElement PATH = DAVElement.getElement(DAVElement.SVN_NAMESPACE, "path");
    protected static final DAVElement REVISION = DAVElement.getElement(DAVElement.SVN_NAMESPACE, "revision");
    protected static final DAVElement START_REVISION = DAVElement.getElement(DAVElement.SVN_NAMESPACE, "start-revision");
    protected static final DAVElement END_REVISION = DAVElement.getElement(DAVElement.SVN_NAMESPACE, "end-revision");

    protected static final String NAME_ATTR = "name";
    protected static final String NAMESPACE_ATTR = "namespace";

    private DAVElement myRootElement;
    private Attributes myRootElementAttributes;
    private Map myProperties;

    protected Map getProperties() {
        if (myProperties == null) {
            myProperties = new HashMap();
        }
        return myProperties;
    }

    protected DAVElementProperty getProperty(Map properties, DAVElement element) {
        return (DAVElementProperty) properties.get(element);
    }

    protected Collection getElements(Map properties) {
        return properties.keySet();
    }

    protected Collection getElements() {
        return getElements(getProperties());
    }

    public void setRootElement(DAVElement rootElement) {
        myRootElement = rootElement;
    }

    public DAVElement getRootElement() {
        return myRootElement;
    }

    protected Attributes getRootElementAttributes() {
        return myRootElementAttributes;
    }

    private void setRootElementAttributes(Attributes rootElementAttributes) {
        myRootElementAttributes = rootElementAttributes;
    }

    public void startElement(DAVElement parent, DAVElement element, Attributes attrs) throws SVNException {
        if (parent == null) {
            setRootElement(element);
            setRootElementAttributes(attrs);
        } else if (parent == getRootElement()) {
            put(getProperties(), element, attrs);
        } else {
            DAVElementProperty parentProperty = (DAVElementProperty) getProperties().get(parent);
            if (parentProperty != null) {
                parentProperty.addChild(element, attrs);
            }
        }
    }

    public void endElement(DAVElement parent, DAVElement element, StringBuffer cdata) throws SVNException {
        if (cdata != null) {
            if (parent == getRootElement()) {
                put(getProperties(), element, cdata);
            } else if (parent != null) {
                DAVElementProperty parentProperty = (DAVElementProperty) getProperties().get(parent);
                if (parentProperty == null) {
                    invalidXML();
                } else {
                    parentProperty.addChild(element, cdata);
                }
            }
        }
    }

    protected void put(Map properties, DAVElement element, Attributes attrs) {
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

    protected void put(Map properties, DAVElement element, StringBuffer cdata) throws SVNException {
        DAVElementProperty elementProperty = (DAVElementProperty) properties.get(element);
        if (elementProperty == null) {
            invalidXML();
        } else {
            elementProperty.addValue(cdata.toString());
        }
    }

    protected abstract void init() throws SVNException;

    protected void invalidXML() throws SVNException {
        SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.XML_MALFORMED, "Malformed XML"));

    }

    protected class DAVElementProperty {

        private ArrayList myValues;
        private Attributes myAttributes;
        private Map myChildren;

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
            if (attributes != null) {
                myAttributes = attributes;
            }
        }

        protected Map getChildren() {
            return myChildren;
        }

        protected void addChild(DAVElement element, Attributes attrs) throws SVNException {
            if (myValues != null) {
                invalidXML();
            } else if (myChildren == null) {
                myChildren = new HashMap();
            }
            put(myChildren, element, attrs);
        }

        protected void addChild(DAVElement element, StringBuffer cdata) throws SVNException {
            if (myValues != null || myChildren == null) {
                invalidXML();
            }
            put(myChildren, element, cdata);
        }
    }
}
