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
import org.tmatesoft.svn.core.internal.io.dav.DAVElement;
import org.tmatesoft.svn.core.internal.io.dav.handlers.BasicDAVHandler;
import org.tmatesoft.svn.core.internal.server.dav.DAVDepth;
import org.tmatesoft.svn.core.internal.server.dav.DAVRepositoryManager;
import org.tmatesoft.svn.core.internal.server.dav.DAVResource;
import org.tmatesoft.svn.core.internal.server.dav.TimeFormatUtil;
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
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;


/**
 * @author TMate Software Ltd.
 * @version 1.1.2
 */
public abstract class ServletDAVHandler extends BasicDAVHandler {

    protected static final int SC_OK = 200;
    protected static final int SC_MULTISTATUS = 207;

    protected static final String HTTP_STATUS_OK_LINE = "HTTP/1.1 200 OK";
    protected static final String HTTP_NOT_FOUND_LINE = "HTTP/1.1 404 NOT FOUND";

    protected static final String DEFAULT_XML_CONTENT_TYPE = "text/html; charset=utf-8";
    protected static final String DEFAULT_COLLECTION_CONTENT_TYPE = "text/html; charset=utf-8";
    protected static final String DEFAULT_FILE_CONTENT_TYPE = "text/plain";

    protected static final int XML_STYLE_NORMAL = 1;
    protected static final int XML_STYLE_PROTECT_PCDATA = 2;
    protected static final int XML_STYLE_SELF_CLOSING = 4;

    protected static final String LAST_MODIFIED_HEADER = "Last-Modified";
    protected static final String LABEL_HEADER = "Label";
    private static final String DEPTH_HEADER = "Depth";
    private static final String VARY_HEADER = "Vary";
    private static final String USER_AGENT_HEADER = "User-Agent";
    protected static final String ETAG_HEADER = "ETag";    

    protected static final String XML_HEADER = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n";

    protected static final String DAV_NAMESPACE_PREFIX = "D";
    protected static final String SVN_DAV_PROPERTY_PREFIX = "V";
    protected static final String SVN_CUSTOM_PROPERTY_PREFIX = "C";
    protected static final String SVN_SVN_PROPERTY_PREFIX = "S";


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

    protected void addResponseHeader(String name, String value){
        myResponse.addHeader(name, value);        
    }

    protected void setResponseStatus(int statusCode) {
        myResponse.setStatus(statusCode);
    }

    protected void setResponseContentType(String contentType){
        myResponse.setContentType(contentType);
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

    protected void gatherRequestHeadersInformation(DAVResource resource) {
        String userAgent = getRequestHeader(USER_AGENT_HEADER);
        boolean isSVNClient = userAgent != null && "SVN/".equals(userAgent);
        resource.setSVNClient(isSVNClient);
    }

    protected void setDefaultResponseHeaders(DAVResource resource) {
        myResponse.setContentType("text/xml; charset=UTF-8");
        setResponseHeader("Accept-Ranges", "bytes");
        try {
            Date lastModifiedTime = resource.getLastModified();
            if (lastModifiedTime != null){
                setResponseHeader(LAST_MODIFIED_HEADER, TimeFormatUtil.formatDate(lastModifiedTime));
            }
        } catch (SVNException e) {
            //nothing to do we just skip this header
        }
        if (resource.getETag() != null) {
            setResponseHeader(ETAG_HEADER, resource.getETag());
        }
        if (getRequestHeader(LABEL_HEADER) != null && getRequestHeader(LABEL_HEADER).length() > 0) {
            setResponseHeader(VARY_HEADER, LABEL_HEADER);
        }
    }

    protected StringBuffer appendXMLHeader(String prefix, String header, Collection incomingDAVElements, StringBuffer target) {
        target.append("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n");
        target.append("<");
        target.append(prefix);
        target.append(":");
        target.append(header);
        target.append(" xmlns:");
        target.append(DAV_NAMESPACE_PREFIX);
        target.append("=\"");
        target.append(DAVElement.DAV_NAMESPACE);
        target.append("\"");
        if (incomingDAVElements != null) {
            Collection elementNamespaces = new ArrayList();
            for (Iterator iterator = incomingDAVElements.iterator(); iterator.hasNext();) {
                DAVElement currentElement = (DAVElement) iterator.next();
                String currentNamespace = currentElement.getNamespace();
                if (currentNamespace != null && currentNamespace.length() > 0 && !elementNamespaces.contains(currentNamespace)) {
                    elementNamespaces.add(currentNamespace);
                    target.append(" xmlns:ns");
                    target.append(elementNamespaces.size());
                    target.append("=\"");
                    target.append(currentNamespace);
                    target.append("\"");
                }
            }
            elementNamespaces.clear();
        }
        target.append(">\n");
        return target;
    }

    protected StringBuffer appendXMLFooter(String prefix, String header, StringBuffer target) {
        target.append("</");
        target.append(prefix);
        target.append(":");
        target.append(header);
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
            for (Iterator iterator = attributes.entrySet().iterator(); iterator.hasNext();) {
                Map.Entry entry = (Map.Entry) iterator.next();
                String name = (String) entry.getKey();
                String value = (String) entry.getValue();
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

    protected String addHrefTags(String uri) {
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
