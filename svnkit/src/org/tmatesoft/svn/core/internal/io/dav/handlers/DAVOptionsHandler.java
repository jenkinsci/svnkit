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

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.io.dav.DAVElement;
import org.xml.sax.Attributes;


/**
 * @version 1.1.0
 * @author  TMate Software Ltd.
 */
public class DAVOptionsHandler extends BasicDAVHandler {
    
    public static final StringBuffer OPTIONS_REQUEST = new StringBuffer("<?xml version=\"1.0\" encoding=\"utf-8\" ?>" +
                                                    "<D:options xmlns:D=\"DAV:\" >" +
                                                    "<D:activity-collection-set />" +
                                                    "</D:options>");
    private String myActivityCollectionURL = null;
    
    public DAVOptionsHandler() {
        init();
    }
    public String getActivityCollectionURL() {
        return myActivityCollectionURL;
    }
    protected void startElement(DAVElement parent, DAVElement element, Attributes attrs) throws SVNException {
    }
    protected void endElement(DAVElement parent, DAVElement element, StringBuffer cdata) throws SVNException {
        if (element == DAVElement.HREF) {
            myActivityCollectionURL = cdata.toString();
        }
    }
}