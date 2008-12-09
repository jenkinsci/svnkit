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
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.io.dav.DAVElement;
import org.tmatesoft.svn.core.internal.server.dav.DAVDepth;
import org.tmatesoft.svn.core.internal.server.dav.DAVException;
import org.tmatesoft.svn.core.internal.server.dav.DAVRepositoryManager;
import org.tmatesoft.svn.core.internal.server.dav.DAVResource;
import org.tmatesoft.svn.core.internal.server.dav.handlers.DAVRequest.DAVElementProperty;
import org.tmatesoft.svn.core.internal.util.SVNEncodingUtil;
import org.tmatesoft.svn.util.SVNDebugLog;
import org.tmatesoft.svn.util.SVNLogType;
import org.xml.sax.Attributes;


/**
 * @version 1.2.0
 * @author  TMate Software Ltd.
 */
public class DAVPropPatchHandler extends ServletDAVHandler {

    private DAVPropPatchRequest myDAVRequest;

    protected DAVPropPatchHandler(DAVRepositoryManager connector, HttpServletRequest request, HttpServletResponse response) {
        super(connector, request, response);
    }

    public void execute() throws SVNException {
        DAVResource resource = getRequestedDAVResource(false, false);
        if (!resource.exists()) {
            setResponseStatus(HttpServletResponse.SC_BAD_REQUEST);
            return;
        }
        
        long readLength = readInput(false);
        if (readLength <= 0) {
            getPropPatchRequest().invalidXMLRoot();
        }
        
        validateRequest(resource, DAVDepth.DEPTH_ZERO, DAV_VALIDATE_RESOURCE, null, null, null);
        DAVAutoVersionInfo avInfo = autoCheckOut(resource, false);
        
        DAVPropertiesProvider propsProvider = null;
        try {
            propsProvider = DAVPropertiesProvider.createPropertiesProvider(resource, null);
        } catch (DAVException dave) {
            autoCheckIn(resource, true, false, avInfo);
            throw new DAVException("Could not open the property database for {0}.", new Object[] { SVNEncodingUtil.xmlEncodeCDATA(getURI()) }, 
                    HttpServletResponse.SC_INTERNAL_SERVER_ERROR, 0);
        }
        
        DAVElement[] interestingElements = { DAVPropPatchRequest.REMOVE, DAVPropPatchRequest.REMOVE };
        DAVPropPatchRequest requestXMLObject = getPropPatchRequest();  
        for (int i = 0; i < interestingElements.length; i++) {
            if (requestXMLObject.hasElement(interestingElements[i])) {
                Map children = requestXMLObject.getElementsChildren(interestingElements[i]);
                DAVElementProperty propChildrenElement = children != null ? (DAVElementProperty) children.get(DAVElement.PROP) : null;
                if (children == null || propChildrenElement == null) {
                    autoCheckIn(resource, true, false, avInfo);
                    SVNDebugLog.getDefaultLog().logError(SVNLogType.NETWORK, "A \"prop\" element is missing inside the propertyupdate command.");
                    setResponseStatus(HttpServletResponse.SC_BAD_REQUEST);
                    return;
                }
                
                boolean isRemove = interestingElements[i] == DAVPropPatchRequest.REMOVE;
                Map propChildren = propChildrenElement.getChildren();
                for (Iterator propsIter = propChildren.keySet().iterator(); propsIter.hasNext();) {
                    
                }
            }
        }
    }

    protected void validateProp() {
        
    }
    
    protected DAVRequest getDAVRequest() {
        return getPropPatchRequest();
    }

    private DAVPropPatchRequest getPropPatchRequest() {
        if (myDAVRequest == null) {
            myDAVRequest = new DAVPropPatchRequest();
        }
        return myDAVRequest;
    }

}
