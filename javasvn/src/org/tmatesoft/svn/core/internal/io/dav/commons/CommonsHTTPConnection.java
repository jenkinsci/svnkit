/*
 * ====================================================================
 * Copyright (c) 2004 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://tmate.org/svn/license.html.
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.core.internal.io.dav.commons;

import java.io.ByteArrayInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import javax.xml.parsers.FactoryConfigurationError;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.apache.commons.httpclient.ConnectTimeoutException;
import org.apache.commons.httpclient.Credentials;
import org.apache.commons.httpclient.Header;
import org.apache.commons.httpclient.HostConfiguration;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpException;
import org.apache.commons.httpclient.HttpMethod;
import org.apache.commons.httpclient.UsernamePasswordCredentials;
import org.apache.commons.httpclient.auth.AuthScheme;
import org.apache.commons.httpclient.auth.AuthScope;
import org.apache.commons.httpclient.auth.CredentialsNotAvailableException;
import org.apache.commons.httpclient.auth.CredentialsProvider;
import org.apache.commons.httpclient.methods.EntityEnclosingMethod;
import org.apache.commons.httpclient.methods.InputStreamRequestEntity;
import org.apache.commons.httpclient.methods.RequestEntity;
import org.apache.commons.httpclient.methods.StringRequestEntity;
import org.apache.commons.httpclient.params.HttpConnectionParams;
import org.apache.commons.httpclient.protocol.Protocol;
import org.apache.commons.httpclient.protocol.ProtocolSocketFactory;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationManager;
import org.tmatesoft.svn.core.auth.ISVNProxyManager;
import org.tmatesoft.svn.core.auth.SVNAuthentication;
import org.tmatesoft.svn.core.auth.SVNPasswordAuthentication;
import org.tmatesoft.svn.core.internal.io.dav.DAVStatus;
import org.tmatesoft.svn.core.internal.io.dav.IHTTPConnection;
import org.tmatesoft.svn.core.internal.io.dav.XMLReader;
import org.tmatesoft.svn.core.internal.util.IMeasurable;
import org.tmatesoft.svn.core.internal.util.SVNSocketFactory;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.util.SVNDebugLog;
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
public class CommonsHTTPConnection implements IHTTPConnection, CredentialsProvider {
    
    private ISVNAuthenticationManager myAuthManager;
    private SVNURL myLocation;
    private SVNAuthentication myLastAuth;
    
    private SAXParser mySAXParser;    
    private static SAXParserFactory ourSAXParserFactory;
    
    private HttpClient myClient;

    public CommonsHTTPConnection(SVNURL location, SVNRepository repository) {
        myLocation = location;
        myAuthManager = repository.getAuthenticationManager();
    }

    public SVNAuthentication getLastValidCredentials() {
        return myLastAuth;
    }

    public DAVStatus request(String method, String path, int depth, String label, StringBuffer requestBody, DefaultHandler handler, int[] okCodes) throws SVNException {
        HttpMethod httpMethod = sendRequest(method, path, initHeader(depth, label, null), bufferToStream(requestBody));
        assertOk(path, httpMethod, okCodes);
        if (httpMethod != null && httpMethod.getStatusCode() != 204) {
            readResponse(handler, httpMethod);
        }
        return createDAVStatus(httpMethod);
    }

    private static DAVStatus createDAVStatus(HttpMethod httpMethod) {
        DAVStatus status = new DAVStatus(httpMethod.getStatusCode(), httpMethod.getStatusText(), null);
        Header[] headers = httpMethod.getResponseHeaders();
        Map headersMap = new HashMap();
        for (int i = 0; headers != null && i < headers.length; i++) {
            String name = headers[i].getName();
            String value = headers[i].getValue();
            if (name != null) {
                headersMap.put(name, value);
            }
        }
        status.setResponseHeader(headersMap);
        return status;
    }

    public DAVStatus request(String method, String path, int depth, String label, StringBuffer requestBody, OutputStream result, int[] okCodes) throws SVNException {
        HttpMethod httpMethod = sendRequest(method, path, initHeader(depth, label, null), bufferToStream(requestBody));
        assertOk(path, httpMethod, okCodes);
        if (httpMethod != null && httpMethod.getStatusCode() != 204) {
            readResponse(result, httpMethod);
        }
        return createDAVStatus(httpMethod);
    }

    public DAVStatus request(String method, String path, Map header, InputStream body, DefaultHandler handler, int[] okCodes) throws SVNException {
        HttpMethod httpMethod = sendRequest(method, path, header, body);
        assertOk(path, httpMethod, okCodes);
        if (httpMethod != null && httpMethod.getStatusCode() != 204) {
            readResponse(handler, httpMethod);
        }
        return createDAVStatus(httpMethod);
    }

    public DAVStatus request(String method, String path, Map header, StringBuffer reqBody, DefaultHandler handler, int[] okCodes) throws SVNException {
        HttpMethod httpMethod = sendRequest(method, path, header, bufferToStream(reqBody));
        assertOk(path, httpMethod, okCodes);
        if (httpMethod != null && httpMethod.getStatusCode() != 204) {
            readResponse(handler, httpMethod);
        }
        return createDAVStatus(httpMethod);
    }

    public void close() {
        if (myClient != null) {
            myClient.getHttpConnectionManager().closeIdleConnections(0);
            myClient = null;
        }
    }

    private HttpMethod sendRequest(String method, String path, Map header, InputStream requestBody) throws SVNException {
        EntityEnclosingMethod httpMethod = new CommonsHTTPMethod(method, path);
        if (header != null) {
            for (Iterator keys = header.keySet().iterator(); keys.hasNext();) {
                String key = (String) keys.next();
                String value = (String) header.get(key);
                httpMethod.addRequestHeader(key, value);
            }
        }
        RequestEntity entity = createRequestEntity(requestBody);
        httpMethod.setRequestEntity(entity);
        if (entity.getContentLength() < 0) {
            httpMethod.setContentChunked(true);
        }
        try {
            getHttpClient().executeMethod(httpMethod);
        } catch (HttpException e) {
            throw new SVNException(e);
        } catch (IOException e) {
            throw new SVNException(e);
        }
        return httpMethod;
    }
    
    private HttpClient getHttpClient() throws SVNException {
        if (myClient == null) {
            myClient = new HttpClient();
            // host
            HostConfiguration hostConfiguration = new HostConfiguration();
            if (!"https".equalsIgnoreCase(myLocation.getProtocol())) {
                hostConfiguration.setHost(myLocation.getHost(), myLocation.getPort(), myLocation.getProtocol());
            } else {
                Protocol protocol = new Protocol("https", new ProtocolSocketFactory() {
                    public Socket createSocket(String host, int port, InetAddress localAddress, int localPort) throws IOException, UnknownHostException {
                        return createSocket(host, port);
                    }
                    public Socket createSocket(String host, int port, InetAddress localAddress, int localPort, HttpConnectionParams params) throws IOException, UnknownHostException, ConnectTimeoutException {
                        return createSocket(host, port);
                    }
                    public Socket createSocket(String host, int port) throws IOException, UnknownHostException {
                        try {
                            return SVNSocketFactory.createSSLSocket(myAuthManager != null ? myAuthManager.getSSLManager(myLocation) : null, host, port);
                        } catch (SVNException e) {
                            throw new IOException(e.getMessage());
                        }
                    }
                }, 443);
                hostConfiguration.setHost(myLocation.getHost(), myLocation.getPort(), protocol);
            }
            ISVNProxyManager proxyManager = myAuthManager != null ? myAuthManager.getProxyManager(myLocation) : null;
            if (proxyManager != null && proxyManager.getProxyHost() != null) {
                hostConfiguration.setProxy(proxyManager.getProxyHost(), proxyManager.getProxyPort());
            }
            myClient.setHostConfiguration(hostConfiguration);
            // params.
            myClient.getParams().setAuthenticationPreemptive(true);
            myClient.getParams().setContentCharset("UTF-8");
            myClient.getParams().setParameter(CredentialsProvider.PROVIDER, this);
            // proxy.
            myClient.getHttpConnectionManager().getParams().setStaleCheckingEnabled(true);
            if (proxyManager != null && proxyManager.getProxyUserName() != null && proxyManager.getProxyPassword() != null) {
                myClient.getState().setProxyCredentials(AuthScope.ANY, 
                        new UsernamePasswordCredentials(proxyManager.getProxyUserName() + ":" + proxyManager.getProxyPassword()));
            }
        }
        return myClient;
    }
    
    private static RequestEntity createRequestEntity(InputStream is) {
        if (is == null) {
            return new StringRequestEntity("");
        }
        long length = -1; 
        if (is instanceof ByteArrayInputStream) {
            length = InputStreamRequestEntity.CONTENT_LENGTH_AUTO;
        } else if (is instanceof IMeasurable) {
            length = ((IMeasurable) is).getLength(); 
        }
        return new InputStreamRequestEntity(is, length, null);
    }

    private static Map initHeader(int depth, String label, Map map) {
        map = map == null ? new HashMap() : map;
        if (label != null && !map.containsKey("Label")) {
            map.put("Label", label);
        }
        if (!map.containsKey("Depth")) {
            if (depth == 1 || depth == 0) {
                map.put("Depth", Integer.toString(depth));
            } else {
                map.put("Depth", "infinity");
            }
        }
        return map;
    }
    
    private static InputStream bufferToStream(StringBuffer buffer) {
        if (buffer == null) {
            return new ByteArrayInputStream(new byte[0]);
        }
        byte[] bytes;
        try {
            bytes = buffer.toString().getBytes("UTF-8");            
        } catch (UnsupportedEncodingException e) {
            bytes = buffer.toString().getBytes();
        }
        return new ByteArrayInputStream(bytes);
    }
    
    private static DAVStatus readError(HttpMethod method, String url) throws SVNException {
        DAVStatus status = createDAVStatus(method);
        status.setErrorText("svn: Request '" + method.getName() + "' failed on '" + url + "': " + method.getStatusText());
        if (method.getStatusCode() == 404) {
            status.setErrorText("svn: '" + url + "' path not found");
        } else {
            if(method != null){
                String errorMessage;
                try {
                    errorMessage = method.getResponseBodyAsString();
                } catch (IOException e) {
                    throw new SVNException(e);
                }
                if (errorMessage.indexOf("<m:human-readable") >= 0) {
                    errorMessage = errorMessage.substring(errorMessage.indexOf("<m:human-readable") + "<m:human-readable".length());
                    if (errorMessage.indexOf('>') >= 0) {
                        errorMessage = errorMessage.substring(errorMessage.indexOf('>') + 1);
                    }
                    if (errorMessage.indexOf("</m:human-readable>") >= 0) {
                        errorMessage = errorMessage.substring(0, errorMessage.indexOf("</m:human-readable>"));
                        errorMessage = "svn: " + errorMessage.trim();
                        status.setErrorText(method.getStatusText() + " : " + errorMessage);
                    }
                }
            }
        }
        return status;
    }

    private static void assertOk(String url, HttpMethod method, int[] codes) throws SVNException {
        int code = method.getStatusCode();
        if (codes == null) {
            // check that all are > 200.
            if (code >= 200 && code < 300) {
                return;
            }
            DAVStatus status = readError(method, url);
            throw new SVNException(status.getErrorText());
        }
        for (int i = 0; i < codes.length; i++) {
            if (code == codes[i]) {
                return;
            }
        }
        DAVStatus status = readError(method, url);
        throw new SVNException(status.getErrorText());
    }

    private void readResponse(OutputStream result, HttpMethod method) throws SVNException {
        InputStream stream = null;
        try {
            stream = SVNDebugLog.createLogStream(method.getResponseBodyAsStream());
            byte[] buffer = new byte[32*1024];
            while (true) {
                int count = stream.read(buffer);
                if (count <= 0) {
                    break;
                }
                if (result != null) {
                    result.write(buffer, 0, count);
                }
            }
        } catch (IOException e) {
            throw new SVNException(e);
        } finally {
            SVNDebugLog.flushStream(stream);
        }
    }

    private void readResponse(DefaultHandler handler, HttpMethod method) throws SVNException {
        InputStream is = null;
        try {
            is = SVNDebugLog.createLogStream(method.getResponseBodyAsStream());
            if (handler == null) {
                while (true) {
                    int r = is.read();
                    if (r < 0) {
                        break;
                    }
                }
            } else {
                if (mySAXParser == null) {
                    mySAXParser = getSAXParserFactory().newSAXParser();
                }
                XMLReader reader = new XMLReader(is);
                while (!reader.isClosed()) {
                    mySAXParser.parse(new InputSource(reader), handler);
                }
            }
        } catch (SAXException e) {
            if (e instanceof SAXParseException) {
                return;
            }
            if (e.getCause() instanceof SVNException) {
                throw (SVNException) e.getCause();
            }
            throw new SVNException(e);
        } catch (ParserConfigurationException e) {
            throw new SVNException(e);
        } catch (EOFException e) {
            // skip it.
        } catch (IOException e) {
            throw new SVNException(e);
        } finally {
            SVNDebugLog.flushStream(is);
        }
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

    public Credentials getCredentials(AuthScheme scheme, String host, int port, boolean proxy) throws CredentialsNotAvailableException {
        if (proxy) {
            return null;
        }
        if (myAuthManager == null) {
            return null;
        }
        String realm = "<" + myLocation.getProtocol() + "://" + host + ":" + port + ">";
        if (scheme.getRealm() != null) {
            realm += " " + scheme.getRealm();
        }
        try {
            if (myLastAuth == null) {
                myLastAuth = myAuthManager.getFirstAuthentication(ISVNAuthenticationManager.PASSWORD, realm, myLocation);
            } else {
                myLastAuth = myAuthManager.getNextAuthentication(ISVNAuthenticationManager.PASSWORD, realm, myLocation);
            }
        } catch (SVNException e) {
            return null;
        }
        return new UsernamePasswordCredentials(myLastAuth.getUserName() + ":" + ((SVNPasswordAuthentication) myLastAuth).getPassword());
    }

}
