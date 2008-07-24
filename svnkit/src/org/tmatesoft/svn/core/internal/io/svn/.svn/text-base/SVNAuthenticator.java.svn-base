/*
 * ====================================================================
 * Copyright (c) 2004-2007 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */

package org.tmatesoft.svn.core.internal.io.svn;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;

/**
 * @version 1.1.1
 * @author  TMate Software Ltd.
 */
public abstract class SVNAuthenticator {
    
    protected static final String SUCCESS = "success";
    protected static final String FAILURE = "failure";
    protected static final String STEP = "step";

    private SVNConnection myConnection;
    private OutputStream myConnectionOutputStream;
    private InputStream myConnectionInputStream;
    private boolean myHasTried;
    private SVNErrorMessage myLastError;

    protected SVNAuthenticator(SVNConnection connection) throws SVNException {
        myConnection = connection;
        myConnectionInputStream = connection.getInputStream();
        myConnectionOutputStream = connection.getOutputStream();
    }
    
    protected void setOutputStream(OutputStream os) {
        myConnection.setOutputStream(os);
    }

    protected void setInputStream(InputStream is) {
        myConnection.setInputStream(is);
    }
    
    protected InputStream getConnectionInputStream() {
        return myConnectionInputStream;
    }

    protected OutputStream getConnectionOutputStream() {
        return myConnectionOutputStream;
    }
    
    protected SVNConnection getConnection() {
        return myConnection;
    }
    
    protected void onAuthAttempt() {
        myHasTried = true;
    }
    
    public boolean hasTried() {
        return myHasTried;
    }
    
    protected SVNErrorMessage getLastError() {
        return myLastError;
    }
    
    public void dispose() {
    }
    
    protected void setLastError(SVNErrorMessage err) {
        myLastError = err;
    }

    public abstract void authenticate(List mechs, String realm, SVNRepositoryImpl repository) throws SVNException;
}
