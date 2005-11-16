/*
 * ====================================================================
 * Copyright (c) 2004 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://tmate.org/svn/license.html.
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */

package org.tmatesoft.svn.core.internal.io.dav;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.util.SVNEncodingUtil;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;

/**
 * @version 1.0
 * @author  TMate Software Ltd.
 */
public class DAVUtil {

    public static DAVResponse getResourceProperties(DAVConnection connection, String path, String label, DAVElement[] properties, boolean skipNotFound) throws SVNException {
        final DAVResponse[] result = new DAVResponse[1];
        connection.doPropfind(path, 0, label, properties, new IDAVResponseHandler() {
            public void handleDAVResponse(DAVResponse response) {
                if (result[0] == null) {
                    result[0] = response;
                }
            }
        }, skipNotFound ? new int[] {200, 207, 404} : new int[] {200, 207});
        return result[0];
    }

    public static Object getPropertyValue(DAVConnection connection, String path, String label, DAVElement property) throws SVNException {
        final DAVResponse[] result = new DAVResponse[1];
        connection.doPropfind(path, 0, label, new DAVElement[] {property}, new IDAVResponseHandler() {
            public void handleDAVResponse(DAVResponse response) {
                if (result[0] == null) {
                    result[0] = response;
                }
            }
        });
        if (result[0] != null) {
            return result[0].getPropertyValue(property);
        }
        return null;
    }

    public static void getChildren(DAVConnection connection, final String parentPath, DAVElement[] properties, IDAVResponseHandler handler) throws SVNException {
        connection.doPropfind(parentPath, 1, null, properties, handler);
    }

    public static DAVBaselineInfo getBaselineInfo(DAVConnection connection, String path, long revision,
                                                  boolean includeType, boolean includeRevision, DAVBaselineInfo info) throws SVNException {
        DAVElement[] properties = includeRevision ? DAVElement.BASELINE_PROPERTIES : new DAVElement[] {DAVElement.BASELINE_COLLECTION};
        DAVResponse baselineProperties = getBaselineProperties(connection, path, revision, properties);

        info = info == null ? new DAVBaselineInfo() : info;
        info.baselinePath = baselineProperties.getHref();
        info.baselineBase = (String) baselineProperties.getPropertyValue(DAVElement.BASELINE_COLLECTION);
        info.baselineBase = SVNEncodingUtil.uriEncode(info.baselineBase);
        if (includeRevision) {
            info.revision = Long.parseLong((String) baselineProperties.getPropertyValue(DAVElement.VERSION_NAME));
        }
        if (includeType) {
            info.isDirectory = getPropertyValue(connection, SVNPathUtil.append(info.baselineBase, info.baselinePath),
                    null, DAVElement.RESOURCE_TYPE) != null;
        }
        return info;
    }

    public static DAVResponse getBaselineProperties(DAVConnection connection, String path, long revision, DAVElement[] elements) throws SVNException {
        DAVResponse properties = null;
        String loppedPath = "";
        String originalPath = path;
        while(true) {
            properties = getResourceProperties(connection, path, null, DAVElement.STARTING_PROPERTIES, true);
            if (properties != null) {
                break;
            }
            loppedPath = SVNPathUtil.append(SVNPathUtil.tail(path), loppedPath);
            if ("/".equals(path) || "".equals(path)) {
                break;
            }
            path = SVNPathUtil.removeTail(path);
            if ("".equals(path)) {
                path = "/";
            }
        }
        if (properties == null) {
            SVNErrorManager.error("svn: resource '" + originalPath + "' is not part of repository");
        }
        String vcc = (String) properties.getPropertyValue(DAVElement.VERSION_CONTROLLED_CONFIGURATION);
        String baselineRelativePath = (String) properties.getPropertyValue(DAVElement.BASELINE_RELATIVE_PATH);
        if (vcc == null) {
            SVNErrorManager.error("svn: important properties are missing for " + path);
        }
        if (baselineRelativePath == null) {
            baselineRelativePath = "";
        }
        baselineRelativePath = SVNEncodingUtil.uriEncode(baselineRelativePath);
        baselineRelativePath = SVNPathUtil.append(baselineRelativePath, loppedPath);

        String label = null;
        if (revision < 0) {
            // get vcc's "checked-in"
            vcc = (String) getPropertyValue(connection, vcc, null, DAVElement.CHECKED_IN);
        } else {
            label = Long.toString(revision);
        }
        DAVResponse result = getResourceProperties(connection, vcc, label, elements, false);
        result.setHref(baselineRelativePath);
        return result;
    }

