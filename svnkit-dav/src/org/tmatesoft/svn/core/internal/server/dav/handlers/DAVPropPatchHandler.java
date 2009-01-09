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

import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNPropertyValue;
import org.tmatesoft.svn.core.internal.io.dav.DAVElement;
import org.tmatesoft.svn.core.internal.server.dav.DAVDepth;
import org.tmatesoft.svn.core.internal.server.dav.DAVErrorCode;
import org.tmatesoft.svn.core.internal.server.dav.DAVException;
import org.tmatesoft.svn.core.internal.server.dav.DAVRepositoryManager;
import org.tmatesoft.svn.core.internal.server.dav.DAVResource;
import org.tmatesoft.svn.core.internal.server.dav.DAVXMLUtil;
import org.tmatesoft.svn.core.internal.server.dav.handlers.DAVRequest.DAVElementProperty;
import org.tmatesoft.svn.core.internal.util.SVNEncodingUtil;
import org.tmatesoft.svn.core.internal.util.SVNXMLUtil;
import org.tmatesoft.svn.util.SVNDebugLog;
import org.tmatesoft.svn.util.SVNLogType;


/**
 * @version 1.2.0
 * @author  TMate Software Ltd.
 */
public class DAVPropPatchHandler extends ServletDAVHandler {

    private static final Map OUR_LIVE_PROPS = new HashMap(); 
    
    static {
        OUR_LIVE_PROPS.put(DAVElement.GET_CONTENT_LENGTH, new LivePropertySpecification(DAVElement.GET_CONTENT_LENGTH, false, true));
        OUR_LIVE_PROPS.put(DAVElement.GET_CONTENT_TYPE, new LivePropertySpecification(DAVElement.GET_CONTENT_TYPE, false, true));
        OUR_LIVE_PROPS.put(DAVElement.GET_ETAG, new LivePropertySpecification(DAVElement.GET_ETAG, false, true)); 
        OUR_LIVE_PROPS.put(DAVElement.CREATION_DATE, new LivePropertySpecification(DAVElement.CREATION_DATE, false, true)); 
        OUR_LIVE_PROPS.put(DAVElement.GET_LAST_MODIFIED, new LivePropertySpecification(DAVElement.GET_LAST_MODIFIED, false, true)); 
        OUR_LIVE_PROPS.put(DAVElement.BASELINE_COLLECTION, new LivePropertySpecification(DAVElement.BASELINE_COLLECTION, false, true));
        OUR_LIVE_PROPS.put(DAVElement.CHECKED_IN, new LivePropertySpecification(DAVElement.CHECKED_IN, false, true)); 
        OUR_LIVE_PROPS.put(DAVElement.VERSION_CONTROLLED_CONFIGURATION, 
                new LivePropertySpecification(DAVElement.VERSION_CONTROLLED_CONFIGURATION, false, true));
        OUR_LIVE_PROPS.put(DAVElement.VERSION_NAME, new LivePropertySpecification(DAVElement.VERSION_NAME, false, true)); 
        OUR_LIVE_PROPS.put(DAVElement.CREATOR_DISPLAY_NAME, new LivePropertySpecification(DAVElement.CREATOR_DISPLAY_NAME, false, true));
        OUR_LIVE_PROPS.put(DAVElement.AUTO_VERSION, new LivePropertySpecification(DAVElement.AUTO_VERSION, false, true));
        OUR_LIVE_PROPS.put(DAVElement.BASELINE_RELATIVE_PATH, new LivePropertySpecification(DAVElement.BASELINE_RELATIVE_PATH, false, true)); 
        OUR_LIVE_PROPS.put(DAVElement.MD5_CHECKSUM, new LivePropertySpecification(DAVElement.MD5_CHECKSUM, false, true));
        OUR_LIVE_PROPS.put(DAVElement.REPOSITORY_UUID, new LivePropertySpecification(DAVElement.REPOSITORY_UUID, false, true)); 
        OUR_LIVE_PROPS.put(DAVElement.DEADPROP_COUNT, new LivePropertySpecification(DAVElement.DEADPROP_COUNT, false, true));
        
        OUR_LIVE_PROPS.put(DAVElement.GET_CONTENT_LANGUAGE, new LivePropertySpecification(DAVElement.GET_CONTENT_LANGUAGE, false, false));
        OUR_LIVE_PROPS.put(DAVElement.LOCK_DISCOVERY, new LivePropertySpecification(DAVElement.LOCK_DISCOVERY, false, false));
        OUR_LIVE_PROPS.put(DAVElement.SUPPORTED_LOCK, new LivePropertySpecification(DAVElement.SUPPORTED_LOCK, false, false));
    };
    
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
        
