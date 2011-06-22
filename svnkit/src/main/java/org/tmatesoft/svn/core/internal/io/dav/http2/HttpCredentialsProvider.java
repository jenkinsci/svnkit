package org.tmatesoft.svn.core.internal.io.dav.http2;

import java.util.HashMap;
import java.util.Map;

import org.apache.http.auth.AuthScope;
import org.apache.http.auth.Credentials;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.protocol.HttpContext;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationManager;
import org.tmatesoft.svn.core.auth.SVNAuthentication;
import org.tmatesoft.svn.core.auth.SVNPasswordAuthentication;

public class HttpCredentialsProvider implements CredentialsProvider {

    private static final String AUTH_USED_CREDENTIALS = "auth.used.credentials";
    private static final String AUTH_REALM = "auth.realm";
    private static final String AUTH_USER_ERROR = "auth.user.exception";
    private static final String AUTH_VALID_CREDENTIALS = "auth.valid.credentials";
    
    private ISVNAuthenticationManager myAuthenticationManager;
    private HttpContext myContext;
    private SVNURL myLocation;
    private Map<AuthScope, Credentials> myCredentialsMap;

    public HttpCredentialsProvider(HttpContext context, SVNURL location, ISVNAuthenticationManager authenticationManager) {
        myContext = context;
        myLocation = location;
        myAuthenticationManager = authenticationManager;
        myCredentialsMap = new HashMap<AuthScope, Credentials>();
    }

    public void clear() {
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
        if (authscope != null) {
            if (credentials != null) {
                myCredentialsMap.put(authscope, credentials);
            } else {
                myCredentialsMap.remove(authscope);
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

    public Credentials getCredentials(AuthScope authscope) {
        Credentials proxyCredentials = findMatchingCredentials(authscope);
        if (proxyCredentials != null) {
            return proxyCredentials;
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

    private Credentials findMatchingCredentials(AuthScope authscope) {
        int bestFactor = -1;
        Credentials matchingCredentials = null;
        
        for (AuthScope scope : myCredentialsMap.keySet()) {
            int factor = scope.match(authscope);
            if (factor >= 0 && factor > bestFactor) {
                bestFactor = factor;
                matchingCredentials = myCredentialsMap.get(scope);
            }
        }
        return matchingCredentials;
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
}
