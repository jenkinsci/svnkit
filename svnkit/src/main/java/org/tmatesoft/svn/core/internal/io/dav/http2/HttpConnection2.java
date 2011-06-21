package org.tmatesoft.svn.core.internal.io.dav.http2;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.text.ParseException;
import java.util.Collection;
import java.util.Map;

import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.TrustManager;
import javax.xml.parsers.FactoryConfigurationError;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;

import org.apache.http.Header;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.ProtocolException;
import org.apache.http.StatusLine;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.Credentials;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.protocol.ClientContext;
import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.conn.scheme.PlainSocketFactory;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.entity.AbstractHttpEntity;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.entity.InputStreamEntity;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.BasicAuthCache;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.client.DefaultRedirectStrategy;
import org.apache.http.impl.conn.SingleClientConnManager;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.HTTP;
import org.apache.http.protocol.HttpContext;
import org.apache.http.util.EntityUtils;
import org.tmatesoft.svn.core.SVNCancelException;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationManager;
import org.tmatesoft.svn.core.auth.SVNAuthentication;
import org.tmatesoft.svn.core.auth.SVNPasswordAuthentication;
import org.tmatesoft.svn.core.internal.io.dav.DAVElement;
import org.tmatesoft.svn.core.internal.io.dav.handlers.DAVErrorHandler;
import org.tmatesoft.svn.core.internal.io.dav.http.HTTPHeader;
import org.tmatesoft.svn.core.internal.io.dav.http.HTTPSSLKeyManager;
import org.tmatesoft.svn.core.internal.io.dav.http.HTTPStatus;
import org.tmatesoft.svn.core.internal.io.dav.http.IHTTPConnection;
import org.tmatesoft.svn.core.internal.io.dav.http.XMLReader;
import org.tmatesoft.svn.core.internal.util.SVNSSLUtil;
import org.tmatesoft.svn.core.internal.wc.SVNCancellableOutputStream;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.util.SVNLogType;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

public class HttpConnection2 implements IHTTPConnection, CredentialsProvider {
    
    private static final String AUTH_USED_CREDENTIALS = "auth.used.credentials";
    private static final String AUTH_REALM = "auth.realm";
    private static final String AUTH_USER_ERROR = "auth.user.exception";
    
    private DefaultHttpClient myHttpClient;
    private HttpHost myHttpHost;
    private SAXParser mySAXParser;
    private BasicHttpContext myHttpContext;
    private ISVNAuthenticationManager myAuthenticationManager;
    private SVNURL myLocation;
    private SVNAuthentication myLastValidCredentials;
    private TrustManager myTrustManager;
    private HTTPSSLKeyManager myKeyManager;

    public HttpConnection2(SVNRepository repository) {
        SVNURL location = repository.getLocation();
        
        myHttpHost = new HttpHost(location.getHost(), location.getPort(), location.getProtocol());
        myHttpContext = new BasicHttpContext();
        myAuthenticationManager = repository.getAuthenticationManager();
        myLocation = repository.getLocation();

        clearAuthenticationCache();
    }
    
    
    private DefaultHttpClient createHttpClient() throws SVNException {
        String sslRealm = "<" + myLocation.getProtocol() + "://" + myLocation.getHost() + ":" + myLocation.getPort() + ">";

        SchemeRegistry registry = new SchemeRegistry();
        if ("http".equalsIgnoreCase(myLocation.getProtocol())) {
            registry.register(new Scheme("http", myLocation.getPort(), new PlainSocketFactory()));
        } else {
            myTrustManager = hasAuthenticationManager() ? myAuthenticationManager.getTrustManager(myLocation) : null;
            myKeyManager = hasAuthenticationManager() ? new HTTPSSLKeyManager(myAuthenticationManager, sslRealm, myLocation) : null;
            registry.register(new Scheme("https", myLocation.getPort(), new HttpSSLSocketFactory(new KeyManager[] {myKeyManager}, myTrustManager)));
        }
        
        ClientConnectionManager connectionManager = new SingleClientConnManager(registry);
        DefaultHttpClient client = new DefaultHttpClient(connectionManager);
        client.setCredentialsProvider(this);
        client.setRedirectStrategy(new DefaultRedirectStrategy() {            
            public HttpUriRequest getRedirect(HttpRequest request, HttpResponse response, HttpContext context) throws ProtocolException {
                if (request instanceof HttpDAVRequest) {
                    HttpDAVRequest davRequest = (HttpDAVRequest) request;
                    URI uri = getLocationURI(request, response, context);
                    URI oldURI = davRequest.getURI();
                    try {
                        oldURI = new URI(oldURI.getScheme(), oldURI.getUserInfo(), oldURI.getHost(), oldURI.getPort(), oldURI.getPath() + "/", oldURI.getQuery(), oldURI.getFragment());
                    } catch (URISyntaxException e) {
                        throw new ProtocolException(e.getMessage());
                    }
                    if (oldURI.equals(uri)) {
                        davRequest = new HttpDAVRequest(davRequest.getMethod());
                        davRequest.setURI(uri);
                        return davRequest;
                    }
                }
                throw new ProtocolException(response.getStatusLine().toString());
            }
        });
        
        return client;
    }

