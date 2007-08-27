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

import java.util.ArrayList;
import java.util.Collection;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.io.dav.DAVElement;
import org.xml.sax.Attributes;

/**
 * @author TMate Software Ltd.
 * @version 1.1.2
 */
public class DAVOptionsRequest extends DAVRequest {

    private static final DAVElement OPTIONS = DAVElement.getElement(DAVElement.DAV_NAMESPACE, "options");
    private static final DAVElement ACTIVITY_COLLECTION_SET = DAVElement.getElement(DAVElement.DAV_NAMESPACE, "activity-collection-set");
    private static final DAVElement SUPPORTED_METHOD_SET = DAVElement.getElement(DAVElement.DAV_NAMESPACE, "supported-method-set");
    private static final DAVElement SUPPORTED_METHOD = DAVElement.getElement(DAVElement.DAV_NAMESPACE, "supported-method");
    private static final DAVElement SUPPORTED_LIVE_PROPERTY_SET = DAVElement.getElement(DAVElement.DAV_NAMESPACE, "supported-live-property-set");
    private static final DAVElement SUPPORTED_LIVE_PROPERTY = DAVElement.getElement(DAVElement.DAV_NAMESPACE, "supported-live-property");
    private static final DAVElement SUPPORTED_REPORT_SET = DAVElement.getElement(DAVElement.DAV_NAMESPACE, "supported-report-set");
    private static final DAVElement SUPPORTED_REPORT = DAVElement.getElement(DAVElement.DAV_NAMESPACE, "supported-report");

    private boolean myIsActivitySetRequest = false;

    private Collection myRequestedMethods;
    private Collection myRequestedReports;
    private Collection myRequestedLiveProperties;

    public DAVOptionsRequest() {
        super();
    }

    protected void initialize() throws SVNException {
        if(getRootElement() != OPTIONS){
            invalidXML();
        }
    }

    protected void startElement(DAVElement parent, DAVElement element, Attributes attrs) throws SVNException {
        if (parent == null) {
            setRootElement(element);
        } else if (parent == OPTIONS) {
            if (element == SUPPORTED_METHOD_SET) {
                myRequestedMethods = new ArrayList();
            } else if (element == SUPPORTED_REPORT_SET) {
                myRequestedReports = new ArrayList();
            } else if (element == SUPPORTED_LIVE_PROPERTY) {
                myRequestedLiveProperties = new ArrayList();
            } else if (element == ACTIVITY_COLLECTION_SET) {
                myIsActivitySetRequest = true;
            }
        } else if (parent == SUPPORTED_METHOD_SET && element == SUPPORTED_METHOD) {
            String requestedMethodName = attrs.getValue(DAVElement.DAV_NAMESPACE, NAME_ATTR);
            if (requestedMethodName == null || myRequestedMethods == null) {
                invalidXML();
            }
            myRequestedMethods.add(requestedMethodName);
        } else if (parent == SUPPORTED_REPORT_SET && element == SUPPORTED_REPORT) {
            String requestedReportName = attrs.getValue(DAVElement.DAV_NAMESPACE, NAME_ATTR);
            String requestedReportNamespace = attrs.getValue(DAVElement.DAV_NAMESPACE, NAMESPACE_ATTR);
            if (requestedReportName == null || myRequestedReports == null) {
                invalidXML();
            }
            if (requestedReportNamespace == null) {
                requestedReportNamespace = DAVElement.SVN_NAMESPACE;
            }
            myRequestedReports.add(DAVElement.getElement(requestedReportNamespace, requestedReportName));
        } else if (parent == SUPPORTED_LIVE_PROPERTY_SET && element == SUPPORTED_LIVE_PROPERTY) {
            String requestedLivePropertyName = attrs.getValue(DAVElement.DAV_NAMESPACE, NAME_ATTR);
            String requestedLivePropertyNamespace = attrs.getValue(DAVElement.DAV_NAMESPACE, NAMESPACE_ATTR);
            if (requestedLivePropertyName == null || myRequestedLiveProperties == null) {
                invalidXML();
            }
            if (requestedLivePropertyNamespace == null) {
                requestedLivePropertyNamespace = DAVElement.DAV_NAMESPACE;
            }
            myRequestedLiveProperties.add(DAVElement.getElement(requestedLivePropertyNamespace, requestedLivePropertyName));
        }
    }

    protected void endElement(DAVElement parent, DAVElement element, StringBuffer cdata) throws SVNException {
    }


    public boolean isEmpty() {
        return !(isActivitySetRequest() || isSupportedLivePropertiesRequest() || isSupportedMethodsRequest() || isSupportedMethodsRequest());
    }

    public boolean isActivitySetRequest() {
        return myIsActivitySetRequest;
    }

    public boolean isSupportedMethodsRequest() {
        return myRequestedMethods != null;
    }

    public boolean isSupportedLivePropertiesRequest() {
        return myRequestedLiveProperties != null;
    }

    public boolean isSupportedReportsRequest() {
        return myRequestedReports != null;
    }

    public Collection getRequestedMethods() {
        return myRequestedMethods;
    }

    public Collection getRequestedLiveProperties() {
        return myRequestedLiveProperties;
    }

    public Collection getRequestedReports() {
        return myRequestedReports;
    }
}

