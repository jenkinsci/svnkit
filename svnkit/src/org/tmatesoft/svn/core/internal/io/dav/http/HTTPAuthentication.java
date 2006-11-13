/*
 * ====================================================================
 * Copyright (c) 2004-2006 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.core.internal.io.dav.http;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.TreeMap;

import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.auth.SVNPasswordAuthentication;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;

/**
 * @version 1.1.0
 * @author  TMate Software Ltd.
 */
abstract class HTTPAuthentication {

    private Map myChallengeParameters;
    private String myUserName;
    private String myPassword;
    
    private static final String AUTH_METHODS_PROPERTY = "svnkit.http.methods";
    private static final String OLD_AUTH_METHODS_PROPERTY = "javasvn.http.methods";
    
    protected HTTPAuthentication (SVNPasswordAuthentication credentials) {
        if (credentials != null) {
            myUserName = credentials.getUserName();
            myPassword = credentials.getPassword();
        }
    }

    protected HTTPAuthentication (String name, String password) {
        myUserName = name;
        myPassword = password;
    }

    protected HTTPAuthentication () {
    }

    public void setChallengeParameter(String name, String value) {
        Map params = getChallengeParameters();
        params.put(name, value);
    }
    
    public String getChallengeParameter(String name) {
        if (myChallengeParameters == null) {
            return null;
        }
        return (String)myChallengeParameters.get(name);
    }
    
    protected Map getChallengeParameters() {
        if (myChallengeParameters == null) {
            myChallengeParameters = new TreeMap();
        }
        return myChallengeParameters;
    }
    
    public void setCredentials(SVNPasswordAuthentication credentials) {
        if (credentials != null) {
            myUserName = credentials.getUserName();
            myPassword = credentials.getPassword();
        }
    }

    public String getUserName() {
        if (myUserName == null) {
            myUserName = System.getProperty("user.name", "");
        }
        return myUserName;
    }
    
    public String getPassword() {
        return myPassword;
    }

    public void setUserName(String name) {
        myUserName = name;
    }
    
    public void setPassword(String password) {
        myPassword = password;
    }
    
