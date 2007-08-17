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
import java.io.OutputStream;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.io.dav.DAVElement;
import org.tmatesoft.svn.core.internal.io.dav.handlers.BasicDAVHandler;
import org.tmatesoft.svn.core.internal.server.dav.DAVDepth;
import org.tmatesoft.svn.core.internal.server.dav.DAVRepositoryManager;
import org.tmatesoft.svn.core.internal.server.dav.DAVResource;
import org.tmatesoft.svn.core.internal.server.dav.DAVResourceKind;
import org.tmatesoft.svn.core.internal.util.SVNTimeUtil;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXNotRecognizedException;
import org.xml.sax.SAXNotSupportedException;
import org.xml.sax.XMLReader;

/**
 * @author TMate Software Ltd.
 * @version 1.1.2
 */
public abstract class ServletDAVHandler extends BasicDAVHandler {

    protected static final int SC_MULTISTATUS = 207;

    protected static final String HTTP_STATUS_OK_LINE = "HTTP/1.1 200 OK";
    protected static final String HTTP_NOT_FOUND_LINE = "HTTP/1.1 404 NOT FOUND";

    protected static final String DEFAULT_XML_CONTENT_TYPE = "text/xml; charset=utf-8";

    protected static final String SVN_OPTIONS_HEADER = "X-SVN-Options";
    protected static final String SVN_DELTA_BASE_HEADER = "X-SVN-VR-Base";
    protected static final String SVN_VERSION_NAME_HEADER = "X-SVN-Version-Name";
    protected static final String SVN_CREATIONDATE_HEADER = "X-SVN-Creation-Date";
    protected static final String SVN_LOCK_OWNER_HEADER = "X-SVN-Lock-Owner";
    protected static final String SVN_BASE_FULLTEXT_MD5_HEADER = "X-SVN-Base-Fulltext-MD5";
    protected static final String SVN_RESULT_FULLTEXT_MD5_HEADER = "X-SVN-Result-Fulltext-MD5";

    private static final String DEPTH_HEADER = "Depth";
    private static final String VARY_HEADER = "Vary";
    protected static final String LAST_MODIFIED_HEADER = "Last-Modified";
    protected static final String LABEL_HEADER = "Label";
    protected static final String USER_AGENT_HEADER = "User-Agent";
    protected static final String ETAG_HEADER = "ETag";
    protected static final String ACCEPT_RANGES_HEADER = "Accept-Ranges";

    protected static final String ACCEPT_RANGES_VALUE = "bytes";

    public static final Map PREFIX_MAP = new HashMap();

    public static final String DAV_NAMESPACE_PREFIX = "D";
    public static final String SVN_DAV_PROPERTY_PREFIX = "V";
    public static final String SVN_CUSTOM_PROPERTY_PREFIX = "C";
    public static final String SVN_SVN_PROPERTY_PREFIX = "S";
    public static final String SVN_APACHE_PROPERTY_PREFIX = "M";

    protected static final DAVElement PROPFIND = DAVElement.getElement(DAVElement.DAV_NAMESPACE, "propfind");
    protected static final DAVElement PROPNAME = DAVElement.getElement(DAVElement.DAV_NAMESPACE, "propname");
    protected static final DAVElement ALLPROP = DAVElement.getElement(DAVElement.DAV_NAMESPACE, "allprop");
    protected static final DAVElement GET_CONTENT_TYPE = DAVElement.getElement(DAVElement.DAV_NAMESPACE, "getcontenttype");
    protected static final DAVElement GET_LAST_MODIFIED = DAVElement.getElement(DAVElement.DAV_NAMESPACE, "getlastmodified");
    protected static final DAVElement GET_ETAG = DAVElement.getElement(DAVElement.DAV_NAMESPACE, "getetag");
    protected static final DAVElement LOG = DAVElement.getElement(DAVElement.SVN_SVN_PROPERTY_NAMESPACE, "log");

    private static SAXParserFactory ourSAXParserFactory;
    private SAXParser mySAXParser;

    private DAVRepositoryManager myRepositoryManager = null;
    private HttpServletRequest myRequest;
    private HttpServletResponse myResponse;

