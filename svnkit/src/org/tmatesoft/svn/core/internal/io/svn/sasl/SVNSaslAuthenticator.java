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
package org.tmatesoft.svn.core.internal.io.svn.sasl;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.List;
import java.util.Map;

import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.NameCallback;
import javax.security.auth.callback.PasswordCallback;
import javax.security.auth.callback.UnsupportedCallbackException;
import javax.security.sasl.RealmCallback;
import javax.security.sasl.Sasl;
import javax.security.sasl.SaslClient;
import javax.security.sasl.SaslException;

import org.tmatesoft.svn.core.SVNAuthenticationException;
import org.tmatesoft.svn.core.SVNCancelException;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationManager;
import org.tmatesoft.svn.core.auth.SVNAuthentication;
import org.tmatesoft.svn.core.auth.SVNPasswordAuthentication;
import org.tmatesoft.svn.core.internal.io.svn.SVNAuthenticator;
import org.tmatesoft.svn.core.internal.io.svn.SVNConnection;
import org.tmatesoft.svn.core.internal.io.svn.SVNRepositoryImpl;
import org.tmatesoft.svn.core.internal.util.SVNBase64;
import org.tmatesoft.svn.core.internal.util.SVNHashMap;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;


/**
 * @version 1.1.2
 * @author  TMate Software Ltd.
 */
public class SVNSaslAuthenticator extends SVNAuthenticator {

    private SaslClient myClient;

    public SVNSaslAuthenticator(SVNConnection connection) throws SVNException {
        super(connection);
    }