    public static HTTPAuthentication parseAuthParameters(Collection authHeaderValues, HTTPAuthentication prevResponse) throws SVNException {
        if (authHeaderValues == null) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNSUPPORTED_FEATURE, "Missing HTTP authorization method"); 
            SVNErrorManager.error(err);
        }

        HTTPAuthentication auth = null;
        String authHeader = null;
        // sort auth headers accordingly to priorities.
        authHeaderValues = sortSchemes(authHeaderValues);
        
        for (Iterator authSchemes = authHeaderValues.iterator(); authSchemes.hasNext();) {
            authHeader = (String)authSchemes.next();
            String source = authHeader.trim();
            // parse strings: name="value" or name=value
            int index = source.indexOf(' ');
            
            if (index <= 0) {
                index = source.length();
                if (!"NTLM".equalsIgnoreCase(source.substring(0, index))) {
                    continue;
                }
            }
            String method = source.substring(0, index);
        
            source = source.substring(index).trim();
            if ("Basic".equalsIgnoreCase(method)) {
                auth = new HTTPBasicAuthentication();
                
                if (source.indexOf("realm=") >= 0) {
                    source = source.substring(source.indexOf("realm=") + "realm=".length());
                    source = source.trim();
                    if (source.startsWith("\"")) {
                        source = source.substring(1);
                    }
                    if (source.endsWith("\"")) {
                        source = source.substring(0, source.length() - 1);
                    }
                    //parameters.put("realm", source);
                    auth.setChallengeParameter("realm", source);
                }
                break;
            } else if ("Digest".equalsIgnoreCase(method)) {
                auth = new HTTPDigestAuthentication();
                
                char[] chars = source.toCharArray();
                int tokenIndex = 0;
                boolean parsingToken = true;
                String name = null;
                String value;
                int quotesCount = 0;
            
                for(int i = 0; i < chars.length; i++) {
                    if (parsingToken) {
                        if (chars[i] == '=') {
                            name = new String(chars, tokenIndex, i - tokenIndex);
                            name = name.trim();
                            tokenIndex = i + 1;
                            parsingToken = false;
                        }
                    } else {
                        if (chars[i] == '\"') {
                            quotesCount = quotesCount > 0 ? 0 : 1;
                        } else if ( i + 1 >= chars.length || (chars[i] == ',' && quotesCount == 0)) {
                            value = new String(chars, tokenIndex, i - tokenIndex);
                            value = value.trim();
                            if (value.charAt(0) == '\"' && value.charAt(value.length() - 1) == '\"') {
                                value = value.substring(1);
                                value = value.substring(0, value.length() - 1);
                            }
                            //parameters.put(name, value);
                            auth.setChallengeParameter(name, value);
                            tokenIndex = i + 1;
                            parsingToken = true;
                        }
                    }
                }
                HTTPDigestAuthentication digestAuth = (HTTPDigestAuthentication)auth; 
                digestAuth.init();
                
                break;
            } else if ("NTLM".equalsIgnoreCase(method)) {
                HTTPNTLMAuthentication ntlmAuth = null;
                if (source.length() == 0) {
                    ntlmAuth = new HTTPNTLMAuthentication();
                    ntlmAuth.setType1State();
                } else {
                    ntlmAuth = (HTTPNTLMAuthentication)prevResponse;
                    ntlmAuth.parseChallenge(source);
                    ntlmAuth.setType3State();
                }
                auth = ntlmAuth;
                break;
            }
        }

        if (auth == null) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNSUPPORTED_FEATURE, "HTTP authorization method ''{0}'' is not supported", authHeader); 
            SVNErrorManager.error(err);
        }
        
        if (prevResponse != null) {
            auth.setUserName(prevResponse.getUserName());
            auth.setPassword(prevResponse.getPassword());
        }
        
        return auth;
    }
    
    public static boolean isSchemeSupportedByServer(String scheme, Collection authHeaderValues) throws SVNException {
        if (authHeaderValues == null) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNSUPPORTED_FEATURE, "Missing HTTP authorization method"); 
            SVNErrorManager.error(err);
        }

        String authHeader = null;
        for (Iterator authSchemes = authHeaderValues.iterator(); authSchemes.hasNext();) {
            authHeader = (String)authSchemes.next();
            String source = authHeader.trim();
            int index = source.indexOf(' ');
            
            if (index <= 0) {
                index = source.length();
            }
            String method = source.substring(0, index);
            if (method.equalsIgnoreCase(scheme)) {
                return true;
            }
        }   
        return false;
    }
    
    private static Collection sortSchemes(Collection authHeaders) {
        String priorities = System.getProperty(AUTH_METHODS_PROPERTY, System.getProperty(OLD_AUTH_METHODS_PROPERTY));
        if (priorities == null) {
            return authHeaders;
        }
        final List schemes = new ArrayList();
        for(StringTokenizer tokens = new StringTokenizer(priorities, " ,"); tokens.hasMoreTokens();) {
            String scheme = tokens.nextToken();
            if (!schemes.contains(scheme)) {
                schemes.add(scheme);
            }
        }
        List ordered = new ArrayList(authHeaders);
        Collections.sort(ordered, new Comparator() {
            public int compare(Object o1, Object o2) {
                String header1 = (String) o1;
                String header2 = (String) o2;
                
                String scheme1 = getSchemeName(header1);
                String scheme2 = getSchemeName(header2);
                
                int index1 = schemes.indexOf(scheme1);
                int index2 = schemes.indexOf(scheme2);

                index1 = index1 < 0 ? Integer.MAX_VALUE : index1;
                index2 = index2 < 0 ? Integer.MAX_VALUE : index2;
                if (index1 == index2) {
                    return 0;
                }
                return index1 > index2 ? 1 : -1;
            }
        });
        return ordered;
    }
    
    private static String getSchemeName(String header) {
        String source = header.trim();
        int index = source.indexOf(' ');
        if (index <= 0) {
            index = source.length();
        }
        return source.substring(0, index);
    }
    
    public abstract String getAuthenticationScheme();
    
    public abstract String authenticate() throws SVNException;

    protected static byte[] getASCIIBytes(final String data) {
        return getBytes(data, "US-ASCII");
    }

    protected static byte[] getBytes(final String data, String charset) {
        try {
            return data.getBytes(charset);
        } catch (UnsupportedEncodingException e) {
            return data.getBytes();
        }
    }

}
