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

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.io.dav.DAVElement;
import org.tmatesoft.svn.core.internal.io.dav.handlers.BasicDAVHandler;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXNotRecognizedException;
import org.xml.sax.SAXNotSupportedException;
import org.xml.sax.XMLReader;

/**
 * @author TMate Software Ltd.
 * @version 1.1.2
 */
public abstract class DAVRequest extends BasicDAVHandler {

    protected static final String NAME_ATTR = "name";
    protected static final String NAMESPACE_ATTR = "namespace";

    private static SAXParserFactory ourSAXParserFactory;
    private SAXParser mySAXParser;

    private DAVElement myRootElement;
    private Map myProperties;

    public DAVRequest() {
        init();
    }

    public DAVRequest(DAVElement rootElement, Map properties) {
        init();
        myRootElement = rootElement;
        myProperties = properties;
    }

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
            if (parentProperty != null) {
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
        if (cdata != null) {
            if (parent == getRootElement()) {
                put(getProperties(), element, cdata);
            } else {
                DAVElementProperty parentProperty = (DAVElementProperty) getProperties().get(parent);
                if (parentProperty == null) {
                    invalidXML();
                } else {
                    parentProperty.addChild(element, cdata);
                }
            }
        }
    }

    private void put(Map properties, DAVElement element, StringBuffer cdata) throws SVNException {
        DAVElementProperty elementProperty = (DAVElementProperty) properties.get(element);
        if (elementProperty == null) {
            invalidXML();
        } else {
            elementProperty.addValue(cdata.toString());
        }
    }

    protected abstract void initialize() throws SVNException;

    public void readInput(InputStream is) throws SVNException {
        if (mySAXParser == null) {
            try {
                mySAXParser = getSAXParserFactory().newSAXParser();
                XMLReader reader = mySAXParser.getXMLReader();
                reader.setContentHandler(this);
                reader.setDTDHandler(this);
                reader.setErrorHandler(this);
                reader.setEntityResolver(this);
                reader.parse(new InputSource(is));
                initialize();
            } catch (ParserConfigurationException e) {
                SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.RA_DAV_REQUEST_FAILED, e), e);
            } catch (SAXException e) {
                SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.RA_DAV_REQUEST_FAILED, e), e);
            } catch (IOException e) {
                SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.RA_DAV_REQUEST_FAILED, e), e);
            }
        }
    }


    private synchronized static SAXParserFactory getSAXParserFactory() {
        if (ourSAXParserFactory == null) {
            ourSAXParserFactory = SAXParserFactory.newInstance();
            try {
                ourSAXParserFactory.setFeature("http://xml.org/sax/features/namespaces", true);
            } catch (SAXNotRecognizedException e) {
            } catch (SAXNotSupportedException e) {
            } catch (ParserConfigurationException e) {
            }
            try {
                ourSAXParserFactory.setFeature("http://xml.org/sax/features/validation", false);
            } catch (SAXNotRecognizedException e) {
            } catch (SAXNotSupportedException e) {
            } catch (ParserConfigurationException e) {
            }
            try {
                ourSAXParserFactory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            } catch (SAXNotRecognizedException e) {
            } catch (SAXNotSupportedException e) {
            } catch (ParserConfigurationException e) {
            }
            ourSAXParserFactory.setNamespaceAware(true);
            ourSAXParserFactory.setValidating(false);
        }
        return ourSAXParserFactory;
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
            if (attributes != null) {
                myAttributes = attributes;
            }
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

        private void addChild(DAVElement element, StringBuffer cdata) throws SVNException {
            if (myValues != null || myChildren == null) {
                invalidXML();
            }
            put(myChildren, element, cdata);
        }
    }
}