        boolean isFailure = false;
        List properties = new LinkedList();
        DAVPropPatchRequest requestXMLObject = getPropPatchRequest();  
        DAVElementProperty rootElement = requestXMLObject.getRoot();
        List childrenElements = rootElement.getChildren();
        for (Iterator childrenIter = childrenElements.iterator(); childrenIter.hasNext();) {
            DAVElementProperty childElement = (DAVElementProperty) childrenIter.next();
            DAVElement childElementName = childElement.getName();
            if (!DAVElement.DAV_NAMESPACE.equals(childElementName.getNamespace()) || (childElementName != DAVPropPatchRequest.REMOVE && 
                    childElementName != DAVPropPatchRequest.SET)) {
                continue;
            }

            DAVElementProperty propChildrenElement = childElement.getChild(DAVElement.PROP);
            if (propChildrenElement == null) {
                autoCheckIn(resource, true, false, avInfo);
                SVNDebugLog.getDefaultLog().logError(SVNLogType.NETWORK, "A \"prop\" element is missing inside the propertyupdate command.");
                setResponseStatus(HttpServletResponse.SC_BAD_REQUEST);
                return;
            }
            
            boolean isRemove = childElementName == DAVPropPatchRequest.REMOVE;
            List propChildren = propChildrenElement.getChildren();
            for (Iterator propsIter = propChildren.iterator(); propsIter.hasNext();) {
                DAVElementProperty property = (DAVElementProperty) propsIter.next();
                DAVElement propertyName = property.getName();
                PropertyChangeContext propContext = new PropertyChangeContext();
                propContext.myIsSet = !isRemove;
                propContext.myProperty = property;
                properties.add(propContext);
                validateProp(resource, propertyName, propsProvider, propContext);
                if (propContext.myError != null && propContext.myError.getResponseCode() >= 300) {
                    isFailure = true;
                }
            }
        }
        
        String propStatText = null;
        DAVPropertyExecuteHandler executeHandler = new DAVPropertyExecuteHandler(propsProvider);
        if (!isFailure && processPropertyContextList(executeHandler, properties, true, false)) {
            isFailure = true;
        }
        
        if (isFailure) {
            DAVPropertyRollBackHandler rollBackHandler = new DAVPropertyRollBackHandler(propsProvider);
            processPropertyContextList(rollBackHandler, properties, false, true);
            propStatText = getFailureMessage(properties);
        } else {
            propStatText = getSuccessMessage(properties);
        }
        
