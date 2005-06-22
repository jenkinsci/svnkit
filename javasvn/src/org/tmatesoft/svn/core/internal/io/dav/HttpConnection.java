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

package org.tmatesoft.svn.core.internal.io.dav;

import org.tmatesoft.svn.core.io.SVNAuthenticationException;
import org.tmatesoft.svn.core.io.SVNCancelException;
import org.tmatesoft.svn.core.io.SVNException;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.io.SVNRepositoryLocation;
import org.tmatesoft.svn.core.wc.ISVNAuthenticationManager;
import org.tmatesoft.svn.core.wc.SVNAuthentication;
import org.tmatesoft.svn.core.internal.io.svn.SVNAuthenticator;
import org.tmatesoft.svn.util.Base64;
import org.tmatesoft.svn.util.DebugLog;
import org.tmatesoft.svn.util.LoggingInputStream;
import org.tmatesoft.svn.util.LoggingOutputStream;
import org.tmatesoft.svn.util.PathUtil;
import org.tmatesoft.svn.util.SocketFactory;
import org.tmatesoft.svn.util.Version;
import org.xml.sax.SAXException;
import org.xml.sax.SAXNotRecognizedException;
import org.xml.sax.SAXNotSupportedException;
import org.xml.sax.SAXParseException;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.parsers.FactoryConfigurationError;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.zip.GZIPInputStream;

/**
 * @author TMate Software Ltd.
 * 
 */
class HttpConnection {

    private LoggingOutputStream myOutputStream;
    private InputStream myInputStream;
    private Socket mySocket;

    private SVNRepositoryLocation mySVNRepositoryLocation;
    private SAXParser mySAXParser;

    private static SAXParserFactory ourSAXParserFactory;

    private Map myCredentialsChallenge;
    private ISVNAuthenticationManager myAuthManager;
    private SVNAuthentication myLastValidAuth;

    public HttpConnection(SVNRepositoryLocation location, SVNRepository repos) {
        mySVNRepositoryLocation = location;
        myAuthManager = repos.getAuthenticationManager();
    }
    public void connect() throws IOException {
        if (mySocket == null || isStale()) {
            if (mySocket != null) {
                DebugLog.log("connection is silently closed by server, forcing reconnect.");
            }
            close();
            String host = mySVNRepositoryLocation.getHost();
            int port = mySVNRepositoryLocation.getPort();
            IDAVProxyManager proxyManager = DAVRepositoryFactory.getProxyManager();
            String proxyHost = proxyManager.getProxyHost(mySVNRepositoryLocation);
            int proxyPort = proxyManager.getProxyPort(mySVNRepositoryLocation);
            if (proxyManager.isProxyEnabled(mySVNRepositoryLocation) && 
                    proxyPort > 0 && 
                    proxyHost != null) {
                mySocket = SocketFactory.createPlainSocket(proxyHost, proxyPort);
                if (isSecured()) {
                    Map props = new HashMap();
                    if (getProxyAuthString() != null) {
                        props.put("Proxy-Authorization", getProxyAuthString());
                    }
                    myOutputStream = DebugLog.getLoggingOutputStream("http", mySocket.getOutputStream());
                    sendHeader("CONNECT", mySVNRepositoryLocation.getHost() + ":" + mySVNRepositoryLocation.getPort(), props, null);
                    myOutputStream.flush();
                    DAVStatus status = readHeader(new HashMap());
                    if (status != null && status.getResponseCode() == 200) {
                        myInputStream = null;
                        myOutputStream = null;
                        mySocket = SocketFactory.createSSLSocket(DAVRepositoryFactory.getSSLManager(), host, port, mySocket);
                        return;
                    }
                    throw new IOException("couldn't establish http tunnel for proxied secure connection: " + (status != null ? status.getErrorText() + "" : " for unknow reason"));
                }
            } else {
                mySocket = isSecured() ? SocketFactory.createSSLSocket(DAVRepositoryFactory.getSSLManager(), host, port)
                        : SocketFactory.createPlainSocket(host, port);
            }
        }
    }

