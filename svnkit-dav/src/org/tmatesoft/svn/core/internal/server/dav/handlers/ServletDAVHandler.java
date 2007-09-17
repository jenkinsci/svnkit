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
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.Date;
import java.util.Enumeration;
import java.util.regex.Pattern;

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
public abstract class ServletDAVHandler extends BasicDAVHandler {

    protected static final int SC_MULTISTATUS = 207;

    protected static final String HTTP_STATUS_OK_LINE = "HTTP/1.1 200 OK";
    protected static final String HTTP_NOT_FOUND_LINE = "HTTP/1.1 404 NOT FOUND";

    protected static final String DEFAULT_XML_CONTENT_TYPE = "text/xml; charset=\"UTF-8\"";

    //Specific svn headers
    protected static final String SVN_OPTIONS_HEADER = "X-SVN-Options";
    protected static final String SVN_DELTA_BASE_HEADER = "X-SVN-VR-Base";
    protected static final String SVN_VERSION_NAME_HEADER = "X-SVN-Version-Name";
    protected static final String SVN_CREATIONDATE_HEADER = "X-SVN-Creation-Date";
    protected static final String SVN_LOCK_OWNER_HEADER = "X-SVN-Lock-Owner";
    protected static final String SVN_BASE_FULLTEXT_MD5_HEADER = "X-SVN-Base-Fulltext-MD5";
    protected static final String SVN_RESULT_FULLTEXT_MD5_HEADER = "X-SVN-Result-Fulltext-MD5";

    //Precondition headers
    protected static final String IF_MATCH_HEADER = "If-Match";
    protected static final String IF_UNMODIFIED_SINCE_HEADER = "If-Unmodified-Since";
    protected static final String IF_NONE_MATCH_HEADER = "If-None-Match";
    protected static final String IF_MODIFIED_SINCE_HEADER = "If-Modified-Since";
    protected static final String RANGE_HEADER = "Range";

    //Common HTTP headers
    protected static final String DEPTH_HEADER = "Depth";
    protected static final String VARY_HEADER = "Vary";
    protected static final String LAST_MODIFIED_HEADER = "Last-Modified";
    protected static final String LABEL_HEADER = "Label";
    protected static final String USER_AGENT_HEADER = "User-Agent";
    protected static final String ETAG_HEADER = "ETag";
    protected static final String CONNECTION_HEADER = "Connection";
    protected static final String DATE_HEADER = "Date";
    protected static final String KEEP_ALIVE_HEADER = "Keep-Alive";
    protected static final String ACCEPT_RANGES_HEADER = "Accept-Ranges";
    protected static final String ACCEPT_ENCODING_HEADER = "Accept-Encoding";

    //Supported live properties
    protected static final DAVElement GET_CONTENT_TYPE = DAVElement.getElement(DAVElement.DAV_NAMESPACE, "getcontenttype");
    protected static final DAVElement GET_LAST_MODIFIED = DAVElement.getElement(DAVElement.DAV_NAMESPACE, "getlastmodified");
    protected static final DAVElement GET_ETAG = DAVElement.getElement(DAVElement.DAV_NAMESPACE, "getetag");
    protected static final DAVElement LOG = DAVElement.getElement(DAVElement.SVN_SVN_PROPERTY_NAMESPACE, "log");

    protected static final String DIFF_VERSION_1 = "svndiff1";
    protected static final String DIFF_VERSION = "svndiff";

    protected static final String UTF_8_ENCODING = "UTF-8";

    private static final Pattern COMMA = Pattern.compile(",");

    private static SAXParserFactory ourSAXParserFactory;
    private SAXParser mySAXParser;

    private DAVRepositoryManager myRepositoryManager = null;
    private HttpServletRequest myRequest;
    private HttpServletResponse myResponse;

    protected ServletDAVHandler(DAVRepositoryManager connector, HttpServletRequest request, HttpServletResponse response) {
        myRepositoryManager = connector;
        myRequest = request;
        myResponse = response;
        init();
    }

