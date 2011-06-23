package org.tmatesoft.svn.core.internal.io.dav.http2;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;

import javax.net.ssl.TrustManager;

import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.Credentials;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.auth.params.AuthPNames;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.params.AuthPolicy;
import org.apache.http.client.protocol.ClientContext;
import org.apache.http.conn.params.ConnRouteParams;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.protocol.HttpContext;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationManager;
import org.tmatesoft.svn.core.auth.ISVNProxyManager;
import org.tmatesoft.svn.core.auth.SVNAuthentication;
import org.tmatesoft.svn.core.auth.SVNPasswordAuthentication;
import org.tmatesoft.svn.core.internal.io.dav.http.HTTPSSLKeyManager;
import org.tmatesoft.svn.core.internal.wc.DefaultSVNAuthenticationManager;
import org.tmatesoft.svn.core.io.SVNRepository;

public class HttpCredentialsProvider implements CredentialsProvider {

    private static final int DEFAULT_HTTP_READ_TIMEOUT = 3600*1000;
    private static final int DEFAULT_HTTP_CONNECT_TIMEOUT = 0;

    private static final String AUTH_USED_CREDENTIALS = "auth.used.credentials";
    private static final String AUTH_REALM = "auth.realm";
    private static final String AUTH_USER_ERROR = "auth.user.exception";
    private static final String AUTH_VALID_CREDENTIALS = "auth.valid.credentials";
    
    private ISVNAuthenticationManager myAuthenticationManager;
    private HttpContext myContext;
    private SVNURL myLocation;
    private Map<AuthScope, Credentials> myCredentialsMap;
    private TrustManager myTrustManager;
    private HTTPSSLKeyManager myKeyManager;
    private UsernamePasswordCredentials myProxyCredentials;
    private AuthScope myProxyAuthScope;
    private SVNRepository myRepository;

    public HttpCredentialsProvider(HttpContext context, SVNRepository repository, ISVNAuthenticationManager authenticationManager) {
        myContext = context;
        myContext.setAttribute(ClientContext.CREDS_PROVIDER, this);
        myLocation = repository.getLocation();
        myRepository = repository;
        myAuthenticationManager = authenticationManager;
        myCredentialsMap = new HashMap<AuthScope, Credentials>();
    }

    public void clear() {
        myProxyAuthScope = null;
        myProxyCredentials = null;
        setContextAttribute(AUTH_VALID_CREDENTIALS, null);
        myCredentialsMap.clear();
        reset();
    }

    public void reset() {
        setContextAttribute(AUTH_REALM, null);
        setContextAttribute(AUTH_USED_CREDENTIALS, null);
        setContextAttribute(AUTH_USER_ERROR, null);
    }
    
    public SVNErrorMessage getAuthenticationError() {
        return (SVNErrorMessage) getContextAttribute(AUTH_USER_ERROR);
    }
    
    public SVNAuthentication getLastValidCredentials() {
        return (SVNAuthentication) getContextAttribute(AUTH_VALID_CREDENTIALS);
    }
    
    public void setCredentials(AuthScope authscope, Credentials credentials) {
    }
    
    public void configureRequest(HttpRequest request) throws SVNException {
        if (myAuthenticationManager != null) {
            ISVNProxyManager proxyManager = myAuthenticationManager.getProxyManager(myLocation);
            myProxyCredentials = null;
            if (proxyManager != null && proxyManager.getProxyHost() != null) {
                HttpHost proxyHost = new HttpHost(proxyManager.getProxyHost(), proxyManager.getProxyPort());
                request.getParams().setParameter(ConnRouteParams.DEFAULT_PROXY, proxyHost);
                if (proxyManager.getProxyUserName() != null) {
                    myProxyAuthScope = new AuthScope(proxyManager.getProxyHost(), proxyManager.getProxyPort());
                    myProxyCredentials = new UsernamePasswordCredentials(proxyManager.getProxyUserName(), proxyManager.getProxyPassword());
                }
            }
        }
        
        Collection<String> authPreferences = computeAuthenticationSchemesList();        
        request.getParams().setParameter(AuthPNames.TARGET_AUTH_PREF, authPreferences);
        request.getParams().setParameter(AuthPNames.PROXY_AUTH_PREF, authPreferences);
        
        int connectTimeout = myAuthenticationManager != null ? myAuthenticationManager.getConnectTimeout(myRepository) : DEFAULT_HTTP_CONNECT_TIMEOUT;
        int readTimeout = myAuthenticationManager != null ? myAuthenticationManager.getReadTimeout(myRepository) : DEFAULT_HTTP_READ_TIMEOUT;
        
        HttpConnectionParams.setConnectionTimeout(request.getParams(), connectTimeout);
        HttpConnectionParams.setSoTimeout(request.getParams(), readTimeout);
    }
    
