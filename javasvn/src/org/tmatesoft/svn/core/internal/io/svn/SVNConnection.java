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

package org.tmatesoft.svn.core.internal.io.svn;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

import org.tmatesoft.svn.core.io.ISVNCredentials;
import org.tmatesoft.svn.core.io.SVNAuthenticationException;
import org.tmatesoft.svn.core.io.SVNException;

/**
 * @author Alexander Kitaev
 */
class SVNConnection {

    private final ISVNConnector myConnector;

    private OutputStream myOutputStream;
    private InputStream myInputStream;

    private static final String SUCCESS = "success";
    private static final String FAILURE = "failure";
    private static final String STEP = "step";
    private static final String EDIT_PIPELINE = "edit-pipeline";

    public SVNConnection(ISVNConnector connector) {
        myConnector = connector;
    }

    public void open(SVNRepositoryImpl repository) throws SVNException {
        myIsCredentialsReceived = false;
        myConnector.open(repository);
        handshake(repository);
    }

    protected void handshake(SVNRepositoryImpl repository) throws SVNException {
        Object[] items = read("[(*N(*W)(*W))]", null);
        if (!SVNReader.hasValue(items, 0, 2) || !SVNReader.hasValue(items, 2, EDIT_PIPELINE)) {
            throw new SVNException("unsupported version or capability");
        }
        write("(n(w)s)", new Object[] { "2", EDIT_PIPELINE, repository.getLocation().toString() });
    }

    private boolean myIsCredentialsReceived = false;

    public void authenticate(SVNRepositoryImpl repository, ISVNCredentials credentials) throws SVNException {
        String failureReason = null;
        Object[] items = read("[((*W)S)]", null);
        List mechs = SVNReader.getList(items, 0);

        if (mechs == null || mechs.size() == 0) {
            return;
        }

        for (int i = 0; i < mechs.size(); i++) {
            String mech = (String) mechs.get(i);
            if ("ANONYMOUS".equals(mech) || "EXTERNAL".equals(mech)) {
                // send anon and read response
                write("(w())", new Object[] { mech });
                items = read("(W(?S))", null);
                if (SUCCESS.equals(items[0])) {
                    if (!myIsCredentialsReceived) {
                        Object[] creds = read("[(?S?S)]", null);
                        if (repository != null && repository.getRepositoryRoot() == null) {
                            repository.updateCredentials((String) creds[0], (String) creds[1]);
                        }
                        myIsCredentialsReceived = true;
                    }
                    return;
                } else if (FAILURE.equals(items[0])) {
                    failureReason = (String) items[1];
                }
            } else if ("CRAM-MD5".equals(mech)) {
                CramMD5 authenticator = new CramMD5();
                if (credentials == null) {
                    throw new SVNAuthenticationException("authentication failed, no credentials");
                }
                write("(w())", new Object[] { mech });
                while (true) {
                    authenticator.setUserCredentials(credentials);
                    items = read("(W(?B))", null);
                    if (SUCCESS.equals(items[0])) {
                        // should it be here?
                        if (!myIsCredentialsReceived) {
                            Object[] creds = read("[(S?S)]", null);
                            if (creds != null && creds.length == 2 && creds[0] != null && creds[1] != null) {
                                repository.updateCredentials((String) creds[0], (String) creds[1]);
                            }
                            myIsCredentialsReceived = true;
                        }
                        return;
                    } else if (FAILURE.equals(items[0])) {
                        failureReason = new String((byte[]) items[1]);
                        throw new SVNAuthenticationException("authentication failed: " + failureReason);
                    } else if (STEP.equals(items[0])) {
                        byte[] response = authenticator.buildChallengeReponse((byte[]) items[1]);
                        try {
                            getOutputStream().write(response);
                        } catch (IOException e) {
                            throw new SVNException(e);
                        } finally {
                            SVNLoggingConnector.flush();
                        }
                    }
                }
            } else {
                failureReason = mech + " authorization requested, but not supported";
            }
        }
        throw new SVNException(failureReason);
    }

    public void close() throws SVNException {
        myConnector.close();
    }

    public Object[] read(String template, Object[] items) throws SVNException {
        try {
            return SVNReader.parse(getInputStream(), template, items);
        } catch (IOException e) {
            throw new SVNException(e);
        } finally {
            SVNLoggingConnector.flush();
        }
    } 

    public void write(String template, Object[] items) throws SVNException {
        try {
            SVNWriter.write(getOutputStream(), template, items);
        } catch (IOException e) {
            throw new SVNException(e);
        } finally {
            SVNLoggingConnector.flush();
        }
    }

    public OutputStream getOutputStream() throws IOException {
        if (myOutputStream == null) {
            return myOutputStream = myConnector.getOutputStream();
        }
        return myOutputStream;
    }

    public InputStream getInputStream() throws IOException {
        if (myInputStream == null) {
            myInputStream = new RollbackInputStream(new BufferedInputStream(myConnector.getInputStream()));
        }
        return myInputStream;
    }
}