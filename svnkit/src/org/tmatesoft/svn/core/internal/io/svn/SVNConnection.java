/*
 * ====================================================================
 * Copyright (c) 2004-2006 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */

package org.tmatesoft.svn.core.internal.io.svn;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationManager;
import org.tmatesoft.svn.core.auth.SVNPasswordAuthentication;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;

/**
 * @version 1.1.0
 * @author  TMate Software Ltd.
 */
class SVNConnection {

    private final ISVNConnector myConnector;
    private String myRealm;
    private String myRoot;
    private OutputStream myOutputStream;
    private InputStream myInputStream;
    private SVNRepositoryImpl myRepository;
    private boolean myIsSVNDiff1;

    private static final String SUCCESS = "success";
    private static final String FAILURE = "failure";
    private static final String STEP = "step";
    private static final String EDIT_PIPELINE = "edit-pipeline";
    private static final String SVNDIFF1 = "svndiff1";
    private static final String ABSENT_ENTRIES = "absent-entries";

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
    
    public boolean isSVNDiff1() {
        return myIsSVNDiff1;
    }

    protected void handshake(SVNRepositoryImpl repository) throws SVNException {
        Object[] items = read("[(*N(*W)(*W))]", null, true);
        if (!SVNReader.hasValue(items, 0, 2)) {
            SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.RA_SVN_BAD_VERSION, "Only protocol of version '2' or older is supported"));
        } else if (!SVNReader.hasValue(items, 2, EDIT_PIPELINE)) {
            SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.RA_SVN_BAD_VERSION, "Only servers with 'edit-pipeline' capability is supported"));
        }
        myIsSVNDiff1 = SVNReader.hasValue(items, 2, SVNDIFF1);
        write("(n(www)s)", new Object[] { "2", EDIT_PIPELINE, SVNDIFF1, ABSENT_ENTRIES, 
                repository.getLocation().toString() });
    }

    private boolean myIsCredentialsReceived = false;
    private InputStream myLoggingInputStream;

    public void authenticate(SVNRepositoryImpl repository) throws SVNException {
        SVNErrorMessage failureReason = null;
        Object[] items = read("[((*W)?S)]", null, true);
        List mechs = SVNReader.getList(items, 0);
        myRealm = SVNReader.getString(items, 1);
        if (mechs == null || mechs.size() == 0) {
            return;
        }
        ISVNAuthenticationManager authManager = myRepository.getAuthenticationManager();
        if (authManager != null && authManager.isAuthenticationForced() && mechs.contains("ANONYMOUS") && mechs.contains("CRAM-MD5")) {
            mechs.remove("ANONYMOUS");
        }
        SVNURL location = myRepository.getLocation();        
        SVNPasswordAuthentication auth = null;
        if (repository.getExternalUserName() != null && mechs.contains("EXTERNAL")) {
            write("(w(s))", new Object[] { "EXTERNAL", repository.getExternalUserName() });
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
                    failureReason = SVNErrorMessage.create(SVNErrorCode.RA_NOT_AUTHORIZED, "Authentication is required for ''{0}''", realm);
                    break;
                }
                write("(w())", new Object[] { "CRAM-MD5" });
                while (true) {
                    authenticator.setUserCredentials(auth);
                    items = read("(W(?B))", null, true);
                    if (SUCCESS.equals(items[0])) {
                        if (!myIsCredentialsReceived) {
                            Object[] creds = read("[(S?S)]", null, true);
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
                        failureReason = SVNErrorMessage.create(SVNErrorCode.RA_NOT_AUTHORIZED, new String((byte[]) items[1]));
                        break;
                    } else if (STEP.equals(items[0])) {
                        try {
                            byte[] response = authenticator.buildChallengeResponse((byte[]) items[1]);
                            getOutputStream().write(response);
                            getOutputStream().flush();
                        } catch (IOException e) {
                            SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.RA_SVN_IO_ERROR, e.getMessage()), e);
                        } 
                    }
                }
            }
        } else {
            SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.RA_UNKNOWN_AUTH));
        }
        if (failureReason == null) {
            return;
        }
        SVNErrorManager.error(failureReason);
    }

    private SVNErrorMessage readAuthResponse(SVNRepositoryImpl repository) throws SVNException {
        Object[] items = read("(W(?S))", null, true);
        if (SUCCESS.equals(items[0])) {
            if (!myIsCredentialsReceived) {
                Object[] creds = read("[(?S?S)]", null, true);
                if (repository != null
                        && repository.getRepositoryRoot(false) == null) {
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
            return SVNErrorMessage.create(SVNErrorCode.RA_NOT_AUTHORIZED, (String) items[1]);
        }
        return SVNErrorMessage.create(SVNErrorCode.RA_NOT_AUTHORIZED, "unexpected server response");
    }
    
    public void free() {
        myConnector.free();
    }
    
    public void occupy() throws SVNException {
        if (!myConnector.occupy()) {
            close();
            open(myRepository);
            authenticate(myRepository);
        }
    }

    public void close() throws SVNException {
        myInputStream = null;
        myLoggingInputStream = null;
        myOutputStream = null;
        myConnector.close(myRepository);
    }

    public Object[] read(String template, Object[] items, boolean readMalformedData) throws SVNException {
        try {
            checkConnection();
            return SVNReader.parse(getInputStream(), template, items);
        } catch (SVNException e) {
            if (readMalformedData && e.getErrorMessage().getErrorCode() == SVNErrorCode.RA_SVN_MALFORMED_DATA) {
                // read let's say next 255 bytes into the logging stream.
                byte[] malfored = new byte[1024];
                try {
                    // could it hang here for timeout?
                    getInputStream().read(malfored);
                } catch (IOException e1) {
                    // ignore.
                }
            }
            throw e;
        } finally {
            myRepository.getDebugLog().flushStream(myLoggingInputStream);
        }
    }
    
    private boolean myIsReopening = false;
    
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
            myRepository.getDebugLog().flushStream(getOutputStream());
        }
    }

    private void checkConnection() throws SVNException {
        if (!myIsReopening && !myConnector.isConnected(myRepository)) {
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
                myOutputStream = myRepository.getDebugLog().createLogStream(myConnector.getOutputStream());
            } catch (IOException e) {
                SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.RA_SVN_IO_ERROR, e.getMessage()), e);
            }
        }
        return myOutputStream;
    }

    public InputStream getInputStream() throws SVNException {
        if (myInputStream == null) {
            try {
                myInputStream = myRepository.getDebugLog().createLogStream(new BufferedInputStream(myConnector.getInputStream()));
                myLoggingInputStream = myInputStream;
                myInputStream = new SVNRollbackInputStream(myInputStream, 1024);
            } catch (IOException e) {
                SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.RA_SVN_IO_ERROR, e.getMessage()), e);
            }
        }
        return myInputStream;
    }
}