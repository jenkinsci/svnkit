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
import java.util.List;

import org.tmatesoft.svn.core.auth.ISVNAuthenticationManager;
import org.tmatesoft.svn.core.auth.SVNPasswordAuthentication;
import org.tmatesoft.svn.core.io.SVNAuthenticationException;
import org.tmatesoft.svn.core.io.SVNException;
import org.tmatesoft.svn.core.io.SVNRepositoryLocation;
import org.tmatesoft.svn.util.DebugLog;
import org.tmatesoft.svn.util.LoggingInputStream;
import org.tmatesoft.svn.util.LoggingOutputStream;

/**
 * @version 1.0
 * @author TMate Software Ltd.
 */
class SVNConnection {

    private final ISVNConnector myConnector;
    private ISVNAuthenticationManager myAuthManager;
    private SVNRepositoryLocation myLocation;
    private String myRealm;
    private String myRoot;
    private LoggingOutputStream myOutputStream;
    private LoggingInputStream myInputStream;

    private static final String SUCCESS = "success";
    private static final String FAILURE = "failure";
    private static final String STEP = "step";
    private static final String EDIT_PIPELINE = "edit-pipeline";

    public SVNConnection(ISVNConnector connector,
            SVNRepositoryLocation location, ISVNAuthenticationManager manager) {
        myConnector = connector;
        myAuthManager = manager;
        myLocation = location;
    }

    public void open(SVNRepositoryImpl repository) throws SVNException {
        myIsCredentialsReceived = false;
        myConnector.open(repository);
        handshake(repository);
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

    public void authenticate(SVNRepositoryImpl repository) throws SVNException {
        // use provider to get creds.
        String failureReason = null;
        Object[] items = read("[((*W)?S)]", null);
        List mechs = SVNReader.getList(items, 0);
        myRealm = SVNReader.getString(items, 1);
        if (mechs == null || mechs.size() == 0) {
            return;
        }
        SVNPasswordAuthentication auth = null;
        for (int i = 0; i < mechs.size(); i++) {
            String mech = (String) mechs.get(i);
            if ("EXTERNAL".equals(mech)) {
                write("(w(s))", new Object[] { mech,
                        repository.getExternalUserName() });
                failureReason = readAuthResponse(repository);
                if (failureReason == null) {
                    return;
                }
            } else if ("ANONYMOUS".equals(mech)) {
                write("(w())", new Object[] { mech });
                failureReason = readAuthResponse(repository);
                if (failureReason == null) {
                    return;
                }
            } else if ("CRAM-MD5".equals(mech)) {
                while (true) {
                    CramMD5 authenticator = new CramMD5();
                    String realm = getRealm();
                    if (myLocation != null) {
                        realm = "<" + myLocation.getProtocol() + "://"
                                + myLocation.getHost() + ":"
                                + myLocation.getPort() + "> " + realm;
                    }
                    if (auth == null && myAuthManager != null) {
                        auth = (SVNPasswordAuthentication) myAuthManager.getFirstAuthentication(ISVNAuthenticationManager.PASSWORD, realm);
                    } else if (myAuthManager != null) {
                        myAuthManager.acknowledgeAuthentication(false, ISVNAuthenticationManager.PASSWORD, realm, failureReason, auth);
                        auth = (SVNPasswordAuthentication) myAuthManager.getNextAuthentication(ISVNAuthenticationManager.PASSWORD, realm);
                    }
                    if (auth == null || auth.getUserName() == null || auth.getPassword() == null) {
                        failureReason = "no credentials for '" + mech + "'";
                        break;
                    }
                    write("(w())", new Object[] { mech });
                    while (true) {
                        authenticator.setUserCredentials(auth);
                        items = read("(W(?B))", null);
                        if (SUCCESS.equals(items[0])) {
                            // should it be here?
                            if (!myIsCredentialsReceived) {
                                Object[] creds = read("[(S?S)]", null);
                                if (creds != null && creds.length == 2
                                        && creds[0] != null && creds[1] != null) {
                                    repository.updateCredentials(
                                            (String) creds[0],
                                            (String) creds[1]);
                                    if (myRealm == null) {
                                        myRealm = (String) creds[0];
                                    }
                                }
                                myIsCredentialsReceived = true;
                            }
                            myAuthManager.acknowledgeAuthentication(true, ISVNAuthenticationManager.PASSWORD, realm, null, auth);
                            return;
                        } else if (FAILURE.equals(items[0])) {
                            failureReason = new String((byte[]) items[1]);
                            break;
                        } else if (STEP.equals(items[0])) {
                            byte[] response = authenticator
                                    .buildChallengeReponse((byte[]) items[1]);
                            try {
                                getOutputStream().write(response);
                            } catch (IOException e) {
                                throw new SVNException(e);
                            } finally {
                                getOutputStream().log();
                            }
                        }
                    }
                }
            } else {
                failureReason = mech
                        + " authorization requested, but not supported";
            }
        }
        throw new SVNAuthenticationException(failureReason);
    }

    private String readAuthResponse(SVNRepositoryImpl repository)
            throws SVNException {
        Object[] items = read("(W(?S))", null);
        if (SUCCESS.equals(items[0])) {
            if (!myIsCredentialsReceived) {
                Object[] creds = read("[(?S?S)]", null);
                if (repository != null
                        && repository.getRepositoryRoot() == null) {
                    repository.updateCredentials((String) creds[0],
                            (String) creds[1]);
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
        myConnector.close();
    }

    public Object[] read(String template, Object[] items) throws SVNException {
        try {
            return SVNReader.parse(getInputStream(), template, items);
        } finally {
            getInputStream().log();
        }
    }

    public void write(String template, Object[] items) throws SVNException {
        try {
            SVNWriter.write(getOutputStream(), template, items);
        } finally {
            try {
                getOutputStream().flush();
            } catch (IOException e) {
                //
            } catch (SVNException e) {
                //
            }
            getOutputStream().log();
        }
    }

    public LoggingOutputStream getOutputStream() throws SVNException {
        if (myOutputStream == null) {
            try {
                return myOutputStream = DebugLog.getLoggingOutputStream("svn",
                        myConnector.getOutputStream());
            } catch (IOException ex) {
                throw new SVNException(ex);
            }
        }
        return myOutputStream;
    }

    public LoggingInputStream getInputStream() throws SVNException {
        if (myInputStream == null) {
            try {
                myInputStream = DebugLog.getLoggingInputStream("svn",
                        new RollbackInputStream(new BufferedInputStream(
                                myConnector.getInputStream())));
            } catch (IOException ex) {
                throw new SVNException(ex);
            }
        }
        return myInputStream;
    }
}