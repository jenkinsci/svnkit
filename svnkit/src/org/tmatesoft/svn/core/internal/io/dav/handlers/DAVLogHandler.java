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

package org.tmatesoft.svn.core.internal.io.dav.handlers;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import org.tmatesoft.svn.core.ISVNLogEntryHandler;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNLogEntry;
import org.tmatesoft.svn.core.SVNLogEntryPath;
import org.tmatesoft.svn.core.internal.io.dav.DAVElement;
import org.tmatesoft.svn.core.internal.util.SVNTimeUtil;
import org.xml.sax.Attributes;


/**
 * @version 1.1.0
 * @author  TMate Software Ltd.
 */
public class DAVLogHandler extends BasicDAVHandler {
	
	public static StringBuffer generateLogRequest(StringBuffer buffer, long startRevision, long endRevision,
			boolean includeChangedPaths, boolean strictNodes, long limit, String[] paths) {
		buffer = buffer == null ? new StringBuffer() : buffer;
        buffer.append("<?xml version=\"1.0\" encoding=\"utf-8\"?>");
        buffer.append("<S:log-report xmlns:S=\"svn:\">");
        if (startRevision >= 0) {
        	buffer.append("<S:start-revision>"  + startRevision + "</S:start-revision>");
        } 
        if (endRevision >= 0) {
        	buffer.append("<S:end-revision>"  + endRevision + "</S:end-revision>");
        }
        if (limit > 0) {
            buffer.append("<S:limit>" + limit + "</S:limit>");
        }
        if (includeChangedPaths) {
            buffer.append("<S:discover-changed-paths />");
        }
        if (strictNodes) {
            buffer.append("<S:strict-node-history />");
        }
        for (int i = 0; i < paths.length; i++) {
            buffer.append("<S:path>"  + paths[i] + "</S:path>");
		}
        buffer.append("</S:log-report>");
        return buffer;
	}
	
	private static final DAVElement LOG_ITEM = DAVElement.getElement(DAVElement.SVN_NAMESPACE, "log-item");
	
	private static final DAVElement ADDED_PATH = DAVElement.getElement(DAVElement.SVN_NAMESPACE, "added-path");
	private static final DAVElement DELETED_PATH = DAVElement.getElement(DAVElement.SVN_NAMESPACE, "deleted-path");
	private static final DAVElement MODIFIED_PATH = DAVElement.getElement(DAVElement.SVN_NAMESPACE, "modified-path");
	private static final DAVElement REPLACED_PATH = DAVElement.getElement(DAVElement.SVN_NAMESPACE, "replaced-path");
	
	private ISVNLogEntryHandler myLogEntryHandler;
	private long myRevision;
	private Map myPaths;
	private String myAuthor;
	private Date myDate;
	private String myComment;
	private SVNLogEntryPath myPath;

	private int myCount;
    private long myLimit;

    private boolean myIsCompatibleMode;

	public DAVLogHandler(ISVNLogEntryHandler handler, long limit) {
		myLogEntryHandler = handler;
		myRevision = -1;
		myCount = 0;
        myLimit = limit;
		init();
	}
    
    public boolean isCompatibleMode() {
        return myIsCompatibleMode;
    }
	
	protected void startElement(DAVElement parent, DAVElement element, Attributes attrs) {
		char type = 0;
		String copyPath = null;
		long copyRevision = -1;
		if (element == ADDED_PATH || element == REPLACED_PATH) {
			type = element == ADDED_PATH ? SVNLogEntryPath.TYPE_ADDED : SVNLogEntryPath.TYPE_REPLACED;
			copyPath = attrs.getValue("copyfrom-path");
			String copyRevisionStr = attrs.getValue("copyfrom-rev");
			if (copyPath != null && copyRevisionStr != null) {
				try {
					copyRevision = Long.parseLong(copyRevisionStr);
				} catch (NumberFormatException e) {
				}
			} 
		} else if (element == MODIFIED_PATH) {
			type = SVNLogEntryPath.TYPE_MODIFIED;
		} else if (element == DELETED_PATH) {
			type = SVNLogEntryPath.TYPE_DELETED;			
		}
		if (type != 0) {
			myPath = new SVNLogEntryPath(null, type, copyPath, copyRevision);
		}
		
	}
	
	protected void endElement(DAVElement parent, DAVElement element, StringBuffer cdata) throws SVNException {
		if (element == LOG_ITEM) {
			myCount++;
            if (myLimit <= 0 || myCount <= myLimit) {
    			if (myLogEntryHandler != null) {
    				if (myPaths == null) {
    					myPaths = new HashMap();
    				}
    				SVNLogEntry logEntry = new SVNLogEntry(myPaths, myRevision, myAuthor, myDate, myComment);
    				myLogEntryHandler.handleLogEntry(logEntry);
    			}
            } else if (myLimit < myCount) {
                myIsCompatibleMode = true;
            }
			myPaths = null;
			myRevision = -1;
			myAuthor = null;
			myDate = null;
			myComment = null;
		} else if (element == DAVElement.VERSION_NAME && cdata != null) {
			myRevision = Long.parseLong(cdata.toString());
		} else if (element == DAVElement.CREATOR_DISPLAY_NAME && cdata != null) {
			myAuthor = cdata.toString();
		} else if (element == DAVElement.COMMENT && cdata != null) {
			myComment = cdata.toString();
		} else if (element == DAVElement.DATE && cdata != null) {
			myDate = SVNTimeUtil.parseDate(cdata.toString());
		} else if (element == ADDED_PATH || element == MODIFIED_PATH || element == REPLACED_PATH ||
				element == DELETED_PATH) {
			if (myPath != null && cdata != null) {
				if (myPaths == null) {
					myPaths = new HashMap();
				}
				myPath.setPath(cdata.toString());
                String path = myPath.getPath();
                myPath.setPath(path);
                myPaths.put(myPath.getPath(), myPath);
			}
			myPath = null;
		} 
	}

	public int getEntriesCount() {
		return myCount;
	}
}
