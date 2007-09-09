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
import org.tmatesoft.svn.core.internal.server.dav.DAVPathUtil;
import org.tmatesoft.svn.core.internal.server.dav.DAVResource;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;

/**
 * @author TMate Software Ltd.
 * @version 1.1.2
 */
public class DAVUpdateRequest extends DAVRequest {

    public static final int UPDATE_ACTION = 2;
    public static final int DIFF_ACTION = 9;
    public static final int STATUS_ACTION = 10;
    public static final int SWITCH_ACTION = 12;
    public static final int UNKNOWN_ACTION = 20;

    private static final DAVElement TARGET_REVISION = DAVElement.getElement(DAVElement.SVN_NAMESPACE, "target-revision");
    private static final DAVElement SRC_PATH = DAVElement.getElement(DAVElement.SVN_NAMESPACE, "src-path");
    private static final DAVElement DST_PATH = DAVElement.getElement(DAVElement.SVN_NAMESPACE, "dst-path");
    private static final DAVElement UPDATE_TARGET = DAVElement.getElement(DAVElement.SVN_NAMESPACE, "update-target");
    private static final DAVElement DEPTH = DAVElement.getElement(DAVElement.SVN_NAMESPACE, "depth");
    private static final DAVElement RECURSIVE = DAVElement.getElement(DAVElement.SVN_NAMESPACE, "recursive");
    private static final DAVElement IGNORE_ANCESTRY = DAVElement.getElement(DAVElement.SVN_NAMESPACE, "ignore-ancestry");
    private static final DAVElement TEXT_DELTAS = DAVElement.getElement(DAVElement.SVN_NAMESPACE, "text-deltas");
    private static final DAVElement RESOURCE_WALK = DAVElement.getElement(DAVElement.SVN_NAMESPACE, "resource-walk");

    private boolean mySendAll = false;
    private long myRevision = DAVResource.INVALID_REVISION;
    private String mySrcURL = null;
    private SVNURL myDstURL = null;
    private String myTarget = "";
    private boolean myTextDeltas = true;
    private SVNDepth myDepth = SVNDepth.UNKNOWN;
    private boolean myDepthRequested = false;
    private boolean myRecursiveRequested = false;
    private boolean myIgnoreAncestry = false;
    private boolean myResourceWalk = false;

    private boolean myIsInitialized;


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

    public String getSrcURL() {
        return mySrcURL;
    }

    private void setSrcURL(String srcURL) throws SVNException {
        mySrcURL = srcURL;
    }

    public SVNURL getDstURL() {
        return myDstURL;
    }

    private void setDstURL(String dstURL) throws SVNException {
        myDstURL = SVNURL.parseURIEncoded(dstURL);
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

    private boolean isInitialized() {
        return myIsInitialized;
    }

    private void setInitialized(boolean isInitialized) {
        myIsInitialized = isInitialized;
    }

    protected void init() throws SVNException {
        if (!isInitialized()) {
            setSendAll("true".equals(getRootElementAttributeValue("send-all")));
            for (Iterator iterator = getProperties().entrySet().iterator(); iterator.hasNext();) {
                Map.Entry entry = (Map.Entry) iterator.next();
                DAVElement element = (DAVElement) entry.getKey();
                DAVElementProperty property = (DAVElementProperty) entry.getValue();
                String value = property.getFirstValue();
                if (element == TARGET_REVISION) {
                    assertNullCData(property);
                    setRevision(Long.parseLong(value));
                } else if (element == SRC_PATH) {
                    assertNullCData(property);
                    DAVPathUtil.testCanonical(value);
                    setSrcURL(value);
                } else if (element == DST_PATH) {
                    assertNullCData(property);
                    DAVPathUtil.testCanonical(value);
                    setDstURL(value);
                } else if (element == UPDATE_TARGET) {
                    DAVPathUtil.testCanonical(value);
                    setTarget(value);
                } else if (element == DEPTH) {
                    assertNullCData(property);
                    setDepth(SVNDepth.fromString(value));
                    setDepthRequested(true);
                } else if (element == RECURSIVE && !isDepthRequested()) {
                    assertNullCData(property);
                    SVNDepth.fromRecurse(!"no".equals(value));
                    setRecursiveRequested(true);
                } else if (element == IGNORE_ANCESTRY) {
                    assertNullCData(property);
                    setIgnoreAncestry(!"no".equals(value));
                } else if (element == TEXT_DELTAS) {
                    assertNullCData(property);
                    setTextDeltas(!"no".equals(value));
                } else if (element == RESOURCE_WALK) {
                    assertNullCData(property);
                    setResourceWalk(!"no".equals(value));
                }
            }
            if (!isDepthRequested() && !isRecursiveRequested() && (getDepth() == SVNDepth.UNKNOWN)) {
                setDepth(SVNDepth.INFINITY);
            }
            if (getSrcURL() == null) {
                SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.RA_DAV_REQUEST_FAILED, "The request did not contain the '<src-path>' element.\nThis may indicate that your client is too old."));
            }
            if (!isSendAll()) {
                setTextDeltas(false);
            }
            setInitialized(true);
        }
    }

    private void assertNullCData(DAVElementProperty property) throws SVNException {
        if (property.getValues() == null) {
            invalidXML();
        }
    }

    public int getAction() {
        if (!isInitialized()) {
            return UNKNOWN_ACTION;
        }
        String sPath;
        if (getTarget() != null) {
            sPath = SVNPathUtil.append(getSrcURL(), getTarget());
        } else {
            sPath = getSrcURL();
        }
        if (getDstURL() != null) {
            if (!isSendAll() && getDstURL().equals(sPath)) {
                return DIFF_ACTION;
            } else {
                return isSendAll() ? SWITCH_ACTION : DIFF_ACTION;
            }
        } else {
            if (isTextDeltas()) {
                return UPDATE_ACTION;
            } else {
                return STATUS_ACTION;
            }
        }
    }
}
