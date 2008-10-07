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
import java.util.Set;
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
import org.tmatesoft.svn.core.internal.server.dav.DAVException;
import org.tmatesoft.svn.core.internal.server.dav.DAVRepositoryManager;
import org.tmatesoft.svn.core.internal.server.dav.DAVResource;
import org.tmatesoft.svn.core.internal.server.dav.DAVResourceKind;
import org.tmatesoft.svn.core.internal.server.dav.DAVServlet;
import org.tmatesoft.svn.core.internal.util.CountingInputStream;
import org.tmatesoft.svn.core.internal.util.SVNHashSet;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.util.SVNLogType;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXNotRecognizedException;
import org.xml.sax.SAXNotSupportedException;
import org.xml.sax.XMLReader;

/**
 * @author TMate Software Ltd.
 * @version 1.2.0
 */
public abstract class ServletDAVHandler extends BasicDAVHandler {

    protected static final int SC_MULTISTATUS = 207;

    protected static final String HTTP_STATUS_OK_LINE = "HTTP/1.1 200 OK";
    protected static final String HTTP_NOT_FOUND_LINE = "HTTP/1.1 404 NOT FOUND";

    protected static final String DEFAULT_XML_CONTENT_TYPE = "text/xml; charset=\"utf-8\"";

    protected static final String UTF8_ENCODING = "UTF-8";
    protected static final String BASE64_ENCODING = "base64";    

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
    protected static final String ETAG_HEADER = "ETag";
    protected static final String RANGE_HEADER = "Range";

    //Common HTTP headers
    protected static final String DEPTH_HEADER = "Depth";
    protected static final String VARY_HEADER = "Vary";
    protected static final String LAST_MODIFIED_HEADER = "Last-Modified";
    protected static final String LABEL_HEADER = "Label";
    protected static final String USER_AGENT_HEADER = "User-Agent";
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

    //Common xml attributes
    protected static final String NAME_ATTR = "name";
    protected static final String NAMESPACE_ATTR = "namespace";

    protected static final String DIFF_VERSION_1 = "svndiff1";
    protected static final String DIFF_VERSION = "svndiff";

    protected static final String ACCEPT_RANGES_DEFAULT_VALUE = "bytes";    

    private static final Pattern COMMA = Pattern.compile(",");

    //Report related stuff DAVOptionsHandler uses
    protected static Set REPORT_ELEMENTS = new SVNHashSet();
    protected static final DAVElement UPDATE_REPORT = DAVElement.getElement(DAVElement.SVN_NAMESPACE, "update-report");
    protected static final DAVElement LOG_REPORT = DAVElement.getElement(DAVElement.SVN_NAMESPACE, "log-report");
    protected static final DAVElement DATED_REVISIONS_REPORT = DAVElement.getElement(DAVElement.SVN_NAMESPACE, "dated-rev-report");
    protected static final DAVElement GET_LOCATIONS = DAVElement.getElement(DAVElement.SVN_NAMESPACE, "get-locations");
    protected static final DAVElement FILE_REVISIONS_REPORT = DAVElement.getElement(DAVElement.SVN_NAMESPACE, "file-revs-report");
    protected static final DAVElement GET_LOCKS_REPORT = DAVElement.getElement(DAVElement.SVN_NAMESPACE, "get-locks-report");
    protected static final DAVElement REPLAY_REPORT = DAVElement.getElement(DAVElement.SVN_NAMESPACE, "replay-report");
    protected static final DAVElement MERGEINFO_REPORT = DAVElement.getElement(DAVElement.SVN_NAMESPACE, "mergeinfo-report");

    private static SAXParserFactory ourSAXParserFactory;
    private SAXParser mySAXParser;

    private DAVRepositoryManager myRepositoryManager = null;
    private HttpServletRequest myRequest;
    private HttpServletResponse myResponse;
        
    static {
        REPORT_ELEMENTS.add(UPDATE_REPORT);
        REPORT_ELEMENTS.add(LOG_REPORT);
        REPORT_ELEMENTS.add(DATED_REVISIONS_REPORT);
        REPORT_ELEMENTS.add(GET_LOCATIONS);
        REPORT_ELEMENTS.add(FILE_REVISIONS_REPORT);
        REPORT_ELEMENTS.add(GET_LOCKS_REPORT);
        REPORT_ELEMENTS.add(REPLAY_REPORT);
        REPORT_ELEMENTS.add(MERGEINFO_REPORT);
    }

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