    public void setSpoolResponse(boolean spoolResponse) {
    }

    public HTTPStatus request(String method, String path, HTTPHeader header,
            StringBuffer body, int ok1, int ok2, OutputStream dst,
            DefaultHandler handler) throws SVNException {
        return request(method, path, header, body, ok1, ok2, dst, handler, null);
    }

    public HTTPStatus request(String method, String path, HTTPHeader header,
            StringBuffer body, int ok1, int ok2, OutputStream dst,
            DefaultHandler handler, SVNErrorMessage context)
            throws SVNException {
        return request(method, path, header, (Object) body, ok1, ok2, dst, handler, null);
    }

    public HTTPStatus request(String method, String path, HTTPHeader header,
            InputStream body, int ok1, int ok2, OutputStream dst,
            DefaultHandler handler) throws SVNException {
        return request(method, path, header, body, ok1, ok2, dst, handler, null);
    }
    
    public HTTPStatus request(String method, String path, HTTPHeader header,
            InputStream body, int ok1, int ok2, OutputStream dst,
            DefaultHandler handler, SVNErrorMessage context)
            throws SVNException {
        return request(method, path, header, (Object) body, ok1, ok2, dst, handler, context);
    }
    
    private HTTPStatus request(String method, String path, HTTPHeader header,
            Object body, int ok1, int ok2, OutputStream dst,
            DefaultHandler handler, SVNErrorMessage context)
            throws SVNException {
        HttpDAVRequest request = createHttpRequest(method, path);
        addDefaultAndCustomHeaders(header, request);
        
        HTTPStatus httpStatus = null;        
        HttpResponse response = null;
        SVNErrorMessage error = null;
        
        resetAuthenticationState();
        
        while (true) {
            try {
                request.setEntity(createRequestEntity(body));

                response = getHttpClient().execute(getHttpHost(), request, getHttpContext());
                if (response == null) {
                    throw new ClientProtocolException("Unexpected NULL HttpResponse object");
                } else if (response.getStatusLine() == null) {
                    throw new ClientProtocolException("Unexpected NULL StatusLine object");
                }
            } catch (ClientProtocolException e) {
                error = SVNErrorMessage.create(SVNErrorCode.RA_DAV_REQUEST_FAILED, e);
            } catch (SSLHandshakeException e) {
                if (e.getCause() instanceof SVNSSLUtil.CertificateNotTrustedException) {
                    SVNErrorManager.cancel(e.getMessage(), SVNLogType.NETWORK);
                } else {
                    SVNErrorMessage sslErr = SVNErrorMessage.create(SVNErrorCode.RA_NOT_AUTHORIZED, "SSL handshake failed: ''{0}''", new Object[] { e.getMessage() }, SVNErrorMessage.TYPE_ERROR, e);
                    acknowledgeSSLContext(sslErr);
                    if (hasAuthenticationManager() && myKeyManager != null) {
                        consumeResponse(response);
                        continue;
                    }
                    error = sslErr;
                }
            } catch (SVNCancellableOutputStream.IOCancelException e) {
                SVNErrorManager.cancel(e.getMessage(), SVNLogType.NETWORK);
            } catch (IOException e) {
                error = SVNErrorMessage.create(SVNErrorCode.RA_DAV_REQUEST_FAILED, e);
            } 
         
            if (error == null) {
                error = (SVNErrorMessage) getHttpContext().getAttribute(AUTH_USER_ERROR);
            }
            if (error != null) {
                consumeResponse(response);
                close();
                SVNErrorManager.error(error, SVNLogType.NETWORK);
            }
            
            try {
                acknowledgeSSLContext(null);
                
                httpStatus = createHttpStatus(response);
                
                int code = response.getStatusLine().getStatusCode();
                error = createErrorFromStatus(response.getStatusLine(), path, context); 
                if (code == HttpURLConnection.HTTP_FORBIDDEN) {
                    acknowledgeCredentials(error);
                    SVNErrorManager.error(error, SVNLogType.NETWORK);
                } else if (code == HttpURLConnection.HTTP_UNAUTHORIZED) {
                    acknowledgeCredentials(error);
                    if (hasAuthenticationManager()) {
                        error = null;
                        continue;
                    }
                    SVNErrorManager.error(error, SVNLogType.NETWORK);
                } else if (code == HttpURLConnection.HTTP_PROXY_AUTH) {
                    acknowledgeProxyContext();
                    SVNErrorManager.error(error, SVNLogType.NETWORK);
                } else if (code == HttpURLConnection.HTTP_MOVED_PERM || code == HttpURLConnection.HTTP_MOVED_TEMP) {
                    SVNErrorManager.error(error, SVNLogType.NETWORK);
                }
                acknowledgeCredentials(null);

                InputStream is = response.getEntity().getContent();
                if (!isExpectedCode(method, code, ok1, ok2)) {
                    error = readError(method, path, response.getStatusLine(), is, context);
                } else if (code >= 300) {
                    SVNErrorMessage serverError = readError(method, path, response.getStatusLine(), is, context);
                    httpStatus.setError(serverError);
                    error = null;
                } else if (dst != null) {
                    response.getEntity().writeTo(dst);
                    error = null;
                } else if (handler != null) {
                    error = parseXMLEntity(method, path, handler, response.getEntity().getContent());
                } else {
                    error = null;
                }
            } catch (SVNCancellableOutputStream.IOCancelException e) {
                error = SVNErrorMessage.create(SVNErrorCode.CANCELLED, e);
            } catch (IOException e) {
                error = SVNErrorMessage.create(SVNErrorCode.RA_DAV_REQUEST_FAILED, e);
            } finally {
                consumeResponse(response);
            }         
            break;
        }
        if (error != null) {
            SVNErrorManager.error(error, SVNLogType.NETWORK);
        }
        return httpStatus;
    }


