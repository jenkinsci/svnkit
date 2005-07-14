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
package org.tmatesoft.svn.core.internal.io.dav;

import org.tmatesoft.svn.core.io.SVNAuthenticationException;
import org.tmatesoft.svn.core.auth.SVNPasswordAuthentication;

import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.StringTokenizer;

/**
 * @version 1.0
 * @author  TMate Software Ltd.
 */
public class HttpDigestAuth {

    private static final char[] HEXADECIMAL = {
        '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd',
        'e', 'f'
    };
    private static final String NC = "00000001";

    private Map myChallenge;
    private String myCnonce;
    private String myQop;

    private SVNPasswordAuthentication myCredentials;

    public HttpDigestAuth(SVNPasswordAuthentication credentials, Map challenge) throws SVNAuthenticationException {
        myChallenge = challenge;
        String qop = (String) challenge.get("qop");
        String selectedQop = null;

        if (qop != null) {
            for(StringTokenizer tok = new StringTokenizer(qop,","); tok.hasMoreTokens();) {
                selectedQop = tok.nextToken().trim();
                if ("auth".equals(selectedQop)) {
                    break;
                }
            }
        }
        if (selectedQop != null && !"auth".equals(selectedQop)) {
            throw new SVNAuthenticationException("Digest HTTP auth: '" + selectedQop + "' is not supported");
        }
        myQop = selectedQop;
        myCnonce = createCnonce();
        myCredentials = credentials;
    }

    public String authenticate() throws SVNAuthenticationException {
        String uname = myCredentials.getUserName();

        String digest = createDigest(uname, myCredentials.getPassword(), "US-ASCII");

        String uri = getParameter("uri");
        String realm = getParameter("realm");
        String nonce = getParameter("nonce");
//        String nc = getParameter("nc");
        String opaque = getParameter("opaque");
//        String response = digest;
//        String qop = getParameter("qop");
        String algorithm = getParameter("algorithm", "MD5");

        StringBuffer sb = new StringBuffer();
        sb.append("username=\"" + uname + "\"")
          .append(", realm=\"" + realm + "\"")
          .append(", nonce=\"" + nonce + "\"").append(", uri=\"" + uri + "\"")
          .append(", response=\"" + digest + "\"");
        if (myQop != null) {
            sb.append(", qop=\"" + myQop + "\"")
              .append(", nc="+ NC)
              .append(", cnonce=\"" + myCnonce + "\"");
        }
        if (algorithm != null) {
            sb.append(", algorithm=\"" + algorithm + "\"");
        }
        if (opaque != null) {
            sb.append(", opaque=\"" + opaque + "\"");
        }
        return sb.toString();
    }

    private String createDigest(String uname, String pwd, String charset) throws SVNAuthenticationException {
        final String digAlg = "MD5";

        String uri = getParameter("uri");
        String realm = getParameter("realm");
        String nonce = getParameter("nonce");
//        String qop = getParameter("qop");
        String method = getParameter("methodname");
        String algorithm = getParameter("algorithm", "MD5");

        MessageDigest md5Helper;
        try {
            md5Helper = MessageDigest.getInstance(digAlg);
        } catch (Exception e) {
            throw new SVNAuthenticationException(
              "Unsupported algorithm in HTTP Digest authentication: "
               + digAlg);
        }
        StringBuffer tmp = new StringBuffer(uname.length() + realm.length() + pwd.length() + 2);
        tmp.append(uname);
        tmp.append(':');
        tmp.append(realm);
        tmp.append(':');
        tmp.append(pwd);
        String a1 = tmp.toString();
        if(algorithm.equals("MD5-sess")) {
            String tmp2=encode(md5Helper.digest(getBytes(a1, charset)));
            StringBuffer tmp3 = new StringBuffer(tmp2.length() + nonce.length() + myCnonce.length() + 2);
            tmp3.append(tmp2);
            tmp3.append(':');
            tmp3.append(nonce);
            tmp3.append(':');
            tmp3.append(myCnonce);
            a1 = tmp3.toString();
        }

        String md5a1 = encode(md5Helper.digest(getBytes(a1, charset)));
        String a2 = method + ":" + uri;
        String md5a2 = encode(md5Helper.digest(getAsciiBytes(a2)));

        StringBuffer tmp2;
        if (myQop == null) {
            tmp2 = new StringBuffer(md5a1.length() + nonce.length() + md5a2.length());
            tmp2.append(md5a1);
            tmp2.append(':');
            tmp2.append(nonce);
            tmp2.append(':');
            tmp2.append(md5a2);
        } else {
            String qopOption = "auth";
            tmp2 = new StringBuffer(md5a1.length() + nonce.length()
                    + NC.length() + myCnonce.length() + qopOption.length() + md5a2.length() + 5);
            tmp2.append(md5a1);
            tmp2.append(':');
            tmp2.append(nonce);
            tmp2.append(':');
            tmp2.append(NC);
            tmp2.append(':');
            tmp2.append(myCnonce);
            tmp2.append(':');
            tmp2.append(qopOption);
            tmp2.append(':');
            tmp2.append(md5a2);
        }

        return encode(md5Helper.digest(getAsciiBytes(tmp2.toString())));
    }

    private String getParameter(String name) {
        return getParameter(name, null);
    }

    private String getParameter(String name, String defaultValue) {
        String value = (String) myChallenge.get(name);
        if (value == null) {
            value = defaultValue;
        }
        return value;
    }

    private static String createCnonce() {
        String cnonce;
        final String digAlg = "MD5";
        MessageDigest md5Helper;

        try {
            md5Helper = MessageDigest.getInstance(digAlg);
        } catch (NoSuchAlgorithmException e) {
            return null;
        }
        cnonce = Long.toString(System.currentTimeMillis());
        cnonce = encode(md5Helper.digest(getAsciiBytes(cnonce)));
        return cnonce;
    }

    private static String encode(byte[] binaryData) {
        if (binaryData.length != 16) {
            return null;
        }

        char[] buffer = new char[32];
        for (int i = 0; i < 16; i++) {
            int low = binaryData[i] & 0x0f;
            int high = (binaryData[i] & 0xf0) >> 4;
            buffer[i * 2] = HEXADECIMAL[high];
            buffer[(i * 2) + 1] = HEXADECIMAL[low];
        }

        return new String(buffer);
    }

    private static byte[] getAsciiBytes(final String data) {
        return getBytes(data, "US-ASCII");
    }

    private static byte[] getBytes(final String data, String charset) {
        try {
            return data.getBytes(charset);
        } catch (UnsupportedEncodingException e) {
            return data.getBytes();
        }
    }

}
