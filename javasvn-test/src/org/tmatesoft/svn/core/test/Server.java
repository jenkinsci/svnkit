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

package org.tmatesoft.svn.core.test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * @author TMate Software Ltd.
 */
public class Server {

    public static void main(String[] args) {
        
        try {
            ServerSocket sSocket = new ServerSocket(8080);
            Socket socket = sSocket.accept();
            BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            
            String line;
            while((line = reader.readLine()) != null) {
                System.err.println(line);
            }
            
            reader.close();
            socket.close();
            
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
