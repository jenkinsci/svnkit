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
import java.util.Map;

import javax.servlet.http.HttpServletResponse;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNProperties;
import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.SVNPropertyValue;
import org.tmatesoft.svn.core.internal.io.dav.DAVElement;
import org.tmatesoft.svn.core.internal.io.fs.FSFS;
import org.tmatesoft.svn.core.internal.io.fs.FSRoot;
import org.tmatesoft.svn.core.internal.io.fs.FSTransactionInfo;
import org.tmatesoft.svn.core.internal.server.dav.DAVErrorCode;
import org.tmatesoft.svn.core.internal.server.dav.DAVException;
import org.tmatesoft.svn.core.internal.server.dav.DAVResource;
import org.tmatesoft.svn.core.internal.server.dav.DAVResourceType;
import org.tmatesoft.svn.core.internal.server.dav.DAVResourceURI;
import org.tmatesoft.svn.core.internal.server.dav.handlers.DAVRequest.DAVElementProperty;


/**
 * @version 1.2.0
 * @author  TMate Software Ltd.
 */
public class DAVPropertiesProvider {
    private boolean myIsDeferred;
    private boolean myIsOperative;
    private DAVResource myResource;
    
    private DAVPropertiesProvider() {
    }
    
    public static DAVPropertiesProvider createPropertiesProvider(DAVResource resource, List namespaceXLate) throws DAVException {
        DAVResourceURI resourceURI = resource.getResourceURI();
        if (resourceURI.getURI() == null) {
            throw new DAVException("INTERNAL DESIGN ERROR: resource must define its URI.", HttpServletResponse.SC_INTERNAL_SERVER_ERROR, 0);
        }
        
        DAVPropertiesProvider provider = new DAVPropertiesProvider();
        return provider;
    }

    public void open(DAVResource resource, boolean readOnly) throws DAVException {
        myIsDeferred = false;
        try {
            doOpen(resource, readOnly);
        } catch (DAVException dave) {
            throw new DAVException("Could not open the property database.", HttpServletResponse.SC_INTERNAL_SERVER_ERROR, DAVErrorCode.PROP_OPENING);
        }
    }

    public void storeProperty(DAVElementProperty property) {
        String propValue = property.getFirstValue();
        Map attributes = property.getAttributes();
        if (attributes != null) {
            for (Iterator attrsIter = attributes.keySet().iterator(); attrsIter.hasNext();) {
                String attrName = (String) attrsIter.next();
                String attrValue = (String) attributes.get(attrName);
                
                if (ServletDAVHandler.ENCODING_ATTR.equals(attrName)) {
                    
                }
           
               
            }
        }
    }
   
    public SVNPropertyValue getPropertyValue(DAVElement propName) throws DAVException {
        String reposPropName = getReposPropName(propName);
        if (reposPropName == null) {
            return null;
        }
        
        SVNProperties props = null;
        FSFS fsfs = myResource.getFSFS();
        try {
            if (myResource.isBaseLined()) {
                if (myResource.getType() == DAVResourceType.WORKING) {
                    FSTransactionInfo txn = myResource.getTxnInfo();
                    props = fsfs.getTransactionProperties(txn.getTxnId());
                } else {
                    long revision = myResource.getRevision();
                    props = fsfs.getRevisionProperties(revision);
                }
            } else {
                FSRoot root = myResource.getRoot();
                props = fsfs.getProperties(root.getRevisionNode(myResource.getResourceURI().getPath()));
            }
        } catch (SVNException svne) {
            throw DAVException.convertError(svne.getErrorMessage(), HttpServletResponse.SC_INTERNAL_SERVER_ERROR, 
                    "could not fetch a property", null);
        }
        
        if (props != null) {
            return props.getSVNPropertyValue(reposPropName);
        }
        return null;
    }
    
    private String getReposPropName(DAVElement propName) {
        if (DAVElement.SVN_SVN_PROPERTY_NAMESPACE.equals(propName.getNamespace())) {
            return SVNProperty.SVN_PREFIX + propName.getName();
        } else if (DAVElement.SVN_CUSTOM_PROPERTY_NAMESPACE.equals(propName.getNamespace())) {
            return propName.getName();
        }
        return null;
    }

    public boolean isOperative() {
        return myIsOperative;
    }

    public boolean isDeferred() {
        return myIsDeferred;
    }

    public void setDeferred(boolean isDeferred) {
        myIsDeferred = isDeferred;
    }

    private void doOpen(DAVResource resource, boolean readOnly) throws DAVException {
        DAVResourceType resType = resource.getType();
        if (resType == DAVResourceType.HISTORY || resType == DAVResourceType.ACTIVITY || resType == DAVResourceType.PRIVATE) {
            myIsOperative = false;
            return;
        }
        
        if (!readOnly && resType != DAVResourceType.WORKING) {
            if (!(resource.isBaseLined() && resType == DAVResourceType.VERSION)) {
                throw new DAVException("Properties may only be changed on working resources.", HttpServletResponse.SC_CONFLICT, 0);
            }
        }
        
        myResource = resource;
    }
}