    protected DAVRepositoryManager getRepositoryManager() {
        return myRepositoryManager;
    }

    public abstract void execute() throws SVNException;

    protected abstract DAVRequest getDAVRequest();

    protected void startElement(DAVElement parent, DAVElement element, Attributes attrs) throws SVNException {
        getDAVRequest().startElement(parent, element, attrs);
    }

    protected void endElement(DAVElement parent, DAVElement element, StringBuffer cdata) throws SVNException {
        getDAVRequest().endElement(parent, element, cdata);
    }

    protected DAVResource createDAVResource(boolean labelAllowed, boolean useCheckedIn) throws SVNException {
        String label = labelAllowed ? getRequestHeader(LABEL_HEADER) : null;
        String versionName = getRequestHeader(SVN_VERSION_NAME_HEADER);
        long version;
        try {
            version = Long.parseLong(versionName);
        } catch (NumberFormatException e) {
            version = DAVResource.INVALID_REVISION;
        }
        String clientOptions = getRequestHeader(SVN_OPTIONS_HEADER);
        String baseChecksum = getRequestHeader(SVN_BASE_FULLTEXT_MD5_HEADER);
        String resultChecksum = getRequestHeader(SVN_RESULT_FULLTEXT_MD5_HEADER);
        String deltaBase = getRequestHeader(SVN_DELTA_BASE_HEADER);
        String userAgent = getRequestHeader(USER_AGENT_HEADER);
        boolean isSVNClient = userAgent != null && (userAgent.startsWith("SVN/") || userAgent.startsWith("SVNKit"));
        return getRepositoryManager().createDAVResource(getURI(), isSVNClient, deltaBase, version, clientOptions, baseChecksum, resultChecksum, label, useCheckedIn);
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

    protected void setDefaultResponseHeaders() {
        if (getRequestHeader(LABEL_HEADER) != null && getRequestHeader(LABEL_HEADER).length() > 0) {
            setResponseHeader(VARY_HEADER, LABEL_HEADER);
        }
    }

    protected String getURI() {
        return myRequest.getPathInfo();
    }

    protected String getRequestHeader(String name) {
        return myRequest.getHeader(name);
    }

    protected Enumeration getRequestHeaders(String name) {
        return myRequest.getHeaders(name);
    }

    protected long getRequestDateHeader(String name) {
        return myRequest.getDateHeader(name);
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

    protected void setResponseContentLength(int length) {
        myResponse.setContentLength(length);
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

    protected static Collection getSupportedLiveProperties(DAVResource resource, Collection properties) throws SVNException {
        if (properties == null) {
            properties = new ArrayList();
        }
        properties.add(DAVElement.DEADPROP_COUNT);
        properties.add(DAVElement.REPOSITORY_UUID);
        properties.add(DAVElement.VERSION_CONTROLLED_CONFIGURATION);
        if (resource.getResourceURI().getKind() != DAVResourceKind.BASELINE_COLL) {
            properties.add(DAVElement.BASELINE_COLLECTION);
        } else {
            properties.remove(DAVElement.BASELINE_COLLECTION);
        }
        properties.add(DAVElement.BASELINE_RELATIVE_PATH);
        properties.add(DAVElement.RESOURCE_TYPE);
        properties.add(DAVElement.CHECKED_IN);
        properties.add(GET_ETAG);
        properties.add(DAVElement.CREATOR_DISPLAY_NAME);
        properties.add(DAVElement.CREATION_DATE);
        properties.add(GET_LAST_MODIFIED);
        properties.add(DAVElement.VERSION_NAME);
        properties.add(GET_CONTENT_TYPE);
        if (!resource.isCollection()) {
            properties.add(DAVElement.GET_CONTENT_LENGTH);
            properties.add(DAVElement.MD5_CHECKSUM);
        } else {
            properties.remove(DAVElement.GET_CONTENT_LENGTH);
            properties.remove(DAVElement.MD5_CHECKSUM);
        }
        return properties;
    }

    protected void checkPreconditions(String eTag, Date lastModified) {
        lastModified = lastModified == null ? new Date() : lastModified;
        long lastModifiedTime = lastModified.getTime();
        Enumeration ifMatch = getRequestHeaders(IF_MATCH_HEADER);
        if (ifMatch != null && ifMatch.hasMoreElements()) {
            String first = (String) ifMatch.nextElement();
            if (!"*".equals(first) && (eTag == null || "W".startsWith(eTag) || !first.equals(eTag))) {
                if ("W".startsWith(eTag) || !containsValue(ifMatch, eTag, null)) {
                    //Precondition failed!
                }
            }
        } else {
            long ifUnmodified = getRequestDateHeader(IF_UNMODIFIED_SINCE_HEADER);
            if (ifUnmodified != -1 && lastModifiedTime > ifUnmodified) {
                //Precondition failed!
            }
        }
        Enumeration ifNoneMatch = getRequestHeaders(IF_NONE_MATCH_HEADER);
        if (ifNoneMatch != null && containsValue(ifNoneMatch, eTag, "*")) {
            //Precondition failed!
        }
    }

    protected boolean containsValue(Enumeration values, String stringToFind, String matchAllString) {
        boolean contains = false;
        if (values != null) {
            while (values.hasMoreElements()) {
                String currentCondition = (String) values.nextElement();
                contains = currentCondition.equals(stringToFind) || currentCondition.equals(matchAllString);
                if (contains) {
                    break;
                }
            }
        }
        return contains;
    }

    protected boolean getSVNDiffVersion() {
        boolean diffCompress = false;
        for (Enumeration headerEncodings = getRequestHeaders(ACCEPT_ENCODING_HEADER); headerEncodings.hasMoreElements();)
        {
            String currentEncodings = (String) headerEncodings.nextElement();
            String[] encodings = COMMA.split(currentEncodings);
            if (encodings.length > 1) {

                Arrays.sort(encodings, new Comparator() {
                    public int compare(Object o1, Object o2) {
                        String encoding1 = (String) o1;
                        String encoding2 = (String) o2;
                        return getEncodingRange(encoding1) > getEncodingRange(encoding2) ? 1 : -1;
                    }
                });

                for (int i = encodings.length - 1; i >= 0; i--) {
                    if (DIFF_VERSION_1.equals(getEncodingName(encodings[i]))) {
                        diffCompress = true;
                        break;
                    } else if (DIFF_VERSION.equals(getEncodingName(encodings[i]))) {
                        break;
                    }
                }
            }
        }
        return diffCompress;
    }

    private float getEncodingRange(String encoding) {
        int delimiterIndex = encoding.indexOf(";");
        if (delimiterIndex != -1) {
            String qualityString = encoding.substring(delimiterIndex + 1);
            if (qualityString.startsWith("q=")) {
                try {
                    return Float.parseFloat(qualityString.substring("q=".length()));
                } catch (NumberFormatException e) {
                }
            }
        }
        return 1.0f;
    }

    private String getEncodingName(String encoding) {
        int delimiterIndex = encoding.indexOf(";");
        if (delimiterIndex != -1) {
            return encoding.substring(0, delimiterIndex);
        }
        return encoding;
    }

    protected void readInput() throws SVNException {
        if (mySAXParser == null) {
            try {
                mySAXParser = getSAXParserFactory().newSAXParser();
                XMLReader reader = mySAXParser.getXMLReader();
                reader.setContentHandler(this);
                reader.setDTDHandler(this);
                reader.setErrorHandler(this);
                reader.setEntityResolver(this);
                reader.parse(new InputSource(getRequestInputStream()));
            } catch (ParserConfigurationException e) {
                SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.RA_DAV_REQUEST_FAILED, e), e);
            } catch (SAXException e) {
                SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.RA_DAV_REQUEST_FAILED, e), e);
            } catch (IOException e) {
                SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.RA_DAV_REQUEST_FAILED, e), e);
            }
            getDAVRequest().init();
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
