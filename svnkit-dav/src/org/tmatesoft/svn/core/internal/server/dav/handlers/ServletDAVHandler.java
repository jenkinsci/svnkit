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

import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.io.dav.handlers.BasicDAVHandler;
import org.tmatesoft.svn.core.internal.server.dav.DAVDepth;
import org.tmatesoft.svn.core.internal.server.dav.DAVRepositoryManager;
import org.tmatesoft.svn.core.internal.server.dav.DAVResource;
import org.tmatesoft.svn.core.internal.util.SVNEncodingUtil;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXNotRecognizedException;
import org.xml.sax.SAXNotSupportedException;
import org.xml.sax.XMLReader;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;


/**
 * @author TMate Software Ltd.
 * @version 1.1.2
 */
public abstract class ServletDAVHandler extends BasicDAVHandler {


    protected static final int SC_MULTISTATUS = 207;
    protected static final int SC_NOT_FOUND = 404;

    protected static final String HTTP_STATUS_OK_STRING = "HTTP/1.1 200 OK";

    protected static final int XML_STYLE_NORMAL = 1;
    protected static final int XML_STYLE_PROTECT_PCDATA = 2;
    protected static final int XML_STYLE_SELF_CLOSING = 4;

    protected static final String LABEL_HEADER = "label";
    private static final String DEPTH_HEADER = "Depth";

    protected static final String XML_HEADER = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n";
    protected static final String DAV_NAMESPACE_PREFIX = "D";


    private static SAXParserFactory ourSAXParserFactory;
    private SAXParser mySAXParser;

    private DAVRepositoryManager myRepositoryManager = null;
    private HttpServletRequest myRequest;
    private HttpServletResponse myResponse;

    protected ServletDAVHandler(DAVRepositoryManager connector, HttpServletRequest request, HttpServletResponse response) {
        init();
        myRepositoryManager = connector;
        myRequest = request;
        myResponse = response;
    }

    public abstract void execute() throws SVNException;

    protected String getRequestURI() {
        return myRequest.getRequestURI();
    }

    protected String getRequestContext() {
        return myRequest.getContextPath();
    }

    protected String getRequestHeader(String name) {
        return myRequest.getHeader(name);
    }

    protected InputStream getRequestInputStream() throws SVNException {
        try {
            return myRequest.getInputStream();
        } catch (IOException e) {
            SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.IO_ERROR, e), e);
        }
        return null;
    }

    protected void setResponseHeader(String name, String value) {
        myResponse.setHeader(name, value);
    }

    protected void setResponseStatus(int statusCode) {
        myResponse.setStatus(statusCode);
    }

    protected Writer getResponseWriter() throws SVNException {
        try {
            return myResponse.getWriter();
        } catch (IOException e) {
            SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.IO_ERROR, e), e);
        }
        return null;
    }

    protected DAVRepositoryManager getRepositoryManager() {
        return myRepositoryManager;
    }

    protected DAVDepth getRequestDepth(DAVDepth defaultDepth) throws SVNException {
        String depth = getRequestHeader(DEPTH_HEADER);
        if (depth == null) {
            return defaultDepth;
        }
        DAVDepth result = DAVDepth.parseDepth(depth);
        if (result == null) {
            SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.RA_DAV_MALFORMED_DATA, "Invalid depth ''{0}''", depth));
        }
        return result;
    }

    protected void setDefaultResponseHeaders(DAVResource resource) {
        myResponse.setContentType("text/xml; charset=UTF-8");
        myResponse.setHeader("Accept-Ranges", "bytes");
    }

    protected StringBuffer appendXMLHeader(String prefix, String header, Set namespaces, StringBuffer target) {
        target.append("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n");
        target.append("<");
        target.append(prefix);
        target.append(":");
        target.append(header);
        target.append(" xmlns:");
        target.append(DAV_NAMESPACE_PREFIX);
        target.append("=");
        target.append("\"DAV:\"");
        if (namespaces != null) {
            int i = 0;
            for (Iterator iterator = namespaces.iterator(); iterator.hasNext(); i++) {
                target.append(" xmlns:ns");
                target.append(i);
                target.append("=\"");
                target.append(iterator.next().toString());
                target.append("\"");
            }
        }
        target.append(">\n");
        return target;
    }

    protected StringBuffer appendXMLFooter(String prefix, String header, int style, StringBuffer target) {
        target.append("</");
        target.append(prefix);
        target.append(":");
        target.append(header);
        target.append(">");
        return target;
    }

    protected StringBuffer openNamespaceTag(String prefix, String tagName, int style, Map namespaces, StringBuffer target) {
        target = target == null ? new StringBuffer() : target;
        target.append("<");
        target.append(prefix);
        target.append(":");
        target.append(tagName);
        for (Iterator iterator = namespaces.keySet().iterator(); iterator.hasNext();) {
            String currentNamespace = iterator.next().toString();
            target.append(" xmlns:");
            target.append(namespaces.get(currentNamespace).toString());
            target.append("=\"");
            target.append(currentNamespace);
            target.append("\"");
        }
        if (style == XML_STYLE_SELF_CLOSING) {
            target.append("/");
        }
        target.append(">");
        return target;
    }

    protected StringBuffer openCDataTag(String prefix, String tagName, String cdata, StringBuffer target) {
        if (cdata == null) {
            return target;
        }
        target = openXMLTag(prefix, tagName, XML_STYLE_PROTECT_PCDATA, null, target);
        target.append(SVNEncodingUtil.xmlEncodeCDATA(cdata));
        target = closeXMLTag(prefix, tagName, target);
        return target;
    }

    protected StringBuffer openXMLTag(String prefix, String tagName, int style, String attr, String value, StringBuffer target) {
        HashMap attributes = new HashMap();
        attributes.put(attr, value);
        return openXMLTag(prefix, tagName, style, attributes, target);
    }

    protected StringBuffer openXMLTag(String prefix, String tagName, int style, Map attributes, StringBuffer target) {
        target = target == null ? new StringBuffer() : target;
        target.append("<");
        target.append(prefix);
        target.append(":");
        target.append(tagName);
        if (attributes != null) {
            for (Iterator iterator = attributes.keySet().iterator(); iterator.hasNext();) {
                String name = (String) iterator.next();
                String value = (String) attributes.get(name);
                target.append(name);
                target.append("=\"");
                target.append(SVNEncodingUtil.xmlEncodeAttr(value));
            }
            attributes.clear();
        }
        if (style == XML_STYLE_SELF_CLOSING) {
            target.append("/");
        }
        target.append(">");       
        return target;
    }

    protected StringBuffer closeXMLTag(String prefix, String tagName, StringBuffer target) {
        target = target == null ? new StringBuffer() : target;
        target.append("</");
        target.append(prefix);
        target.append(":");
        target.append(tagName);
        target.append(">\n");
        return target;
    }

    protected String addHrefTags(String uri){
        StringBuffer tmpBuffer = new StringBuffer();
        openXMLTag(DAV_NAMESPACE_PREFIX, "href", XML_STYLE_NORMAL, null, tmpBuffer);
        tmpBuffer.append(uri);
        closeXMLTag(DAV_NAMESPACE_PREFIX, "href", tmpBuffer);
        return tmpBuffer.toString();
    }

    protected void readInput(InputStream is) throws SVNException {
        if (mySAXParser == null) {
            try {
                mySAXParser = getSAXParserFactory().newSAXParser();
                XMLReader reader = mySAXParser.getXMLReader();
                reader.setContentHandler(this);
                reader.setDTDHandler(this);
                reader.setErrorHandler(this);
                reader.setEntityResolver(this);
                reader.parse(new InputSource(is));
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


}
