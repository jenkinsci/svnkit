/*
 * ====================================================================
 * Copyright (c) 2004-2008 TMate Software Ltd.  All rights reserved.
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
import java.util.Map;

import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.io.dav.DAVElement;
import org.tmatesoft.svn.core.internal.util.SVNHashMap;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.util.SVNLogType;

import org.xml.sax.Attributes;

/**
 * @author TMate Software Ltd.
 * @version 1.2.0
 */
public abstract class DAVRequest {

    protected static final DAVElement PATH = DAVElement.getElement(DAVElement.SVN_NAMESPACE, "path");
    protected static final DAVElement REVISION = DAVElement.getElement(DAVElement.SVN_NAMESPACE, "revision");
    protected static final DAVElement START_REVISION = DAVElement.getElement(DAVElement.SVN_NAMESPACE, "start-revision");
    protected static final DAVElement END_REVISION = DAVElement.getElement(DAVElement.SVN_NAMESPACE, "end-revision");

    protected static final String NAME_ATTR = "name";
    protected static final String NAMESPACE_ATTR = "namespace";

    private DAVElement myRootElement;
    private DAVElementProperty myRootElementProperty;
    private DAVElementProperty myCurrentElement;
    private Map myRootElementAttributes;
    private Map myProperties;

    
    protected Map getProperties() {
        if (myProperties == null) {
            myProperties = new SVNHashMap();
        }
        return myProperties;
    }


	protected DAVElementProperty getProperty(Map properties, DAVElement element) {
        return (DAVElementProperty) properties.get(element);
    }
    public void setRootElement(DAVElement rootElement, DAVElementProperty rootElementProperty) {
        myRootElement = rootElement;
        myRootElementProperty = rootElementProperty;
    }

    public DAVElement getRootElement() {
        return myRootElement;
    }

    protected String getRootElementAttributeValue(String name) {
        if (myRootElementAttributes != null) {
            return (String) myRootElementAttributes.get(name);
        }
        return null;
    }

    private void setRootElementAttributes(Attributes rootElementAttributes) {
        myRootElementAttributes = getAttributesMap(rootElementAttributes);
    }

    public void startElement(DAVElement parent, DAVElement element, Attributes attrs) throws SVNException {
        if (parent == null) {
            myCurrentElement = new DAVElementProperty(element, null);
            myCurrentElement.setAttributes(attrs);
            setRootElement(element, myCurrentElement);
            setRootElementAttributes(attrs);
        } else {
            myCurrentElement = myCurrentElement.addChild(element, attrs);
        }
        
        
/*        
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
        */
    }

    public void endElement(DAVElement parent, DAVElement element, StringBuffer cdata) throws SVNException {
        if (cdata != null) {
            myCurrentElement.addCDATAToChild(element, cdata);
        }
        
        myCurrentElement = myCurrentElement.getParent();
        
/*        if (cdata != null) {
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
        */
    }

    protected abstract void init() throws SVNException;

    protected void invalidXML() throws SVNException {
        SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.XML_MALFORMED, "Malformed XML"), SVNLogType.NETWORK);
    }

    protected void invalidXML(DAVElement element) throws SVNException {
        SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.XML_MALFORMED, "\"The request's ''{0}'' element is malformed; there is a problem with the client.", element.getName()), SVNLogType.NETWORK);
    }

    protected void assertNullCData(DAVElement element, DAVElementProperty property) throws SVNException {
        if (property.getValues() == null) {
            invalidXML(element);
        }
    }

    private Map getAttributesMap(Attributes attrs) {
        Map attributes = null;
        if (attrs.getLength() != 0) {
            attributes = new SVNHashMap();
            for (int i = 0; i < attrs.getLength(); i++) {
                attributes.put(attrs.getQName(i), attrs.getValue(i));
            }
        }
        return attributes;
    }

    protected class DAVElementProperty {

        private ArrayList myValues;
        private Map myAttributes;
        private Map myChildren;
        private DAVElement myElementName;
        private DAVElementProperty myParent;
        
        public DAVElementProperty(DAVElement elementName, DAVElementProperty parent) {
            myElementName = elementName;
            myParent = parent;
        }
        
        public DAVElementProperty getParent() {
            return myParent;
        }
        
        public DAVElement getName() {
            return myElementName;
        }
        
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
                return (String) myAttributes.get(name);
            }
            return null;
        }

        private void setAttributes(Attributes attributes) {
            myAttributes = getAttributesMap(attributes);
        }

        protected Map getChildren() {
            return myChildren;
        }

        protected DAVElementProperty addChild(DAVElement element, Attributes attrs) throws SVNException {
            if (myValues != null) {
                invalidXML();
            } else if (myChildren == null) {
                myChildren = new SVNHashMap();
            }
            
            DAVElementProperty child = (DAVElementProperty) myChildren.get(element);
            if (child == null) {
                child = new DAVElementProperty(element, this);
                myChildren.put(element, child);
            }
            child.setAttributes(attrs);
            return child;
        }

        protected void addCDATAToChild(DAVElement element, StringBuffer cdata) throws SVNException {
            if (myValues != null || myChildren == null) {
                invalidXML();
            }

            DAVElementProperty elementProperty = (DAVElementProperty) myChildren.get(element);
            if (elementProperty == null) {
                invalidXML();
            } else {
                elementProperty.addValue(cdata.toString());
            }
        }
        
/*        
        protected DAVElementProperty addChild(DAVElement element, StringBuffer cdata) throws SVNException {
            if (myValues != null || myChildren == null) {
                invalidXML();
            }
            DAVElementProperty child = put(myChildren, element, cdata);
            child.setParent(this);
            return child;
        }
*/        
    }
}
