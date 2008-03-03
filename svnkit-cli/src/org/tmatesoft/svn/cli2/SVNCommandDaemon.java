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
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.Permission;

import org.tmatesoft.svn.cli2.svn.SVN;
import org.tmatesoft.svn.cli2.svnadmin.SVNAdmin;
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
            OutputStream os = null;
            // read all from the input stream until empty line is met.
            String input = "";
            try {
                InputStream is = socket.getInputStream();
                os = socket.getOutputStream();
                BufferedReader reader = new BufferedReader(new InputStreamReader(is, "UTF-8"));
                while(true) {
                    String line = reader.readLine();
                    if (line == null || "".equals(line.trim())) {
                        break;
                    }
                    if (!"".equals(input)) {
                        input += "\n";
                    }
                    input += line;
                }
            } catch (IOException e) {
                continue;
            }
            System.err.println("running: " + input);            
            String[] args = input.split("\n");
            if (args.length < 2) {
                System.err.println("insufficient number of arguments");
                continue;
            }
            String userDir = args[0];
            String commandName = args[1];
            String[] commandArgs = new String[args.length - 2];
            System.arraycopy(args, 2, commandArgs, 0, commandArgs.length);
            
            String oldUserDir = System.getProperty("user.dir");
            PrintStream oldOut = System.out;
            PrintStream oldErr = System.err;
            
            ByteArrayOutputStream commandOutData = new ByteArrayOutputStream();
            ByteArrayOutputStream commandErrData = new ByteArrayOutputStream();
            PrintStream commandOut = new PrintStream(commandOutData);
            PrintStream commandErr = new PrintStream(commandOutData);
            int rc = 0;
            try {
                System.setProperty("user.dir", userDir);
                System.setOut(commandOut);
                System.setOut(commandErr);
                if ("svn".equals(commandName)) {
                    SVN.main(commandArgs);
                } else if ("svnadmin".equals(commandName)) {
                    SVNAdmin.main(commandArgs);
                }
            } catch (ExitException e) {
                rc = e.getCode();
                System.err.println("exit code: " + rc);
            } catch (Throwable th) {
                th.printStackTrace();
                rc = 1;
            } finally {
                System.setProperty("user.dir", oldUserDir);
                System.setOut(oldOut);
                System.setErr(oldErr);

                // send output to the user!
                try {
                    commandOut.flush();
                    commandOutData.flush();
                    commandErr.flush();
                    commandErrData.flush();
                    os.write(commandOutData.toByteArray());
                    os.write(new byte[] {'$', '$', '$'});
                    os.write(commandErrData.toByteArray());
                    os.write(new byte[] {'$', '$', '$'});
                    os.write(Integer.toString(rc).getBytes());
                    os.flush();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                
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
