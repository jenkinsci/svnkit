/*
 * ====================================================================
 * Copyright (c) 2004-2006 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://tmate.org/svn/license.html.
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.core.internal.io.dav.http;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.Map;
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
import org.tmatesoft.svn.core.internal.util.SVNBase64;
import org.tmatesoft.svn.core.internal.util.SVNSocketFactory;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.util.SVNDebugLog;
import org.xml.sax.EntityResolver;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXNotRecognizedException;
import org.xml.sax.SAXNotSupportedException;
import org.xml.sax.SAXParseException;
import org.xml.sax.helpers.DefaultHandler;


/**
 * @version 1.0
 * @author  TMate Software Ltd.
 */
class HTTPConnection implements IHTTPConnection {
    
    private static final DefaultHandler DEFAULT_SAX_HANDLER = new DefaultHandler();
    private static EntityResolver NO_ENTITY_RESOLVER = new EntityResolver() {
        public InputSource resolveEntity(String publicId, String systemId) throws SAXException, IOException {
            return new InputSource(new ByteArrayInputStream(new byte[0]));
        }
    };

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
    private Map myCredentialsChallenge;
    private String myProxyAuthentication;

    
    public HTTPConnection(SVNRepository repository) throws SVNException {
        myRepository = repository;
        myHost = repository.getLocation().setPath("", false);
        myIsSecured = "https".equalsIgnoreCase(myHost.getProtocol());
    }
    
    public SVNURL getHost() {
        return myHost;
    }

