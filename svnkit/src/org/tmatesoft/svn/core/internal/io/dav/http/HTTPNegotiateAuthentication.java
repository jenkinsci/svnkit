package org.tmatesoft.svn.core.internal.io.dav.http;

import java.io.IOException;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.Arrays;

import javax.security.auth.Subject;
import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.NameCallback;
import javax.security.auth.callback.PasswordCallback;
import javax.security.auth.callback.UnsupportedCallbackException;
import javax.security.auth.login.LoginContext;
import javax.security.auth.login.LoginException;

import org.ietf.jgss.GSSContext;
import org.ietf.jgss.GSSException;
import org.ietf.jgss.GSSManager;
import org.ietf.jgss.GSSName;
import org.ietf.jgss.Oid;

import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.util.SVNBase64;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.util.SVNLogType;

class HTTPNegotiateAuthentication extends HTTPAuthentication {

    private class SVNKitCallbackHandler implements CallbackHandler {

        public void handle(Callback[] callbacks) throws IOException, UnsupportedCallbackException {
            for (int i = 0; i < callbacks.length; i++) {
                if (callbacks[i] instanceof NameCallback) {
                    ((NameCallback)callbacks[i]).setName(getUserName());
                } else if (callbacks[i] instanceof PasswordCallback) {
                    ((PasswordCallback)callbacks[i]).setPassword(getPassword() == null ? null : getPassword().toCharArray());
                }
            }
        }

    }

    private static volatile Boolean ourIsNegotiateSupported;

    private GSSManager myGSSManager = GSSManager.getInstance();
    private GSSContext myGSSContext;
    private Oid mySpnegoOid;
    private Subject mySubject;
    
    public HTTPNegotiateAuthentication(HTTPNegotiateAuthentication prevAuth) {
        if (prevAuth != null) {
            mySubject = prevAuth.mySubject;
        }
    }
    
    public HTTPNegotiateAuthentication() {
        this(null);
    }

    public static synchronized boolean isSupported() {
        if (ourIsNegotiateSupported == null) {
            try {
                Oid spnegoOid = new Oid("1.3.6.1.5.5.2");
                ourIsNegotiateSupported = Boolean.valueOf(Arrays.asList(GSSManager.getInstance().getMechs()).contains(spnegoOid));
            } catch (GSSException gsse) {
                ourIsNegotiateSupported = Boolean.FALSE;
            }
        }
        return ourIsNegotiateSupported.booleanValue();
    }

    public String getAuthenticationScheme() {
        return "Negotiate";
    }

    private String getServerPrincipalName() {
        return "HTTP@" + getChallengeParameter("host");
    }

    private byte[] myToken;
    private int myTokenLength;

    public void respondTo(String challenge) {
        if (challenge == null) {
            myToken = new byte[0];
            myTokenLength = 0;
        } else {
            myToken = new byte[(challenge.length() * 3 + 3) / 4];
            myTokenLength = SVNBase64.base64ToByteArray(new StringBuffer(challenge), myToken);
        }
    }
    
    private void initializeSubject() {
        if (mySubject != null)
            return;
        try {
            LoginContext ctx = new LoginContext("com.sun.security.jgss.krb5.initiate", new SVNKitCallbackHandler());
            ctx.login();
            mySubject = ctx.getSubject();
        } catch (LoginException e) {
        }
    }

    private void initializeContext() throws GSSException {
        if (mySpnegoOid == null) {
            mySpnegoOid = new Oid("1.3.6.1.5.5.2");
        }
        GSSName serverName = myGSSManager.createName(getServerPrincipalName(), GSSName.NT_HOSTBASED_SERVICE);
        myGSSContext = myGSSManager.createContext(serverName, mySpnegoOid, null, GSSContext.DEFAULT_LIFETIME);
    }

    public String authenticate() throws SVNException {
        if (!isStarted())
            initializeSubject();

        PrivilegedExceptionAction action = new PrivilegedExceptionAction() {
            public Object run() throws SVNException {
                if (!isStarted()) {
                    try {
                        initializeContext();
                    } catch (GSSException gsse) {
                        SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.RA_DAV_REQUEST_FAILED, "Negotiate authentication failed: ''{0}''", gsse.getMajorString());
                        SVNErrorManager.error(err, SVNLogType.NETWORK);
                        return null;
                    }
                }
        
                byte[] outtoken;
        
                try {
                    outtoken = myGSSContext.initSecContext(myToken, 0, myTokenLength);
                } catch (GSSException gsse) {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.RA_DAV_REQUEST_FAILED, "Negotiate authentication failed: ''{0}''", gsse.getMajorString());
                    SVNErrorManager.error(err, SVNLogType.NETWORK);
                    return null;
                }
        
                if (myToken != null) {
                    return "Negotiate " + SVNBase64.byteArrayToBase64(outtoken);
                }
                return null;
            }
        };
        
        if (mySubject != null) {
            try {
                return (String) Subject.doAs(mySubject, action);
            } catch (PrivilegedActionException e) {
                Throwable cause = e.getCause();
                if (cause instanceof SVNException) {
                    throw (SVNException)cause;
                }
                throw new RuntimeException(cause);
            }
        }
        
        try {
            return (String) action.run();
        } catch (Exception cause) {
            if (cause instanceof SVNException) {
                throw (SVNException)cause;
            }
            throw new RuntimeException(cause);            
        }
    }

    public boolean isStarted() {
        return myGSSContext != null;
    }
    
    public boolean needsLogin() {
        initializeSubject();
        return mySubject == null;
    }
    
}