        autoCheckIn(resource, isFailure, false, avInfo);
        //TODO: log propCtxt.error here later
        DAVPropsResult propResult = new DAVPropsResult();
        propResult.addPropStatsText(propStatText);
        DAVResponse response = new DAVResponse(null, resource.getResourceURI().getRequestURI(), null, propResult, 0);
        try {
            DAVXMLUtil.sendMultiStatus(response, getHttpServletResponse(), SC_MULTISTATUS, getNamespaces());
        } catch (IOException ioe) {
            throw new DAVException(ioe.getMessage(), HttpServletResponse.SC_INTERNAL_SERVER_ERROR, SVNErrorCode.IO_ERROR.getCode());
        }
    }

    private String getFailureMessage(List propContextList) {
        DAVException err424Set = null;
        DAVException err424Delete = null;
        StringBuffer buffer = new StringBuffer();
        for (Iterator propsIter = propContextList.iterator(); propsIter.hasNext();) {
            PropertyChangeContext propContext = (PropertyChangeContext) propsIter.next();
            SVNXMLUtil.openXMLTag(SVNXMLUtil.DAV_NAMESPACE_PREFIX, "propstat", SVNXMLUtil.XML_STYLE_PROTECT_CDATA, null, buffer);
            SVNXMLUtil.openXMLTag(SVNXMLUtil.DAV_NAMESPACE_PREFIX, "prop", SVNXMLUtil.XML_STYLE_NORMAL, null, buffer);
            DAVXMLUtil.addEmptyElement(getNamespaces(), propContext.myProperty.getName(), buffer);
            SVNXMLUtil.closeXMLTag(SVNXMLUtil.DAV_NAMESPACE_PREFIX, "prop", buffer);
            if (propContext.myError == null) {
                if (propContext.myIsSet) {
                    if (err424Set == null) {
                        err424Set = new DAVException("Attempted DAV:set operation could not be completed due to other errors.", 
                                SC_FAILED_DEPENDANCY, 0);
                    }
                    propContext.myError = err424Set;
                } else {
                    if (err424Delete == null) {
                        err424Delete = new DAVException("Attempted DAV:remove operation could not be completed due to other errors.", 
                                SC_FAILED_DEPENDANCY, 0);
                    }
                    propContext.myError = err424Delete;
                }
            }
            
            SVNXMLUtil.openXMLTag(SVNXMLUtil.DAV_NAMESPACE_PREFIX, "status", SVNXMLUtil.XML_STYLE_NORMAL, null, buffer);
            buffer.append("HTTP/1.1 ");
            buffer.append(propContext.myError.getResponseCode());
            buffer.append(" (status)");
            SVNXMLUtil.closeXMLTag(SVNXMLUtil.DAV_NAMESPACE_PREFIX, "status", buffer);
            
            if (propContext.myError.getMessage() != null) {
                SVNXMLUtil.openXMLTag(SVNXMLUtil.DAV_NAMESPACE_PREFIX, "responsedescription", SVNXMLUtil.XML_STYLE_PROTECT_CDATA, null, buffer);
                buffer.append(propContext);
                SVNXMLUtil.closeXMLTag(SVNXMLUtil.DAV_NAMESPACE_PREFIX, "responsedescription", buffer);
            }
            
            SVNXMLUtil.closeXMLTag(SVNXMLUtil.DAV_NAMESPACE_PREFIX, "propstat", buffer);
        }
        
        return buffer.toString();
    }
    
    private String getSuccessMessage(List propContextList) {
        StringBuffer buffer = new StringBuffer();
        SVNXMLUtil.openXMLTag(SVNXMLUtil.DAV_NAMESPACE_PREFIX, "propstat", SVNXMLUtil.XML_STYLE_PROTECT_CDATA, null, buffer);
        SVNXMLUtil.openXMLTag(SVNXMLUtil.DAV_NAMESPACE_PREFIX, "prop", SVNXMLUtil.XML_STYLE_NORMAL, null, buffer);
        for (Iterator propsIter = propContextList.iterator(); propsIter.hasNext();) {
            PropertyChangeContext propContext = (PropertyChangeContext) propsIter.next();
            DAVXMLUtil.addEmptyElement(getNamespaces(), propContext.myProperty.getName(), buffer);
        }

        SVNXMLUtil.closeXMLTag(SVNXMLUtil.DAV_NAMESPACE_PREFIX, "prop", buffer);
        SVNXMLUtil.openXMLTag(SVNXMLUtil.DAV_NAMESPACE_PREFIX, "status", SVNXMLUtil.XML_STYLE_NORMAL, null, buffer);
        buffer.append("HTTP/1.1 200 OK");
        SVNXMLUtil.closeXMLTag(SVNXMLUtil.DAV_NAMESPACE_PREFIX, "status", buffer);
        SVNXMLUtil.closeXMLTag(SVNXMLUtil.DAV_NAMESPACE_PREFIX, "propstat", buffer);
        return buffer.toString();
    }
    
    private boolean processPropertyContextList(IDAVPropertyContextHandler handler, List propertyContextList, boolean stopOnError, boolean reverse) {
        ListIterator propContextIterator = propertyContextList.listIterator(reverse ? propertyContextList.size() : 0);
        for (; reverse ? propContextIterator.hasPrevious() : propContextIterator.hasNext();) {
            PropertyChangeContext propContext = (PropertyChangeContext) (reverse ? propContextIterator.previous() : propContextIterator.next());
            handler.handleContext(propContext);
            if (stopOnError && propContext.myError != null && propContext.myError.getResponseCode() >= 300) {
                return true;
            }
        }
        return false;
    }
    
    private void validateProp(DAVResource resource, DAVElement property, DAVPropertiesProvider propsProvider, 
            PropertyChangeContext propContext) {
        LivePropertySpecification livePropSpec = findLiveProperty(property);
        propContext.myLivePropertySpec = livePropSpec;
        if (!isPropertyWritable(property, livePropSpec)) {
            propContext.myError = new DAVException("Property is read-only.", HttpServletResponse.SC_CONFLICT, DAVErrorCode.PROP_READONLY);
            return;
        }
        
        if (livePropSpec != null && livePropSpec.isSVNSupported()) {
            return;
        }
        
        if (propsProvider.isDeferred()) {
            try {
                propsProvider.open(resource, false);
            } catch (DAVException dave) {
                propContext.myError = dave;
                return;
            }
        }
        
        if (!propsProvider.isOperative()) {
            propContext.myError = new DAVException("Attempted to set/remove a property without a valid, open, read/write property database.", 
                    HttpServletResponse.SC_INTERNAL_SERVER_ERROR, DAVErrorCode.PROP_NO_DATABASE);
            return;
        }
    }
    
    private LivePropertySpecification findLiveProperty(DAVElement property) {
        String nameSpace = property.getNamespace(); 
        if (!DAVElement.DAV_NAMESPACE.equals(nameSpace) && !DAVElement.SVN_DAV_PROPERTY_NAMESPACE.equals(nameSpace)) {
            return null;
        }
        
        return (LivePropertySpecification) OUR_LIVE_PROPS.get(property);
    }
   
    protected DAVRequest getDAVRequest() {
        return getPropPatchRequest();
    }

    private boolean isPropertyWritable(DAVElement property, LivePropertySpecification livePropSpec) {
        if (livePropSpec != null) {
            return livePropSpec.isWritable();
        }
        if (property == DAVElement.LOCK_DISCOVERY || property == DAVElement.SUPPORTED_LOCK) {
            return false;
        }
        return true;
    }
    
    private DAVPropPatchRequest getPropPatchRequest() {
        if (myDAVRequest == null) {
            myDAVRequest = new DAVPropPatchRequest();
        }
        return myDAVRequest;
    }

    private static class LivePropertySpecification {
        private DAVElement myPropertyName; 
        private boolean myIsWritable;
        private boolean myIsSVNSupported;

        public LivePropertySpecification(DAVElement propertyName, boolean isWritable, boolean isSVNSupported) {
            myIsWritable = isWritable;
            myPropertyName = propertyName;
            myIsSVNSupported = isSVNSupported;
        }
        
        public DAVElement getPropertyName() {
            return myPropertyName;
        }
        
        public boolean isWritable() {
            return myIsWritable;
        }

        public boolean isSVNSupported() {
            return myIsSVNSupported;
        }
        
    }
    
    private static class PropertyChangeContext {
        private boolean myIsSet;
        private DAVElementProperty myProperty;
        private LivePropertySpecification myLivePropertySpec;
        private DAVException myError;
        private RollBackProperty myRollBackProperty;
    }

    private static class RollBackProperty {
        private DAVElement myPropertyName;
        private SVNPropertyValue myRollBackPropertyValue;

        public RollBackProperty(DAVElement propertyName, SVNPropertyValue rollBackPropertyValue) {
            myPropertyName = propertyName;
            myRollBackPropertyValue = rollBackPropertyValue;
        }
    }
    
    private static interface IDAVPropertyContextHandler {
        public void handleContext(PropertyChangeContext propContext);
    }
    
    private class DAVPropertyExecuteHandler implements IDAVPropertyContextHandler {
        private DAVPropertiesProvider myPropsProvider;
        
        public DAVPropertyExecuteHandler(DAVPropertiesProvider propsProvider) {
            myPropsProvider = propsProvider;
        }

        public void handleContext(PropertyChangeContext propContext) {
            if (propContext.myLivePropertySpec == null) {
                try {
                    SVNPropertyValue rollBackPropValue = myPropsProvider.getPropertyValue(propContext.myProperty.getName());
                    propContext.myRollBackProperty = new RollBackProperty(propContext.myProperty.getName(), rollBackPropValue);
                } catch (DAVException dave) {
                    handleError(dave, propContext);
                    return;
                }

                if (propContext.myIsSet) {
                    try {
                        myPropsProvider.storeProperty(propContext.myProperty);
                    } catch (DAVException dave) {
                        handleError(dave, propContext);
                        return;
                    }
                } else {
                    try {
                        myPropsProvider.removeProperty(propContext.myProperty.getName());
                    } catch (DAVException dave) {
                        //
                    }
                }
            }
            
        }
        
        private void handleError(DAVException dave, PropertyChangeContext propContext) {
            DAVException exc = new DAVException("Could not execute PROPPATCH.", HttpServletResponse.SC_INTERNAL_SERVER_ERROR, dave, 
                    DAVErrorCode.PROP_EXEC);
            propContext.myError = exc;
        }
    }
    
    private class DAVPropertyRollBackHandler implements IDAVPropertyContextHandler {
        private DAVPropertiesProvider myPropsProvider;
        
        public DAVPropertyRollBackHandler(DAVPropertiesProvider propsProvider) {
            myPropsProvider = propsProvider;
        }

        public void handleContext(PropertyChangeContext propContext) {
            if (propContext.myRollBackProperty == null) {
                return;
            }
            
            if (propContext.myLivePropertySpec == null) {
                try {
                    myPropsProvider.applyRollBack(propContext.myRollBackProperty.myPropertyName, 
                            propContext.myRollBackProperty.myRollBackPropertyValue);
                } catch (DAVException dave) {
                    if (propContext.myError == null) {
                        propContext.myError = dave;
                    } else {
                        DAVException err = dave;
                        while (err.getPreviousException() != null) {
                            err = err.getPreviousException();
                        }
                        err.setPreviousException(propContext.myError);
                        propContext.myError = dave;
                    }
                }
            }
        }
    }
}
