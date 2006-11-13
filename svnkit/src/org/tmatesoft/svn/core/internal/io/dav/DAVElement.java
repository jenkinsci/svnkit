/*
 * ====================================================================
 * Copyright (c) 2004-2006 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */

package org.tmatesoft.svn.core.internal.io.dav;

import java.util.HashMap;
import java.util.Map;

import org.tmatesoft.svn.core.internal.io.dav.http.XMLReader;


/**
 * @version 1.1.0
 * @author  TMate Software Ltd.
 */
public class DAVElement {

    private static Map ourProperties = new HashMap();    
    
    public static DAVElement getElement(String namespace, String name) {
        if (namespace == null) {
            namespace = "";
        }
        Map properties = (Map) ourProperties.get(namespace);
        if (properties == null) {
            properties = new HashMap();
            ourProperties.put(namespace, properties);
        }
        name = name.replace(XMLReader.COLON_REPLACEMENT, ':');
        DAVElement property = (DAVElement) properties.get(name);
        if (property == null) {
            property = new DAVElement(namespace, name);
            properties.put(name, property);
        }
        return property;
    }
    
    public static final String SVN_DAV_PROPERTY_NAMESPACE = "http://subversion.tigris.org/xmlns/dav/";
    public static final String SVN_CUSTOM_PROPERTY_NAMESPACE = "http://subversion.tigris.org/xmlns/custom/";
    public static final String SVN_SVN_PROPERTY_NAMESPACE = "http://subversion.tigris.org/xmlns/svn/";
    public static final String SVN_APACHE_PROPERTY_NAMESPACE = "http://apache.org/dav/xmlns";
    
    public static final String SVN_DAV_ERROR_NAMESPACE = "svn:";
    public static final String DAV_NAMESPACE = "DAV:";
    public static final String SVN_NAMESPACE = "svn:";
    
    
    public static final DAVElement MULTISTATUS = getElement(DAV_NAMESPACE, "multistatus");
    public static final DAVElement RESPONSE = getElement(DAV_NAMESPACE, "response");
    public static final DAVElement HREF = getElement(DAV_NAMESPACE, "href");
    public static final DAVElement PROPSTAT = getElement(DAV_NAMESPACE, "propstat");
    public static final DAVElement PROP = getElement(DAV_NAMESPACE, "prop");
    public static final DAVElement STATUS = getElement(DAV_NAMESPACE, "status");
    public static final DAVElement BASELINE = getElement(DAV_NAMESPACE, "baseline");
    public static final DAVElement BASELINE_COLLECTION = getElement(DAV_NAMESPACE, "baseline-collection");
    public static final DAVElement CHECKED_IN = getElement(DAV_NAMESPACE, "checked-in");
    public static final DAVElement COLLECTION = getElement(DAV_NAMESPACE, "collection");
    public static final DAVElement RESOURCE_TYPE = getElement(DAV_NAMESPACE, "resourcetype");
    public static final DAVElement VERSION_CONTROLLED_CONFIGURATION = getElement(DAV_NAMESPACE, "version-controlled-configuration");
    public static final DAVElement VERSION_NAME = getElement(DAV_NAMESPACE, "version-name");
    public static final DAVElement GET_CONTENT_LENGTH = getElement(DAV_NAMESPACE, "getcontentlength");
    public static final DAVElement CREATION_DATE = getElement(DAV_NAMESPACE, "creationdate");
    public static final DAVElement CREATOR_DISPLAY_NAME = getElement(DAV_NAMESPACE, "creator-displayname");    
    public static final DAVElement COMMENT = getElement(DAV_NAMESPACE, "comment");    
    public static final DAVElement DATE = getElement(SVN_NAMESPACE, "date");
    
    public static final DAVElement SUPPORTED_LOCK = getElement(DAV_NAMESPACE, "supportedlock");
    public static final DAVElement LOCK_DISCOVERY = getElement(DAV_NAMESPACE, "lockdiscovery");
    public static final DAVElement LOCK_OWNER = getElement(DAV_NAMESPACE, "owner");
    public static final DAVElement LOCK_TIMEOUT = getElement(DAV_NAMESPACE, "timeout");
    public static final DAVElement LOCK_TOKEN = getElement(DAV_NAMESPACE, "locktoken");

    public static final DAVElement SVN_LOCK = getElement(SVN_NAMESPACE, "lock");
    public static final DAVElement SVN_LOCK_PATH = getElement(SVN_NAMESPACE, "path");
    public static final DAVElement SVN_LOCK_TOKEN = getElement(SVN_NAMESPACE, "token");
    public static final DAVElement SVN_LOCK_COMMENT = getElement(SVN_NAMESPACE, "comment");
    public static final DAVElement SVN_LOCK_OWNER = getElement(SVN_NAMESPACE, "owner");
    public static final DAVElement SVN_LOCK_CREATION_DATE = getElement(SVN_NAMESPACE, "creationdate");
    public static final DAVElement SVN_LOCK_EXPIRATION_DATE = getElement(SVN_NAMESPACE, "expirationdate");
    
    public static final DAVElement BASELINE_RELATIVE_PATH = getElement(SVN_DAV_PROPERTY_NAMESPACE, "baseline-relative-path");
    public static final DAVElement REPOSITORY_UUID = getElement(SVN_DAV_PROPERTY_NAMESPACE, "repository-uuid");
    public static final DAVElement MD5_CHECKSUM = getElement(SVN_DAV_PROPERTY_NAMESPACE, "md5-checksum");

    public static final DAVElement AUTO_VERSION = getElement(DAV_NAMESPACE, "auto-version");
    
    public static final DAVElement[] STARTING_PROPERTIES = {VERSION_CONTROLLED_CONFIGURATION, RESOURCE_TYPE, BASELINE_RELATIVE_PATH, REPOSITORY_UUID};
    public static final DAVElement[] BASELINE_PROPERTIES = {BASELINE_COLLECTION, VERSION_NAME};
    
    private String myPropertyName;
    private String myNamespace;

    private DAVElement(String namespace, String propertyName) {
        myNamespace = namespace;
        myPropertyName = propertyName;
    }

    public String getNamespace() {
        return myNamespace;
    }
    public String getName() {
        return myPropertyName;
    }
    
    public String toString() {
        StringBuffer sb = new StringBuffer();
        sb.append(getNamespace());
        if (!getNamespace().endsWith(":")) {
            sb.append(":");
        }
        sb.append(getName());
        return sb.toString();
    }
    
}
