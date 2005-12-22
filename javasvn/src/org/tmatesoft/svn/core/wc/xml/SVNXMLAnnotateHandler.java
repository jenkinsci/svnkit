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
package org.tmatesoft.svn.core.wc.xml;

import java.util.Date;

import org.tmatesoft.svn.core.internal.util.SVNTimeUtil;
import org.tmatesoft.svn.core.wc.ISVNAnnotateHandler;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;


/**
 * @version 1.0
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

    public SVNXMLAnnotateHandler(ContentHandler contentHandler) {
        super(contentHandler);
    }

    protected String getHeaderName() {
        return BLAME_TAG;
    }
    
    public void startTarget(String pathOrURL) {
        myLineNumber = 1;
        try {
            addAttribute(PATH_ATTR, pathOrURL);
            openTag(TARGET_TAG);
        } catch (SAXException e) {
        }
    }

    public void endTarget() {
        myLineNumber = 1;
        try {
            closeTag(TARGET_TAG);
        } catch (SAXException e) {
        }
    }

    public void handleLine(Date date, long revision, String author, String line) {
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
        } finally {
            myLineNumber++;
        }
    }
}