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

import java.util.Iterator;
import java.util.Map;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.io.dav.DAVElement;
import org.tmatesoft.svn.core.internal.server.dav.DAVResource;

/**
 * @author TMate Software Ltd.
 * @version 1.1.2
 */
public class DAVFileRevisionsRequest extends DAVRequest {

    String myPath = null;
    long myStartRevision = DAVResource.INVALID_REVISION;
    long myEndRevision = DAVResource.INVALID_REVISION;

    public String getPath() {
        return myPath;
    }

    private void setPath(String path) {
        myPath = path;
    }

    public long getStartRevision() {
        return myStartRevision;
    }

    private void setStartRevision(long startRevision) {
        myStartRevision = startRevision;
    }

    public long getEndRevision() {
        return myEndRevision;
    }

    private void setEndRevision(long endRevision) {
        myEndRevision = endRevision;
    }

    protected void initialize() throws SVNException {
        for (Iterator iterator = getProperties().entrySet().iterator(); iterator.hasNext();) {
            Map.Entry entry = (Map.Entry) iterator.next();
            DAVElement element = (DAVElement) entry.getKey();
            DAVElementProperty property = (DAVElementProperty) entry.getValue();
            if (element == PATH) {
                setPath(property.getFirstValue());
            } else if (element == START_REVISION) {
                setStartRevision(Long.parseLong(property.getFirstValue()));
            } else if (element == END_REVISION) {
                setEndRevision(Long.parseLong(property.getFirstValue()));
            }
        }
    }
}