    private void consumeResponse(HttpResponse response) {
        try {
            EntityUtils.consume(response.getEntity());
        } catch (IOException e) {
        }
    }


    private SVNErrorMessage createErrorFromStatus(StatusLine statusLine, String path, SVNErrorMessage context) {
        SVNErrorCode errorCode = SVNErrorCode.RA_DAV_REQUEST_FAILED;
        String contextMessage = context != null ? context.getMessageTemplate() : null;
        Object[] contextObjects = context != null ? context.getRelatedObjects() : null; 
        String message = statusLine != null ? statusLine.getStatusCode() + " " + statusLine.getReasonPhrase() : "";
        if (statusLine != null && statusLine.getStatusCode() == HttpURLConnection.HTTP_FORBIDDEN || statusLine.getStatusCode() == HttpURLConnection.HTTP_UNAUTHORIZED) {
            errorCode = SVNErrorCode.RA_NOT_AUTHORIZED;
            message = statusLine.getStatusCode() + " " + statusLine.getReasonPhrase();
        } else if (statusLine != null && statusLine.getStatusCode() == HttpURLConnection.HTTP_NOT_FOUND) {
            errorCode = SVNErrorCode.FS_NOT_FOUND;
        } else if (statusLine != null && (statusLine.getStatusCode() == HttpURLConnection.HTTP_MOVED_PERM || 
                statusLine.getStatusCode() == HttpURLConnection.HTTP_MOVED_TEMP)) {
            message = statusLine.getStatusCode() == HttpURLConnection.HTTP_MOVED_PERM ? "Repository moved permanently to ''{0}''; please relocate" : 
                "Repository moved temporarily to ''{0}''; please relocate";
            return SVNErrorMessage.create(SVNErrorCode.RA_DAV_RELOCATED, message, path);
        }
        // extend context object to include host:port (empty location).
        Object[] messageObjects = contextObjects == null ? new Object[1] : new Object[contextObjects.length + 1];
        int index = messageObjects.length - 1;
        messageObjects[messageObjects.length - 1] = myLocation.getHost() + ":" + myLocation.getPort();
        if (messageObjects.length > 1) {
            System.arraycopy(contextObjects, 0, messageObjects, 0, contextObjects.length);
        }
        return SVNErrorMessage.create(errorCode, contextMessage + ": " + message + " ({" + index + "})", messageObjects);
    }


