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
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Iterator;
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
import javax.security.sasl.SaslClientFactory;
import javax.security.sasl.SaslException;

import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationManager;
import org.tmatesoft.svn.core.auth.SVNAuthentication;
import org.tmatesoft.svn.core.auth.SVNPasswordAuthentication;
import org.tmatesoft.svn.core.internal.io.svn.SVNAuthenticator;
import org.tmatesoft.svn.core.internal.io.svn.SVNConnection;
import org.tmatesoft.svn.core.internal.io.svn.SVNPlainAuthenticator;
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
    private ISVNAuthenticationManager myAuthenticationManager;
    private SVNAuthentication myAuthentication;

    public SVNSaslAuthenticator(SVNConnection connection) throws SVNException {
        super(connection);
    }

    public void authenticate(List mechs, String realm, SVNRepositoryImpl repository) throws SVNException {
        boolean failed = true;
        setLastError(null);
        myAuthenticationManager = repository.getAuthenticationManager();
        myAuthentication = null;
        
        for (Iterator mech = mechs.iterator(); mech.hasNext();) {
            String m = (String) mech.next();
            if ("ANONYMOUS".equals(m) || "EXTERNAL".equals(m)) {
                mechs = new ArrayList();
                mechs.add(m);
                break;
            }
        }
        
        dispose();
        try {
            myClient = createSaslClient(mechs, realm, repository.getLocation());
            while(true) {
                if (myClient == null) {
                    new SVNPlainAuthenticator(getConnection()).authenticate(mechs, realm, repository);
                    return;
                }
                try {
                    if (tryAuthentication()) {
                        if (myAuthenticationManager != null && myAuthentication != null) {
                            String realmName = getFullRealmName(repository.getLocation(), realm);
                            myAuthenticationManager.acknowledgeAuthentication(true, myAuthentication.getKind(), realmName, null, myAuthentication);
                        }
                        failed = false;
                        setLastError(null);
                        setEncryption();
                        break;
                    }
                } catch (SaslException e) {
                    mechs.remove(getMechanismName(myClient));
                } 
                if (myAuthenticationManager != null && myAuthentication != null) {
                    SVNErrorMessage error = getLastError();
                    if (error == null) {
                        error = SVNErrorMessage.create(SVNErrorCode.RA_NOT_AUTHORIZED);
                        setLastError(error);
                    }
                    String realmName = getFullRealmName(repository.getLocation(), realm);
                    myAuthenticationManager.acknowledgeAuthentication(false, myAuthentication.getKind(), realmName, getLastError(), myAuthentication);
                }
                dispose();
                myClient = createSaslClient(mechs, realm, repository.getLocation());
            }
        } finally {
            if (failed) {
                dispose();
            }
        }
        if (getLastError() != null) {
            SVNErrorManager.error(getLastError());
        }
    }
    
    public void dispose() {
        if (myClient != null) {
            try {
                myClient.dispose();
            } catch (SaslException e) {
                //
            }
        }
    }
    
    protected boolean tryAuthentication() throws SaslException, SVNException {
        String initialChallenge = null;
        String mechName = getMechanismName(myClient);
        boolean expectChallenge = !("ANONYMOUS".equals(mechName) || "EXTERNAL".equals(mechName));
        if (myClient.hasInitialResponse()) {
            // compute initial response
            byte[] initialResponse = null;
            initialResponse = myClient.evaluateChallenge(new byte[0]);
            if (initialResponse == null) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.RA_NOT_AUTHORIZED, "Unexpected initial response received from {0}", mechName);
                SVNErrorManager.error(err);
            }
            initialChallenge = toBase64(initialResponse);
        }
        if (initialChallenge != null) {
            getConnection().write("(w(s))", new Object[] {mechName, initialChallenge});
        } else {
            getConnection().write("(w())", new Object[] {mechName});
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
            if (challenge == null && "CRAM-MD5".equals(mechName) && SVNAuthenticator.SUCCESS.equals(status)) {
                challenge = "";
            }
            if ((!SVNAuthenticator.STEP.equals(status) && !SVNAuthenticator.SUCCESS.equals(status)) || 
                    (challenge == null && expectChallenge)) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.RA_NOT_AUTHORIZED, "Unexpected server response to authentication");
                SVNErrorManager.error(err);
            }
            byte[] challengeBytes = "CRAM-MD5".equals(mechName) ? challenge.getBytes() : fromBase64(challenge);
            byte[] response = null;
            if (!myClient.isComplete()) {
                response = myClient.evaluateChallenge(challengeBytes);
            }
            if (SVNAuthenticator.SUCCESS.equals(status)) {
                return true;
            }
            if (response == null) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.RA_NOT_AUTHORIZED, "Unexpected response received from {0}", mechName);
                SVNErrorManager.error(err);
            }
            if (response.length > 0) {
                String responseStr = "CRAM-MD5".equals(mechName) ? new String(response) : toBase64(response);
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
    
    protected SaslClient createSaslClient(List mechs, String realm, SVNURL location) throws SVNException {
        Map props = new SVNHashMap();
        props.put(Sasl.QOP, "auth-conf,auth-int,auth");
        props.put(Sasl.MAX_BUFFER, "8192");
        props.put(Sasl.POLICY_NOPLAINTEXT, "false");
        props.put(Sasl.REUSE, "false");
        props.put(Sasl.POLICY_NOANONYMOUS, "true");
        
        String[] mechsArray = (String[]) mechs.toArray(new String[mechs.size()]);
        SaslClient client = null;
        for (int i = 0; i < mechsArray.length; i++) {
            String mech = mechsArray[i];
            try {
                if ("ANONYMOUS".equals(mech) || "EXTERNAL".equals(mech)) {
                    props.put(Sasl.POLICY_NOANONYMOUS, "false");
                }
                SaslClientFactory clientFactory = getSaslClientFactory(mech, props);
                if (clientFactory == null) {
                    continue;
                }
                SVNAuthentication auth = null;
                if ("ANONYMOUS".equals(mech) || "EXTERNAL".equals(mech)) {
                    auth = new SVNPasswordAuthentication("", "", false);
                } else {                
                    if (myAuthenticationManager == null) {
                        SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.RA_NOT_AUTHORIZED, "Authentication required for ''{0}''", realm));
                    }
                    String realmName = getFullRealmName(location, realm);
                    if (myAuthentication != null) {
                        myAuthentication = myAuthenticationManager.getNextAuthentication(ISVNAuthenticationManager.PASSWORD, realmName, location);
                    } else {
                        myAuthentication = myAuthenticationManager.getFirstAuthentication(ISVNAuthenticationManager.PASSWORD, realmName, location);
                    }
                    if (myAuthentication == null) {
                        if (getLastError() != null) {
                            SVNErrorManager.error(getLastError());
                        }
                        SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.RA_NOT_AUTHORIZED, "Authentication required for ''{0}''", realm));
                    }
                    auth = myAuthentication;
                }
                if ("ANONYMOUS".equals(mech)) {
                    mech = "PLAIN";
                }
                client = clientFactory.createSaslClient(new String[] {mech}, null, "svn", location.getHost(), props, new SVNCallbackHandler(realm, auth));
                if (client != null) {
                    break;
                }
                myAuthentication = null;
            } catch (SaslException e) {
                // remove mech from the list and try next
                // so next time we wouldn't even try this mech next time.
                mechs.remove(mechsArray[i]);
                myAuthentication = null;
            }
        }
        return client;
    }
    
    private static String getFullRealmName(SVNURL location, String realm) {
        if (location == null || realm == null) {
            return realm;
        } 
        return "<" + location.getProtocol() + "://" + location.getHost() + ":" + location.getPort() + "> " + realm;
    }
    
    private static String toBase64(byte[] src) {
        return SVNBase64.byteArrayToBase64(src);
    }
    
    private static byte[] fromBase64(String src) {
        if (src == null) {
            return new byte[0];
        }
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
    
    private static String getMechanismName(SaslClient client) {
        if (client == null) {
            return null;
        }
        String mech = client.getMechanismName();
        if ("PLAIN".equals(mech)) {
            return "ANONYMOUS";
        }
        return mech;
    }
    
    private static SaslClientFactory getSaslClientFactory(String mechName, Map props) {
        if (mechName == null) {
            return null;
        }
        if ("ANONYMOUS".equals(mechName)) {
            mechName = "PLAIN";
        }
        for(Enumeration factories = Sasl.getSaslClientFactories(); factories.hasMoreElements();) {
            SaslClientFactory factory = (SaslClientFactory) factories.nextElement();
            String[] mechs = factory.getMechanismNames(props);
            for (int i = 0; mechs != null && i < mechs.length; i++) {
                if (mechName.endsWith(mechs[i])) {
                    return factory; 
                }
            }
        }
        return null;
    }

    private static class SVNCallbackHandler implements CallbackHandler {
        
        private String myRealm;
        private SVNAuthentication myAuthentication;
        
        public SVNCallbackHandler(String realm, SVNAuthentication auth) {
            myRealm = realm;
            myAuthentication = auth;
        }

        public void handle(Callback[] callbacks) throws IOException, UnsupportedCallbackException {
            for (int i = 0; i < callbacks.length; i++) {
                Callback callback = callbacks[i];
                if (callback instanceof NameCallback) {
                    String userName = myAuthentication.getUserName();
                    ((NameCallback) callback).setName(userName != null ? userName : "");
                } else if (callback instanceof PasswordCallback) {
                    String password = ((SVNPasswordAuthentication) myAuthentication).getPassword();
                    ((PasswordCallback) callback).setPassword(password != null ? password.toCharArray() : new char[0]);
                } else if (callback instanceof RealmCallback) {
                    ((RealmCallback) callback).setText(myRealm);
                } else {
                    throw new UnsupportedCallbackException(callback);
                }
            }
        }
    }
}
