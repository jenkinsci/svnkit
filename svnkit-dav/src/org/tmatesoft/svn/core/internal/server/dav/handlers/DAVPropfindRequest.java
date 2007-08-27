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

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.io.dav.DAVElement;

/**
 * @author TMate Software Ltd.
 * @version 1.1.2
 */
public class DAVPropfindRequest extends DAVRequest {

    private static final DAVElement PROPFIND = DAVElement.getElement(DAVElement.DAV_NAMESPACE, "propfind");
    private static final DAVElement PROPNAME = DAVElement.getElement(DAVElement.DAV_NAMESPACE, "propname");
    private static final DAVElement ALLPROP = DAVElement.getElement(DAVElement.DAV_NAMESPACE, "allprop");

    public DAVPropfindRequest() {
        super();
    }

    protected void initialize() throws SVNException {
        if (getRootElement() != PROPFIND) {
            invalidXML();
        }
    }

    public boolean isAllPropRequest() {
        return getProperties().keySet().iterator().next() == ALLPROP;
    }

    public boolean isPropNameRequest() {
        return getProperties().keySet().iterator().next() == PROPNAME;
    }

    public boolean isPropRequest() {
        return getProperties().keySet().iterator().next() == DAVElement.PROP;
    }

    public Collection getPropertyElements() {
        DAVElementProperty propElementProperty = (DAVElementProperty) getProperties().get(DAVElement.PROP);
        return propElementProperty.getChildren().keySet();
    }

}
