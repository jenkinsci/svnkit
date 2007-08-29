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

import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.internal.io.dav.DAVElement;
import org.tmatesoft.svn.core.internal.server.dav.DAVResource;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.xml.sax.Attributes;

/**
 * @author TMate Software Ltd.
 * @version 1.1.2
 */
public class DAVUpdateRequest extends DAVReportRequest {

    private static final DAVElement TARGET_REVISION = DAVElement.getElement(DAVElement.SVN_NAMESPACE, "target-revision");
    private static final DAVElement SRC_PATH = DAVElement.getElement(DAVElement.SVN_NAMESPACE, "src-path");
    private static final DAVElement DST_PATH = DAVElement.getElement(DAVElement.SVN_NAMESPACE, "dst-path");
    private static final DAVElement UPDATE_TARGET = DAVElement.getElement(DAVElement.SVN_NAMESPACE, "update-target");
    private static final DAVElement DEPTH = DAVElement.getElement(DAVElement.SVN_NAMESPACE, "depth");
    private static final DAVElement RECURSIVE = DAVElement.getElement(DAVElement.SVN_NAMESPACE, "recursive");
    private static final DAVElement IGNORE_ANCESTRY = DAVElement.getElement(DAVElement.SVN_NAMESPACE, "ignore-ancestry");
    private static final DAVElement TEXT_DELTAS = DAVElement.getElement(DAVElement.SVN_NAMESPACE, "text-deltas");
    private static final DAVElement RESOURCE_WALK = DAVElement.getElement(DAVElement.SVN_NAMESPACE, "resource-walk");

    boolean mySendAll;
    long myRevision;
    long myFromRevision;
    String mySrcPath;
    String myDstPath;
    String myTarget;
    boolean myTextDeltas;
    SVNDepth myDepth;
    boolean myDepthRequested;
    boolean myRecursiveRequested;
    boolean myIgnoreAncestry;
    boolean myResourceWalk;


    public DAVUpdateRequest(Map properties, Attributes rootElementAttributes) throws SVNException {
        super(UPDATE_REPORT, properties);

        mySendAll = "true".equals(rootElementAttributes.getValue("send-all"));

        myRevision = DAVResource.INVALID_REVISION;
        myFromRevision = DAVResource.INVALID_REVISION;
        mySrcPath = null;
        myDstPath = null;
        myTarget = "";
        myTextDeltas = true;
        myDepth = SVNDepth.UNKNOWN;
        myDepthRequested = false;
        myRecursiveRequested = false;
        myIgnoreAncestry = false;
        myResourceWalk = false;

        initialize();
    }


    public boolean isSendAll() {
        return mySendAll;
    }

    public long getRevision() {
        return myRevision;
    }

    private void setRevision(long revision) {
        myRevision = revision;
    }

    public String getSrcPath() {
        return mySrcPath;
    }

    private void setSrcPath(String srcPath) {
        mySrcPath = srcPath;
    }

    public String getDstPath() {
        return myDstPath;
    }

    private void setDstPath(String dstPath) {
        myDstPath = dstPath;
    }

    public String getTarget() {
        return myTarget;
    }

    private void setTarget(String target) {
        myTarget = target;
    }

    public boolean isTextDeltas() {
        return myTextDeltas;
    }

    private void setTextDeltas(boolean textDeltas) {
        myTextDeltas = textDeltas;
    }

    public SVNDepth getDepth() {
        return myDepth;
    }

    private void setDepth(SVNDepth depth) {
        myDepth = depth;
    }

    public boolean isDepthRequested() {
        return myDepthRequested;
    }

    private void setDepthRequested(boolean depthRequested) {
        myDepthRequested = depthRequested;
    }

    public boolean isRecursiveRequested() {
        return myRecursiveRequested;
    }

    private void setRecursiveRequested(boolean recursiveRequested) {
        myRecursiveRequested = recursiveRequested;
    }

    public boolean isIgnoreAncestry() {
        return myIgnoreAncestry;
    }

    private void setIgnoreAncestry(boolean ignoreAncestry) {
        myIgnoreAncestry = ignoreAncestry;
    }

    public boolean isResourceWalk() {
        return myResourceWalk;
    }

    private void setResourceWalk(boolean resourceWalk) {
        myResourceWalk = resourceWalk;
    }

    protected void initialize() throws SVNException {
        for (Iterator iterator = getProperties().entrySet().iterator(); iterator.hasNext();) {
            Map.Entry entry = (Map.Entry) iterator.next();
            DAVElement element = (DAVElement) entry.getKey();
            DAVElementProperty property = (DAVElementProperty) entry.getValue();
            if (element == TARGET_REVISION) {
                assertNullCData(property);
                setRevision(Long.parseLong(property.getFirstValue()));
            } else if (element == SRC_PATH) {
                assertNullCData(property);
                setSrcPath(property.getFirstValue());
            } else if (element == DST_PATH) {
                assertNullCData(property);
                setDstPath(property.getFirstValue());
            } else if (element == UPDATE_TARGET) {
                setTarget(property.getFirstValue());
            } else if (element == DEPTH) {
                assertNullCData(property);
                setDepth(SVNDepth.fromString(property.getFirstValue()));
                setDepthRequested(true);
            } else if (element == RECURSIVE && !isDepthRequested()) {
                assertNullCData(property);
                SVNDepth.fromRecurse(!"no".equals(property.getFirstValue()));
                setRecursiveRequested(true);
            } else if (element == IGNORE_ANCESTRY) {
                assertNullCData(property);
                setIgnoreAncestry(!"no".equals(property.getFirstValue()));
            } else if (element == TEXT_DELTAS) {
                assertNullCData(property);
                setTextDeltas(!"no".equals(property.getFirstValue()));
            } else if (element == RESOURCE_WALK) {
                assertNullCData(property);
                setResourceWalk(!"no".equals(property.getFirstValue()));
            }
        }
        if (!isDepthRequested() && !isRecursiveRequested() && (getDepth() == SVNDepth.UNKNOWN)) {
            setDepth(SVNDepth.INFINITY);
        }
        if (getSrcPath() == null) {
            SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.RA_DAV_REQUEST_FAILED));
        }
    }

    private void assertNullCData(DAVElementProperty property) throws SVNException {
        if (property.getValues() == null) {
            invalidXML();
        }
    }

}
