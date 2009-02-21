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
package org.tmatesoft.svn.core.test.daemon;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.Permission;

import org.tmatesoft.svn.core.internal.io.dav.DAVRepositoryFactory;
import org.tmatesoft.svn.core.internal.io.fs.FSRepositoryFactory;
import org.tmatesoft.svn.core.internal.io.svn.SVNRepositoryFactoryImpl;
import org.tmatesoft.svn.util.ISVNDebugLog;
import org.tmatesoft.svn.util.SVNDebugLog;
import org.tmatesoft.svn.util.SVNLogType;


/**
 * @version 1.2
 * @author  TMate Software Ltd.
 */
public class SVNCommandDaemon implements Runnable {

    private String myCurrentTestsType;
    private int myPort;
    private SecurityManager myDefaultSecurityManager;
    
    private volatile int myLastExitCode = 0;

    public SVNCommandDaemon(int port) {
        SVNRepositoryFactoryImpl.setup();
        DAVRepositoryFactory.setup();
        FSRepositoryFactory.setup();
        
        myPort = port;
        myDefaultSecurityManager = System.getSecurityManager();
        System.setSecurityManager(new SecurityManager() {
            public void checkExit(int status) {
                synchronized (SVNCommandDaemon.class) {
                    myLastExitCode = status;
                }
                super.checkExit(status);
                throw new SVNCommandExitException(status);
            }
            
            public void checkPermission(Permission perm, Object context) {}
            public void checkPermission(Permission perm) {}
        });
    }
    
    public void shutdown() {
        System.setSecurityManager(myDefaultSecurityManager);
    }

    public void run() {
        ISVNDebugLog log = SVNDebugLog.getDefaultLog();
        ServerSocket serverSocket = null;
        try {
            serverSocket = new ServerSocket(myPort);
        } catch (IOException e) {
            log.logSevere(SVNLogType.DEFAULT, "cannot create server socket at port " + myPort);
            log.logSevere(SVNLogType.DEFAULT, e);
            return;
        }
        if (serverSocket == null) {
            log.logSevere(SVNLogType.DEFAULT, "cannot create server socket at port " + myPort);
            return;
        }
        while(true) {
            Socket socket = null;
            try {
                socket = serverSocket.accept();
            } catch (IOException e) {
                log.logSevere(SVNLogType.DEFAULT, "cannot accept connection");
                log.logSevere(SVNLogType.DEFAULT, e);
                continue;
            }
            if (socket == null) {
                log.logSevere(SVNLogType.DEFAULT, "cannot accept connection");
                continue;
            }
            OutputStream os = null;
            InputStream is = null;
            
            try {
                is = socket.getInputStream();
                os = socket.getOutputStream();

                SVNCommandDaemonEnvironment environment = createEnvironment(is);
                // run!
                int rc = environment.run();
                // throwing exception doesn't work :(
                synchronized (SVNCommandDaemon.class) {
                    rc = myLastExitCode;
                }
                log.logSevere(SVNLogType.DEFAULT, "command exit code: " + rc);
                // send back
                try {
                    os.write(escape(environment.getStdOut()));
                    os.write(new byte[] {
                            's', 'v', 'n', 'k', 'i', 't', '_', 'e', 'r', 'r'
                    });
                    os.write(escape(environment.getStdErr()));
                    os.write(new byte[] {
                            's', 'v', 'n', 'k', 'i', 't', '_', 'r', 'c'
                    });
                    os.write(Integer.toString(rc).getBytes());
                    os.flush();
                } catch (IOException e) {
                    log.logSevere(SVNLogType.DEFAULT, "error sending execution results");
                    log.logSevere(SVNLogType.DEFAULT, e);
                }
            } catch (IOException e) {
                log.logSevere(SVNLogType.DEFAULT, "error processing client request");
                log.logSevere(SVNLogType.DEFAULT, e);
            } finally {
                try {
                    is.close();
                } catch (IOException e) {
                    log.logSevere(SVNLogType.DEFAULT, "error closing client stream");
                    log.logSevere(SVNLogType.DEFAULT, e);
                }
                try {
                    os.close();
                } catch (IOException e) {
                    log.logSevere(SVNLogType.DEFAULT, "error closing client stream");
                    log.logSevere(SVNLogType.DEFAULT, e);
                }
                try {
                    socket.close();
                } catch (IOException e) {
                    log.logSevere(SVNLogType.DEFAULT, "error closing client socket");
                    log.logSevere(SVNLogType.DEFAULT, e);
                }
            }
        }
    }
    
    private SVNCommandDaemonEnvironment createEnvironment(InputStream is) throws IOException {
        // read all environment.
        int lastChar = 0;
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        while(true) {
            int b = is.read();
            if (b < 0) {
                break;
            }
            if (b == '\n' && lastChar == '\n') {
                break;
            }
            lastChar = b;
            buffer.write(b);
        }
        buffer.close();
        // now parse.
        SVNCommandDaemonEnvironment environment = new SVNCommandDaemonEnvironment(getTestsType());
        BufferedReader inputReader = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(buffer.toByteArray()), "UTF-8"));
        while(true) {
            String line = inputReader.readLine();
            if (line == null || "".equals(line.trim())) {
                break;
            }
            environment.addArgumentLine(line);
        }
        // read stdin data.
        buffer = new ByteArrayOutputStream();
        while(true) {
            int b = is.read();
            if (b < 0) {
                break;
            }
            if (b == '\0') {
                break;
            }
            buffer.write(b);
        }
        buffer.close();
        environment.setStdIn(buffer.toByteArray());
        return environment;
    }
    
    public void setTestsType(String type) {
        myCurrentTestsType = type;
    }
    
    private String getTestsType() {
        return myCurrentTestsType;
    }
    
    private byte[] escape(byte[] src) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        for (int i = 0; i < src.length; i++) {
            if (src[i] == '\n') {
                buffer.write('\\');
                buffer.write('n');
            } else if (src[i] < 32) {
                buffer.write('\\');
                buffer.write('0');
                buffer.write(Integer.toOctalString(src[i]).getBytes());
            } else if (src[i] == '\\') {
                buffer.write('\\');
                buffer.write('\\');
            } else {
                buffer.write(src[i]);
            }
        }
        buffer.close();
        return buffer.toByteArray();
    }
}