    private HTTPStatus createHttpStatus(HttpResponse response) {
        HTTPStatus httpStatus;
        try {
            httpStatus = HTTPStatus.createHTTPStatus(response.getStatusLine().toString());
        } catch (ParseException e) {
            return null;
        }
        HTTPHeader responseHeaders = new HTTPHeader();
        Header[] allHeaders = response.getAllHeaders();
        for (int i = 0; i < allHeaders.length; i++) {
            responseHeaders.addHeaderValue(allHeaders[i].getName(), allHeaders[i].getValue());
        }
        httpStatus.setHeader(responseHeaders);
        return httpStatus;
    }


    private void acknowledgeProxyContext() {
    }

    private void acknowledgeSSLContext(SVNErrorMessage error) throws SVNException {
        if (myKeyManager != null) {
            myKeyManager.acknowledgeAndClearAuthentication(error);
        }
    }

    private void acknowledgeCredentials(SVNErrorMessage error) throws SVNException {
        if (hasAuthenticationManager()) {
            SVNAuthentication credentials = (SVNAuthentication) getHttpContext().getAttribute(AUTH_USED_CREDENTIALS);
            String realm = (String) getHttpContext().getAttribute(AUTH_REALM);
            if (credentials != null && realm != null) {
                myAuthenticationManager.acknowledgeAuthentication(error == null, ISVNAuthenticationManager.PASSWORD, realm, error, credentials);
                if (error == null) {
                    myLastValidCredentials = credentials;
                }
            }
        }
        
    }

    private void resetAuthenticationState() {
        getHttpContext().setAttribute(AUTH_REALM, null);
        getHttpContext().setAttribute(AUTH_USED_CREDENTIALS, null);
        getHttpContext().setAttribute(AUTH_USER_ERROR, null);
    }

    private boolean isExpectedCode(String method, int code, int ok1, int ok2) {
        boolean notExpected = false;        
        if (ok1 >= 0) {
            if (ok1 == 0) {
                ok1 = "PROPFIND".equals(method) ? 207 : 200;
            }
            if (ok2 <= 0) {
                ok2 = ok1;
            }
            notExpected = !(code == ok1 || code == ok2); 
        }
        return !notExpected;
    }


    private boolean hasAuthenticationManager() {
        return myAuthenticationManager != null;
    }

    private HttpContext getHttpContext() {
        return myHttpContext;
    }


