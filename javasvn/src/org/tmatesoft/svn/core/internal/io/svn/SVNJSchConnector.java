package org.tmatesoft.svn.core.internal.io.svn;

import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import org.tmatesoft.svn.core.io.SVNAuthenticationException;
import org.tmatesoft.svn.core.io.SVNException;
import org.tmatesoft.svn.core.wc.ISVNAuthenticationManager;
import org.tmatesoft.svn.core.wc.SVNAuthentication;
import org.tmatesoft.svn.util.DebugLog;

import java.io.FilterInputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * @author Marc Strapetz
 * @author alex
 */
public class SVNJSchConnector implements ISVNConnector {

    private static final String CHANNEL_TYPE = "exec";
	private static final String SVNSERVE_COMMAND = "svnserve --tunnel";
	
	private ChannelExec myChannel;
    private InputStream myInputStream;
    private OutputStream myOutputStream;

    public void open(SVNRepositoryImpl repository) throws SVNException {
        ISVNAuthenticationManager authManager = repository.getAuthenticationManager();

        String realm = repository.getLocation().getProtocol() + "://" + repository.getLocation().getHost() + ":" + repository.getLocation().getPort();
        SVNAuthentication authentication = authManager.getFirstAuthentication(ISVNAuthenticationManager.SSH, realm);
        if (authentication == null) {
            throw new SVNAuthenticationException();
        }
        SVNAuthenticationException lastException = null;
        Session session = null;
        
        while (authentication != null) {
            try {
            	session = SVNJSchSession.getSession(repository.getLocation(), authentication);
            	if (session != null && !session.isConnected()) {
            		session = null;
            		continue;
            	}
                lastException = null;
                if (authentication.isStorageAllowed()) {
                    authManager.addAuthentication(realm, authentication, authManager.isAuthStorageEnabled());
                }
                repository.setExternalUserName(authentication.getUserName());
                break;
            } catch (SVNAuthenticationException e) {
                if (session != null && session.isConnected()) {
            		DebugLog.log("DISCONNECTING: " + session);
            		session.disconnect();
            		session = null;
            	}
                lastException = e;
                authentication = authManager.getNextAuthentication(ISVNAuthenticationManager.SSH, realm);
            }
        }
        if (lastException != null || session == null) {
            if (lastException != null) {
                throw lastException;
            }
            throw new SVNAuthenticationException("Can't establish SSH connection without credentials");
        }
        try {
            int retry = 1;
            while(true) {
	            myChannel = (ChannelExec) session.openChannel(CHANNEL_TYPE);
	            String command = SVNSERVE_COMMAND;
	            myChannel.setCommand(command);
	            
	            myOutputStream = myChannel.getOutputStream();
	            myInputStream = myChannel.getInputStream();
	
	            DebugLog.log("JSCH command: " + command);
            	try {
		            myChannel.connect();
            	} catch (JSchException e) {
            		retry--;
            		if (retry < 0) {
            			throw new SVNException(e);
            		}
            		if (session.isConnected()) {
            			session.disconnect();
            		}
            		continue;
            	}
	            break;
            }
        } catch (Throwable e) {
        	close();
        	if (session.isConnected()) {
        		session.disconnect();
        	}
            throw new SVNException("Failed to open SSH session: " + e.getMessage());
        }
        
		myInputStream = new FilterInputStream(myInputStream) {
			public void close() {
            }
		};
		myOutputStream = new FilterOutputStream(myOutputStream) {
			public void close() {
            }
		};
    }

    public void close() throws SVNException {
        if (myChannel != null) {
        	myChannel.disconnect();
        }
        myChannel = null;
        myOutputStream = null;
        myInputStream = null;
    }

    public InputStream getInputStream() throws IOException {
        return myInputStream;
    }

    public OutputStream getOutputStream() throws IOException {
        return myOutputStream;
    }
}