    public void authenticate(List mechs, String realm, SVNRepositoryImpl repository) throws SVNException {
        boolean failed = false;
        SVNCallbackHandler callback = new SVNCallbackHandler(realm, repository.getLocation(), repository.getAuthenticationManager());
        try {
            while(true) {
                dispose();
                myClient = createSaslClient(mechs, repository.getLocation(), callback);
                if (myClient == null) {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.RA_NOT_AUTHORIZED, "Cannot create suitable SASL client");
                    throw new SVNAuthenticationException(err);
                }
                if (tryAuthentication()) {
                    setEncryption();
                    return;
                }
            }
        } catch (SVNException e) {
            failed = true;
            if (getLastError() != null) {
                SVNErrorManager.error(getLastError());
            }
            throw e;
        } finally {
            if (failed) {
                dispose();
            }
        }
    }
    
    public void dispose() {
        if (myClient != null) {
            try {
                myClient.dispose();
            } catch (SaslException e) {
                
            }
        }
    }
    
    protected boolean tryAuthentication() throws SVNException {
        onAuthAttempt();
        
        String initialChallenge = null;
        if (myClient.hasInitialResponse()) {
            // compute initial response
            byte[] initialResponse = null;
            try {
                initialResponse = myClient.evaluateChallenge(new byte[0]);
            } catch (SaslException e) {
                if (e.getCause() instanceof SVNException) {
                    throw (SVNException) e.getCause();
                }
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.RA_NOT_AUTHORIZED, e.getMessage());
                SVNErrorManager.error(err);
            }
            if (initialResponse == null) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.RA_NOT_AUTHORIZED, "Unexpected initial response received from {0}", myClient.getMechanismName());
                SVNErrorManager.error(err);
            }
            initialChallenge = toBase64(initialResponse);
        }
        if (initialChallenge != null) {
            getConnection().write("(w(s))", new Object[]{myClient.getMechanismName(), initialChallenge});
        } else {
            getConnection().write("(w())", new Object[]{myClient.getMechanismName()});
        }

        // read response (challenge)
        String status = SVNAuthenticator.STEP;

        while(SVNAuthenticator.STEP.equals(status)) {
            List items = getConnection().readTuple("w(?s)", true);
            status = (String) items.get(0);
            if (SVNAuthenticator.FAILURE.equals(status)) {
                String msg = (String) (items.size() > 1 ? items.get(1) : ""); 
                setLastError(SVNErrorMessage.create(SVNErrorCode.RA_NOT_AUTHORIZED, msg));
                return false;
            }
            String challenge = (String) (items.size() > 1 ? items.get(1) : null); 
            if (challenge == null && "CRAM-MD5".equals(myClient.getMechanismName()) && SVNAuthenticator.SUCCESS.equals(status)) {
                challenge = "";
            }
            if ((!SVNAuthenticator.STEP.equals(status) && !SVNAuthenticator.SUCCESS.equals(status)) || challenge == null) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.RA_NOT_AUTHORIZED, "Unexpected server response to authentication");
                SVNErrorManager.error(err);
            }
            byte[] challengeBytes = "CRAM-MD5".equals(myClient.getMechanismName()) ? challenge.getBytes() : fromBase64(challenge);
            byte[] response = null;
            try {
                if (!myClient.isComplete()) {
                    response = myClient.evaluateChallenge(challengeBytes);
                }
            } catch (SaslException e) {
                if (e.getCause() instanceof SVNException) {
                    throw (SVNException) e.getCause();
                }
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.RA_NOT_AUTHORIZED, e.getMessage());
                SVNErrorManager.error(err);
            }
            if (SVNAuthenticator.SUCCESS.equals(status)) {
                return true;
            }
            if (response == null) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.RA_NOT_AUTHORIZED, "Unexpected response received from {0}", myClient.getMechanismName());
                SVNErrorManager.error(err);
            }
            if (response.length > 0) {
                String responseStr = "CRAM-MD5".equals(myClient.getMechanismName()) ? new String(response) : toBase64(response);
                getConnection().write("s", new Object[] {responseStr});
            } else {
                getConnection().write("s", new Object[] {""});
            }
        }
        return true;
        
    }
    
    protected void setEncryption() {
        String qop = (String) myClient.getNegotiatedProperty(Sasl.QOP);
        String buffSizeStr = (String) myClient.getNegotiatedProperty(Sasl.MAX_BUFFER);
        
        if ("auth-int".equals(qop) || "auth-conf".equals(qop)) {
            int buffSize = 8192;
            if (buffSizeStr != null) {
                try {
                    buffSize = Integer.parseInt(buffSizeStr);
                } catch (NumberFormatException nfe) {
                    buffSize = 8192;
                }
                buffSize = Math.min(8192, buffSize);
            }
            setOutputStream(new SaslOutputStream(myClient, buffSize, getConnectionOutputStream()));
            setInputStream(new SaslInputStream(myClient, buffSize, getConnectionInputStream()));
        }
    }
    
    protected SaslClient createSaslClient(List mechs, SVNURL location, CallbackHandler callback) throws SVNException {
        Map props = new SVNHashMap();
        props.put(Sasl.POLICY_NOPLAINTEXT, "true");
        props.put(Sasl.QOP, "auth-conf,auth-int,auth");
        props.put(Sasl.MAX_BUFFER, "8192");
        props.put(Sasl.REUSE, "false");
        
        String[] mechsArray = (String[]) mechs.toArray(new String[mechs.size()]);
        SaslClient client = null;
        
        try {
            client = Sasl.createSaslClient(mechsArray, null, "svn", location.getHost(), props, callback);
        } catch (SaslException e) {
            if (e.getCause() instanceof SVNException) {
                throw (SVNException) e.getCause();
            }
            SVNAuthenticationException exception = new SVNAuthenticationException(SVNErrorMessage.create(SVNErrorCode.RA_NOT_AUTHORIZED, e.getMessage()));
            throw exception;
        }
        return client;
    }
    
    private String toBase64(byte[] src) {
        return SVNBase64.byteArrayToBase64(src);
    }
    
    private byte[] fromBase64(String src) {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        for (int i = 0; i < src.length(); i++) {
            char ch = src.charAt(i);
            if (!Character.isWhitespace(ch) && ch != '\n' && ch != '\r') {
                bos.write((byte) ch & 0xFF);
            }                    
        }
        byte[] cbytes = new byte[src.length()];
        try {
            src = new String(bos.toByteArray(), "US-ASCII");
        } catch (UnsupportedEncodingException e) {
            //
        }
        int clength = SVNBase64.base64ToByteArray(new StringBuffer(src), cbytes);
        byte[] result = new byte[clength];
        // strip trailing -1s.
        for(int i = clength - 1; i>=0; i--) {
            if (i == -1) {
                clength--;
            }
        }
        System.arraycopy(cbytes, 0, result, 0, clength);
        return result;
    }
    
    private class SVNCallbackHandler implements CallbackHandler {
        
        private String myRealm;
        private ISVNAuthenticationManager myAuthenticationManager;
        private SVNAuthentication myAuthentication;
        private SVNURL myLocation;
        private SVNErrorMessage myError;
        private int myCallbackCount;

        public SVNCallbackHandler(String realm, SVNURL location, ISVNAuthenticationManager authManager) {
            myRealm = realm;
            myLocation = location;
            myAuthenticationManager = authManager;
        }

        public void handle(Callback[] callbacks) throws IOException, UnsupportedCallbackException {
            for (int i = 0; i < callbacks.length; i++) {
                Callback callback = callbacks[i];
                if (callback instanceof NameCallback) {
                    fetchCredentials();
                    if (myError == null) {
                        String userName = myAuthentication.getUserName();
                        ((NameCallback) callback).setName(userName != null ? userName : "");
                    } else {
                        SVNException svne = myError.getErrorCode() == SVNErrorCode.CANCELLED ? new SVNCancelException(myError) : (SVNException)new SVNAuthenticationException(myError);
                        throw (IOException) new IOException().initCause(svne);
                    }
                } else if (callback instanceof PasswordCallback) {
                    fetchCredentials();
                    if (myError == null) {
                        String password = ((SVNPasswordAuthentication) myAuthentication).getPassword();
                        ((PasswordCallback) callback).setPassword(password != null ? password.toCharArray() : new char[0]);
                    } else {
                        SVNException svne = myError.getErrorCode() == SVNErrorCode.CANCELLED ? new SVNCancelException(myError) : (SVNException)new SVNAuthenticationException(myError);
                        throw (IOException) new IOException().initCause(svne);
                    }
                } else if (callback instanceof RealmCallback) {
                    ((RealmCallback) callback).setText(myRealm);
                } else {
                    throw new UnsupportedCallbackException(callback);
                }
            }
        }
        
        private void fetchCredentials() {
            if (myAuthentication != null && myCallbackCount == 1) {
                myCallbackCount = 0;
                return;
            }
            myCallbackCount++;
            try {
                if (myAuthentication != null) {
                    myAuthentication = myAuthenticationManager.getNextAuthentication(ISVNAuthenticationManager.PASSWORD, myRealm, myLocation);
                } else {
                    myAuthentication = myAuthenticationManager.getFirstAuthentication(ISVNAuthenticationManager.PASSWORD, myRealm, myLocation);
                }
                if (myAuthentication == null) {
                    myError = SVNErrorMessage.create(SVNErrorCode.CANCELLED, "Authentication cancelled");
                    setLastError(myError);
                }
            } catch (SVNException e) {
                myError = e.getErrorMessage();
            }
        }
        
    }

}
