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

package org.tmatesoft.svn.core.internal.io.dav.handlers;

import org.tmatesoft.svn.core.internal.io.dav.DAVElement;
import org.tmatesoft.svn.core.internal.io.dav.DAVUtil;
import org.tmatesoft.svn.core.io.ISVNFileRevisionHandler;
import org.tmatesoft.svn.core.io.SVNException;
import org.tmatesoft.svn.core.io.SVNFileRevision;
import org.tmatesoft.svn.core.io.diff.SVNDiffWindow;
import org.tmatesoft.svn.util.Base64;
import org.xml.sax.Attributes;

import java.io.OutputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;


/**
 * @version 1.0
 * @author  TMate Software Ltd.
 */
public class DAVFileRevisionHandler extends BasicDAVDeltaHandler {
	
	public static StringBuffer generateFileRevisionsRequest(StringBuffer buffer, long startRevision, long endRevision,
			String path) {
		buffer = buffer == null ? new StringBuffer() : buffer;
        buffer.append("<?xml version=\"1.0\" encoding=\"utf-8\"?>");
        buffer.append("<S:file-revs-report xmlns:S=\"svn:\">");
        if (startRevision >= 0) {
        	buffer.append("<S:start-revision>"  + startRevision + "</S:start-revision>");
        } 
        if (endRevision >= 0) {
        	buffer.append("<S:end-revision>"  + endRevision + "</S:end-revision>");
        }
        buffer.append("<S:path>"  + DAVUtil.xmlEncode(path) + "</S:path>");
        buffer.append("</S:file-revs-report>");
        return buffer;
	}
    
    private static final DAVElement REVISION_PROPERTY = DAVElement.getElement(DAVElement.SVN_NAMESPACE, "rev-prop");
    private static final DAVElement FILE_REVISION = DAVElement.getElement(DAVElement.SVN_NAMESPACE, "file-rev");

    private static final DAVElement SET_PROPERTY = DAVElement.getElement(DAVElement.SVN_NAMESPACE, "set-prop");
    private static final DAVElement DELETE_PROPERTY = DAVElement.getElement(DAVElement.SVN_NAMESPACE, "remove-prop");

	private ISVNFileRevisionHandler myFileRevisionsHandler;
    private String myPath;
    private long myRevision;
    private Map myProperties;
    private Map myPropertiesDelta;
    private String myPropertyName;
    private String myPropertyEncoding;
	
    private int myCount;

	public DAVFileRevisionHandler(ISVNFileRevisionHandler handler) {
		myFileRevisionsHandler = handler;
		myCount = 0;
		init();
	}
	
	protected void startElement(DAVElement parent, DAVElement element, Attributes attrs) throws SVNException {
        if (element == FILE_REVISION) {
            myPath = attrs.getValue("path");
            myRevision = Long.parseLong(attrs.getValue("rev"));
        } else if (element == REVISION_PROPERTY || element == SET_PROPERTY || element == DELETE_PROPERTY) {
            myPropertyName = attrs.getValue("name");
            myPropertyEncoding = attrs.getValue("encoding");
        } if (element == TX_DELTA) {
            if (myPath != null && myFileRevisionsHandler != null) {
                if (myProperties == null) {
                    myProperties = Collections.EMPTY_MAP;
                }
                if (myPropertiesDelta == null) {
                    myPropertiesDelta = Collections.EMPTY_MAP;
                }
                SVNFileRevision revision = new SVNFileRevision(myPath, myRevision, myProperties, myPropertiesDelta);
                myFileRevisionsHandler.handleFileRevision(revision);
                myPath = null;
            }
            setDeltaProcessing(true);
		}
	}
    
	protected void endElement(DAVElement parent, DAVElement element, StringBuffer cdata) throws SVNException {
        if (element == FILE_REVISION) {
            myPath = null;
            myProperties = null;
            myPropertiesDelta = null;
            myPropertyEncoding = null;
            myPropertyName = null;
        } else if (element == TX_DELTA) {
            setDeltaProcessing(false);
            myCount++;
        } else if (element == REVISION_PROPERTY) {
            if (myProperties == null) {
                myProperties = new HashMap();
            }
            myProperties.put(myPropertyName, cdata != null ? cdata.toString() : "");
            myPropertyName = null;
        } else if (element == SET_PROPERTY) {
            if (myPropertiesDelta == null) {
                myPropertiesDelta = new HashMap();
            }
            if (myPropertyName != null) {
                String value;
                if ("base64".equals(myPropertyEncoding)) {
                    byte[] bytes = Base64.base64ToByteArray(cdata, null);
                    value = new String(bytes);
                } else {
                    value = cdata.toString();
                }
                myPropertiesDelta.put(myPropertyName, value);
            }
            myPropertyName = null;
            myPropertyEncoding = null;
        } else if (element == DELETE_PROPERTY) {
            if (myPropertiesDelta == null) {
                myPropertiesDelta = new HashMap();
            }
            if (myPropertyName != null) {
                myPropertiesDelta.put(myPropertyName, null);
            }
            myPropertyEncoding = null;
            myPropertyName = null;
        }
    }

	public int getEntriesCount() {
		return myCount;
	}

    protected void handleDiffWindowClosed() throws SVNException {
        myFileRevisionsHandler.handleDiffWindowClosed(myPath);
    }
    
    protected OutputStream handleDiffWindow(SVNDiffWindow window) throws SVNException {
        return myFileRevisionsHandler.handleDiffWindow(myPath, window);
    }
}
