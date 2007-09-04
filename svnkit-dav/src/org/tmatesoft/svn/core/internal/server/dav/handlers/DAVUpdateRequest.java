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
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.io.dav.DAVElement;
import org.tmatesoft.svn.core.internal.server.dav.DAVResource;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;

/**
 * @author TMate Software Ltd.
 * @version 1.1.2
 */
public class DAVUpdateRequest extends DAVRequest {

    private static final DAVElement TARGET_REVISION = DAVElement.getElement(DAVElement.SVN_NAMESPACE, "target-revision");
    private static final DAVElement SRC_PATH = DAVElement.getElement(DAVElement.SVN_NAMESPACE, "src-path");
    private static final DAVElement DST_PATH = DAVElement.getElement(DAVElement.SVN_NAMESPACE, "dst-path");
    private static final DAVElement UPDATE_TARGET = DAVElement.getElement(DAVElement.SVN_NAMESPACE, "update-target");
    private static final DAVElement DEPTH = DAVElement.getElement(DAVElement.SVN_NAMESPACE, "depth");
    private static final DAVElement RECURSIVE = DAVElement.getElement(DAVElement.SVN_NAMESPACE, "recursive");
    private static final DAVElement IGNORE_ANCESTRY = DAVElement.getElement(DAVElement.SVN_NAMESPACE, "ignore-ancestry");
    private static final DAVElement TEXT_DELTAS = DAVElement.getElement(DAVElement.SVN_NAMESPACE, "text-deltas");
    private static final DAVElement RESOURCE_WALK = DAVElement.getElement(DAVElement.SVN_NAMESPACE, "resource-walk");
    private static final DAVElement ENTRY = DAVElement.getElement(DAVElement.SVN_NAMESPACE, "entry");
    private static final DAVElement MISSING = DAVElement.getElement(DAVElement.SVN_NAMESPACE, "missing");

    boolean mySendAll = false;
    long myRevision = DAVResource.INVALID_REVISION;
    String mySrcPath = null;
    String myDstPath = null;
    String myTarget = "";
    boolean myTextDeltas = true;
    SVNDepth myDepth = SVNDepth.UNKNOWN;
    boolean myDepthRequested = false;
    boolean myRecursiveRequested = false;
    boolean myIgnoreAncestry = false;
    boolean myResourceWalk = false;

    String myEntryPath = null;
    long myEntryRevision = DAVResource.INVALID_REVISION;
    String myEntryLinkPath = null;
    boolean myEntryStartEmpty = false;
    String myEntryLockToken = null;

    String myMissing = null;

    public boolean isSendAll() {
        return mySendAll;
    }

    private void setSendAll(boolean sendAll) {
        mySendAll = sendAll;
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

    private void setSrcPath(String srcPath) throws SVNException {
        try {
            SVNURL.parseURIEncoded(srcPath);
        } catch (SVNException e) {
            SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.RA_DAV_REQUEST_FAILED, e), e);
        }
        mySrcPath = srcPath;
    }

    public String getDstPath() {
        return myDstPath;
    }

    private void setDstPath(String dstPath) throws SVNException {
        SVNURL.parseURIEncoded(dstPath);
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


    public String getEntryPath() {
        return myEntryPath;
    }

    private void setEntryPath(String entryPath) {
        myEntryPath = entryPath;
    }

    public long getEntryRevision() {
        return myEntryRevision;
    }

    private void setEntryRevision(long entryRevision) {
        myEntryRevision = entryRevision;
    }

    public String getEntryLinkPath() {
        return myEntryLinkPath;
    }

    private void setEntryLinkPath(String entryLinkPath) {
        myEntryLinkPath = entryLinkPath;
    }

    public boolean isEntryStartEmpty() {
        return myEntryStartEmpty;
    }

    private void setEntryStartEmpty(boolean entryStartEmpty) {
        myEntryStartEmpty = entryStartEmpty;
    }

    public String getEntryLockToken() {
        return myEntryLockToken;
    }

    private void setEntryLockToken(String entryLockToken) {
        myEntryLockToken = entryLockToken;
    }


    public String getMissing() {
        return myMissing;
    }

    private void setMissing(String missing) {
        myMissing = missing;
    }

    protected void initialize() throws SVNException {
        setSendAll("true".equals(getRootElementAttributes().getValue("send-all")));
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
            } else if (element == ENTRY) {
                try {
                    long revision = Long.parseLong(property.getAttributeValue("rev"));
                    setEntryRevision(revision);
                } catch (NumberFormatException e) {
                    SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.RA_DAV_REQUEST_FAILED, e), e);
                }

                setEntryPath(property.getFirstValue());
                setDepth(SVNDepth.fromString(property.getFirstValue()));
                setEntryLinkPath(property.getAttributeValue("linkpath"));
                setEntryStartEmpty(property.getAttributeValue("start-empty") != null);
                setEntryLockToken(property.getAttributeValue("lock-token"));

            } else if (element == MISSING) {
                setMissing(property.getFirstValue());
            }
        }
        if (!isDepthRequested() && !isRecursiveRequested() && (getDepth() == SVNDepth.UNKNOWN)) {
            setDepth(SVNDepth.INFINITY);
        }
        if (getSrcPath() == null) {
            SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.RA_DAV_REQUEST_FAILED));
        }
        if (!isSendAll()) {
            setTextDeltas(false);
        }
    }

    private void assertNullCData(DAVElementProperty property) throws SVNException {
        if (property.getValues() == null) {
            invalidXML();
        }
    }

}
