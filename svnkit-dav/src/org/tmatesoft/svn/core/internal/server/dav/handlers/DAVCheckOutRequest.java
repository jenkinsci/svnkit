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

import java.util.Map;
import java.util.logging.Level;

import javax.servlet.http.HttpServletResponse;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.io.dav.DAVElement;
import org.tmatesoft.svn.core.internal.server.dav.DAVException;
import org.tmatesoft.svn.util.SVNLogType;


/**
 * @version 1.2.0
 * @author  TMate Software Ltd.
 */
public class DAVCheckOutRequest extends DAVRequest {
    public static final DAVElement NEW = DAVElement.getElement(DAVElement.DAV_NAMESPACE, "new");

    private static final DAVElement CHECKOUT = DAVElement.getElement(DAVElement.DAV_NAMESPACE, "checkout");
    private static final DAVElement APPLY_TO_VERSION = DAVElement.getElement(DAVElement.DAV_NAMESPACE, "apply-to-version");
    private static final DAVElement UNRESERVED = DAVElement.getElement(DAVElement.DAV_NAMESPACE, "unreserved");
    private static final DAVElement FORK_OK = DAVElement.getElement(DAVElement.DAV_NAMESPACE, "fork-ok");
    private static final DAVElement ACTIVITY_SET = DAVElement.getElement(DAVElement.DAV_NAMESPACE, "activity-set");

    public boolean isUnreserved() {
        return getProperties().containsKey(UNRESERVED);
    }
    
    public boolean isForkOk() {
        return getProperties().containsKey(FORK_OK);
    }

    public boolean isApplyToVersion() {
        return getProperties().containsKey(APPLY_TO_VERSION);
    }

    public Map getActivitySetPropertyElements() {
        DAVElementProperty activitySet = (DAVElementProperty) getProperties().get(ACTIVITY_SET);
        return activitySet != null && activitySet.getChildren() != null ? activitySet.getChildren() : null;
    }

    protected void init() throws SVNException {
        if (getRootElement() != CHECKOUT) {
            invalidXML();
        }
    }

    protected void invalidXML() throws SVNException {
        throw new DAVException("The request body, if present, must be a DAV:checkout element.", null, HttpServletResponse.SC_BAD_REQUEST, 
                null, SVNLogType.NETWORK, Level.FINE, null, null, null, 0, null);
    }

}