    public void acknowledgeProxyContext(SVNErrorMessage error) throws SVNException {
        if (myAuthenticationManager != null) {
            ISVNProxyManager proxyManager = myAuthenticationManager.getProxyManager(myLocation);
            if (proxyManager != null) {
                proxyManager.acknowledgeProxyContext(error == null, error);
            }
        }
    }
    
    public void acknowledgeCredentials(SVNErrorMessage error) throws SVNException {
        if (getAuthenticationManager() != null) {
            if (getContextAttribute(AUTH_REALM) != null && getContextAttribute(AUTH_USED_CREDENTIALS) != null) {
                getAuthenticationManager().acknowledgeAuthentication(error == null, ISVNAuthenticationManager.PASSWORD, (String) getContextAttribute(AUTH_REALM), error, (SVNAuthentication) getContextAttribute(AUTH_USED_CREDENTIALS));
                setContextAttribute(AUTH_VALID_CREDENTIALS, error == null ? getContextAttribute(AUTH_USED_CREDENTIALS) : null);
            }
        }
    }
    
    public boolean hasMoreCredentials() {
        return myAuthenticationManager != null && getContextAttribute(AUTH_USER_ERROR) != null;
    }
    
    public boolean acknowledgeSSLContext(SVNErrorMessage error) throws SVNException {
        if (myKeyManager != null) {
            myKeyManager.acknowledgeAndClearAuthentication(error);
            return true;
        } 
        return false;
    }
    
    public HTTPSSLKeyManager getSSLKeyManager() {
        if (myKeyManager == null && myAuthenticationManager != null) {
            String sslRealm = "<" + myLocation.getProtocol() + "://" + myLocation.getHost() + ":" + myLocation.getPort() + ">";
            myKeyManager = new HTTPSSLKeyManager(myAuthenticationManager, sslRealm, myLocation);
        }
        return myKeyManager;
    }
    
    public TrustManager getSSLTrustManager() throws SVNException {
        if (myTrustManager == null && myAuthenticationManager != null) {
            myTrustManager = myAuthenticationManager.getTrustManager(myLocation);
        }
        return myTrustManager;
    }

    public Credentials getCredentials(AuthScope authscope) {
        if (myProxyAuthScope != null && myProxyAuthScope.match(authscope) > 0 && myProxyCredentials != null) {
            return myProxyCredentials;
        }
        
        SVNPasswordAuthentication lastValidCredentials = (SVNPasswordAuthentication) getContextAttribute(AUTH_VALID_CREDENTIALS);
        if (authscope.getRealm() == null && lastValidCredentials != null) {
            return createHttpCredentials(lastValidCredentials);
        }
        if (getAuthenticationManager() == null) {
            return null;
        }
        String realm = null;
        if (authscope.getRealm() != null) {
            realm = composeRealm(authscope);
        } else {
            return null;
        }        
        setContextAttribute(AUTH_VALID_CREDENTIALS, null);
        SVNAuthentication credentials = null;
        if (realm != null) {
            try {
                if (getContextAttribute(AUTH_USED_CREDENTIALS) == null) {
                    credentials = getAuthenticationManager().getFirstAuthentication(ISVNAuthenticationManager.PASSWORD, realm, getTargetLocation());
                } else {
                    credentials = getAuthenticationManager().getNextAuthentication(ISVNAuthenticationManager.PASSWORD, realm, getTargetLocation());
                }
            } catch (SVNException e) {
                setContextAttribute(AUTH_USER_ERROR, e.getErrorMessage());
                realm = null;
                credentials = null;
            }
        }
        setContextAttribute(AUTH_REALM, realm);
        setContextAttribute(AUTH_USED_CREDENTIALS, credentials);
        if (credentials != null && credentials instanceof SVNPasswordAuthentication) {
            setContextAttribute(AUTH_USER_ERROR, null);
            return createHttpCredentials((SVNPasswordAuthentication) credentials);
        }
        
        if (getHttpContext().getAttribute(AUTH_USER_ERROR) == null) {
            getHttpContext().setAttribute(AUTH_USER_ERROR, SVNErrorMessage.create(SVNErrorCode.CANCELLED, "authentication cancelled"));
        }
        return null;
    }