    static {
        PREFIX_MAP.put(DAVElement.DAV_NAMESPACE, DAV_NAMESPACE_PREFIX);
        PREFIX_MAP.put(DAVElement.SVN_DAV_PROPERTY_NAMESPACE, SVN_DAV_PROPERTY_PREFIX);
        PREFIX_MAP.put(DAVElement.SVN_SVN_PROPERTY_NAMESPACE, SVN_SVN_PROPERTY_PREFIX);
        PREFIX_MAP.put(DAVElement.SVN_CUSTOM_PROPERTY_NAMESPACE, SVN_CUSTOM_PROPERTY_PREFIX);
        PREFIX_MAP.put(DAVElement.SVN_APACHE_PROPERTY_NAMESPACE, SVN_APACHE_PROPERTY_PREFIX);
    }

    protected ServletDAVHandler(DAVRepositoryManager connector, HttpServletRequest request, HttpServletResponse response) {
        init();
        myRepositoryManager = connector;
        myRequest = request;
        myResponse = response;
    }

    public abstract void execute() throws SVNException;

    protected String getURI() {
        return myRequest.getPathInfo();
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

    protected void addResponseHeader(String name, String value) {
        myResponse.addHeader(name, value);
    }

    protected void setResponseStatus(int statusCode) {
        myResponse.setStatus(statusCode);
    }

    protected void setResponseContentType(String contentType) {
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

    protected OutputStream getResponseOutputStream() throws SVNException {
        try {
            return myResponse.getOutputStream();
        } catch (IOException e) {
            SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.IO_ERROR, e), e);
        }
        return null;
    }

    protected DAVRepositoryManager getRepositoryManager() {
        return myRepositoryManager;
    }

    protected DAVResource createDAVResource(boolean useCheckedIn) throws SVNException {
        String label = getRequestHeader(LABEL_HEADER);
        String versionName = getRequestHeader(SVN_VERSION_NAME_HEADER);
        String clientOptions = getRequestHeader(SVN_OPTIONS_HEADER);
        String baseChecksum = getRequestHeader(SVN_BASE_FULLTEXT_MD5_HEADER);
        String resultChecksum = getRequestHeader(SVN_RESULT_FULLTEXT_MD5_HEADER);
        String userAgent = getRequestHeader(USER_AGENT_HEADER);
        boolean isSVNClient = userAgent != null && (userAgent.startsWith("SVN/") || userAgent.startsWith("SVNKit"));
        return getRepositoryManager().createDAVResource(getRequestContext(), getURI(), isSVNClient, versionName, clientOptions, baseChecksum, resultChecksum, label, useCheckedIn);
    }

    protected Collection getSupportedLiveProperties(DAVResource resource) {
        Collection liveProperties = new ArrayList();
        liveProperties.add(DAVElement.AUTO_VERSION);
        liveProperties.add(DAVElement.VERSION_NAME);
        liveProperties.add(DAVElement.CREATION_DATE);
        liveProperties.add(DAVElement.CREATOR_DISPLAY_NAME);
        liveProperties.add(DAVElement.BASELINE_RELATIVE_PATH);
        liveProperties.add(DAVElement.REPOSITORY_UUID);
        liveProperties.add(DAVElement.CHECKED_IN);
        liveProperties.add(DAVElement.RESOURCE_TYPE);
        liveProperties.add(DAVElement.VERSION_CONTROLLED_CONFIGURATION);
        liveProperties.add(GET_ETAG);
        liveProperties.add(GET_LAST_MODIFIED);
        liveProperties.add(GET_CONTENT_TYPE);
        if (!resource.isCollection()) {
            liveProperties.add(DAVElement.MD5_CHECKSUM);
            liveProperties.add(DAVElement.GET_CONTENT_LENGTH);
        }
        if (resource.getResourceURI().getKind() != DAVResourceKind.BASELINE_COLL) {
            liveProperties.add(DAVElement.BASELINE_COLLECTION);
        }
        return liveProperties;
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
        try {
            myResponse.setContentType(resource.getContentType());
        } catch (SVNException e) {
            //nothing to do we just skip this header    
        }
        setResponseHeader(ACCEPT_RANGES_HEADER, ACCEPT_RANGES_VALUE);
        try {
            Date lastModifiedTime = resource.getLastModified();
            if (lastModifiedTime != null) {
                setResponseHeader(LAST_MODIFIED_HEADER, SVNTimeUtil.formatRFC1123Date(lastModifiedTime));
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