    protected DAVResource getRequestedDAVResource(boolean labelAllowed, boolean useCheckedIn) throws SVNException {
        String label = labelAllowed ? getRequestHeader(LABEL_HEADER) : null;
        String versionName = getRequestHeader(SVN_VERSION_NAME_HEADER);
        long version = DAVResource.INVALID_REVISION;
        try {
            version = Long.parseLong(versionName);
        } catch (NumberFormatException e) {
        }
        String clientOptions = getRequestHeader(SVN_OPTIONS_HEADER);
        String baseChecksum = getRequestHeader(SVN_BASE_FULLTEXT_MD5_HEADER);
        String resultChecksum = getRequestHeader(SVN_RESULT_FULLTEXT_MD5_HEADER);
        String deltaBase = getRequestHeader(SVN_DELTA_BASE_HEADER);
        String userAgent = getRequestHeader(USER_AGENT_HEADER);
        boolean isSVNClient = userAgent != null && (userAgent.startsWith("SVN/") || userAgent.startsWith("SVNKit"));
        return getRepositoryManager().getRequestedDAVResource(isSVNClient, deltaBase, version, clientOptions, baseChecksum, resultChecksum, 
                label, useCheckedIn);
    }

    protected DAVDepth getRequestDepth(DAVDepth defaultDepth) throws SVNException {
        String depth = getRequestHeader(DEPTH_HEADER);
        if (depth == null) {
            return defaultDepth;
        }
        DAVDepth result = DAVDepth.parseDepth(depth);
        if (result == null) {
            SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.RA_DAV_MALFORMED_DATA, "Invalid depth ''{0}''", depth), SVNLogType.NETWORK);
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
            SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.IO_ERROR, e), e, SVNLogType.NETWORK);
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
            SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.IO_ERROR, e), e, SVNLogType.NETWORK);
        }
        return null;
    }

    protected OutputStream getResponseOutputStream() throws SVNException {
        try {
            return myResponse.getOutputStream();
        } catch (IOException e) {
            SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.IO_ERROR, e), e, SVNLogType.NETWORK);
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

    protected void readInput(boolean ignoreInput) throws SVNException {
        if (ignoreInput) {
            InputStream inputStream = null;
            try {
                inputStream = getRequestInputStream();
                while (inputStream.read() != -1) {
                    continue;
                }
            } catch (IOException ioe) {
                //
            } finally {
                SVNFileUtil.closeFile(inputStream);
            }
            return;
        }
        
        if (mySAXParser == null) {
            CountingInputStream stream = null;
            try {
                mySAXParser = getSAXParserFactory().newSAXParser();
                if (myRequest.getContentLength() > 0) {
                    XMLReader reader = mySAXParser.getXMLReader();
                    reader.setContentHandler(this);
                    reader.setDTDHandler(this);
                    reader.setErrorHandler(this);
                    reader.setEntityResolver(this);
                    stream = new CountingInputStream(getRequestInputStream());
                    reader.parse(new InputSource(stream));
                }
            } catch (ParserConfigurationException e) {
                if (stream == null || stream.getBytesRead() > 0) {
                    SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.RA_DAV_REQUEST_FAILED, e), e, SVNLogType.NETWORK);
                }
            } catch (SAXException e) {
                if (stream == null || stream.getBytesRead() > 0) {
                    SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.RA_DAV_REQUEST_FAILED, e), e, SVNLogType.NETWORK);
                }
            } catch (IOException e) {
                if (stream == null || stream.getBytesRead() > 0) {
                    SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.RA_DAV_REQUEST_FAILED, e), e, SVNLogType.NETWORK);
                }
            }
            getDAVRequest().init();
        }
    }

    protected void handleError(DAVException error, DAVResponse response) {
        if (response == null) {
            DAVException stackErr = error;
            while (stackErr != null && stackErr.getTagName() == null) {
                stackErr = stackErr.getPreviousException();
            }
            
            if (stackErr != null && stackErr.getTagName() != null) {
                myResponse.setContentType(DEFAULT_XML_CONTENT_TYPE);
                
                StringBuffer errorMessageBuffer = new StringBuffer();
                errorMessageBuffer.append('\n');
                errorMessageBuffer.append("<D:error xmlns:D=\"DAV:\"");
                
                if (stackErr.getMessage() != null) {
                    errorMessageBuffer.append(" xmlns:m=\"http://apache.org/dav/xmlns\"");
                }
                
                if (stackErr.getNameSpace() != null) {
                    errorMessageBuffer.append(" xmlns:C=\"");
                    errorMessageBuffer.append(stackErr.getNameSpace());
                    errorMessageBuffer.append("\">\n<C:");
                    errorMessageBuffer.append(stackErr.getTagName());
                    errorMessageBuffer.append("/>");
                } else {
                    errorMessageBuffer.append(">\n<D:");
                    errorMessageBuffer.append(stackErr.getTagName());
                    errorMessageBuffer.append("/>");
                }
                
                if (stackErr.getMessage() != null) {
                    
                }

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
