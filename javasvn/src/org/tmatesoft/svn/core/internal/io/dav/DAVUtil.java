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

import org.tmatesoft.svn.core.io.SVNException;
import org.tmatesoft.svn.util.PathUtil;


/**
 * @author Alexander Kitaev
 */
public class DAVUtil {
    
    public static DAVResponse getResourceProperties(DAVConnection connection, String path, String label, DAVElement[] properties) throws SVNException {
        final DAVResponse[] result = new DAVResponse[1];
        connection.doPropfind(path, 0, label, properties, new IDAVResponseHandler() {
            public void handleDAVResponse(DAVResponse response) {
                if (result[0] == null) {
                    result[0] = response;
                }
            }
        });
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
        info.baselineBase = PathUtil.encode(info.baselineBase);
        if (includeRevision) {
            info.revision = Long.parseLong((String) baselineProperties.getPropertyValue(DAVElement.VERSION_NAME));
        } 
        if (includeType) {            
            info.isDirectory = getPropertyValue(connection, PathUtil.append(info.baselineBase, info.baselinePath), 
                    null, DAVElement.RESOURCE_TYPE) != null;
        }
        return info;
    }
    
    public static DAVResponse getBaselineProperties(DAVConnection connection, String path, long revision, DAVElement[] elements) throws SVNException {
        DAVResponse properties = null;
        String loppedPath = "";
        while(true) {
	        try {
	            properties = getResourceProperties(connection, path, null, DAVElement.STARTING_PROPERTIES);
	            break;
	        } catch (SVNException e) {
	        }
	        loppedPath = PathUtil.append(PathUtil.tail(path), loppedPath);
	        path = PathUtil.removeTail(path);
	        if (PathUtil.isEmpty(path)) {
	            break;
	        }
        }
        if (properties == null) {
            throw new SVNException("resource " + path + " is not part of repository");
        }
        String vcc = (String) properties.getPropertyValue(DAVElement.VERSION_CONTROLLED_CONFIGURATION);
        String baselineRelativePath = (String) properties.getPropertyValue(DAVElement.BASELINE_RELATIVE_PATH);
        if (vcc == null) {
            throw new SVNException("important properties are missing for " + path);
        } 
        if (baselineRelativePath == null) {
            baselineRelativePath = "";
        }
        baselineRelativePath += loppedPath;
        baselineRelativePath = PathUtil.removeLeadingSlash(baselineRelativePath);
        baselineRelativePath = PathUtil.removeTrailingSlash(baselineRelativePath);
        baselineRelativePath = PathUtil.encode(baselineRelativePath);
        
        String label = null;
        if (revision < 0) {
            // get vcc's "checked-in"
            vcc = (String) getPropertyValue(connection, vcc, null, DAVElement.CHECKED_IN);
        } else {
            label = Long.toString(revision);
        }
        DAVResponse result = getResourceProperties(connection, vcc, label, elements);
        result.setHref(baselineRelativePath);
        return result;
    }
    
    public static Map filterProperties(DAVResponse source, Map target) {
        target = target == null ? new HashMap() : target;
        for(Iterator props = source.properties(); props.hasNext();) {
            DAVElement property = (DAVElement) props.next();
            String namespace = property.getNamespace();
            Object value = source.getPropertyValue(property);
            if (value != null) {
                value = value.toString();
            }
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
                target.put("version-url", source.getPropertyValue(property));
            }
        }
        return target;
    }

}
