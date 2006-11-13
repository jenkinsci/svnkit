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
package org.tmatesoft.svn.core.wc.xml;

import java.util.Iterator;

import org.tmatesoft.svn.core.ISVNLogEntryHandler;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNLogEntry;
import org.tmatesoft.svn.core.SVNLogEntryPath;
import org.tmatesoft.svn.core.internal.util.SVNEncodingUtil;
import org.tmatesoft.svn.core.internal.util.SVNTimeUtil;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.util.ISVNDebugLog;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;


/**
 * This log handler implementation writes xml formatted information 
 * about the log entries it's passed to a specified <b>ContentHandler</b>. 
 * 
 * @version 1.1.0
 * @author  TMate Software Ltd.
 */
public class SVNXMLLogHandler extends AbstractXMLHandler implements ISVNLogEntryHandler {

    public static final String COPYFROM_REV_ATTR = "copyfrom-rev";
    public static final String COPYFROM_PATH_ATTR = "copyfrom-path";
    public static final String ACTION_ATTR = "action";
    public static final String REVISION_ATTR = "revision";
    public static final String MSG_TAG = "msg";
    public static final String PATH_TAG = "path";
    public static final String PATHS_TAG = "paths";
    public static final String DATE_TAG = "date";
    public static final String AUTHOR_TAG = "author";
    public static final String LOGENTRY_TAG = "logentry";
    public static final String LOG_TAG = "log";
    
    /**
     * Creates a new log handler.
     * 
     * @param contentHandler a <b>ContentHandler</b> to form 
     *                       an XML tree
     */
    public SVNXMLLogHandler(ContentHandler contentHandler) {
        this(contentHandler, null);
    }

    /**
     * Creates a new log handler.
     * 
     * @param contentHandler a <b>ContentHandler</b> to form 
     *                       an XML tree
     * @param log            a debug logger
     */
    public SVNXMLLogHandler(ContentHandler contentHandler, ISVNDebugLog log) {
        super(contentHandler, log);
    }
    /**
     * Returns the header name specific for a log handler.
     * 
     * @return {@link #LOG_TAG} string
     */
    public String getHeaderName() {
        return LOG_TAG;
    }

    public void handleLogEntry(SVNLogEntry logEntry) throws SVNException {
        try {
            sendToHandler(logEntry);
        } catch (SAXException e) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.XML_MALFORMED, e.getLocalizedMessage());
            SVNErrorManager.error(err, e);
        }
    }
    
    private void sendToHandler(SVNLogEntry logEntry) throws SAXException {
        if (logEntry.getRevision() == 0 && logEntry.getMessage() == null) {
            return;
        }
        addAttribute(REVISION_ATTR, logEntry.getRevision() + "");
        openTag(LOGENTRY_TAG);
        if (logEntry.getAuthor() != null) {
            addTag(AUTHOR_TAG, logEntry.getAuthor());
        }
        if (logEntry.getDate() != null && logEntry.getDate().getTime() != 0) {
            addTag(DATE_TAG, SVNTimeUtil.formatDate(logEntry.getDate()));
        }
        if (logEntry.getChangedPaths() != null && !logEntry.getChangedPaths().isEmpty()) {
            openTag(PATHS_TAG);
            for (Iterator paths = logEntry.getChangedPaths().keySet().iterator(); paths.hasNext();) {
                String key = (String) paths.next();
                SVNLogEntryPath path = (SVNLogEntryPath) logEntry.getChangedPaths().get(key);
                addAttribute(ACTION_ATTR, path.getType() + "");
                if (path.getCopyPath() != null) {
                    addAttribute(COPYFROM_PATH_ATTR, SVNEncodingUtil.xmlEncodeAttr(path.getCopyPath()));
                    addAttribute(COPYFROM_REV_ATTR, path.getCopyRevision() + "");
                } 
                addTag(PATH_TAG, SVNEncodingUtil.xmlEncodeAttr(path.getPath()));
            }
            closeTag(PATHS_TAG);
        }
        String message = logEntry.getMessage();
        message = message == null ? "" : message;
        message = SVNEncodingUtil.xmlEncodeCDATA(message);
        addTag(MSG_TAG, message);
        closeTag(LOGENTRY_TAG);
    }
    
}
