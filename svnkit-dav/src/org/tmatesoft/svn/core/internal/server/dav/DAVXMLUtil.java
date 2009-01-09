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
package org.tmatesoft.svn.core.internal.server.dav;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletResponse;

import org.tmatesoft.svn.core.internal.io.dav.DAVElement;
import org.tmatesoft.svn.core.internal.server.dav.handlers.DAVPropsResult;
import org.tmatesoft.svn.core.internal.server.dav.handlers.DAVResponse;
import org.tmatesoft.svn.core.internal.util.SVNEncodingUtil;
import org.tmatesoft.svn.core.internal.util.SVNXMLUtil;


/**
 * @author TMate Software Ltd.
 * @version 1.2.0
 */
public class DAVXMLUtil extends SVNXMLUtil {
    public static final String SVN_DAV_ERROR_TAG = "error";

    public static StringBuffer addEmptyElement(List namespaces, DAVElement element, StringBuffer target) {
        if (element.getNamespace() == null || "".equals(element.getNamespace())) {
            target.append("<");
            target.append(element.getName());
            target.append("/>");
            target.append('\n');
            return target;
        }
        
        int index = namespaces.indexOf(element.getNamespace()); 
        target.append("<ns");
        target.append(index);
        target.append(":");
        target.append(element.getName());
        target.append("/>");
        return target;
    }
    
    public static StringBuffer openNamespaceDeclarationTag(String prefix, String header, Collection namespaces, StringBuffer target, 
            boolean useIndexedPrefixes) {
        return openNamespaceDeclarationTag(prefix, header, namespaces, null, target, true, useIndexedPrefixes);
    }

    public static StringBuffer openNamespaceDeclarationTag(String prefix, String header, Collection namespaces, Map attrs, StringBuffer target, 
            boolean addEOL, boolean useIndexedPrefixes) {
        target = target == null ? new StringBuffer() : target;
        target.append("<");
        target.append(prefix);
        target.append(":");
        target.append(header);
        //We should always add "DAV:" namespace
        target.append(" xmlns:");
        target.append(DAV_NAMESPACE_PREFIX);
        target.append("=\"");
        target.append(DAVElement.DAV_NAMESPACE);
        target.append("\"");
        if (namespaces != null && !namespaces.isEmpty()) {
            Collection usedNamespaces = new ArrayList();
            usedNamespaces.add(DAVElement.DAV_NAMESPACE);
            int i = 0;
            for (Iterator iterator = namespaces.iterator(); iterator.hasNext();) {
                Object item = iterator.next();
                String currentNamespace = null;
                if (item instanceof DAVElement) {
                    DAVElement currentElement = (DAVElement) item;
                    currentNamespace = currentElement.getNamespace();
                } else if (item instanceof String) {
                    currentNamespace = (String) item;
                }
                if (currentNamespace != null && currentNamespace.length() > 0 && !usedNamespaces.contains(currentNamespace)) {
                    usedNamespaces.add(currentNamespace);
                    target.append(" xmlns:");
                    if (useIndexedPrefixes) {
                        target.append("ns" + i); 
                    } else {
                        target.append(PREFIX_MAP.get(currentNamespace));
                    }
                    target.append("=\"");
                    target.append(currentNamespace);
                    target.append("\"");
                }
                i++;
            }
            usedNamespaces.clear();
        }
        if (attrs != null && !attrs.isEmpty()) {
            for (Iterator iterator = attrs.entrySet().iterator(); iterator.hasNext();) {
                Map.Entry entry = (Map.Entry) iterator.next();
                String name = (String) entry.getKey();
                String value = (String) entry.getValue();
                target.append(" ");
                target.append(name);
                target.append("=\"");
                target.append(SVNEncodingUtil.xmlEncodeAttr(value));
                target.append("\"");
            }
        }
     
        target.append(">");
        if (addEOL) {
            target.append('\n');
        }
        return target;
    }
    
    public static StringBuffer beginMultiStatus(HttpServletResponse servletResponse, int status, Collection namespaces, StringBuffer xmlBuffer) {
        servletResponse.setContentType(DAVServlet.XML_CONTENT_TYPE);
        servletResponse.setStatus(status);
        
        xmlBuffer = xmlBuffer == null ? new StringBuffer() : xmlBuffer;
        SVNXMLUtil.addXMLHeader(xmlBuffer);
        DAVXMLUtil.openNamespaceDeclarationTag(SVNXMLUtil.DAV_NAMESPACE_PREFIX, "multistatus", namespaces, xmlBuffer, namespaces != null);
        return xmlBuffer;
    }
    
    public static void sendMultiStatus(DAVResponse davResponse, HttpServletResponse servletResponse, int statusCode, 
            Collection namespaces) throws IOException {
        StringBuffer xmlBuffer = new StringBuffer();
        xmlBuffer = beginMultiStatus(servletResponse, statusCode, namespaces, xmlBuffer);
        while (davResponse != null) {
            DAVPropsResult propResult = davResponse.getPropResult();
            String xmlnsText = propResult.getXMLNSText(); 
            if (xmlnsText == null || xmlnsText.length() == 0) {
                SVNXMLUtil.openXMLTag(SVNXMLUtil.DAV_NAMESPACE_PREFIX, "response", SVNXMLUtil.XML_STYLE_NORMAL, null, xmlBuffer);
            } else {
                xmlBuffer.append("<D:response");
                xmlBuffer.append(xmlnsText);
                xmlBuffer.append(">\n");
            }
            
            SVNXMLUtil.openXMLTag(SVNXMLUtil.DAV_NAMESPACE_PREFIX, "href", SVNXMLUtil.XML_STYLE_NORMAL, null, xmlBuffer);
            String href = davResponse.getHref();
            xmlBuffer.append(href.indexOf('&') != -1 ? SVNEncodingUtil.xmlEncodeCDATA(href) : href);
            SVNXMLUtil.closeXMLTag(SVNXMLUtil.DAV_NAMESPACE_PREFIX, "href", xmlBuffer);

            String propStatsText = propResult.getPropStatsText();
            if (propStatsText == null || propStatsText.length() == 0) {
                SVNXMLUtil.openXMLTag(SVNXMLUtil.DAV_NAMESPACE_PREFIX, "status", SVNXMLUtil.XML_STYLE_NORMAL, null, xmlBuffer);
                xmlBuffer.append("HTTP/1.1 ");
                String statusLine = DAVServlet.getStatusLine(davResponse.getStatusCode());
                xmlBuffer.append(statusLine);
                SVNXMLUtil.closeXMLTag(SVNXMLUtil.DAV_NAMESPACE_PREFIX, "status", xmlBuffer);
            } else {
                xmlBuffer.append(propStatsText);
            }
            
            if (davResponse.getDescription() != null) {
                SVNXMLUtil.openXMLTag(SVNXMLUtil.DAV_NAMESPACE_PREFIX, "responsedescription", SVNXMLUtil.XML_STYLE_NORMAL, null, xmlBuffer);
                xmlBuffer.append(davResponse.getDescription());
                SVNXMLUtil.closeXMLTag(SVNXMLUtil.DAV_NAMESPACE_PREFIX, "responsedescription", xmlBuffer);
            }
            
            SVNXMLUtil.closeXMLTag(SVNXMLUtil.DAV_NAMESPACE_PREFIX, "response", xmlBuffer);
            davResponse = davResponse.getNextResponse();
        }

        SVNXMLUtil.closeXMLTag(SVNXMLUtil.DAV_NAMESPACE_PREFIX, "multistatus", xmlBuffer);
        servletResponse.getWriter().write(xmlBuffer.toString());
    }

}
