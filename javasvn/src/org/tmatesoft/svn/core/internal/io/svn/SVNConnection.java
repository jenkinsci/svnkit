/*
 * ====================================================================
 * Copyright (c) 2004 TMate Software Ltd. All rights reserved.
 * 
 * This software is licensed as described in the file COPYING, which you should
 * have received as part of this distribution. The terms are also available at
 * http://tmate.org/svn/license.html. If newer versions of this license are
 * posted there, you may use a newer version instead, at your option.
 * ====================================================================
 */

package org.tmatesoft.svn.core.internal.io.svn;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

import org.tmatesoft.svn.core.SVNAuthenticationException;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationManager;
import org.tmatesoft.svn.core.auth.SVNPasswordAuthentication;
import org.tmatesoft.svn.util.SVNDebugLog;

/**
 * @version 1.0
 * @author TMate Software Ltd.
 */
class SVNConnection {

    private final ISVNConnector myConnector;
    private String myRealm;
    private String myRoot;
    private OutputStream myOutputStream;
    private InputStream myInputStream;
    private SVNRepositoryImpl myRepository;

    private static final String SUCCESS = "success";
    private static final String FAILURE = "failure";
    private static final String STEP = "step";
    private static final String EDIT_PIPELINE = "edit-pipeline";

    public SVNConnection(ISVNConnector connector, SVNRepositoryImpl repository) {
        myConnector = connector;
        myRepository = repository;
    }

    public void open(SVNRepositoryImpl repository) throws SVNException {
        myIsReopening = true;
        try {
            myIsCredentialsReceived = false;
            myConnector.open(repository);
            myRepository = repository;
            handshake(repository);
        } finally {
            myIsReopening = false;
        }
    }

    public String getRealm() {
        return myRealm;
    }

    protected void handshake(SVNRepositoryImpl repository) throws SVNException {
        Object[] items = read("[(*N(*W)(*W))]", null);
        if (!SVNReader.hasValue(items, 0, 2)
                || !SVNReader.hasValue(items, 2, EDIT_PIPELINE)) {
            throw new SVNException("unsupported version or capability");
        }
        write("(n(w)s)", new Object[] { "2", EDIT_PIPELINE,
                repository.getLocation().toString() });
    }

    private boolean myIsCredentialsReceived = false;
    private InputStream myLoggingInputStream;

    public void authenticate(SVNRepositoryImpl repository) throws SVNException {
        String failureReason = null;
        Object[] items = read("[((*W)?S)]", null);
        List mechs = SVNReader.getList(items, 0);
        myRealm = SVNReader.getString(items, 1);
        if (mechs == null || mechs.size() == 0) {
            return;
        }
        ISVNAuthenticationManager authManager = myRepository.getAuthenticationManager();
        SVNURL location = myRepository.getLocation();        
        SVNPasswordAuthentication auth = null;
        if (repository.getExternalUserName() != null && mechs.contains("EXTERNAL")) {
            write("(w(s))", new Object[] { "EXTERNAL", "" });
            failureReason = readAuthResponse(repository);
        } else if (mechs.contains("ANONYMOUS")) {
            write("(w())", new Object[] { "ANONYMOUS" });
            failureReason = readAuthResponse(repository);
        } else if (mechs.contains("CRAM-MD5")) {
            while (true) {
                CramMD5 authenticator = new CramMD5();
                String realm = getRealm();
                if (location != null) {
                    realm = "<" + location.getProtocol() + "://"
                            + location.getHost() + ":"
                            + location.getPort() + "> " + realm;
                }
                if (auth == null && authManager != null) {
                    auth = (SVNPasswordAuthentication) authManager.getFirstAuthentication(ISVNAuthenticationManager.PASSWORD, realm, location);
                } else if (authManager != null) {
                    authManager.acknowledgeAuthentication(false, ISVNAuthenticationManager.PASSWORD, realm, failureReason, auth);
                    auth = (SVNPasswordAuthentication) authManager.getNextAuthentication(ISVNAuthenticationManager.PASSWORD, realm, location);
                }
                if (auth == null || auth.getUserName() == null || auth.getPassword() == null) {
                    failureReason = "authentication is required for '" + realm + "'";
                    break;
                }
                write("(w())", new Object[] { "CRAM-MD5" });
                while (true) {
                    authenticator.setUserCredentials(auth);
                    items = read("(W(?B))", null);
                    if (SUCCESS.equals(items[0])) {
                        if (!myIsCredentialsReceived) {
                            Object[] creds = read("[(S?S)]", null);
                            if (creds != null && creds.length == 2
                                    && creds[0] != null && creds[1] != null) {
                                SVNURL rootURL = SVNURL.parseURIEncoded((String) creds[1]); 
                                repository.updateCredentials((String) creds[0], rootURL);
                                if (myRealm == null) {
                                    myRealm = (String) creds[0];
                                }
                            }
                            myIsCredentialsReceived = true;
                        }
                        authManager.acknowledgeAuthentication(true, ISVNAuthenticationManager.PASSWORD, realm, null, auth);
                        return;
                    } else if (FAILURE.equals(items[0])) {
                        failureReason = new String((byte[]) items[1]);
                        break;
                    } else if (STEP.equals(items[0])) {
                        byte[] response = authenticator.buildChallengeReponse((byte[]) items[1]);
                        try {
                            getOutputStream().write(response);
                            getOutputStream().flush();
                        } catch (IOException e) {
                            throw new SVNException(e);
                        } 
                    }
                }
            }
        }
        if (failureReason == null) {
            return;
        }
        throw new SVNAuthenticationException(failureReason);
    }

