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
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.util.SVNLogType;

/**
 * @author TMate Software Ltd.
 * @version 1.2.0
 */
public class DAVGetLocationsRequest extends DAVRequest {

    private static final DAVElement GET_LOCATIONS_REPORT = DAVElement.getElement(DAVElement.SVN_NAMESPACE, "get-locations-report");

    private static final DAVElement PEG_REVISION = DAVElement.getElement(DAVElement.SVN_NAMESPACE, "peg-revision");
    private static final DAVElement LOCATION_REVISION = DAVElement.getElement(DAVElement.SVN_NAMESPACE, "location-revision");

    private String myPath = null;
    private long myPegRevision = DAVResource.INVALID_REVISION;
    private long[] myRevisions = null;

    public String getPath() {
        return myPath;
    }

    private void setPath(String path) {
        myPath = path;
    }

    public long getPegRevision() {
        return myPegRevision;
    }

    private void setPegRevision(long pegRevision) {
        myPegRevision = pegRevision;
    }

    public long[] getRevisions() {
        return myRevisions;
    }

    private void setRevisions(long[] revisions) {
        myRevisions = revisions;
    }

    protected void init() throws SVNException {
        getRootElement().setElementName(GET_LOCATIONS_REPORT);
        List children = getRootElement().getChildren();
        for (Iterator iterator = children.iterator(); iterator.hasNext();) {
            DAVElementProperty property = (DAVElementProperty) iterator.next();
            DAVElement element = property.getName();
            if (element == DAVElement.PATH) {
                String path = property.getFirstValue(false);
                DAVPathUtil.testCanonical(path);
                setPath(path);
            } else if (element == PEG_REVISION) {
                String pegRevisionString = property.getFirstValue(true);
                try {
                    setPegRevision(Long.parseLong(pegRevisionString));
                } catch (NumberFormatException nfe) {
                    SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.RA_DAV_REQUEST_FAILED, nfe), SVNLogType.NETWORK);
                }
            } else if (element == LOCATION_REVISION) {
                long[] revisions = new long[property.getValues().size()];
                int i = 0;
                for (Iterator revisionsIterator = property.getValues().iterator(); revisionsIterator.hasNext(); i++) {
                    try {
                        revisions[i] = Long.parseLong((String) revisionsIterator.next());
                    } catch (NumberFormatException nfe) {
                        SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.RA_DAV_REQUEST_FAILED, nfe), SVNLogType.NETWORK);
                    }
                }
                setRevisions(revisions);
            }
        }

        if (getPath() == null && !SVNRevision.isValidRevisionNumber(getPegRevision())) {
            SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.RA_DAV_REQUEST_FAILED, "Not all parameters passed."), SVNLogType.NETWORK);
        }
    }
}
