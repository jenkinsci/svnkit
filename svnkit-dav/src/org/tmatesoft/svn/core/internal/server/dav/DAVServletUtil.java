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
package org.tmatesoft.svn.core.internal.server.dav;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.LinkedList;

import javax.servlet.http.HttpServletResponse;

import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.internal.io.fs.FSFS;
import org.tmatesoft.svn.core.internal.io.fs.FSRoot;
import org.tmatesoft.svn.core.internal.io.fs.FSTransactionInfo;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.util.SVNDebugLog;
import org.tmatesoft.svn.util.SVNLogType;


/**
 * @version 1.2.0
 * @author  TMate Software Ltd.
 */
public class DAVServletUtil {
    
    public static LinkedList processIfHeader(String value) throws DAVException {
        if (value == null) {
            return null;
        }
        
        StringBuffer valueBuffer = new StringBuffer(value);
        ListType listType = ListType.UNKNOWN;
        String uri = null;
        LinkedList ifHeaders = new LinkedList();
        DAVIFHeader ifHeader = null;
        while (valueBuffer.length() > 0) {
            if (valueBuffer.charAt(0) == '<') {
                if (listType == ListType.NO_TAGGED || (uri = DAVServletUtil.fetchNextToken(valueBuffer, '>')) == null) {
                    throw new DAVException("Invalid If-header: unclosed \"<\" or unexpected tagged-list production.", 
                            HttpServletResponse.SC_BAD_REQUEST, DAVErrorCode.IF_TAGGED);
                }
                
                URI parsedURI = null;
                try {
                    parsedURI = new URI(uri);
                } catch (URISyntaxException urise) {
                    throw new DAVException("Invalid URI in tagged If-header.", HttpServletResponse.SC_BAD_REQUEST, DAVErrorCode.IF_TAGGED);
                }
                
                uri = parsedURI.getPath();
                uri = uri.length() > 1 && uri.endsWith("/") ? uri.substring(0, uri.length() - 1) : uri;
                listType = ListType.TAGGED;
            } else if (valueBuffer.charAt(0) == '(') {
                if (listType == ListType.UNKNOWN) {
                    listType = ListType.NO_TAGGED;
                }
                
                StringBuffer listBuffer = null;
                String list = null;
                if ((list = DAVServletUtil.fetchNextToken(valueBuffer, ')')) == null) {
                    throw new DAVException("Invalid If-header: unclosed \"(\".", HttpServletResponse.SC_BAD_REQUEST, 
                            DAVErrorCode.IF_UNCLOSED_PAREN);
                }
                
                ifHeader = new DAVIFHeader(uri);
                ifHeaders.addFirst(ifHeader);
                
                int condition = DAVIFState.IF_CONDITION_NORMAL;
                String stateToken = null;
                
                listBuffer = new StringBuffer(list);
                while (listBuffer.length() > 0) {
                    if (listBuffer.charAt(0) == '<') {
                        if ((stateToken = DAVServletUtil.fetchNextToken(listBuffer, '>')) == null) {
                            throw new DAVException(null, HttpServletResponse.SC_BAD_REQUEST, DAVErrorCode.IF_PARSE);
                        }
                        
                        addIfState(stateToken, DAVIFStateType.IF_OPAQUE_LOCK, condition, ifHeader);
                        condition = DAVIFState.IF_CONDITION_NORMAL;
                    } else if (listBuffer.charAt(0) == '[') {
                        if ((stateToken = fetchNextToken(listBuffer, ']')) == null) {
                            throw new DAVException(null, HttpServletResponse.SC_BAD_REQUEST, DAVErrorCode.IF_PARSE);
                        }
                        
                        addIfState(stateToken, DAVIFStateType.IF_ETAG, condition, ifHeader);
                        condition = DAVIFState.IF_CONDITION_NORMAL;
                    } else if (listBuffer.charAt(0) == 'N') {
                        if (listBuffer.length() > 2 && listBuffer.charAt(1) == 'o' && listBuffer.charAt(2) == 't') {
                            if (condition != DAVIFState.IF_CONDITION_NORMAL) {
                                throw new DAVException("Invalid \"If:\" header: Multiple \"not\" entries for the same state.", 
                                        HttpServletResponse.SC_BAD_REQUEST, DAVErrorCode.IF_MULTIPLE_NOT);
                            }
                            condition = DAVIFState.IF_CONDITION_NOT;
                        }
                        listBuffer.delete(0, 2);
                    } else if (listBuffer.charAt(0) != ' ' && listBuffer.charAt(0) != '\t') {
                        throw new DAVException("Invalid \"If:\" header: Unexpected character encountered ({0}, ''{1}'').", 
                                new Object[] { Integer.toHexString(listBuffer.charAt(0)), new Character(listBuffer.charAt(0)) }, 
                                HttpServletResponse.SC_BAD_REQUEST, DAVErrorCode.IF_UNK_CHAR);
                    }
                    listBuffer.deleteCharAt(0);
                }
            } else if (valueBuffer.charAt(0) != ' ' && valueBuffer.charAt(0) != '\t') {
                throw new DAVException("Invalid \"If:\" header: Unexpected character encountered ({0}, ''{1}'').", 
                        new Object[] { Integer.toHexString(valueBuffer.charAt(0)), new Character(valueBuffer.charAt(0)) }, 
                        HttpServletResponse.SC_BAD_REQUEST, DAVErrorCode.IF_UNK_CHAR);
                
            }
            valueBuffer.deleteCharAt(0);
        }
        
        return ifHeaders;
    }
    