    private String readAuthResponse(SVNRepositoryImpl repository) throws SVNException {
        Object[] items = read("(W(?S))", null);
        if (SUCCESS.equals(items[0])) {
            if (!myIsCredentialsReceived) {
                Object[] creds = read("[(?S?S)]", null);
                if (repository != null
                        && repository.getRepositoryRoot() == null) {
                    SVNURL rootURL = creds[1] != null ? SVNURL.parseURIEncoded((String) creds[1]) : null; 
                    repository.updateCredentials((String) creds[0], rootURL);
                }
                if (myRealm == null) {
                    myRealm = (String) creds[0];
                }
                if (myRoot == null) {
                    myRoot = (String) creds[1];
                }
                myIsCredentialsReceived = true;
            }
            return null;
        } else if (FAILURE.equals(items[0])) {
            return (String) items[1];
        }
        return "unexpected server responce";
    }

    public void close() throws SVNException {
        myInputStream = null;
        myLoggingInputStream = null;
        myOutputStream = null;
        myConnector.close();
    }

    public Object[] read(String template, Object[] items) throws SVNException {
        try {
            checkConnection();
            return SVNReader.parse(getInputStream(), template, items);
        } finally {
            SVNDebugLog.flushStream(myLoggingInputStream);
        }
    }
    
    private boolean myIsReopening = false;
    
    public void write(String template, Object[] items) throws SVNException {
        try {
//            checkConnection();
            SVNWriter.write(getOutputStream(), template, items);
        } finally {
            try {
                getOutputStream().flush();
            } catch (IOException e) {
                //
            } catch (SVNException e) {
                //
            }
            SVNDebugLog.flushStream(getOutputStream());
        }
    }

    private void checkConnection() throws SVNException {
        if (!myIsReopening && !myConnector.isConnected(myRepository)) {
            SVNDebugLog.logInfo(new Exception());
            myIsReopening = true;
            try {
                close();
                open(myRepository);
            } finally {
                myIsReopening = false;
            }
        }
    }

    public OutputStream getOutputStream() throws SVNException {
        if (myOutputStream == null) {
            try {
                myOutputStream = SVNDebugLog.createLogStream(myConnector.getOutputStream());
            } catch (IOException ex) {
                throw new SVNException(ex);
            }
        }
        return myOutputStream;
    }

    public InputStream getInputStream() throws SVNException {
        if (myInputStream == null) {
            try {
                myInputStream = SVNDebugLog.createLogStream(new BufferedInputStream(myConnector.getInputStream()));
                myLoggingInputStream = myInputStream;
                myInputStream = new RollbackInputStream(myInputStream);
            } catch (IOException ex) {
                throw new SVNException(ex);
            }
        }
        return myInputStream;
    }
}