    private boolean isSecured() {
        return "https".equals(mySVNRepositoryLocation.getProtocol());
    }

    private boolean isStale() throws IOException {
        boolean isStale = true;
        if (mySocket != null) {
            isStale = false;
            try {
                if (mySocket.getInputStream().available() == 0) {
                    int timeout = mySocket.getSoTimeout();
                    try {
                        mySocket.setSoTimeout(1);
                        mySocket.getInputStream().mark(1);
                        int byteRead = mySocket.getInputStream().read();
                        if (byteRead == -1) {
                            isStale = true;
                        } else {
                            mySocket.getInputStream().reset();
                        }
                    } finally {
                        mySocket.setSoTimeout(timeout);
                    }
                }
            } catch (InterruptedIOException e) {
                if (!SocketTimeoutException.class.isInstance(e)) {
                    throw e;
                    
                }
            } catch (IOException e) {
                isStale = true;
            }
        }
        return isStale;
    }

    public void close() {
        if (mySocket != null) {
            try {
                if (myOutputStream != null) {
	                  myOutputStream.log();
                    myOutputStream.flush();
                }
                mySocket.close();
            } catch (IOException e) {
                //
            }
            mySocket = null;
            myOutputStream = null;
            myInputStream = null;
        }
    }

    public DAVStatus request(String method, String path, Map header, InputStream body, DefaultHandler handler, int[] okCodes) throws SVNException {
        DAVStatus status = sendRequest(method, path, initHeader(0, null, header), body);
        // check okCodes, read to status if not ok.
        assertOk(path, status, okCodes);
        if (status != null && status.getResponseCode() == 204) {
            finishResponse(status.getResponseHeader());
        } else if (status != null) {
            readResponse(handler, status.getResponseHeader());
        }
        return status;
    }

    public DAVStatus request(String method, String path, Map header, StringBuffer reqBody, DefaultHandler handler, int[] okCodes) throws SVNException {
        DAVStatus status = sendRequest(method, path, initHeader(0, null, header), reqBody, okCodes);
        if (status != null && status.getResponseCode() == 204) {
            finishResponse(status.getResponseHeader());
        } else if (status != null) {
            readResponse(handler, status.getResponseHeader());
        }
        return status;
    }

    public DAVStatus request(String method, String path, int depth, String label, StringBuffer requestBody, OutputStream result, int[] okCodes) throws SVNException {
        DAVStatus status = sendRequest(method, path, initHeader(depth, label, null), requestBody, okCodes);
        if (status != null && status.getResponseCode() == 204) {
            finishResponse(status.getResponseHeader());
        } else if (status != null) {
            readResponse(result, status.getResponseHeader());
        }
        return status;
    }

    public DAVStatus request(String method, String path, int depth, String label, StringBuffer requestBody, DefaultHandler handler, int[] okCodes)
            throws SVNException {
        DAVStatus status = sendRequest(method, path, initHeader(depth, label, null), requestBody, okCodes);
        if (status != null && status.getResponseCode() == 204) {
            finishResponse(status.getResponseHeader());
        } else if (status != null) {
            readResponse(handler, status.getResponseHeader());
        }
        return status;
    }

    private DAVStatus sendRequest(String method, String path, Map header, StringBuffer requestBody, int[] okCodes) throws SVNException {
		byte[] request = null;
		if (requestBody != null) {
			try {
				request = requestBody.toString().getBytes("UTF-8");
			} catch (UnsupportedEncodingException e) {
				request = requestBody.toString().getBytes();
			}
		}
        DAVStatus status = sendRequest(method, path, header, request != null ? new ByteArrayInputStream(request) : null);
        assertOk(path, status, okCodes);
        return status;
    }

