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

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;

import javax.net.ssl.HttpsURLConnection;

/**
 * @author TMate Software Ltd.
 */
public class HTTPSTest {
    public static void main(String argv[]) {
        
        System.setProperty("javax.net.ssl.keyStore", "c:/keystore");
        System.setProperty("javax.net.ssl.keyStoreType", "jks");
        System.setProperty("javax.net.ssl.keyStorePassword", "password");
        
        try {
            
            URL url = new URL("https://svn.apache.org/repos/asf");
            HttpsURLConnection connection = (HttpsURLConnection) url.openConnection();
            connection.connect();
            connection.disconnect();
        } catch (MalformedURLException mue) {
            mue.printStackTrace(System.out);
        } catch (IOException ie) {
            ie.printStackTrace(System.out);
        }
    }
}