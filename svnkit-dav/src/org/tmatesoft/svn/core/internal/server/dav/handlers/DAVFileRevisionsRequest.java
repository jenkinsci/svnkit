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
package org.tmatesoft.svn.core.internal.server.dav.handlers;

import java.util.Iterator;
import java.util.List;

import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.io.dav.DAVElement;
import org.tmatesoft.svn.core.internal.server.dav.DAVPathUtil;
import org.tmatesoft.svn.core.internal.server.dav.DAVResource;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.util.SVNLogType;

/**
 * @author TMate Software Ltd.
 * @version 1.2.0
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

    protected void init() throws SVNException {
        DAVElementProperty rootElement = getRootElement();
        List children = rootElement.getChildren();
        for (Iterator iterator = children.iterator(); iterator.hasNext();) {
            DAVElementProperty childElement = (DAVElementProperty) iterator.next();
            DAVElement childElementName = childElement.getName();
            if (childElementName == PATH) {
                String path = childElement.getFirstValue(false);
                DAVPathUtil.testCanonical(path);
                setPath(path);
            } else if (childElementName == START_REVISION) {
                try {
                    setStartRevision(Long.parseLong(childElement.getFirstValue(true)));
                } catch (NumberFormatException nfe) {
                    SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.RA_DAV_REQUEST_FAILED, nfe), SVNLogType.NETWORK);
                }
            } else if (childElementName == END_REVISION) {
                try {
                    setEndRevision(Long.parseLong(childElement.getFirstValue(true)));
                } catch (NumberFormatException nfe) {
                    SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.RA_DAV_REQUEST_FAILED, nfe), SVNLogType.NETWORK);
                }
            }
        }
    }
}
