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
public class DAVReplayRequest extends DAVReportRequest {

    private static final DAVElement EDITOR_REPORT = DAVElement.getElement(DAVElement.SVN_NAMESPACE, "editor-report");

    private static final DAVElement LOW_WATER_MARK = DAVElement.getElement(DAVElement.SVN_NAMESPACE, "low-water-mark");
    private static final DAVElement SEND_DELTAS = DAVElement.getElement(DAVElement.SVN_NAMESPACE, "send-deltas");

    boolean myIsSendDeltas;
    long myLowRevision;
    long myRevision;

    public DAVReplayRequest(Map properties) throws SVNException {
        super(EDITOR_REPORT, properties);

        myIsSendDeltas = true;
        myLowRevision = DAVResource.INVALID_REVISION;
        myRevision = DAVResource.INVALID_REVISION;

        initialize();
    }


    public boolean isSendDeltas() {
        return myIsSendDeltas;
    }

    private void setSendDeltas(boolean isSendDelta) {
        myIsSendDeltas = isSendDelta;
    }

    public long getLowRevision() {
        return myLowRevision;
    }

    private void setLowRevision(long lowRevision) {
        myLowRevision = lowRevision;
    }

    public long getRevision() {
        return myRevision;
    }

    private void setRevision(long revision) {
        myRevision = revision;
    }

    protected void initialize() throws SVNException {
        for (Iterator iterator = getProperties().entrySet().iterator(); iterator.hasNext();) {
            Map.Entry entry = (Map.Entry) iterator.next();
            DAVElement element = (DAVElement) entry.getKey();
            DAVElementProperty property = (DAVElementProperty) entry.getValue();
            if (element == REVISION) {
                if (property.getFirstValue() == null) {
                    invalidXML();
                }
                setRevision(Long.parseLong(property.getFirstValue()));
            } else if (element == LOW_WATER_MARK) {
                if (property.getFirstValue() == null) {
                    invalidXML();
                }
                setLowRevision(Long.parseLong(property.getFirstValue()));
            } else if (element == SEND_DELTAS) {
                if (property.getFirstValue() == null) {
                    invalidXML();
                }
                int sendDeltas = Integer.parseInt(property.getFirstValue());
                setSendDeltas(sendDeltas != 0);
            }
        }
        //TODO: check revisions
    }
}
