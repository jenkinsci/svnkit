/*
 * ====================================================================
 * Copyright (c) 2004-2006 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://tmate.org/svn/license.html.
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.core.internal.io.dav.http;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.SecretKeySpec;

import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.auth.SVNPasswordAuthentication;
import org.tmatesoft.svn.core.internal.util.SVNBase64;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;


class HTTPNtlmAuthentication2 extends HTTPAuthentication {
    private static final String DEFAULT_CHARSET = "ASCII";
    private static final String PROTOCOL_NAME = "NTLMSSP";
    private static final int LM_RESPONSE_LENGTH = 24;
    private static final int UNINITIATED = 0;
    private static final int TYPE1 = 1;
    private static final int TYPE3 = 3;
    private static byte[] ourMagicBytes = {
        (byte) 0x4B, (byte) 0x47, (byte) 0x53, (byte) 0x21, 
        (byte) 0x40, (byte) 0x23, (byte) 0x24, (byte) 0x25
        };
    
    private int myState;
    private byte[] myResponse;
    private int myPosition; 
    private byte[] myNonce;
    
    public HTTPNtlmAuthentication2(SVNPasswordAuthentication credentials){
        super(credentials);
        myState = UNINITIATED;
    }
    
    public void setType1State(){
        myState = TYPE1;
    }

    public void setType3State(){
        myState = TYPE3;
    }

    public boolean isInType3State(){
        return myState == TYPE3;
    }
    
    private void initResponse(int bufferSize){
        myResponse = new byte[bufferSize];
        myPosition = 0;
    }
    
    private void addByte(byte b){
        myResponse[myPosition++] = b;
    }

    private void addBytes(byte[] bytes) {
        for (int i = 0; i < bytes.length; i++) {
            myResponse[myPosition++] = bytes[i];
        }
    }

    private byte[] convertToShortValue(int num){
        byte[] val = new byte[2];
        val[0] = (byte)(num & 0xff);
        val[1] = (byte)((num >> 8) & 0xff);
        return val;
    }
    
    private String getResponse(){
        byte[] response;
        if (myResponse.length > myPosition) {
            response = new byte[myPosition];
            for (int i = 0; i < myPosition; i++) {
                response[i] = myResponse[i];
            }
        } else {
            response = myResponse;
        }
        
        String base64EncodedResponse = SVNBase64.byteArrayToBase64(response);
        return new String(HTTPAuthentication.getASCIIBytes(base64EncodedResponse));
    }
    
    public void parseChallenge(String challenge){
        byte[] challengeBase64Bytes = HTTPAuthentication.getBytes(challenge, DEFAULT_CHARSET);
        byte[] resultBuffer = new byte[challengeBase64Bytes.length];
        SVNBase64.base64ToByteArray(new StringBuffer(new String(challengeBase64Bytes)), resultBuffer);
        myNonce = new byte[8];
        for (int i = 0; i < 8; i++) {
            myNonce[i] = resultBuffer[i + 24];
        }
    }
    
    public String authenticate() throws SVNException {
        if (myState != TYPE1 && myState != TYPE3) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.RA_DAV_REQUEST_FAILED, "Unsupported message type in HTTP NTLM authentication");
            SVNErrorManager.error(err);
        }
        
        String login = getUserName();
        String domain = null;
        String username = null;

        int slashInd = login != null ? login.indexOf('\\') : -1; 
        if (slashInd != -1) {
            domain = login.substring(0, slashInd);
            username = login.substring(slashInd - 1 + "\\\\".length());
        } else {
            domain = "";
            username = login;
        }

        String hostName = null;
        try {
            InetAddress localhost = InetAddress.getLocalHost();
            hostName = localhost.getHostName();
        } catch (UnknownHostException uhe) {
            hostName = "";
        }
        
        domain = domain.toUpperCase();
        hostName = hostName.toUpperCase();
        //username = username.toUpperCase();
        
        byte[] protocol = HTTPAuthentication.getBytes(PROTOCOL_NAME, DEFAULT_CHARSET);
        byte[] domainBytes = HTTPAuthentication.getBytes(domain, DEFAULT_CHARSET);
        byte[] hostNameBytes = HTTPAuthentication.getBytes(hostName, DEFAULT_CHARSET);
        byte[] domLen = convertToShortValue(domainBytes.length);
        byte[] hostLen = convertToShortValue(hostNameBytes.length);

        if (myState == TYPE1) {
            int responseLength = 32 + domainBytes.length + hostNameBytes.length;
            
            initResponse(responseLength);

            //NTLMSSP\0 signature (8 bytes long)
            addBytes(protocol);
            addByte((byte) 0);

            // Type1 - Negotiate (4 bytes long)
            addByte((byte) 1);
            addByte((byte) 0);
            addByte((byte) 0);
            addByte((byte) 0);

            // Flags (4 bytes long): 'Negotiate OEM', 'Request Target', 
            // 'Negotiate NTLM', 'Negotiate Always Sign'
            addByte((byte) 6);
            addByte((byte) 82);
            addByte((byte) 0);
            addByte((byte) 0);

            // Domain name length (2 bytes short)
            addBytes(domLen);
            // Allocated space for the domain name (2 bytes short)
            addBytes(domLen);

            // Domain name offset (4 bytes long)
            byte[] domainOffset = convertToShortValue(hostNameBytes.length + 32);
            addBytes(domainOffset);
            addByte((byte) 0);
            addByte((byte) 0);

            // Host name length (2 bytes short).
            addBytes(hostLen);
            // Allocated space for the host name (2 bytes short)
            addBytes(hostLen);

            // Host name offset (always 32, 4 bytes long).
            byte[] hostOffset = convertToShortValue(32);
            addBytes(hostOffset);
            addByte((byte) 0);
            addByte((byte) 0);

            // Host name 
            addBytes(hostNameBytes);

            // Domain name
            addBytes(domainBytes);
        } else if (myState == TYPE3) {
            byte[] userBytes = username.getBytes();
            int responseLength = 64 + LM_RESPONSE_LENGTH + domainBytes.length + hostNameBytes.length + userBytes.length;
            
            initResponse(responseLength);

            addBytes(protocol);
            addByte((byte) 0);

            //Type3
            addByte((byte) 3);
            addByte((byte) 0);
            addByte((byte) 0);
            addByte((byte) 0);

            byte[] lmResponseLength = convertToShortValue(24); 
            // LM Response Length 
            addBytes(lmResponseLength);
            // LM Response allocated space
            addBytes(lmResponseLength);

            // LM Response Offset
            addBytes(convertToShortValue(responseLength - 24));
            addByte((byte) 0);
            addByte((byte) 0);

            byte[] ntlmResponseLength = convertToShortValue(0); 
            // NTLM Response Length 
            addBytes(ntlmResponseLength);
            // NTLM Response allocated space
            addBytes(ntlmResponseLength);

            byte[] responseLengthShortBytes = convertToShortValue(responseLength); 
            // NTLM Response Offset
            addBytes(responseLengthShortBytes);
            addByte((byte) 0);
            addByte((byte) 0);

            // Domain length
            addBytes(domLen);
            // Domain allocated space
            addBytes(domLen);
            
            // Domain Offset
            addBytes(convertToShortValue(64));
            addByte((byte) 0);
            addByte((byte) 0);

            byte[] usernameLength = convertToShortValue(userBytes.length); 
            // Username Length 
            addBytes(usernameLength);
            // Username allocated space
            addBytes(usernameLength);

            // User offset
            addBytes(convertToShortValue(64 + domainBytes.length));
            addByte((byte) 0);
            addByte((byte) 0);

            // Host name length
            addBytes(hostLen);
            // Host name allocated space
            addBytes(hostLen);

            // Host offset
            addBytes(convertToShortValue(64 + domainBytes.length + userBytes.length));

            for (int i = 0; i < 6; i++) {
                addByte((byte) 0);
            }

            // Message length
            addBytes(responseLengthShortBytes);
            addByte((byte) 0);
            addByte((byte) 0);

            // Flags
            addByte((byte) 6);
            addByte((byte) 82);
            addByte((byte) 0);
            addByte((byte) 0);

            addBytes(domainBytes);
            addBytes(userBytes);
            addBytes(hostNameBytes);
            addBytes(hashPassword(getPassword()));
        }
        
        return "NTLM " + getResponse();
    }

    private byte[] hashPassword(String password) throws SVNException {
        byte[] passw = password.toUpperCase().getBytes();
        byte[] lmPw1 = new byte[7];
        byte[] lmPw2 = new byte[7];

        int len = passw.length;
        if (len > 7) {
            len = 7;
        }

        int idx;
        for (idx = 0; idx < len; idx++) {
            lmPw1[idx] = passw[idx];
        }
        
        for (; idx < 7; idx++) {
            lmPw1[idx] = (byte) 0;
        }

        len = passw.length;
        if (len > 14) {
            len = 14;
        }
        for (idx = 7; idx < len; idx++) {
            lmPw2[idx - 7] = passw[idx];
        }
        for (; idx < 14; idx++) {
            lmPw2[idx - 7] = (byte) 0;
        }

        byte[] lmHpw1;
        lmHpw1 = encrypt(lmPw1, ourMagicBytes);

        byte[] lmHpw2 = encrypt(lmPw2, ourMagicBytes);

        byte[] lmHpw = new byte[21];
        for (int i = 0; i < lmHpw1.length; i++) {
            lmHpw[i] = lmHpw1[i];
        }
        for (int i = 0; i < lmHpw2.length; i++) {
            lmHpw[i + 8] = lmHpw2[i];
        }
        for (int i = 0; i < 5; i++) {
            lmHpw[i + 16] = (byte) 0;
        }

        // Create the responses.
        byte[] lmResp = new byte[24];
        calcResp(lmHpw, lmResp);

        return lmResp;
    }
    
    private void calcResp(byte[] keys, byte[] results) throws SVNException {
        byte[] keys1 = new byte[7];
        byte[] keys2 = new byte[7];
        byte[] keys3 = new byte[7];
        
        for (int i = 0; i < 7; i++) {
            keys1[i] = keys[i];
        }

        for (int i = 0; i < 7; i++) {
            keys2[i] = keys[i + 7];
        }

        for (int i = 0; i < 7; i++) {
            keys3[i] = keys[i + 14];
        }
        
        byte[] results1 = encrypt(keys1, myNonce);

        byte[] results2 = encrypt(keys2, myNonce);

        byte[] results3 = encrypt(keys3, myNonce);

        for (int i = 0; i < 8; i++) {
            results[i] = results1[i];
        }
        
        for (int i = 0; i < 8; i++) {
            results[i + 8] = results2[i];
        }
        
        for (int i = 0; i < 8; i++) {
            results[i + 16] = results3[i];
        }
    }
    
    private byte[] encrypt(byte[] key, byte[] bytes) throws SVNException {
        Cipher ecipher = getCipher(key);
        try {
            byte[] enc = ecipher.doFinal(bytes);
            return enc;
        } catch (IllegalBlockSizeException e) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, "Invalid block size for DES encryption: {0}", e.getLocalizedMessage());
            SVNErrorManager.error(err);
        } catch (BadPaddingException e) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, "Data not padded correctly for DES encryption: {0}", e.getLocalizedMessage());
            SVNErrorManager.error(err);
        }
        return null;
    }
    
    private Cipher getCipher(byte[] key) throws SVNException {
        try {
            final Cipher ecipher = Cipher.getInstance("DES/ECB/NoPadding");
            key = setupKey(key);
            ecipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "DES"));
            return ecipher;
        } catch (NoSuchAlgorithmException e) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, "DES encryption is not available: {0}", e.getLocalizedMessage());
            SVNErrorManager.error(err);
        } catch (InvalidKeyException e) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, "Invalid key for DES encryption: {0}", e.getLocalizedMessage());
            SVNErrorManager.error(err);
        } catch (NoSuchPaddingException e) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, "NoPadding option for DES is not available: {0}", e.getLocalizedMessage());
            SVNErrorManager.error(err);
        }
        return null;
    }
    
    private byte[] setupKey(byte[] key56) {
        byte[] key = new byte[8];
        key[0] = (byte) ((key56[0] >> 1) & 0xff);
        key[1] = (byte) ((((key56[0] & 0x01) << 6) 
            | (((key56[1] & 0xff) >> 2) & 0xff)) & 0xff);
        key[2] = (byte) ((((key56[1] & 0x03) << 5) 
            | (((key56[2] & 0xff) >> 3) & 0xff)) & 0xff);
        key[3] = (byte) ((((key56[2] & 0x07) << 4) 
            | (((key56[3] & 0xff) >> 4) & 0xff)) & 0xff);
        key[4] = (byte) ((((key56[3] & 0x0f) << 3) 
            | (((key56[4] & 0xff) >> 5) & 0xff)) & 0xff);
        key[5] = (byte) ((((key56[4] & 0x1f) << 2) 
            | (((key56[5] & 0xff) >> 6) & 0xff)) & 0xff);
        key[6] = (byte) ((((key56[5] & 0x3f) << 1) 
            | (((key56[6] & 0xff) >> 7) & 0xff)) & 0xff);
        key[7] = (byte) (key56[6] & 0x7f);
        
        for (int i = 0; i < key.length; i++) {
            key[i] = (byte) (key[i] << 1);
        }
        return key;
    }

    public String getAuthenticationScheme(){
        return "NTLM";
    }

}
