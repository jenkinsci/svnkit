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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.MessageFormat;

import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationManager;
import org.tmatesoft.svn.core.auth.SVNAuthentication;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;

import ch.ethz.ssh2.StreamGobbler;


/**
 * @version 1.1.0
 * @author  TMate Software Ltd.
 */
public class SVNTunnelConnector implements ISVNConnector {
    
    private static final String TUNNEL_COMMAND = "{0} {1} svnserve -t";
    
    private String myTunnelSpec;
    private String myName;
    private OutputStream myOutputStream;
    private InputStream myInputStream;
    private Process myProcess;
    
    public SVNTunnelConnector(String name, String tunnelSpec) {
        myName = name;
        myTunnelSpec = tunnelSpec;
    }

    public void open(SVNRepositoryImpl repository) throws SVNException {
        // 1. expand tunnel spec (when env. is used).
        String expandedTunnel = expandTunnelSpec(myName, myTunnelSpec);
        // 2. create tunnel command using repo URL. 
        String host = repository.getLocation().getHost();
        if (repository.getLocation().getUserInfo() != null) {
            String username = repository.getLocation().getUserInfo();
            host = username + "@" + host;
        }
        expandedTunnel = MessageFormat.format(TUNNEL_COMMAND, new String[] {expandedTunnel, host});
        // 3. get and append --tunnel-user if needed.
        if (repository.getAuthenticationManager() != null) {
            SVNAuthentication auth = repository.getAuthenticationManager().getFirstAuthentication(ISVNAuthenticationManager.USERNAME, host, repository.getLocation());
            if (auth == null) {
                SVNErrorManager.cancel("Authentication cancelled");
            }
            repository.getAuthenticationManager().acknowledgeAuthentication(true, ISVNAuthenticationManager.USERNAME, host, null, auth);
            expandedTunnel += " --tunnel-user " + auth.getUserName();
            repository.setExternalUserName(auth.getUserName());
        } 
        
        // 4. launch process.       
        try {
            myProcess = Runtime.getRuntime().exec(expandedTunnel);
            myInputStream = repository.getDebugLog().createLogStream(myProcess.getInputStream()); 
            myOutputStream = repository.getDebugLog().createLogStream(myProcess.getOutputStream()); 
            
            new StreamGobbler(myProcess.getErrorStream());
        } catch (IOException e) {
            try {
                close(repository);
            } catch (SVNException inner) {
            }
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.EXTERNAL_PROGRAM, "Cannot create tunnel: ''{0}''", e.getMessage());            
            SVNErrorManager.error(err, e);
        }
    }

    public InputStream getInputStream() throws IOException {
        return myInputStream;
    }

    public OutputStream getOutputStream() throws IOException {
        return myOutputStream;
    }

    public boolean isConnected(SVNRepositoryImpl repos) throws SVNException {
        return myInputStream != null;
    }

    public void close(SVNRepositoryImpl repository) throws SVNException {
        if (myProcess != null) {
            if (myInputStream != null) {
                repository.getDebugLog().flushStream(myInputStream);
                SVNFileUtil.closeFile(myInputStream);
            }
            if (myOutputStream != null) {
                repository.getDebugLog().flushStream(myOutputStream);
                SVNFileUtil.closeFile(myOutputStream);
            } 
            myProcess.destroy();
            myInputStream = null;
            myOutputStream = null;
            myProcess = null;
        }
    }

    private static String expandTunnelSpec(String name, String tunnelSpec) throws SVNException {
        if (tunnelSpec == null || tunnelSpec.trim().length() == 0) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.EXTERNAL_PROGRAM, "No tunnel spec foound for ''{0}''", name);
            SVNErrorManager.error(err);
        }
        tunnelSpec = tunnelSpec.trim();

        int spaceIndex = tunnelSpec.indexOf(' ');
        String firstSegment = spaceIndex > 0 ? tunnelSpec.substring(0, spaceIndex) : tunnelSpec;
        String lastSegment = spaceIndex > 0 ? tunnelSpec.substring(spaceIndex).trim() : tunnelSpec;
        
        if (firstSegment.charAt(0) == '%' && firstSegment.charAt(firstSegment.length() - 1) == '%') {
            firstSegment = firstSegment.substring(1);
            firstSegment = firstSegment.substring(0, firstSegment.length() - 1);
            firstSegment = SVNFileUtil.getEnvironmentVariable(firstSegment);
        } else if (firstSegment.charAt(0) == '$') {
            firstSegment = firstSegment.substring(1);
            firstSegment = SVNFileUtil.getEnvironmentVariable(firstSegment);
        } else {
            firstSegment = null;
            lastSegment = null;
        }
        if (firstSegment != null) {
            // was expanded.
            tunnelSpec = firstSegment;
        } else if (lastSegment != null) {
            // was expanded with no result.
            tunnelSpec = lastSegment;
        }
        return tunnelSpec;
    }

    public void free() {
    }

    public boolean occupy() {
        return true;
    }

}
