/*
 * ====================================================================
 * Copyright (c) 2004-2007 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html.
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.core.internal.wc;

import org.tmatesoft.svn.core.internal.util.SVNBase64;


/**
 * @version 1.1.2
 * @author  TMate Software Ltd.
 */
public class SVNWinCryptPasswordCipher extends SVNPasswordCipher {
    
    private static boolean ourIsLibraryLoaded;

    static {
        try {
            System.loadLibrary("SVNKitWinCryptHelper");
            ourIsLibraryLoaded = true;
        } catch (Throwable th) {
            ourIsLibraryLoaded = false;
        }
    }
    
    public static boolean isEnabled() {
        return ourIsLibraryLoaded;
    }
    
    public String decrypt(String encryptedData) {
        if (encryptedData == null) {
            return null; 
        }

        byte[] buffer = new byte[encryptedData.length()];
        int decodedBytes = SVNBase64.base64ToByteArray(new StringBuffer(encryptedData), buffer);
        byte[] decodedBuffer = new byte[decodedBytes];
        System.arraycopy(buffer, 0, decodedBuffer, 0, decodedBytes);
        return decryptData(decodedBuffer);
    }

    public String encrypt(String rawData) {
        if (rawData == null) {
            return null;
        }

        byte[] encryptedBytes = encryptData(rawData);
        if (encryptedBytes != null) {
            return SVNBase64.byteArrayToBase64(encryptedBytes);
        }
        return null;
    }

    private native byte[] encryptData(String rawData);

    private native String decryptData(byte[] encryptedData);

    public String getCipherType() {
        return SVNPasswordCipher.WINCRYPT_CIPHER_TYPE;
    }

}