    public static FSTransactionInfo openTxn(FSFS fsfs, String txnName) throws DAVException {
        FSTransactionInfo txnInfo = null;
        try {
            txnInfo = fsfs.openTxn(txnName);
        } catch (SVNException svne) {
            if (svne.getErrorMessage().getErrorCode() == SVNErrorCode.FS_NO_SUCH_TRANSACTION) {
                throw DAVException.convertError(svne.getErrorMessage(), HttpServletResponse.SC_INTERNAL_SERVER_ERROR, 
                        "The transaction specified by the activity does not exist", null);
            }
            throw DAVException.convertError(svne.getErrorMessage(), HttpServletResponse.SC_INTERNAL_SERVER_ERROR, 
                    "There was a problem opening the transaction specified by this activity.", null);
        }
        return txnInfo;
    }
    
    public static String getTxn(File activitiesDB, String activityID) {
        File activityFile = DAVPathUtil.getActivityPath(activitiesDB, activityID);
        try {
            return DAVServletUtil.readTxn(activityFile);
        } catch (IOException e) {
            SVNDebugLog.getDefaultLog().logFine(SVNLogType.FSFS, e.getMessage());
        }
        return null;
    }
    
    public static String readTxn(File activityFile) throws IOException {
        String txnName = null;
        for (int i = 0; i < 10; i++) {
            txnName = SVNFileUtil.readSingleLine(activityFile);
        }
        return txnName; 
    }
    
    public static SVNNodeKind checkPath(FSRoot root, String path) throws DAVException {
        try {
            return root.checkNodeKind(path);
        } catch (SVNException svne) {
            if (svne.getErrorMessage().getErrorCode() == SVNErrorCode.FS_NOT_DIRECTORY) {
                return SVNNodeKind.NONE;
            }
            throw DAVException.convertError(svne.getErrorMessage(), HttpServletResponse.SC_INTERNAL_SERVER_ERROR, 
                    "Error checking kind of path ''{0}'' in repository", new Object[] { path });
        }
    }

    private static void addIfState(String stateToken, DAVIFStateType type, int condition, DAVIFHeader ifHeader) {
        String eTag = null;
        String lockToken = null;
        if (type == DAVIFStateType.IF_OPAQUE_LOCK) {
            lockToken = stateToken;
        } else {
            eTag = stateToken;
        }
         
        DAVIFState ifState = new DAVIFState(condition, eTag, lockToken, type);
        ifHeader.addIFState(ifState);
    }
    
    private static String fetchNextToken(StringBuffer string, char term) {
        String token = string.substring(1);
        token = token.trim();
        int ind = -1;
        if ((ind = token.indexOf(term)) == -1) {
            return null;
        }
        
        token = token.substring(0, ind);
        string.delete(0, string.indexOf(token) + token.length());
        return token;
    }

    private static class ListType {
        public static final ListType NO_TAGGED = new ListType();
        public static final ListType TAGGED = new ListType();
        public static final ListType UNKNOWN = new ListType();
        
        private ListType() {
        }
    }

}
