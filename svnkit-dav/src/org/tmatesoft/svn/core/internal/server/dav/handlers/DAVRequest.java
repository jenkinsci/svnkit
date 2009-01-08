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
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.io.dav.DAVElement;
import org.tmatesoft.svn.core.internal.util.SVNHashMap;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.util.SVNDebugLog;
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

    private DAVElementProperty myRootElementProperty;
    private DAVElementProperty myCurrentElement;

    public DAVElementProperty getRootElement() {
        return myRootElementProperty;
    }

    protected String getRootElementAttributeValue(String name) {
        if (myRootElementProperty != null) {
            return myRootElementProperty.getAttributeValue(name);
        }
        return null;
    }

    public void startElement(DAVElement parent, DAVElement element, Attributes attrs) throws SVNException {
        if (parent == null) {
            myCurrentElement = new DAVElementProperty(element, null);
            myCurrentElement.setAttributes(attrs);
            myRootElementProperty = myCurrentElement;
        } else {
            myCurrentElement = myCurrentElement.addChild(element, attrs);
        }
    }

    public void endElement(DAVElement parent, DAVElement element, StringBuffer cdata) throws SVNException {
        if (myCurrentElement == null || element != myCurrentElement.getName()) {
            invalidXML();
        }
        
        if (cdata != null) {
            myCurrentElement.addValue(cdata.toString());
        }
        
        myCurrentElement = myCurrentElement.getParent();
        if (myCurrentElement == null && parent != null) {
            invalidXML();
        }
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
        private List myChildren;
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
        
        public void setElementName(DAVElement element) {
            myElementName = element;
        }
        
        public boolean hasChild(DAVElement element) {
            return getChild(element) != null;
        }
        
        public DAVElementProperty getChild(DAVElement element) {
            if (myChildren != null) {
                for (Iterator childrenIter = myChildren.iterator(); childrenIter.hasNext();) {
                    DAVElementProperty nextChild = (DAVElementProperty) childrenIter.next();
                    if (element == nextChild.getName()) {
                        return nextChild;
                    }
                }
            }
            return null;
        }
        
        public Map getAttributes() {
            return myAttributes;
        }
        
        private void addValue(String cdata) throws SVNException {
            if (myChildren != null) {
                invalidXML();
            } else if (myValues == null) {
                myValues = new ArrayList();
            }
            myValues.add(cdata);
        }

        protected String getFirstValue(boolean trim) {
            if (myValues != null) {
                String value = (String) myValues.get(0); 
                if (trim && value != null) {
                    value = value.trim();
                }
                return value;
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

        protected List getChildren() {
            return myChildren;
        }

        protected DAVElementProperty addChild(DAVElement element, Attributes attrs) throws SVNException {
            if (myValues != null) {
                invalidXML();
            } else if (myChildren == null) {
                myChildren = new LinkedList();
            }
            
            DAVElementProperty child = new DAVElementProperty(element, this);
            myChildren.add(child);
            child.setAttributes(attrs);
            return child;
        }

    }
}
