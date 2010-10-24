/*
 * ====================================================================
 * Copyright (c) 2004-2010 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */

package org.tmatesoft.svn.core.internal.io.dav.handlers;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Stack;

import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.io.dav.DAVElement;
import org.tmatesoft.svn.core.internal.util.SVNHashMap;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.util.SVNDebugLog;
import org.tmatesoft.svn.util.SVNLogType;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;


/**
 * @author TMate Software Ltd.
 * @version 1.3
 */
public abstract class BasicDAVHandler extends DefaultHandler {

    protected static final Collection SVN_DAV_NAMESPACES_LIST = new LinkedList();
    protected static final Collection SVN_NAMESPACES_LIST = new LinkedList();
    protected static final Collection DAV_NAMESPACES_LIST = new LinkedList();

    static {
        SVN_DAV_NAMESPACES_LIST.add(DAVElement.SVN_NAMESPACE);
        SVN_DAV_NAMESPACES_LIST.add(DAVElement.DAV_NAMESPACE);
        SVN_NAMESPACES_LIST.add(DAVElement.SVN_NAMESPACE);
        DAV_NAMESPACES_LIST.add(DAVElement.DAV_NAMESPACE);
    }

    private static final Object ROOT = new Object();

    private Map myPrefixesMap;
    private List myNamespacesCollection;
    private String myNamespace;
    private StringBuffer myCDATA;
    private Stack myParent;
    private byte[] myDeltaBuffer;

    protected BasicDAVHandler() {
        myPrefixesMap = new SVNHashMap();
        myNamespacesCollection = new LinkedList();
        myParent = new Stack();
    }

    private void setNamespace(String uri) {
        if ("".equals(uri)) {
            myNamespace = null;
        } else {
            myNamespace = uri;
        }
    }

    protected void init() {
        myPrefixesMap.clear();
        myNamespacesCollection.clear();
        myParent.clear();
        myParent.push(ROOT);
    }

    public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
        setNamespace(uri);
        DAVElement element = getDAVElement(qName, localName, myNamespace);
        try {
            startElement(getParent(), element, attributes);
        } catch (SVNException e) {
            SVNDebugLog.getDefaultLog().logFine(SVNLogType.NETWORK, e);
            throw new SAXException(e);
        }
        myParent.push(element);
        myCDATA = new StringBuffer();
    }

    public void endElement(String uri, String localName, String qName) throws SAXException {
        myParent.pop();
        String namespace = uri != null && !"".equals(uri) ? uri : myNamespace;
        DAVElement element = getDAVElement(qName, localName, namespace);
        try {
            endElement(getParent(), element, myCDATA);
        } catch (SVNException e) {
            SVNDebugLog.getDefaultLog().logFine(SVNLogType.NETWORK, e);
            throw new SAXException(e);
        }
        myCDATA = null;
    }

    public void characters(char[] ch, int start, int length) throws SAXException {
        if (myCDATA != null) {
            myCDATA.append(ch, start, length);
        }
    }

    public void startPrefixMapping(String prefix, String uri) throws SAXException {
        Stack mappings = (Stack) myPrefixesMap.get(prefix);
        if (mappings == null) {
            mappings = new Stack();
            myPrefixesMap.put(prefix, mappings);
        }
        mappings.push(uri);
        
        if (!myNamespacesCollection.contains(uri)) {
            myNamespacesCollection.add(uri);
        }
    }

    public void endPrefixMapping(String prefix) throws SAXException {
        Stack mappings = (Stack) myPrefixesMap.get(prefix);
        if (mappings != null) {
            mappings.pop();
        }
    }

    protected abstract void startElement(DAVElement parent, DAVElement element, Attributes attrs) throws SVNException;

    protected abstract void endElement(DAVElement parent, DAVElement element, StringBuffer cdata) throws SVNException;

    protected void invalidXML() throws SVNException {
        SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.XML_MALFORMED, "Malformed XML"), SVNLogType.NETWORK);

    }
    
    protected List getNamespaces() {
        return myNamespacesCollection;
    }

    private DAVElement getParent() {
        Object parent = myParent.peek();
        if (parent == ROOT) {
            return null;
        }
        return (DAVElement) parent;
    }

    private DAVElement getDAVElement(String qName, String localName, String namespace) {
        if (qName == null || qName.trim().length() == 0) {
            qName = localName;
        }
        String prefix = namespace;
        int index = qName.indexOf(':');
        if (index >= 0) {
            prefix = qName.substring(0, index);
            Stack prefixes = (Stack) myPrefixesMap.get(prefix);
            if (prefixes != null && !prefixes.isEmpty()) {
                prefix = (String) prefixes.peek();
            }
            qName = qName.substring(index + 1);
        }
        return DAVElement.getElement(prefix, qName);
    }

    protected byte[] allocateBuffer(int length) {
        if (myDeltaBuffer == null || myDeltaBuffer.length < length) {
            myDeltaBuffer = new byte[length * 3 / 2];
        }
        return myDeltaBuffer;
    }

}