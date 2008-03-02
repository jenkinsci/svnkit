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
package org.tmatesoft.svn.cli2;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.Permission;

import org.tmatesoft.svn.cli2.svn.SVN;
import org.tmatesoft.svn.core.internal.io.dav.DAVRepositoryFactory;
import org.tmatesoft.svn.core.internal.io.fs.FSRepositoryFactory;
import org.tmatesoft.svn.core.internal.io.svn.SVNRepositoryFactoryImpl;


/**
 * @version 1.2
 * @author  TMate Software Ltd.
 */
public class SVNCommandDaemon implements Runnable {

    private int myPort;

    public static void main(String[] args) {
        int port = -1;
        if (args.length > 0) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException nfe) {
                //
            }
        }
        if (port <= 0) {
            port = 1729;
        }
        
        System.setSecurityManager(new SecurityManager() {
            public void checkExit(int status) { 
                throw new ExitException(status);
            }
            
            public void checkPermission(Permission perm, Object context) {}
            public void checkPermission(Permission perm) {}
        });
        
        SVNRepositoryFactoryImpl.setup();
        DAVRepositoryFactory.setup();
        FSRepositoryFactory.setup();
        
        SVNCommandDaemon daemon = new SVNCommandDaemon(port);

        Thread serverThread = new Thread(daemon);
        serverThread.setDaemon(false);
        serverThread.start();
    }
    
    private SVNCommandDaemon(int port) {
        myPort = port;
    }

    public void run() {
        ServerSocket serverSocket = null;
        try {
            serverSocket = new ServerSocket(myPort);
        } catch (IOException e) {
            e.printStackTrace();
        }
        if (serverSocket == null) {
            return;
        }
        while(true) {
            Socket socket = null;
            try {
                socket = serverSocket.accept();
            } catch (IOException e) {
                e.printStackTrace();
            }
            if (socket == null) {
                continue;
            }
            String commandLine = null;
            OutputStream os = null;
            try {
                InputStream is = socket.getInputStream();
                os = socket.getOutputStream();
                BufferedReader reader = new BufferedReader(new InputStreamReader(is, "UTF-8"));
                commandLine = reader.readLine();
            } catch (IOException e) {
                e.printStackTrace();
                continue;
            }
            System.err.println("running: " + commandLine);            
            String[] args = commandLine.split(" ");
            try {
                System.setOut(new PrintStream(os));
                SVN.main(args);
            } catch (ExitException e) {
                System.err.println("exit code: " + e.getCode());
            } catch (Throwable th) {
                th.printStackTrace();
            } finally {
                try {
                    os.close();
                } catch (IOException e1) {
                    e1.printStackTrace();
                }
                try {
                    socket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }
    private static class ExitException extends RuntimeException {
        private int myCode;

        public ExitException(int code) {
            myCode = code;
        }
        public int getCode() {
            return myCode;
        }
    }
}