    private DAVStatus sendRequest(String method, String path, Map header, InputStream requestBody) throws SVNException {
        Map readHeader = new HashMap();
        path = PathUtil.removeTrailingSlash(path);
        if (myCredentialsChallenge != null) {
            myCredentialsChallenge.put("methodname", method);
            myCredentialsChallenge.put("uri", path);
        }
        String realm = null;
        SVNAuthentication auth = myLastValidAuth;
        while (true) {
            DAVStatus status;
            try {
                connect();
                if (auth != null && myCredentialsChallenge != null) {
                    header.put("Authorization", composeAuthResponce(auth, myCredentialsChallenge));
                }
                sendHeader(method, path, header, requestBody);
	            logOutputStream();
                readHeader.clear();
                status = readHeader(readHeader);
            } catch (IOException e) {
	            logOutputStream();
                close();
                acknowledgeSSLContext(false);
                throw new SVNException(e);
            } 
            acknowledgeSSLContext(true);
            if (status != null
                    && (status.getResponseCode() == HttpURLConnection.HTTP_UNAUTHORIZED || status.getResponseCode() == HttpURLConnection.HTTP_FORBIDDEN)) {
                myLastValidAuth = null;
                try {
                    skipRequestBody(readHeader);
                } catch (IOException e1) {
                    //
                }
                close();
                myCredentialsChallenge = DAVUtil.parseAuthParameters((String) readHeader.get("WWW-Authenticate"));
                if (myCredentialsChallenge == null) {
                    throw new SVNAuthenticationException("Authentication challenge is not supported:\n" + readHeader.get("WWW-Authenticate"));
                }
                myCredentialsChallenge.put("methodname", method);
                myCredentialsChallenge.put("uri", path);
                realm = (String) myCredentialsChallenge.get("realm");
                if (myAuthManager == null) {
                    throw new SVNAuthenticationException("No credentials defined");
                }
                if (auth == null) {
                    auth = myAuthManager.getFirstAuthentication(ISVNAuthenticationManager.PASSWORD, realm);
                } else {
                    auth = myAuthManager.getNextAuthentication(ISVNAuthenticationManager.PASSWORD, realm);
                }
                if (auth == null) {
                    throw new SVNAuthenticationException("Authentication failed");
                }
                // reset stream!
                if (requestBody instanceof ByteArrayInputStream) {
                    try {
                        requestBody.reset();
                    } catch (IOException e) {
                        //
                    }
                } else if (requestBody != null) {
                    throw new SVNAuthenticationException("Authentication failed");
                }
            } else if (status != null &&
                    (status.getResponseCode() == HttpURLConnection.HTTP_MOVED_PERM || status.getResponseCode() == HttpURLConnection.HTTP_MOVED_TEMP)) {
                try {
                    skipRequestBody(readHeader);
                } catch (IOException e1) {
                    //
                }
                close();
                // reconnect
                String newLocation = (String) readHeader.get("Location");
                if (newLocation == null) {
                    throw new SVNException("can't connect: " + status.getMessage());
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
                throw new SVNException("HTTP 301 MOVED PERMANENTLY: " + newLocation);
            } else if (status != null) {
                if (auth != null && myAuthManager != null && realm != null) {
                    myAuthManager.addAuthentication(realm, auth, myAuthManager.isAuthStorageEnabled());
                }
                // remember creds
                myLastValidAuth = auth;
                status.setResponseHeader(readHeader);
                return status;
            } else if (auth != null) {
                close();
                throw new SVNException("can't connect");
            } else {
                close();
                throw new SVNCancelException();
            }
        }
    }

    private void readError(String url, DAVStatus status) throws SVNException {
        StringBuffer text = new StringBuffer();
        LoggingInputStream stream = null;
        try {
			stream = createInputStream(status.getResponseHeader(), getInputStream());
            byte[] buffer = new byte[1024];
            while (true) {
                int count = stream.read(buffer);
                if (count <= 0) {
                    break;
                }
                text.append(new String(buffer, 0, count));
            }
        } catch (IOException e) {
            throw new SVNException(e);
        } finally {
	        logInputStream(stream);
            close();
            if (status.getResponseCode() == 404) {
                status.setErrorText("svn: '" + url + "' path not found");
            } else {
                String errorMessage = text.toString();
                if (errorMessage.indexOf("<m:human-readable") >= 0) {
                    errorMessage = errorMessage.substring(errorMessage.indexOf("<m:human-readable") + "<m:human-readable".length());
                    if (errorMessage.indexOf('>') >= 0) {
                        errorMessage = errorMessage.substring(errorMessage.indexOf('>') + 1);
                    }
                    if (errorMessage.indexOf("</m:human-readable>") >= 0) {
                        errorMessage = errorMessage.substring(0, errorMessage.indexOf("</m:human-readable>"));
                        errorMessage = "svn: " + errorMessage.trim();
                    }
                }
                status.setErrorText(errorMessage);
            }
        }
    }

    private void readResponse(OutputStream result, Map responseHeader) throws SVNException {
        LoggingInputStream stream = null;
        try {
			stream = createInputStream(responseHeader, getInputStream());
            byte[] buffer = new byte[1024];
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
	        logInputStream(stream);
            finishResponse(responseHeader);
        }
    }

    private void readResponse(DefaultHandler handler, Map responseHeader) throws SVNException {
        LoggingInputStream is = null;
        try {
			is = createInputStream(responseHeader, getInputStream());
            XMLInputStream xmlIs = new XMLInputStream(is);

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
                while (!xmlIs.isClosed()) {
                    mySAXParser.parse(xmlIs, handler);
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
            logInputStream(is);
            finishResponse(responseHeader);
        }
    }

    private static final char[] CRLF = { '\r', '\n' };
    private static final byte[] CRLF_BYTES = { '\r', '\n' };

    private void sendHeader(String method, String path, Map header, InputStream requestBody) throws IOException {
        StringBuffer sb = new StringBuffer();
        sb.append(method);
        sb.append(' ');
        boolean isProxied = DAVRepositoryFactory.getProxyManager().isProxyEnabled(mySVNRepositoryLocation); 
        if (isProxied && !isSecured()) {
            // prepend path with host name.
            sb.append("http://");
            sb.append(mySVNRepositoryLocation.getHost());
            sb.append(":");
            sb.append(mySVNRepositoryLocation.getPort());            
        }
        DAVUtil.getCanonicalPath(path, sb);
        sb.append(' ');
        sb.append("HTTP/1.1");
        sb.append(HttpConnection.CRLF);
        sb.append("Host: ");
        sb.append(mySVNRepositoryLocation.getHost());
        sb.append(":");
        sb.append(mySVNRepositoryLocation.getPort());
        sb.append(HttpConnection.CRLF);
        sb.append("User-Agent: ");
        sb.append(Version.getVersionString());
        sb.append(HttpConnection.CRLF);
        sb.append("Keep-Alive:");
        sb.append(HttpConnection.CRLF);
        sb.append("Connection: TE, Keep-Alive");
        sb.append(HttpConnection.CRLF);
        sb.append("TE: trailers");
        sb.append(HttpConnection.CRLF);
        if (isProxied && !isSecured() && getProxyAuthString() != null) {
            sb.append("Proxy-Authorization: " + getProxyAuthString());
            sb.append(HttpConnection.CRLF);
        }
        boolean chunked = false;
        if (requestBody instanceof ByteArrayInputStream) {
            sb.append("Content-Length: ");
            sb.append(requestBody.available());
        } else if (requestBody != null) {
            sb.append("Transfer-Encoding: chunked");
            chunked = true;
        } else {
            sb.append("Content-Lenght: 0");
        }
        sb.append(HttpConnection.CRLF);
        sb.append("Accept-Encoding: gzip");
        sb.append(HttpConnection.CRLF);
        if (header != null) {
            if (!header.containsKey("Content-Type")) {
                sb.append("Content-Type: text/xml; charset=\"utf-8\"");
                sb.append(HttpConnection.CRLF);
            }
            for (Iterator keys = header.keySet().iterator(); keys.hasNext();) {
                Object key = keys.next();
                sb.append(key.toString());
                sb.append(": ");
                sb.append(header.get(key).toString());
                sb.append(HttpConnection.CRLF);
            }
        }

        getOutputStream().write(sb.toString().getBytes());
        getOutputStream().write(HttpConnection.CRLF_BYTES);
        if (requestBody != null) {
            byte[] buffer = new byte[2048];
            while (true) {
                int read = requestBody.read(buffer);
                if (chunked) {
                    if (read > 0) {
                        getOutputStream().write(Integer.toHexString(read).getBytes());
                        getOutputStream().write(HttpConnection.CRLF_BYTES);
                        getOutputStream().write(buffer, 0, read);
                        getOutputStream().write(HttpConnection.CRLF_BYTES);
                    } else {
                        getOutputStream().write('0');
                        getOutputStream().write(HttpConnection.CRLF_BYTES);
                        getOutputStream().write(HttpConnection.CRLF_BYTES);
                        break;
                    }
                } else {
                    if (read > 0) {
                        getOutputStream().write(buffer, 0, read);
                    } else {
                        break;
                    }
                }
            }
        }
        getOutputStream().flush();
    }

    private DAVStatus readHeader(Map headerProperties) throws IOException {
        return readHeader(headerProperties, false);        
    }
    private DAVStatus readHeader(Map headerProperties, boolean firstLineOnly) throws IOException {
        DAVStatus responseCode = null;
        StringBuffer line = new StringBuffer();
        LoggingInputStream is = DebugLog.getLoggingInputStream("http", getInputStream());

        boolean firstLine = true;
        try {
	        while (true) {
	            int read = is.read();
	            if (read < 0) {
	                return null;
	            }
	            if (read != '\n' && read != '\r') {
	                line.append((char) read);
	                continue;
	            }
	            // eol read.
	            if (read == '\r') {
	                is.mark(1);
	                read = is.read();
	                if (read < 0) {
	                    return null;
	                }
	                if (read != '\n') {
	                    is.reset();
	                }
                    if (firstLineOnly) {
                        return DAVStatus.parse(line.toString());
                    }
	            }
	            String lineStr = line.toString();
	            if (lineStr.trim().length() == 0) {
	                if (firstLine) {
	                    line = new StringBuffer();
	                    firstLine = false;
	                    continue;
	                }
	                // first empty line (+ eol) read.
	                break;
	            }
	            firstLine = false;
	
	            int index = line.indexOf(":");
	            if (index >= 0 && headerProperties != null) {
	                String name = line.substring(0, index);
	                String value = line.substring(index + 1);
	                headerProperties.put(name.trim(), value.trim());
	            } else if (responseCode == null) {
	                responseCode = DAVStatus.parse(lineStr);
	            }
	
	            line.delete(0, line.length());
	        }
        } finally {
        	is.log();
        }
        return responseCode;
    }

    private void skipRequestBody(Map header) throws IOException {
        InputStream is = createInputStream(header, getInputStream());
        while (is.skip(2048) > 0) {}
    }

    private LoggingOutputStream getOutputStream() throws IOException {    	
        if (myOutputStream == null) {
        	if (mySocket == null) {
        		return null;
        	}
            myOutputStream = DebugLog.getLoggingOutputStream("http", new BufferedOutputStream(mySocket.getOutputStream(), 2048));
        }
        return myOutputStream;
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

    private static LoggingInputStream createInputStream(Map readHeader, InputStream is) throws IOException {
        if (readHeader.get("Content-Length") != null) {
            is = new FixedSizeInputStream(is, Long.parseLong(readHeader.get("Content-Length").toString()));
        } else if ("chunked".equals(readHeader.get("Transfer-Encoding"))) {
            is = new ChunkedInputStream(is);
        }
        if ("gzip".equals(readHeader.get("Content-Encoding"))) {
            DebugLog.log("using GZIP to decode server responce");
            is = new GZIPInputStream(is);
        }
        return DebugLog.getLoggingInputStream("http", is);
    }


    private static String composeAuthResponce(SVNAuthentication credentials, Map credentialsChallenge) throws SVNAuthenticationException {
        String method = (String) credentialsChallenge.get("");
        StringBuffer result = new StringBuffer();
        if ("basic".equalsIgnoreCase(method)) {
            String auth = credentials.getUserName() + ":" + credentials.getPassword();
            auth = Base64.byteArrayToBase64(auth.getBytes());
            result.append("Basic ");
            result.append(auth);
        } else if ("digest".equalsIgnoreCase(method)) {
            result.append("Digest ");
            HttpDigestAuth digestAuth = new HttpDigestAuth(credentials, credentialsChallenge);
            String response = digestAuth.authenticate();
            result.append(response);
        } else {
            throw new SVNAuthenticationException("Authentication method '" + method + "' is not supported");
        }

        return result.toString();
    }

    private static synchronized SAXParserFactory getSAXParserFactory() throws FactoryConfigurationError, ParserConfigurationException,
            SAXNotRecognizedException, SAXNotSupportedException {
        if (ourSAXParserFactory == null) {
            ourSAXParserFactory = SAXParserFactory.newInstance();
            ourSAXParserFactory.setFeature("http://xml.org/sax/features/namespaces", true);
            ourSAXParserFactory.setNamespaceAware(true);
            ourSAXParserFactory.setValidating(false);
        }
        return ourSAXParserFactory;
    }

    private void assertOk(String url, DAVStatus status, int[] codes) throws SVNException {
        int code = status.getResponseCode();
        if (codes == null) {
            // check that all are > 200.
            if (code >= 200 && code < 300) {
                return;
            }
            readError(url, status);
            throw new SVNException(status.getErrorText());
        }
        for (int i = 0; i < codes.length; i++) {
            if (code == codes[i]) {
                return;
            }
        }
        readError(url, status);
        throw new SVNException(status.getErrorText());
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

    private void acknowledgeSSLContext(boolean accepted) {
        if (mySVNRepositoryLocation == null || !"https".equalsIgnoreCase(mySVNRepositoryLocation.getProtocol())) {
            return;
        }

        String host = mySVNRepositoryLocation.getHost();
        int port = mySVNRepositoryLocation.getPort();
        DAVRepositoryFactory.getSSLManager().acknowledgeSSLContext(host, port, accepted);
    }

    private void finishResponse(Map readHeader) {
        if (myOutputStream != null) {
            try {
                myOutputStream.flush();
            } catch (IOException ex) {
                new SVNException(ex);
            }
        }

        if ("close".equals(readHeader.get("Connection")) || 
                "close".equals(readHeader.get("Proxy-Connection"))) {
            DebugLog.log("closing connection due to server request");
            close();
        }
    }

    private void logInputStream(LoggingInputStream is) {
		if (is != null) {
			is.log();
		}
	}

	private void logOutputStream() {
		try {
			if (getOutputStream() != null) {
				getOutputStream().log();
			}
		} catch (IOException ex) {
            //
        }
	}

    private String getProxyAuthString() {
        String username = DAVRepositoryFactory.getProxyManager().getProxyUserName(mySVNRepositoryLocation);
        String password = DAVRepositoryFactory.getProxyManager().getProxyPassword(mySVNRepositoryLocation);
        if (username != null && password != null) {
            String auth = username + ":" + password;
            return "Basic " + Base64.byteArrayToBase64(auth.getBytes());
        }
        return null;
    }

    public SVNAuthentication getLastValidCredentials() {
        return myLastValidAuth;
    }
}
