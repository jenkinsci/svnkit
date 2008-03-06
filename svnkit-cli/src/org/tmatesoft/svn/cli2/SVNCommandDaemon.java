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
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.Permission;

import org.tmatesoft.svn.cli.SVNSync;
import org.tmatesoft.svn.cli2.svn.SVN;
import org.tmatesoft.svn.cli2.svnadmin.SVNAdmin;
import org.tmatesoft.svn.cli2.svnlook.SVNLook;
import org.tmatesoft.svn.cli2.svnversion.SVNVersion;
import org.tmatesoft.svn.core.internal.io.dav.DAVRepositoryFactory;
import org.tmatesoft.svn.core.internal.io.fs.FSRepositoryFactory;
import org.tmatesoft.svn.core.internal.io.svn.SVNRepositoryFactoryImpl;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.util.ISVNDebugLog;
import org.tmatesoft.svn.util.SVNDebugLog;


/**
 * @version 1.2
 * @author  TMate Software Ltd.
 */
public class SVNCommandDaemon implements Runnable {

    private int myPort;
    private SecurityManager myDefaultSecurityManager;

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
        
        SVNCommandDaemon daemon = new SVNCommandDaemon(port);

        Thread serverThread = new Thread(daemon);
        serverThread.setDaemon(false);
        serverThread.start();
    }
    
    public SVNCommandDaemon(int port) {
        SVNRepositoryFactoryImpl.setup();
        DAVRepositoryFactory.setup();
        FSRepositoryFactory.setup();
        
        myPort = port;
        myDefaultSecurityManager = System.getSecurityManager();
        System.setSecurityManager(new SecurityManager() {
            public void checkExit(int status) {
                super.checkExit(status);
                throw new ExitException(status);
            }
            
            public void checkPermission(Permission perm, Object context) {}
            public void checkPermission(Permission perm) {}
        });
    }
    
    private void shutdown() {
        System.setSecurityManager(myDefaultSecurityManager);
        System.exit(0);
    }

    public void run() {
        ISVNDebugLog log = SVNDebugLog.getDefaultLog();
        ServerSocket serverSocket = null;
        try {
            serverSocket = new ServerSocket(myPort);
        } catch (IOException e) {
            log.error("cannot create server socket at port " + myPort);
            log.error(e);
            return;
        }
        if (serverSocket == null) {
            log.error("cannot create server socket at port " + myPort);
            return;
        }
        while(true) {
            Socket socket = null;
            try {
                socket = serverSocket.accept();
            } catch (IOException e) {
                log.error("cannot accept connection");
                log.error(e);
                continue;
            }
            if (socket == null) {
                log.error("cannot accept connection");
                continue;
            }
            OutputStream os = null;
            InputStream is = null;
            // read all from the input stream until empty line is met.
            String input = "";
            String editor = null;
            String mergeTool = null;
            String testFunction = null;
            
            byte[] body = null;
            try {
                is = socket.getInputStream();
                os = socket.getOutputStream();
                // look for \n\n
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
                byte[] header = buffer.toByteArray();
                ByteArrayOutputStream envBuffer = new ByteArrayOutputStream();
                lastChar = 0;
                while(true) {
                    int b = is.read();
                    if (b < 0) {
                        break;
                    }
                    if (b == '\n' && lastChar == '\n') {
                        break;
                    }
                    lastChar = b;
                    envBuffer.write(b);
                }
                envBuffer.close();
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
                body = buffer.toByteArray();
                
                // 
                BufferedReader envReader = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(envBuffer.toByteArray()), "UTF-8"));
                editor = envReader.readLine();
                if (editor == null || "".equals(editor.trim())) {
                    editor = null;
                } else {
                    editor = editor.trim();
                }
                mergeTool = envReader.readLine();
                if (mergeTool == null || "".equals(mergeTool.trim())) {
                    mergeTool = null;
                } else {
                    mergeTool = mergeTool.trim();
                }
                testFunction = envReader.readLine();
                if (testFunction == null || "".equals(testFunction.trim())) {
                    testFunction = null;
                } else {
                    testFunction = testFunction.trim();
                }
                envReader.close();
                
                BufferedReader reader = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(header), "UTF-8"));
                while(true) {
                    String line = reader.readLine();
                    if (line == null || "".equals(line.trim())) {
                        break;
                    } else if ("SHUTDOWN".equals(line.trim()) && "".equals(input)) {
                        try {
                            os.write(("svnkit daemon shutted down on port " + myPort).getBytes());
                            os.flush();
                        } catch (IOException inner) {
                            log.error("error writing shutdown response");
                            log.error(inner);
                        }
                        try {
                            socket.close();
                        } catch (IOException inner) {
                            log.error("error closing client socket");
                            log.error(inner);
                        }
                        shutdown();
                        return;
                    }
                    if (!"".equals(input)) {
                        input += "\n";
                    }
                    input += line;
                }
                reader.close();
            } catch (IOException e) {
                log.error("error reading input");
                log.error(e);
                try {
                    socket.close();
                } catch (IOException inner) {
                    log.error("error closing client socket");
                    log.error(inner);
                }
                continue;
            }
            //log.info("running: " + input);
            String[] args = input.split("\n");
            if (args.length < 2) {
                log.error("Insufficient number of arguments read, at least two needed");
                try {
                    socket.close();
                } catch (IOException e) {
                    log.error("error closing client socket");
                    log.error(e);
                }
                continue;
            }
            String userDir = args[0];
            String commandName = args[1];
            String[] commandArgs = new String[args.length - 2];
            System.arraycopy(args, 2, commandArgs, 0, commandArgs.length);
            
            String oldUserDir = System.getProperty("user.dir");
            PrintStream oldOut = System.out;
            PrintStream oldErr = System.err;
            InputStream oldIn = System.in;
            
            ByteArrayOutputStream commandOutData = new ByteArrayOutputStream();
            ByteArrayOutputStream commandErrData = new ByteArrayOutputStream();
            PrintStream commandOut = new PrintStream(commandOutData);
            PrintStream commandErr = new PrintStream(commandErrData);
            int rc = 0;
            try {
                SVNFileUtil.setTestEnvironment(editor, mergeTool, testFunction);
                System.setProperty("user.dir", userDir);
                System.setOut(commandOut);
                System.setErr(commandErr);
                System.setIn(new ByteArrayInputStream(body));
                if ("svn".equals(commandName)) {
                    SVN.main(commandArgs);
                } else if ("svnadmin".equals(commandName)) {
                    SVNAdmin.main(commandArgs);
                } else if ("svnlook".equals(commandName)) {
                    SVNLook.main(commandArgs);
                } else if ("svnversion".equals(commandName)) {
                    SVNVersion.main(commandArgs);
                } else if ("svnsync".equals(commandName)) {
                    SVNSync.main(commandArgs);
                }
            } catch (ExitException e) {
                rc = e.getCode();
                log.error("command exit code: " + rc);
            } catch (Throwable th) {
                log.error("error running command");
                log.error(th);
                rc = 1;
            } finally {
                SVNFileUtil.setTestEnvironment(null, null, null);
                System.setProperty("user.dir", oldUserDir);
                System.setIn(oldIn);
                System.setOut(oldOut);
                System.setErr(oldErr);

                // send output to the user!
                log.error("command exit code: " + rc);
                try {
                    commandOut.flush();
                    commandOutData.flush();
                    commandErr.flush();
                    commandErrData.flush();
                    os.write(commandOutData.toByteArray());
                    os.write(new byte[] {
                            '$', '$', '$'
                    });
                    os.write(commandErrData.toByteArray());
                    os.write(new byte[] {
                            '$', '$', '$'
                    });
                    os.write(Integer.toString(rc).getBytes());
                    os.flush();
                } catch (IOException e) {
                    log.error("error sending execution results");
                    log.error(e);
                }

                try {
                    os.close();
                } catch (IOException e) {
                    log.error("error closing client stream");
                    log.error(e);
                }
                try {
                    socket.close();
                } catch (IOException e) {
                    log.error("error closing client socket");
                    log.error(e);
                }
            }
        }
    }
    
    private static class ExitException extends SecurityException {
        private int myCode;

        public ExitException(int code) {
            myCode = code;
        }
        public int getCode() {
            return myCode;
        }
    }
}
