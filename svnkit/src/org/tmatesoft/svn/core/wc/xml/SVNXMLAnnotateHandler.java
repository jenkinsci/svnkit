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

import java.util.Date;

import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.util.SVNTimeUtil;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.wc.ISVNAnnotateHandler;
import org.tmatesoft.svn.util.ISVNDebugLog;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;


/**
 * This is an implementation of the <b>ISVNAnnotateHandler</b> interface 
 * that writes XML formatted annotation information to a specified 
 * <b>ContentHandler</b>. 
 * 
 * @version 1.1.0
 * @author  TMate Software Ltd.
 */
public class SVNXMLAnnotateHandler extends AbstractXMLHandler implements ISVNAnnotateHandler {

    public static final String PATH_ATTR = "path";
    public static final String REVISION_ATTR = "revision";
    public static final String DATE_TAG = "date";
    public static final String AUTHOR_TAG = "author";
    public static final String COMMIT_TAG = "commit";
    public static final String ENTRY_TAG = "entry";
    public static final String LINE_NUMBER_TAG = "line-number";
    public static final String TARGET_TAG = "target";
    public static final String BLAME_TAG = "blame";
    
    private long myLineNumber;
    
    /**
     * Creates a new annotation handler.
     * 
     * @param contentHandler a <b>ContentHandler</b> to form 
     *                       an XML tree
     */
    public SVNXMLAnnotateHandler(ContentHandler contentHandler) {
        this(contentHandler, null);
    }

    /**
     * Creates a new annotation handler.
     * 
     * @param contentHandler a <b>ContentHandler</b> to form 
     *                       an XML tree
     * @param log            a debug logger
     */
    public SVNXMLAnnotateHandler(ContentHandler contentHandler, ISVNDebugLog log) {
        super(contentHandler, log);
    }

    protected String getHeaderName() {
        return BLAME_TAG;
    }
    
    /**
     * Begins an XML tree with the target path/URL for which 
     * annotating is run.
     *  
     * @param pathOrURL a target file WC path or URL 
     */
    public void startTarget(String pathOrURL) {
        myLineNumber = 1;
        try {
            addAttribute(PATH_ATTR, pathOrURL);
            openTag(TARGET_TAG);
        } catch (SAXException e) {
            getDebugLog().error(e);
        }
    }
    
    /**
     * Closes the formatted XML output. 
     *
     */
    public void endTarget() {
        myLineNumber = 1;
        try {
            closeTag(TARGET_TAG);
        } catch (SAXException e) {
            getDebugLog().error(e);
        }
    }

    public void handleLine(Date date, long revision, String author, String line) throws SVNException {
        try {
            addAttribute(LINE_NUMBER_TAG, myLineNumber + "");
            openTag(ENTRY_TAG);
            if (revision >= 0) {
                addAttribute(REVISION_ATTR, revision + "");
                openTag(COMMIT_TAG);
                addTag(AUTHOR_TAG, author);
                addTag(DATE_TAG, SVNTimeUtil.formatDate(date));
                closeTag(COMMIT_TAG);
            }
            closeTag(ENTRY_TAG);
        } catch (SAXException e) {
            getDebugLog().error(e);
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.XML_MALFORMED, e.getLocalizedMessage());
            SVNErrorManager.error(err, e);
        } finally {
            myLineNumber++;
        }
    }
}