    public static Map filterProperties(DAVResponse source, Map target) {
        target = target == null ? new HashMap() : target;
        for(Iterator props = source.properties(); props.hasNext();) {
            DAVElement property = (DAVElement) props.next();
            String namespace = property.getNamespace();
            if (namespace.equals(DAVElement.SVN_CUSTOM_PROPERTY_NAMESPACE)) {
                String name = property.getName();
                // hack!
                if (name.startsWith("svk_")) {
                    name = name.substring(0, "svk".length()) + ":" + name.substring("svk".length() + 1);
                }
                target.put(name, source.getPropertyValue(property));
            } else if (namespace.equals(DAVElement.SVN_SVN_PROPERTY_NAMESPACE)) {
                target.put("svn:" + property.getName(), source.getPropertyValue(property));
            } else if (property == DAVElement.CHECKED_IN) {
                target.put("svn:wc:ra_dav:version-url", source.getPropertyValue(property));
            }
        }
        return target;
    }

    public static StringBuffer getCanonicalPath(String path, StringBuffer target) {
        target = target == null ? new StringBuffer() : target;
        if (path.startsWith("http:") || path.startsWith("https:")) {
            target.append(path);
            return target;
        }

        int end = path.length() - 1;
        for(int i = 0; i <= end; i++) {
            char ch = path.charAt(i);
            switch (ch) {
            case '/':
                if (i == end && i != 0) {
                    // skip trailing slash
                    break;
                } else if (i > 0 && path.charAt(i - 1) == '/') {
                    // skip duplicated slashes
                    break;
                }
            default:
                target.append(ch);
            }
        }
        return target;

    }

    public static Map parseAuthParameters(String source) {
        if (source == null) {
            return null;
        }
        source = source.trim();
        Map parameters = new HashMap();
        // parse strings: name="value" or name=value
        int index = source.indexOf(' ');
        if (index <= 0) {
            return null;
        }
        String method = source.substring(0, index);
        parameters.put("", method);

        source = source.substring(index).trim();
        if ("Basic".equalsIgnoreCase(method)) {
            if (source.indexOf("realm=") >= 0) {
                source = source.substring(source.indexOf("realm=") + "realm=".length());
                source = source.trim();
                if (source.startsWith("\"")) {
                    source = source.substring(1);
                }
                if (source.endsWith("\"")) {
                    source = source.substring(0, source.length() - 1);
                }
                parameters.put("realm", source);
            }
            return parameters;
        }
        char[] chars = source.toCharArray();
        int tokenIndex = 0;
        boolean parsingToken = true;
        String name = null;
        String value;
        int quotesCount = 0;

        for(int i = 0; i < chars.length; i++) {
            if (parsingToken) {
                if (chars[i] == '=') {
                    name = new String(chars, tokenIndex, i - tokenIndex);
                    name = name.trim();
                    tokenIndex = i + 1;
                    parsingToken = false;
                }
            } else {
                if (chars[i] == '\"') {
                    quotesCount = quotesCount > 0 ? 0 : 1;
                } else if ( i + 1 >= chars.length || (chars[i] == ',' && quotesCount == 0)) {
                    value = new String(chars, tokenIndex, i - tokenIndex);
                    value = value.trim();
                    if (value.charAt(0) == '\"' && value.charAt(value.length() - 1) == '\"') {
                        value = value.substring(1);
                        value = value.substring(0, value.length() - 1);
                    }
                    parameters.put(name, value);
                    tokenIndex = i + 1;
                    parsingToken = true;
                }
            }
        }
        return parameters;
    }
}
