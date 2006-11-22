/*
 * ====================================================================
 * Copyright (c) 2004-2006 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.core.internal.io.dav.http;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.text.ParseException;
import java.util.Collection;
import java.util.zip.GZIPInputStream;

import javax.net.ssl.SSLHandshakeException;
import javax.xml.parsers.FactoryConfigurationError;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationManager;
import org.tmatesoft.svn.core.auth.ISVNProxyManager;
import org.tmatesoft.svn.core.auth.ISVNSSLManager;
import org.tmatesoft.svn.core.auth.SVNAuthentication;
import org.tmatesoft.svn.core.auth.SVNPasswordAuthentication;
import org.tmatesoft.svn.core.auth.SVNSSLAuthentication;
import org.tmatesoft.svn.core.internal.io.dav.handlers.DAVErrorHandler;
import org.tmatesoft.svn.core.internal.util.SVNSocketFactory;
import org.tmatesoft.svn.core.internal.wc.SVNCancellableOutputStream;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.xml.sax.EntityResolver;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXNotRecognizedException;
import org.xml.sax.SAXNotSupportedException;
import org.xml.sax.SAXParseException;
import org.xml.sax.helpers.DefaultHandler;

/**
 * @version 1.1.0
 * @author  TMate Software Ltd.
 */
class HTTPConnection implements IHTTPConnection {
    
    private static final DefaultHandler DEFAULT_SAX_HANDLER = new DefaultHandler();
    private static EntityResolver NO_ENTITY_RESOLVER = new EntityResolver() {
        public InputSource resolveEntity(String publicId, String systemId) throws SAXException, IOException {
            return new InputSource(new ByteArrayInputStream(new byte[0]));
        }
    };
    
    private static final int DEFAULT_HTTP_TIMEOUT = 3600*1000;

    private static SAXParserFactory ourSAXParserFactory;
    private byte[] myBuffer;
    private SAXParser mySAXParser;
    private SVNURL myHost;
    private OutputStream myOutputStream;
    private InputStream myInputStream;
    private Socket mySocket;
    private SVNRepository myRepository;
    private boolean myIsSecured;
    private boolean myIsProxied;
    private SVNAuthentication myLastValidAuth;
    private HTTPAuthentication myChallengeCredentials;
    private HTTPAuthentication myProxyAuthentication;
    private boolean myIsKeepAlive;
    private boolean myIsSpoolResponse;
    private ISVNSSLManager mySSLManager;

    
    public HTTPConnection(SVNRepository repository) throws SVNException {
        myRepository = repository;
        myHost = repository.getLocation().setPath("", false);
        myIsSecured = "https".equalsIgnoreCase(myHost.getProtocol());
        myIsKeepAlive = repository.getOptions().keepConnection(repository);
    }
    
    public SVNURL getHost() {
        return myHost;
    }