    private void connect(ISVNSSLManager sslManager) throws IOException, SVNException {
        SVNURL location = myRepository.getLocation();
        if (mySocket == null || SVNSocketFactory.isSocketStale(mySocket)) {
            myIsProxied = false;
            myProxyAuthentication = null;
            close();
            String host = location.getHost();
            int port = location.getPort();
            ISVNAuthenticationManager authManager = myRepository.getAuthenticationManager();
            ISVNProxyManager proxyAuth = authManager != null ? authManager.getProxyManager(location) : null;
            if (proxyAuth != null && proxyAuth.getProxyHost() != null) {
                mySocket = SVNSocketFactory.createPlainSocket(proxyAuth.getProxyHost(), proxyAuth.getProxyPort());
                myProxyAuthentication = getProxyAuthString(proxyAuth.getProxyUserName(), proxyAuth.getProxyPassword());
                if (myIsSecured) {
                    HTTPRequest connectRequest = new HTTPRequest();
                    connectRequest.setConnection(this);
                    connectRequest.setProxyAuthentication(myProxyAuthentication);
                    connectRequest.dispatch("CONNECT", host + ":" + port, null, 0, 0);
                    HTTPStatus status = connectRequest.getStatus();
                    if (status.getCode() == HttpURLConnection.HTTP_OK) {
                        myInputStream = null;
                        myOutputStream = null;
                        mySocket = SVNSocketFactory.createSSLSocket(sslManager, host, port, mySocket);
                        myIsProxied = true;
                        proxyAuth.acknowledgeProxyContext(true, null);
                        return;
                    }
                    SVNURL proxyURL = SVNURL.parseURIEncoded("http://" + proxyAuth.getProxyHost() + ":" + proxyAuth.getProxyPort()); 
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.RA_DAV_REQUEST_FAILED, "'{0}' request failed on ''{1}''", new Object[] {"CONNECT", proxyURL});
                    proxyAuth.acknowledgeProxyContext(true, err);
                    SVNErrorManager.error(err, connectRequest.getErrorMessage());
                }
                proxyAuth.acknowledgeProxyContext(true, null);
            } else {
                mySocket = myIsSecured ? SVNSocketFactory.createSSLSocket(sslManager, host, port) : SVNSocketFactory.createPlainSocket(host, port);
            }
        }
    }
    
    public void readHeader(HTTPRequest request) throws IOException {
        InputStream is = SVNDebugLog.createLogStream(getInputStream());
        try {            
            HTTPStatus status = HTTPParser.parseStatus(is);
            Map header = HTTPParser.parseHeader(is);
            request.setStatus(status);
            request.setResponseHeader(header);
        } finally {
            SVNDebugLog.flushStream(is);
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
            SVNDebugLog.flushStream(getOutputStream());
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
            SVNDebugLog.flushStream(getOutputStream());
        }
    }
    
    public SVNAuthentication getLastValidCredentials() {
        return myLastValidAuth;
    }
    
    public void clearAuthenticationCache() {
        myLastValidAuth = null;
    }

    public HTTPStatus request(String method, String path, Map header, StringBuffer body, int ok1, int ok2, OutputStream dst, DefaultHandler handler) throws SVNException {
        byte[] buffer = null;
        if (body != null) {
            try {
                buffer = body.toString().getBytes("UTF-8");
            } catch (UnsupportedEncodingException e) {
                buffer = body.toString().getBytes();
            }
        } 
        return request(method, path, header, buffer != null ? new ByteArrayInputStream(buffer) : null, ok1, ok2, dst, handler);
    }
    
    public HTTPStatus request(String method, String path, Map header, InputStream body, int ok1, int ok2, OutputStream dst, DefaultHandler handler) throws SVNException {
        if (myCredentialsChallenge != null) {
            myCredentialsChallenge.put("methodname", method);
            myCredentialsChallenge.put("uri", path);
        }
        // 1. prompt for ssl client cert if needed, if cancelled - throw cancellation exception.
        ISVNSSLManager sslManager = promptSSLClientCertificate(true);
        String sslRealm = "<" + myHost.getProtocol() + "://" + myHost.getHost() + ":" + myHost.getPort() + ">";
        SVNAuthentication httpAuth = myLastValidAuth;
        String realm = null;

        // 2. create request instance.
        HTTPRequest request = new HTTPRequest();
        request.setConnection(this);
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
                request.setProxyAuthentication(myProxyAuthentication);
                if (httpAuth != null && myCredentialsChallenge != null) {
                    request.setAuthentication(composeAuthResponce(httpAuth, myCredentialsChallenge));
                }
                request.dispatch(method, path, header, ok1, ok2);
                status = request.getStatus();
            } catch (SSLHandshakeException ssl) {
                if (sslManager != null) {
                    SVNSSLAuthentication sslAuth = sslManager.getClientAuthentication();
                    if (sslAuth != null) {
                        close();
                        SVNErrorMessage sslErr = SVNErrorMessage.create(SVNErrorCode.RA_NOT_AUTHORIZED, "SSL handshake failed: ''{0}''", ssl.getLocalizedMessage());
                        myRepository.getAuthenticationManager().acknowledgeAuthentication(false, ISVNAuthenticationManager.SSL, sslRealm, sslErr, sslAuth);
                        promptSSLClientCertificate(false);
                        continue;
                    }
                }
                err = SVNErrorMessage.create(SVNErrorCode.RA_DAV_REQUEST_FAILED, ssl.getLocalizedMessage());
            } catch (IOException e) {
                if (e instanceof SocketTimeoutException) {
                    err = SVNErrorMessage.create(SVNErrorCode.RA_DAV_REQUEST_FAILED, "timed out waiting for server");
                } else {
                    err = SVNErrorMessage.create(SVNErrorCode.RA_DAV_REQUEST_FAILED, e.getMessage());
                }
            } catch (SVNException e) {
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
                    myRepository.getAuthenticationManager().acknowledgeAuthentication(true, ISVNAuthenticationManager.SSL, sslRealm, null, sslAuth);
                }
            }

            if (status.getCode() == HttpURLConnection.HTTP_FORBIDDEN) {
                myLastValidAuth = null;
                close();
                err = request.getErrorMessage();
            } else if (status.getCode() == HttpURLConnection.HTTP_UNAUTHORIZED) {
                myLastValidAuth = null;
                close();
                
                myCredentialsChallenge = HTTPParser.parseAuthParameters((String) request.getResponseHeader().get("WWW-Authenticate"));
                if (myCredentialsChallenge == null) {
                    err = SVNErrorMessage.create(SVNErrorCode.UNSUPPORTED_FEATURE, "HTTP authorization method ''{0}'' is not supported", request.getResponseHeader().get("WWW-Authenticate")); 
                    break;
                }
                myCredentialsChallenge.put("methodname", method);
                myCredentialsChallenge.put("uri", path);
                
                ISVNAuthenticationManager authManager = myRepository.getAuthenticationManager();
                if (authManager == null) {
                    err = request.getErrorMessage();
                    break;
                }

                realm = (String) myCredentialsChallenge.get("realm");
                realm = realm == null ? "" : " " + realm;
                realm = "<" + myHost.getProtocol() + "://" + myHost.getHost() + ":" + myHost.getPort() + ">" + realm;
                if (httpAuth == null) {
                    httpAuth = authManager.getFirstAuthentication(ISVNAuthenticationManager.PASSWORD, realm, myRepository.getLocation());
                } else {
                    authManager.acknowledgeAuthentication(false, ISVNAuthenticationManager.PASSWORD, realm, null, httpAuth);
                    httpAuth = authManager.getNextAuthentication(ISVNAuthenticationManager.PASSWORD, realm, myRepository.getLocation());
                }
                if (httpAuth == null) {
                    err = SVNErrorMessage.create(SVNErrorCode.CANCELLED, "HTTP authorization cancelled");
                    break;
                }
                continue;
            } else if (status.getCode() == HttpURLConnection.HTTP_MOVED_PERM || status.getCode() == HttpURLConnection.HTTP_MOVED_TEMP) {
                close();
                String newLocation = (String) request.getResponseHeader().get("Location");
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
            if (e.getCause() instanceof SVNException) {
                return ((SVNException) e.getCause()).getErrorMessage();
            }
            throw e;
        } finally {
            SVNDebugLog.flushStream(stream);
        }
        return null;
    }
    
    public SVNErrorMessage readData(HTTPRequest request, String method, String path, DefaultHandler handler) throws IOException {
        InputStream is = createInputStream(request.getResponseHeader(), getInputStream());
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
            SVNDebugLog.flushStream(is);
        }
        return null;
    }
    
    public void skipData(HTTPRequest request) throws IOException {
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
            myOutputStream = SVNDebugLog.createLogStream(myOutputStream);
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
        Map header = request != null ? request.getResponseHeader() : null;
        if (header == null || 
                "close".equalsIgnoreCase((String) header.get("Connection")) || 
                "close".equalsIgnoreCase((String) header.get("Proxy-Connection"))) {
            close();
        }
    }
    
    private static InputStream createInputStream(Map readHeader, InputStream is) throws IOException {
        if (readHeader.get("Content-Length") != null) {
            is = new FixedSizeInputStream(is, Long.parseLong(readHeader.get("Content-Length").toString()));
        } else if ("chunked".equals(readHeader.get("Transfer-Encoding"))) {
            is = new ChunkedInputStream(is);
        }
        if ("gzip".equals(readHeader.get("Content-Encoding"))) {
            is = new GZIPInputStream(is);
        }
        return SVNDebugLog.createLogStream(is);
    }

    private static String getProxyAuthString(String username, String password) {
        if (username != null && password != null) {
            String auth = username + ":" + password;
            return "Basic " + SVNBase64.byteArrayToBase64(auth.getBytes());
        }
        return null;
    }

    private static String composeAuthResponce(SVNAuthentication credentials, Map credentialsChallenge) throws SVNException {
        String method = (String) credentialsChallenge.get("");
        StringBuffer result = new StringBuffer();
        if ("basic".equalsIgnoreCase(method) && credentials instanceof SVNPasswordAuthentication) {
            SVNPasswordAuthentication auth = (SVNPasswordAuthentication) credentials;
            String authStr = auth.getUserName() + ":" + auth.getPassword();
            authStr = SVNBase64.byteArrayToBase64(authStr.getBytes());
            result.append("Basic ");
            result.append(authStr);
        } else if ("digest".equalsIgnoreCase(method) && credentials instanceof SVNPasswordAuthentication) {
            SVNPasswordAuthentication auth = (SVNPasswordAuthentication) credentials;
            result.append("Digest ");
            HTTPDigestAuth digestAuth = new HTTPDigestAuth(auth, credentialsChallenge);
            String response = digestAuth.authenticate();
            result.append(response);
        } else {
            SVNErrorManager.authenticationFailed("Authentication method ''{0}'' is not supported", method);
        }

        return result.toString();
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

}
