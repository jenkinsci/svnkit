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

import java.util.Collection;
import java.util.Iterator;
import java.util.Map;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.io.dav.DAVElement;
import org.tmatesoft.svn.core.internal.server.dav.DAVResource;

/**
 * @author TMate Software Ltd.
 * @version 1.1.2
 */
public class DAVLogRequest extends DAVRequest {

    private static final DAVElement DISCOVER_CHANGED_PATHS = DAVElement.getElement(DAVElement.SVN_NAMESPACE, "discover-changed-paths");
    private static final DAVElement INCLUDE_MERGED_REVISIONS = DAVElement.getElement(DAVElement.SVN_NAMESPACE, "include-merged-revisions");
    private static final DAVElement STRICT_NODE_HISTORY = DAVElement.getElement(DAVElement.SVN_NAMESPACE, "strict-node-history");
    private static final DAVElement OMIT_LOG_TEXT = DAVElement.getElement(DAVElement.SVN_NAMESPACE, "omit-log-text");
    private static final DAVElement LIMIT = DAVElement.getElement(DAVElement.SVN_NAMESPACE, "limit");

    boolean myDiscoverChangedPaths = false;
    boolean myStrictNodeHistory = false;
    boolean myIncludeMergedRevisions = false;
    boolean myOmitLogText = false;
    long myStartRevision = DAVResource.INVALID_REVISION;
    long myEndRevision = DAVResource.INVALID_REVISION;
    long myLimit = 0;
    String[] myTargetPaths = null;

    public boolean isDiscoverChangedPaths() {
        return myDiscoverChangedPaths;
    }

    private void setDiscoverChangedPaths(boolean discoverChangedPaths) {
        myDiscoverChangedPaths = discoverChangedPaths;
    }

    public boolean isStrictNodeHistory() {
        return myStrictNodeHistory;
    }

    private void setStrictNodeHistory(boolean strictNodeHistory) {
        myStrictNodeHistory = strictNodeHistory;
    }

    public boolean isIncludeMergedRevisions() {
        return myIncludeMergedRevisions;
    }

    private void setIncludeMergedRevisions(boolean includeMergedRevisions) {
        myIncludeMergedRevisions = includeMergedRevisions;
    }

    public boolean isOmitLogText() {
        return myOmitLogText;
    }

    private void setOmitLogText(boolean omitLogText) {
        myOmitLogText = omitLogText;
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

    public long getLimit() {
        return myLimit;
    }

    private void setLimit(long limit) {
        myLimit = limit;
    }

    public String[] getTargetPaths() {
        return myTargetPaths;
    }

    private void setTargetPaths(String[] targetPaths) {
        myTargetPaths = targetPaths;
    }

    protected void initialize() throws SVNException {
        for (Iterator iterator = getProperties().entrySet().iterator(); iterator.hasNext();) {
            Map.Entry entry = (Map.Entry) iterator.next();
            DAVElement element = (DAVElement) entry.getKey();
            DAVElementProperty property = (DAVElementProperty) entry.getValue();
            if (element == DISCOVER_CHANGED_PATHS) {
                setDiscoverChangedPaths(true);
            } else if (element == STRICT_NODE_HISTORY) {
                setStrictNodeHistory(true);
            } else if (element == INCLUDE_MERGED_REVISIONS) {
                setIncludeMergedRevisions(true);
            } else if (element == OMIT_LOG_TEXT) {
                setOmitLogText(true);
            } else if (element == START_REVISION) {
                String revisionString = property.getFirstValue();
                setStartRevision(Long.parseLong(revisionString));
            } else if (element == END_REVISION) {
                String revisionString = property.getFirstValue();
                setEndRevision(Long.parseLong(revisionString));
            } else if (element == LIMIT) {
                String limitString = property.getFirstValue();
                setLimit(Integer.parseInt(limitString));
            } else if (element == PATH) {
                Collection paths = property.getValues();
                String[] targetPaths = new String[paths.size()];
                targetPaths = (String[]) paths.toArray(targetPaths);
                setTargetPaths(targetPaths);
            }
        }
    }
}