    private void connect(ISVNSSLManager sslManager) throws IOException, SVNException {
        SVNURL location = myRepository.getLocation();
        if (mySocket == null || SVNSocketFactory.isSocketStale(mySocket)) {
            close();
            String host = location.getHost();
            int port = location.getPort();
            
            ISVNAuthenticationManager authManager = myRepository.getAuthenticationManager();
            ISVNProxyManager proxyAuth = authManager != null ? authManager.getProxyManager(location) : null;
            if (proxyAuth != null && proxyAuth.getProxyHost() != null) {
                mySocket = SVNSocketFactory.createPlainSocket(proxyAuth.getProxyHost(), proxyAuth.getProxyPort());
                if (myProxyAuthentication == null) {
                    myProxyAuthentication = new HTTPBasicAuthentication(proxyAuth.getProxyUserName(), proxyAuth.getProxyPassword());
                }
                myIsProxied = true;
                if (myIsSecured) {
                    HTTPRequest connectRequest = new HTTPRequest();
                    connectRequest.setConnection(this);
                    connectRequest.setProxyAuthentication(myProxyAuthentication.authenticate());
                    connectRequest.setForceProxyAuth(true);
                    connectRequest.dispatch("CONNECT", host + ":" + port, null, 0, 0, null);
                    HTTPStatus status = connectRequest.getStatus();
                    if (status.getCode() == HttpURLConnection.HTTP_OK) {
                        myInputStream = null;
                        myOutputStream = null;
                        mySocket = SVNSocketFactory.createSSLSocket(sslManager, host, port, mySocket);
                        proxyAuth.acknowledgeProxyContext(true, null);
                        return;
                    }
                    SVNURL proxyURL = SVNURL.parseURIEncoded("http://" + proxyAuth.getProxyHost() + ":" + proxyAuth.getProxyPort()); 
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.RA_DAV_REQUEST_FAILED, "{0} request failed on ''{1}''", new Object[] {"CONNECT", proxyURL});
                    proxyAuth.acknowledgeProxyContext(false, err);
                    SVNErrorManager.error(err, connectRequest.getErrorMessage());
                }
            } else {
                myIsProxied = false;
                myProxyAuthentication = null;
                mySocket = myIsSecured ? SVNSocketFactory.createSSLSocket(sslManager, host, port) : SVNSocketFactory.createPlainSocket(host, port);
            }
            long timeout = myRepository.getAuthenticationManager() != null ? myRepository.getAuthenticationManager().getHTTPTimeout(myRepository) : DEFAULT_HTTP_TIMEOUT;
            if (timeout < 0) {
                timeout = DEFAULT_HTTP_TIMEOUT;
            }
            mySocket.setSoTimeout((int) timeout);
        }
    }
    
    public void readHeader(HTTPRequest request) throws IOException {
        InputStream is = myRepository.getDebugLog().createLogStream(getInputStream());
        try {            
            HTTPStatus status = HTTPParser.parseStatus(is);
            HTTPHeader header = HTTPHeader.parseHeader(is);
            request.setStatus(status);
            request.setResponseHeader(header);
        } catch (ParseException e) {
            // in case of parse exception:
            // try to read remaining and log it.
            String line = HTTPParser.readLine(is);
            while(line != null && line.length() > 0) {
                line = HTTPParser.readLine(is);
            }
            throw new IOException(e.getMessage());
        } finally {
            myRepository.getDebugLog().flushStream(is);
        }
    }
    
    public SVNErrorMessage readError(HTTPRequest request, String method, String path) {
        DAVErrorHandler errorHandler = new DAVErrorHandler();
        try {
            readData(request, method, path, errorHandler);
        } catch (IOException e) {
            return null;
        }
        return errorHandler.getErrorMessage();
    }
    
    public void sendData(byte[] body) throws IOException {
        try {
            getOutputStream().write(body, 0, body.length);
            getOutputStream().flush();
        } finally {
            myRepository.getDebugLog().flushStream(getOutputStream());
        }
    }
    
    public void sendData(InputStream source, long length) throws IOException {
        try {
            byte[] buffer = getBuffer(); 
            while(length > 0) {
                int read = source.read(buffer, 0, (int) Math.min(buffer.length, length));
                length -= read;
                if (read > 0) {
                    getOutputStream().write(buffer, 0, read);
                } else {
                    break;
                }
            }
            getOutputStream().flush();
        } finally {
            myRepository.getDebugLog().flushStream(getOutputStream());
        }
    }
    
    public SVNAuthentication getLastValidCredentials() {
        return myLastValidAuth;
    }
    
    public void clearAuthenticationCache() {
        myLastValidAuth = null;
        mySSLManager = null;
    }

    public HTTPStatus request(String method, String path, HTTPHeader header, StringBuffer body, int ok1, int ok2, OutputStream dst, DefaultHandler handler) throws SVNException {
        return request(method, path, header, body, ok1, ok2, dst, handler, null);
    }

    public HTTPStatus request(String method, String path, HTTPHeader header, StringBuffer body, int ok1, int ok2, OutputStream dst, DefaultHandler handler, SVNErrorMessage context) throws SVNException {
        byte[] buffer = null;
        if (body != null) {
            try {
                buffer = body.toString().getBytes("UTF-8");
            } catch (UnsupportedEncodingException e) {
                buffer = body.toString().getBytes();
            }
        } 
        return request(method, path, header, buffer != null ? new ByteArrayInputStream(buffer) : null, ok1, ok2, dst, handler, context);
    }

    public HTTPStatus request(String method, String path, HTTPHeader header, InputStream body, int ok1, int ok2, OutputStream dst, DefaultHandler handler) throws SVNException {
        return request(method, path, header, body, ok1, ok2, dst, handler, null);
    }
    
    public HTTPStatus request(String method, String path, HTTPHeader header, InputStream body, int ok1, int ok2, OutputStream dst, DefaultHandler handler, SVNErrorMessage context) throws SVNException {
        if ("".equals(path) || path == null) {
            path = "/";
        }
        
        if (myChallengeCredentials != null) {
            myChallengeCredentials.setChallengeParameter("methodname", method);
            myChallengeCredentials.setChallengeParameter("uri", path);
        }
        
        // 1. prompt for ssl client cert if needed, if cancelled - throw cancellation exception.
        ISVNSSLManager sslManager = mySSLManager != null ? mySSLManager : promptSSLClientCertificate(true);
        String sslRealm = "<" + myHost.getProtocol() + "://" + myHost.getHost() + ":" + myHost.getPort() + ">";
        SVNAuthentication httpAuth = myLastValidAuth;
        boolean isAuthForced = myRepository.getAuthenticationManager() != null ? myRepository.getAuthenticationManager().isAuthenticationForced() : false;
        if (httpAuth == null && isAuthForced) {
            httpAuth = myRepository.getAuthenticationManager().getFirstAuthentication(ISVNAuthenticationManager.PASSWORD, sslRealm, null);
            myChallengeCredentials = new HTTPBasicAuthentication((SVNPasswordAuthentication)httpAuth);
            myChallengeCredentials.setChallengeParameter("methodname", method);
            myChallengeCredentials.setChallengeParameter("uri", path);
        } 
        String realm = null;

        // 2. create request instance.
        HTTPRequest request = new HTTPRequest();
        request.setConnection(this);
        request.setKeepAlive(myIsKeepAlive);
        request.setRequestBody(body);
        request.setResponseHandler(handler);
        request.setResponseStream(dst);
        
        SVNErrorMessage err = null;

        while (true) {
            HTTPStatus status = null;
            try {
                err = null;
                connect(sslManager);
                request.reset();
                request.setProxied(myIsProxied);
                request.setSecured(myIsSecured);
                if (myProxyAuthentication != null) {
                    request.setProxyAuthentication(myProxyAuthentication.authenticate());
                }
                if (httpAuth != null && myChallengeCredentials != null) {
                    String authResponse = myChallengeCredentials.authenticate();
                    request.setAuthentication(authResponse);
                }
                request.dispatch(method, path, header, ok1, ok2, context);
                status = request.getStatus();
            } catch (SSLHandshakeException ssl) {
                myRepository.getDebugLog().info(ssl);
                if (sslManager != null) {
                    SVNSSLAuthentication sslAuth = sslManager.getClientAuthentication();
                    if (sslAuth != null) {
                        close();
                        SVNErrorMessage sslErr = SVNErrorMessage.create(SVNErrorCode.RA_NOT_AUTHORIZED, "SSL handshake failed: ''{0}''", ssl.getMessage());
                        myRepository.getAuthenticationManager().acknowledgeAuthentication(false, ISVNAuthenticationManager.SSL, sslRealm, sslErr, sslAuth);
                        sslManager = promptSSLClientCertificate(false);
                        continue;
                    }
                }
                err = SVNErrorMessage.create(SVNErrorCode.RA_DAV_REQUEST_FAILED, ssl.getMessage());
            } catch (IOException e) {
                myRepository.getDebugLog().info(e);
                if (e instanceof SocketTimeoutException) {
                    err = SVNErrorMessage.create(SVNErrorCode.RA_DAV_REQUEST_FAILED, "timed out waiting for server");
                } else if (e instanceof SVNCancellableOutputStream.IOCancelException) {
                    SVNErrorManager.cancel(e.getMessage());
                } else {
                    if (sslManager != null && sslManager.isClientCertPromptRequired()) {
                        SVNSSLAuthentication sslAuth = sslManager.getClientAuthentication();
                        if (sslAuth != null) {
                            close();
                            SVNErrorMessage sslErr = SVNErrorMessage.create(SVNErrorCode.RA_NOT_AUTHORIZED, "SSL handshake failed: ''{0}''", e.getMessage());
                            myRepository.getAuthenticationManager().acknowledgeAuthentication(false, ISVNAuthenticationManager.SSL, sslRealm, sslErr, sslAuth);
                            sslManager = promptSSLClientCertificate(false);
                            continue;
                        }
                    }
                    err = SVNErrorMessage.create(SVNErrorCode.RA_DAV_REQUEST_FAILED, e.getMessage());
                }
            } catch (SVNException e) {
                myRepository.getDebugLog().info(e);
                // force connection close on SVNException 
                // (could be thrown by user's auth manager methods).
                close();
                throw e;
            } finally {
                finishResponse(request);                
            }
            if (err != null) {
                close();
                if (sslManager != null) {
                    sslManager.acknowledgeSSLContext(false, err);
                }
                break;
            }
            if (sslManager != null) {
                sslManager.acknowledgeSSLContext(true, null);
                SVNSSLAuthentication sslAuth = sslManager.getClientAuthentication();
                if (sslAuth != null) {
                    mySSLManager = sslManager;
                    myRepository.getAuthenticationManager().acknowledgeAuthentication(true, ISVNAuthenticationManager.SSL, sslRealm, null, sslAuth);
                }
            }

            if (status.getCode() == HttpURLConnection.HTTP_FORBIDDEN) {
                myLastValidAuth = null;
                close();
                err = request.getErrorMessage();
            } else if (myIsProxied && status.getCode() == HttpURLConnection.HTTP_PROXY_AUTH) {
                Collection proxyAuthHeaders = request.getResponseHeader().getHeaderValues(HTTPHeader.PROXY_AUTHENTICATE_HEADER);
                boolean retry = false;
                if (!HTTPAuthentication.isSchemeSupportedByServer(myProxyAuthentication.getAuthenticationScheme(), proxyAuthHeaders)) {
                    retry = true;
                }

                try {
                    myProxyAuthentication = HTTPAuthentication.parseAuthParameters(proxyAuthHeaders, myProxyAuthentication); 
                } catch (SVNException svne) {
                    myRepository.getDebugLog().info(svne);
                    err = svne.getErrorMessage(); 
                    break;
                }

                if (retry) {
                    close();
                    continue;
                }

                if (myProxyAuthentication instanceof HTTPNTLMAuthentication) {
                    HTTPNTLMAuthentication ntlmProxyAuth = (HTTPNTLMAuthentication)myProxyAuthentication;
                    if (ntlmProxyAuth.isInType3State()) {
                        continue;
                    }
                }

                err = SVNErrorMessage.create(SVNErrorCode.CANCELLED, "HTTP proxy authorization cancelled");
                SVNURL location = myRepository.getLocation();
                ISVNAuthenticationManager authManager = myRepository.getAuthenticationManager();
                ISVNProxyManager proxyManager = authManager != null ? authManager.getProxyManager(location) : null;
                if (proxyManager != null) {
                    proxyManager.acknowledgeProxyContext(false, err);
                }
                close();

                break;
            } else if (status.getCode() == HttpURLConnection.HTTP_UNAUTHORIZED) {
                Collection authHeaderValues = request.getResponseHeader().getHeaderValues(HTTPHeader.AUTHENTICATE_HEADER);
                if (authHeaderValues == null || authHeaderValues.size() == 0) {
                    err = request.getErrorMessage();
                    status.setError(SVNErrorMessage.create(SVNErrorCode.RA_DAV_REQUEST_FAILED, err.getMessageTemplate(), err.getRelatedObjects()));
                    if ("LOCK".equalsIgnoreCase(method)) {
                        status.getError().setChildErrorMessage(SVNErrorMessage.create(SVNErrorCode.UNSUPPORTED_FEATURE, 
                                "Probably you are trying to lock file in repository that only allows anonymous access"));
                    }
                    SVNErrorManager.error(status.getError());
                    return status;  
                }

                //we should work around a situation when a server
                //does not support Basic authentication while we're 
                //forcing it, credentials should not be immediately
                //thrown away
                boolean skip = false;
                isAuthForced = myRepository.getAuthenticationManager() != null ? myRepository.getAuthenticationManager().isAuthenticationForced() : false;
                if (isAuthForced) {
                    if (httpAuth != null && myChallengeCredentials != null && !HTTPAuthentication.isSchemeSupportedByServer(myChallengeCredentials.getAuthenticationScheme(), authHeaderValues)) {
                        skip = true;
                    }
                }
                
                try {
                    myChallengeCredentials = HTTPAuthentication.parseAuthParameters(authHeaderValues, myChallengeCredentials); 
                } catch (SVNException svne) {
                    err = svne.getErrorMessage(); 
                    break;
                }

                myChallengeCredentials.setChallengeParameter("methodname", method);
                myChallengeCredentials.setChallengeParameter("uri", path);
                
                if (skip) {
                    close();
                    continue;
                }
                
                if (myChallengeCredentials instanceof HTTPNTLMAuthentication) {
                    HTTPNTLMAuthentication ntlmAuth = (HTTPNTLMAuthentication)myChallengeCredentials;
                    if (ntlmAuth.isInType3State()) {
                        continue;
                    }
                }

                myLastValidAuth = null;
                close();
                
                ISVNAuthenticationManager authManager = myRepository.getAuthenticationManager();
                if (authManager == null) {
                    err = request.getErrorMessage();
                    break;
                }

                realm = myChallengeCredentials.getChallengeParameter("realm");
                realm = realm == null ? "" : " " + realm;
                realm = "<" + myHost.getProtocol() + "://" + myHost.getHost() + ":" + myHost.getPort() + ">" + realm;
                if (httpAuth == null) {
                    httpAuth = authManager.getFirstAuthentication(ISVNAuthenticationManager.PASSWORD, realm, myRepository.getLocation());
                } else {
                    authManager.acknowledgeAuthentication(false, ISVNAuthenticationManager.PASSWORD, realm, request.getErrorMessage(), httpAuth);
                    httpAuth = authManager.getNextAuthentication(ISVNAuthenticationManager.PASSWORD, realm, myRepository.getLocation());
                }
                if (httpAuth == null) {
                    err = SVNErrorMessage.create(SVNErrorCode.CANCELLED, "HTTP authorization cancelled");
                    break;
                }
                myChallengeCredentials.setCredentials((SVNPasswordAuthentication)httpAuth);
                continue;
            } else if (status.getCode() == HttpURLConnection.HTTP_MOVED_PERM || status.getCode() == HttpURLConnection.HTTP_MOVED_TEMP) {
                close();
                String newLocation = request.getResponseHeader().getFirstHeaderValue(HTTPHeader.LOCATION_HEADER);
                if (newLocation == null) {
                    err = request.getErrorMessage();
                    break;
                }
                int hostIndex = newLocation.indexOf("://");
                if (hostIndex > 0) {
                    hostIndex += 3;
                    hostIndex = newLocation.indexOf("/", hostIndex);
                }
                if (hostIndex > 0 && hostIndex < newLocation.length()) {
                    String newPath = newLocation.substring(hostIndex);
                    if (newPath.endsWith("/") &&
                            !newPath.endsWith("//") && !path.endsWith("/") &&
                            newPath.substring(0, newPath.length() - 1).equals(path)) {
                        path += "//";
                        continue;
                    }
                }
                err = request.getErrorMessage();
            } else if (request.getErrorMessage() != null) {
                err = request.getErrorMessage();
            }
            if (err != null) {
                break;
            }
            
            if (myIsProxied) {
                SVNURL location = myRepository.getLocation();
                ISVNAuthenticationManager authManager = myRepository.getAuthenticationManager();
                ISVNProxyManager proxyManager = authManager != null ? authManager.getProxyManager(location) : null;
                if (proxyManager != null) {
                    proxyManager.acknowledgeProxyContext(true, null);
                }
            }
            
            if (httpAuth != null && realm != null && myRepository.getAuthenticationManager() != null) {
                myRepository.getAuthenticationManager().acknowledgeAuthentication(true, ISVNAuthenticationManager.PASSWORD, realm, null, httpAuth);
            }
            myLastValidAuth = httpAuth;
            status.setHeader(request.getResponseHeader());
            return status;
        }
        // force close on error that was not processed before.
        // these are errors that has no relation to http status (processing error or cancellation).
        close();
        if (err != null && err.getErrorCode().getCategory() != SVNErrorCode.RA_DAV_CATEGORY &&
            err.getErrorCode() != SVNErrorCode.UNSUPPORTED_FEATURE) {
            SVNErrorManager.error(err);
        }
        // err2 is another default context...
        myRepository.getDebugLog().info(err.getMessage());
        myRepository.getDebugLog().info(new Exception());
        
        SVNErrorMessage err2 = SVNErrorMessage.create(SVNErrorCode.RA_DAV_REQUEST_FAILED, "{0} request failed on ''{1}''", new Object[] {method, path});
        SVNErrorManager.error(err2, err);
        return null;
    }

    private ISVNSSLManager promptSSLClientCertificate(boolean firstAuth) throws SVNException {
        SVNURL location = myRepository.getLocation();
        ISVNAuthenticationManager authManager = myRepository.getAuthenticationManager();
        ISVNSSLManager sslManager = null;
        SVNSSLAuthentication sslAuth = null;
        String sslRealm = "<" + location.getProtocol() + "://" + location.getHost() + ":" + location.getPort() + ">";
        if (myIsSecured) {
            sslManager = authManager != null ? authManager.getSSLManager(location) : null;
        }
        if (authManager != null && sslManager != null && sslManager.isClientCertPromptRequired()) {
            if (firstAuth) {
                sslAuth = (SVNSSLAuthentication) authManager.getFirstAuthentication(ISVNAuthenticationManager.SSL, sslRealm, location);
            } else {
                sslAuth = (SVNSSLAuthentication) authManager.getNextAuthentication(ISVNAuthenticationManager.SSL, sslRealm, location);
            }
            if (sslAuth == null) {
                SVNErrorManager.cancel("SSL authentication with client certificate cancelled");
            }
            sslManager.setClientAuthentication(sslAuth);
        }
        return sslManager;
    }

    public SVNErrorMessage readData(HTTPRequest request, OutputStream dst) throws IOException {
        InputStream stream = createInputStream(request.getResponseHeader(), getInputStream());
        byte[] buffer = getBuffer();
        boolean willCloseConnection = false;
        try {
            while (true) {
                int count = stream.read(buffer);
                if (count <= 0) {
                    break;
                }
                if (dst != null) {
                    dst.write(buffer, 0, count);
                }
            }
        } catch (IOException e) {
            willCloseConnection = true;
            if (e.getCause() instanceof SVNException) {
                return ((SVNException) e.getCause()).getErrorMessage();
            }
            throw e;
        } finally {
            if (!willCloseConnection) {
                SVNFileUtil.closeFile(stream);
            }
            myRepository.getDebugLog().flushStream(stream);
        }
        return null;
    }
    
    public SVNErrorMessage readData(HTTPRequest request, String method, String path, DefaultHandler handler) throws IOException {
        InputStream is = null; 
        File tmpFile = null; 
        SVNErrorMessage err = null;
        boolean closeStream = myIsSpoolResponse;
        try {
            if (myIsSpoolResponse) {
                OutputStream dst = null;
                try {
                    tmpFile = SVNFileUtil.createTempFile(".svnkit", ".spool");
                    dst = SVNFileUtil.openFileForWriting(tmpFile);
                    // this will exhaust http stream anyway.
                    err = readData(request, dst);
                    closeStream |= err != null;
                    if (err != null) {
                        return err;
                    }
                    // this stream always have to be closed.
                    is = SVNFileUtil.openFileForReading(tmpFile);
                } catch (SVNException e) {
                    return e.getErrorMessage();
                } finally {
                    SVNFileUtil.closeFile(dst);
                }
            } else {
                is = createInputStream(request.getResponseHeader(), getInputStream());
            }
            // this will not close is stream.
            err = readData(is, method, path, handler);
            closeStream |= err != null;
        } catch (IOException e) {
            closeStream = true;
            throw e;
        } finally {
            if (myIsSpoolResponse) {
                // always close spooled stream.
                SVNFileUtil.closeFile(is);
            } else if (err == null && !hasToCloseConnection(request.getResponseHeader())) {
                // exhaust stream if connection is not about to be closed.
                SVNFileUtil.closeFile(is);
            }
            if (tmpFile != null) {
                try {
                    SVNFileUtil.deleteFile(tmpFile);
                } catch (SVNException e) {
                    throw new IOException(e.getMessage());
                }
            }
            myIsSpoolResponse = false;
        }
        return err;
    }

    private SVNErrorMessage readData(InputStream is, String method, String path, DefaultHandler handler) throws FactoryConfigurationError, UnsupportedEncodingException, IOException {
        try {
            if (mySAXParser == null) {
                mySAXParser = getSAXParserFactory().newSAXParser();
            }
            XMLReader reader = new XMLReader(is);
            while (!reader.isClosed()) {
                org.xml.sax.XMLReader xmlReader = mySAXParser.getXMLReader();
                xmlReader.setContentHandler(handler);
                xmlReader.setDTDHandler(handler);
                xmlReader.setErrorHandler(handler);
                xmlReader.setEntityResolver(NO_ENTITY_RESOLVER);
                xmlReader.parse(new InputSource(reader));
            }
        } catch (SAXException e) {
            if (e instanceof SAXParseException) {
                if (handler instanceof DAVErrorHandler) {
                    // failed to read svn-specific error, return null.
                    return null;
                }
            } else if (e.getException() instanceof SVNException) {
                return ((SVNException) e.getException()).getErrorMessage();
            } else if (e.getCause() instanceof SVNException) {
                return ((SVNException) e.getCause()).getErrorMessage();
            } 
            return SVNErrorMessage.create(SVNErrorCode.RA_DAV_REQUEST_FAILED, "Processing {0} request response failed: {1} ({2}) ",  new Object[] {method, e.getMessage(), path});
        } catch (ParserConfigurationException e) {
            return SVNErrorMessage.create(SVNErrorCode.RA_DAV_REQUEST_FAILED, "XML parser configuration error while processing {0} request response: {1} ({2}) ",  new Object[] {method, e.getMessage(), path});
        } catch (EOFException e) {
            // skip it.
        } finally {
            if (mySAXParser != null) {
                // to avoid memory leaks when connection is cached.
                org.xml.sax.XMLReader xmlReader = null;
                try {
                    xmlReader = mySAXParser.getXMLReader();
                } catch (SAXException e) {
                }
                if (xmlReader != null) {
                    xmlReader.setContentHandler(DEFAULT_SAX_HANDLER);
                    xmlReader.setDTDHandler(DEFAULT_SAX_HANDLER);
                    xmlReader.setErrorHandler(DEFAULT_SAX_HANDLER);
                    xmlReader.setEntityResolver(NO_ENTITY_RESOLVER);
                }
            }
            myRepository.getDebugLog().flushStream(is);
        }
        return null;
    }
    
    public void skipData(HTTPRequest request) throws IOException {
        if (hasToCloseConnection(request.getResponseHeader())) {
            return;
        }
        InputStream is = createInputStream(request.getResponseHeader(), getInputStream());
        while(is.skip(2048) > 0);        
    }

    public void close() {
        if (mySocket != null) {
            if (myInputStream != null) {
                try {
                    myInputStream.close();
                } catch (IOException e) {}
            }
            if (myOutputStream != null) {
                try {
                    myOutputStream.flush();
                } catch (IOException e) {}
            }
            if (myOutputStream != null) {
                try {
                    myOutputStream.close();
                } catch (IOException e) {}
            }
            try {
                mySocket.close();
            } catch (IOException e) {}
            mySocket = null;
            myOutputStream = null;
            myInputStream = null;
        }
    }

    private byte[] getBuffer() {
        if (myBuffer == null) {
            myBuffer = new byte[32*1024];
        }
        return myBuffer;
    }

    private InputStream getInputStream() throws IOException {
        if (myInputStream == null) {
            if (mySocket == null) {
                return null;
            }
            myInputStream = new BufferedInputStream(mySocket.getInputStream(), 2048);
        }
        return myInputStream;
    }

    private OutputStream getOutputStream() throws IOException {
        if (myOutputStream == null) {
            if (mySocket == null) {
                return null;
            }
            myOutputStream = new BufferedOutputStream(mySocket.getOutputStream(), 2048);
            myOutputStream = myRepository.getDebugLog().createLogStream(myOutputStream);
        }
        return myOutputStream;
    }

    private void finishResponse(HTTPRequest request) {
        if (myOutputStream != null) {
            try {
                myOutputStream.flush();
            } catch (IOException ex) {
            }
        }
        HTTPHeader header = request != null ? request.getResponseHeader() : null;
        if (hasToCloseConnection(header)) {
            close();
        }
    }
    
    private static boolean hasToCloseConnection(HTTPHeader header) {
        if (header == null || 
                "close".equalsIgnoreCase(header.getFirstHeaderValue(HTTPHeader.CONNECTION_HEADER)) || 
                "close".equalsIgnoreCase(header.getFirstHeaderValue(HTTPHeader.PROXY_CONNECTION_HEADER))) {
            return true;
        }
        return false;
    }
    
    private InputStream createInputStream(HTTPHeader readHeader, InputStream is) throws IOException {
        if ("chunked".equalsIgnoreCase(readHeader.getFirstHeaderValue(HTTPHeader.TRANSFER_ENCODING_HEADER))) {
            is = new ChunkedInputStream(is);
        } else if (readHeader.getFirstHeaderValue(HTTPHeader.CONTENT_LENGTH_HEADER) != null) {
            is = new FixedSizeInputStream(is, Long.parseLong(readHeader.getFirstHeaderValue(HTTPHeader.CONTENT_LENGTH_HEADER).toString()));
        } else if (!hasToCloseConnection(readHeader)) {
            // no content length and no valid transfer-encoding!
            // consider as empty response.

            // but only when there is no "Connection: close" or "Proxy-Connection: close" header,
            // in that case just return "is". 
            // skipData will not read that as it should also analyze "close" instruction.
            
            // return empty stream. 
            // and force connection close? (not to read garbage on the next request).
            is = new FixedSizeInputStream(is, 0);
            // this will force connection to close.
            readHeader.setHeaderValue(HTTPHeader.CONNECTION_HEADER, "close");
        } 
        
        if ("gzip".equals(readHeader.getFirstHeaderValue(HTTPHeader.CONTENT_ENCODING_HEADER))) {
            is = new GZIPInputStream(is);
        }
        return myRepository.getDebugLog().createLogStream(is);
    }

    private static synchronized SAXParserFactory getSAXParserFactory() throws FactoryConfigurationError {
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

    public void setSpoolResponse(boolean spoolResponse) {
        myIsSpoolResponse = spoolResponse;
    }

}