    private ISVNAuthenticationManager getAuthenticationManager() {
        return myAuthenticationManager;
    }
    
    private SVNURL getTargetLocation() {
        return myLocation;
    }

    private HttpContext getHttpContext() {
        return myContext;
    }

    private String composeRealm(AuthScope authscope) {
        return "<" + getTargetLocation().getProtocol() + "://" + authscope.getHost() + ":" + authscope.getPort() + "> " + authscope.getRealm();
    }

    private UsernamePasswordCredentials createHttpCredentials(SVNPasswordAuthentication lastValidCredentials) {
        return new UsernamePasswordCredentials(lastValidCredentials.getUserName(), lastValidCredentials.getPassword());
    }

    private void setContextAttribute(String name, Object value) {
        if (value != null) {
            getHttpContext().setAttribute(name, value);
        } else {
            getHttpContext().removeAttribute(name);
        }
    }

    private Object getContextAttribute(String attributeName) {
        return getHttpContext().getAttribute(attributeName);
    }


    private Collection<String> computeAuthenticationSchemesList() {
        List<String> schemesList = new ArrayList<String>();
        schemesList.add(AuthPolicy.BASIC);
        schemesList.add(AuthPolicy.DIGEST);
        schemesList.add(AuthPolicy.SPNEGO);
        schemesList.add(AuthPolicy.NTLM);
        
        List<String> schemesOrder = getUserAuthenticationSchemesOrder();
        if (schemesOrder == null) {
            schemesOrder = getConfigAuthenticationSchemesOrder();
            if (schemesOrder == null) {
                return schemesList;
            }
        }
        final List<String> order = schemesOrder;
        Collections.sort(schemesList, new Comparator<String>() {
            public int compare(String o1, String o2) {
                int i1 = order.indexOf(o1.trim().toLowerCase());
                int i2 = order.indexOf(o2.trim().toLowerCase());
                i1 = i1 < 0 ? Integer.MAX_VALUE : i1;
                i2 = i2 < 0 ? Integer.MAX_VALUE : i2;
                if (i1 == i2) {
                    return 0;
                }
                return i1 > i2 ? 1 : -1;
            }
        });
        return schemesList;
    }
  
    private List<String> getUserAuthenticationSchemesOrder() {
        String usersList = System.getProperty("svnkit.http.methods", System.getProperty("javasvn.http.methods", null));
        if (usersList == null || "".equals(usersList)) {
            return null;
        }
        final List<String> schemesOrder = new ArrayList<String>();
        for(StringTokenizer tokens = new StringTokenizer(usersList, ",;"); tokens.hasMoreTokens();) {
            schemesOrder.add(tokens.nextToken().trim().toLowerCase());
        }
        return schemesOrder.isEmpty() ? null : schemesOrder;
    }

    private List<String> getConfigAuthenticationSchemesOrder() {
        if (myAuthenticationManager instanceof DefaultSVNAuthenticationManager) {
            Collection<?> configSchemes = ((DefaultSVNAuthenticationManager) myAuthenticationManager).getAuthTypes(myLocation);
            if (configSchemes != null && !configSchemes.isEmpty()) {
                final List<String> schemesOrder = new ArrayList<String>();
                for (Object scheme : configSchemes) {
                    schemesOrder.add(scheme.toString().trim().toLowerCase());
                }
                return schemesOrder;
            }
        }
        return null;
    }

}