    private HttpDAVRequest createHttpRequest(String method, String path) throws SVNException {
        HttpDAVRequest request = new HttpDAVRequest(method);
        try {
            request.setURI(new URI(null, null, path, null));
        } catch (URISyntaxException e) {
            SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.RA_DAV_REQUEST_FAILED, e), SVNLogType.NETWORK);
        }
        return request;
    }


    private void addDefaultAndCustomHeaders(HTTPHeader header, HttpRequest request) {
        if (header != null) {
            @SuppressWarnings("unchecked")
            Map<String, Collection<String>> additionalHeaders = (Map<String, Collection<String>>) header.getRawHeaders();
            for (String headerName : additionalHeaders.keySet()) {
                for (String value : additionalHeaders.get(headerName)) {
                    request.addHeader(headerName, value);
                }
            }
        }
        request.addHeader(HTTPHeader.DAV_HEADER, DAVElement.DEPTH_OPTION);
        request.addHeader(HTTPHeader.DAV_HEADER, DAVElement.MERGE_INFO_OPTION);
        request.addHeader(HTTPHeader.DAV_HEADER, DAVElement.LOG_REVPROPS_OPTION);
        
        if (!request.containsHeader(HTTP.CONTENT_TYPE)) {
            request.addHeader(HTTP.CONTENT_TYPE, "text/xml; charset=\"utf-8\"");
        }
    }

    private AbstractHttpEntity createRequestEntity(Object body) throws UnsupportedEncodingException {
        AbstractHttpEntity entity = null; 
        
        if (body instanceof ByteArrayInputStream) {
            int length = ((ByteArrayInputStream) body).available();
            entity = new InputStreamEntity((InputStream) body, length);
        } else if (body instanceof byte[]) {
            entity = new ByteArrayEntity((byte[]) body);
        } else if (body instanceof InputStream){
            entity = new InputStreamEntity((InputStream) body, -1);
        } else if (body instanceof StringBuffer) {
            entity = new StringEntity(body.toString(), "text/xml", HTTP.UTF_8);
        }
        
        return entity;
    }

    private SVNErrorMessage readError(String method, String path, StatusLine statusLine, InputStream content, SVNErrorMessage context) throws SVNCancelException {
        if (statusLine.getStatusCode() == HttpURLConnection.HTTP_NOT_FOUND) {
            context = SVNErrorMessage.create(context.getErrorCode(), "''{0}'' path not found", path);
        } 
        SVNErrorMessage error = createErrorFromStatus(statusLine, path, context);
        SVNErrorMessage davError = parseXMLErrorEntity(method, path, content);
        if (davError != null) {
            if (error != null) {
                davError.setChildErrorMessage(error);
            }
            return davError; 
        }
        return error;
    }

    private SVNErrorMessage parseXMLErrorEntity(String method, String path, InputStream content) throws SVNCancelException {
        DAVErrorHandler errorHandler = new DAVErrorHandler();
        parseXMLEntity(method, path, errorHandler, content);
        return errorHandler.getErrorMessage();
    }

    private SVNErrorMessage parseXMLEntity(String method, String path, DefaultHandler handler, InputStream content) throws SVNCancelException {
        SVNErrorMessage error = null;
        
        try {
            XMLReader reader = new XMLReader(content);
            while (!reader.isClosed()) {
                org.xml.sax.XMLReader xmlReader = getSAXParser().getXMLReader();
                xmlReader.setContentHandler(handler);
                xmlReader.setDTDHandler(handler);
                xmlReader.setErrorHandler(handler);
                xmlReader.setEntityResolver(HttpXMLUtil.NO_ENTITY_RESOLVER);
                xmlReader.parse(new InputSource(reader));
            }
        } catch (SVNCancellableOutputStream.IOCancelException e) {
            mySAXParser = null;
            SVNErrorManager.cancel(e.getMessage(), SVNLogType.NETWORK);
        } catch (IOException e) {
            mySAXParser = null;
            error = SVNErrorMessage.create(SVNErrorCode.RA_DAV_REQUEST_FAILED, e);
        } catch (SAXException e) {
            mySAXParser = null;
            error = SVNErrorMessage.create(SVNErrorCode.RA_DAV_REQUEST_FAILED, "Processing {0} request response failed: {1} ({2}) ",  new Object[] {method, e.getMessage(), path});
        } catch (ParserConfigurationException e) {
            mySAXParser = null;
            error = SVNErrorMessage.create(SVNErrorCode.RA_DAV_REQUEST_FAILED, "XML parser configuration error while processing {0} request response: {1} ({2}) ",  new Object[] {method, e.getMessage(), path});
        } finally {
            if (mySAXParser != null) {
                org.xml.sax.XMLReader xmlReader = null;
                try {
                    xmlReader = mySAXParser.getXMLReader();
                } catch (SAXException e) {
                }
                if (xmlReader != null) {
                    xmlReader.setContentHandler(HttpXMLUtil.DEFAULT_SAX_HANDLER);
                    xmlReader.setDTDHandler(HttpXMLUtil.DEFAULT_SAX_HANDLER);
                    xmlReader.setErrorHandler(HttpXMLUtil.DEFAULT_SAX_HANDLER);
                    xmlReader.setEntityResolver(HttpXMLUtil.NO_ENTITY_RESOLVER);
                }
            }
        }        
        return error;
    }
    
    private SAXParser getSAXParser() throws ParserConfigurationException, SAXException, FactoryConfigurationError {
        if (mySAXParser == null) {
            mySAXParser = HttpXMLUtil.getSAXParserFactory().newSAXParser();
        }
        return mySAXParser;
    }

    public SVNAuthentication getLastValidCredentials() {
        return myLastValidCredentials;
    }

    public void clearAuthenticationCache() {
        myLastValidCredentials = null;
        
        myHttpContext.setAttribute(ClientContext.AUTH_CACHE, new BasicAuthCache());
        myHttpContext.setAttribute(AUTH_USED_CREDENTIALS, null);
        myHttpContext.setAttribute(AUTH_REALM, null);
        myHttpContext.setAttribute(AUTH_USER_ERROR, null);
    }

    public void close() {
        clearAuthenticationCache();
        myHttpClient.getConnectionManager().shutdown();
    }

    private HttpHost getHttpHost() {
        return myHttpHost;
    }

    private DefaultHttpClient getHttpClient() throws SVNException {
        if (myHttpClient == null) {
            myHttpClient = createHttpClient();
        }
        return myHttpClient;
    }

    public void setCredentials(AuthScope authscope, Credentials credentials) {
    }

    public Credentials getCredentials(AuthScope authscope) {
        if (authscope.getRealm() == null && myLastValidCredentials instanceof SVNPasswordAuthentication) {
            return new UsernamePasswordCredentials(myLastValidCredentials.getUserName(), ((SVNPasswordAuthentication) myLastValidCredentials).getPassword());
        }
        String realm = null;
        if (authscope.getRealm() != null) {
            realm = "<" + myLocation.getProtocol() + "://" + authscope.getHost() + ":" + authscope.getPort() + "> " + authscope.getRealm();
        } else {
            return null;
        }
        
        SVNAuthentication credentials = null;
        if (realm != null) {
            try {
                if (getHttpContext().getAttribute(AUTH_USED_CREDENTIALS) == null) {
                    credentials = myAuthenticationManager.getFirstAuthentication(ISVNAuthenticationManager.PASSWORD, realm, myLocation);
                } else {
                    credentials = myAuthenticationManager.getNextAuthentication(ISVNAuthenticationManager.PASSWORD, realm, myLocation);
                }
            } catch (SVNException e) {
                getHttpContext().setAttribute(AUTH_USER_ERROR, e);
                getHttpContext().setAttribute(AUTH_REALM, null);
                getHttpContext().setAttribute(AUTH_USED_CREDENTIALS, null);
            }
        }
        getHttpContext().setAttribute(AUTH_REALM, realm);
        getHttpContext().setAttribute(AUTH_USED_CREDENTIALS, credentials);
        if (credentials != null && credentials instanceof SVNPasswordAuthentication) {
            getHttpContext().setAttribute(AUTH_USER_ERROR, null);
            return new UsernamePasswordCredentials(credentials.getUserName(), ((SVNPasswordAuthentication) credentials).getPassword());
        }
        getHttpContext().setAttribute(AUTH_USER_ERROR, SVNErrorMessage.create(SVNErrorCode.CANCELLED, "authentication cancelled"));
        return null;
    }

    public void clear() {
        getHttpContext().setAttribute(AUTH_USER_ERROR, null);
        getHttpContext().setAttribute(AUTH_REALM, null);
        getHttpContext().setAttribute(AUTH_USED_CREDENTIALS, null);
    }
}
