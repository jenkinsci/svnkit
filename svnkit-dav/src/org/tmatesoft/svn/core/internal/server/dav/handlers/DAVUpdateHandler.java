/*
 * ====================================================================
 * Copyright (c) 2004-2007 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html.
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.core.internal.server.dav.handlers;

import java.io.Writer;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.server.dav.DAVResource;

/**
 * @author TMate Software Ltd.
 * @version 1.1.2
 */
public class DAVUpdateHandler extends ReportHandler {

    public DAVUpdateHandler(DAVResource resource, DAVUpdateRequest reportRequest, Writer responseWriter) {
        super(resource, reportRequest, responseWriter);
    }

    public int getContentLength() {
        return -1;
    }

    public void sendResponse() throws SVNException {
    }
}
